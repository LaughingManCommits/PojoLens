"""
Plain-text persistent operator console.

Ownership:
  - ConsoleSession / ConsoleJob / LONG_RUNNING_COMMANDS — shared session state;
    imported by tui_console for the TUI surface.
  - DispatchRoute / route_line — pure routing (no I/O); shared by both surfaces so
    branching logic lives in one place.
  - dispatch_line / run_console_session — plain-text REPL surface.
"""
from __future__ import annotations

import shlex
import sys
import threading
import time
from dataclasses import dataclass, field
from typing import Any, Callable

LONG_RUNNING_COMMANDS: frozenset[str] = frozenset({"run", "resume", "retry"})
CONSOLE_BANNER = "pojolens-agents console  (/help for commands, /exit to quit)"
CONSOLE_PROMPT = "pojolens> "

# ── Dispatch route constants ───────────────────────────────────────────────────

ROUTE_EMPTY = "empty"
ROUTE_EXIT = "exit"
ROUTE_HELP = "help"
ROUTE_JOBS = "jobs"
ROUTE_FOCUS = "focus"
ROUTE_CLEAR = "clear"
ROUTE_UNKNOWN_META = "unknown_meta"
ROUTE_PARSE_ERROR = "parse_error"
ROUTE_ARG_ERROR = "arg_error"
ROUTE_UNKNOWN_CMD = "unknown_cmd"
ROUTE_BG_JOB = "bg_job"
ROUTE_INLINE = "inline"


@dataclass
class DispatchRoute:
    """Pure routing result — no I/O side effects.

    Callers own output formatting and execution strategy so each surface
    (plain console, TUI) can render messages in its own style.
    """

    action: str
    job_id: str = ""       # ROUTE_FOCUS: requested job id; empty means last job
    message: str = ""      # ROUTE_*_ERROR / ROUTE_UNKNOWN_*: human-readable detail
    command: str = ""      # ROUTE_BG_JOB / ROUTE_INLINE: subcommand name
    args: Any = field(default=None, repr=False)
    handler: Any = field(default=None, repr=False)  # Callable | None
    json_output: bool = False


# ── Job dataclasses ────────────────────────────────────────────────────────────


@dataclass
class ConsoleJob:
    job_id: str
    command: str
    status: str  # "running" | "completed" | "failed"
    start_time: float
    end_time: float | None = None
    exit_code: int | None = None
    thread: threading.Thread | None = field(default=None, repr=False)

    def elapsed_sec(self) -> float:
        end = self.end_time if self.end_time is not None else time.monotonic()
        return end - self.start_time


class ConsoleSession:
    def __init__(self) -> None:
        self._jobs: dict[str, ConsoleJob] = {}
        self._counter = 0
        self._lock = threading.Lock()

    def add_job(self, command: str) -> ConsoleJob:
        with self._lock:
            self._counter += 1
            job_id = f"job-{self._counter}"
            job = ConsoleJob(
                job_id=job_id,
                command=command,
                status="running",
                start_time=time.monotonic(),
            )
            self._jobs[job_id] = job
            return job

    def all_jobs(self) -> list[ConsoleJob]:
        with self._lock:
            return list(self._jobs.values())

    def get_job(self, job_id: str) -> ConsoleJob | None:
        with self._lock:
            return self._jobs.get(job_id)


# ── Output helpers ─────────────────────────────────────────────────────────────


def _print_console_help() -> None:
    print(
        "\n"
        "Console commands:\n"
        "  /help             Show this help\n"
        "  /jobs             List background jobs\n"
        "  /focus [job-id]   Show status of a background job\n"
        "  /clear            Clear the screen\n"
        "  /exit             Exit the console\n"
        "\n"
        "Orchestrator commands (inline unless marked [bg]):\n"
        "  wizard [goal]     Guided operator flow\n"
        "  validate [plan]   Validate agent/task definitions\n"
        "  plan <goal>       Generate a task plan\n"
        "  run <plan>        Execute task plan          [bg]\n"
        "  resume <run>      Resume partial run         [bg]\n"
        "  retry <run>       Retry failed tasks         [bg]\n"
        "  status <run>      Show run status\n"
        "  inventory         List retained runs\n"
        "  review <run>      Summarize worker changes\n"
        "  diff-run <run>    Show diffs before promote\n"
        "  promote <run>     Apply worker changes to repo\n"
        "  validate-run <r>  Run post-promote validation\n"
        "  evaluate-run <r>  Evaluate run quality\n"
        "  evaluate-corpus   Evaluate corpus quality\n"
        "  cleanup <run>     Remove run artifacts\n"
        "  prune             Prune old runs\n"
        "  summarize-ledger  Print ledger summary\n"
        "  config show       Print resolved config\n"
        "\n"
        "Tip: run/resume/retry are background jobs — issue more commands\n"
        "     while they run. Use /jobs to check status, /focus to inspect.\n"
    )


def _print_jobs(session: ConsoleSession) -> None:
    jobs = session.all_jobs()
    if not jobs:
        print("No background jobs.")
        return
    print(f"{'JOB':<10} {'STATUS':<12} {'ELAPSED':<10} COMMAND")
    print("-" * 62)
    for job in jobs:
        elapsed_str = f"{job.elapsed_sec():.1f}s"
        print(f"{job.job_id:<10} {job.status:<12} {elapsed_str:<10} {job.command[:38]}")


# ── Background job thread ──────────────────────────────────────────────────────


def _run_job_thread(
    job: ConsoleJob,
    handler: Callable[[Any], dict[str, Any]],
    args: Any,
    *,
    json_output: bool,
) -> None:
    from pojo_lens_agents.command_dispatch import _worker_run_exit_code, print_payload
    from pojo_lens_agents.orchestrator_contracts import OrchestratorError, PromotionBlockedError

    try:
        payload = handler(args)
        print_payload(payload, as_json=json_output)
        exit_code = _worker_run_exit_code(payload.get("statusCounts", {}))
        job.status = "completed" if exit_code == 0 else "failed"
        job.exit_code = exit_code
    except PromotionBlockedError as exc:
        print(f"[{job.job_id}] blocked: {exc}", file=sys.stderr, flush=True)
        job.status = "failed"
        job.exit_code = 1
    except OrchestratorError as exc:
        print(f"[{job.job_id}] error: {exc}", file=sys.stderr, flush=True)
        job.status = "failed"
        job.exit_code = 1
    except Exception as exc:
        print(f"[{job.job_id}] unexpected error: {exc}", file=sys.stderr, flush=True)
        job.status = "failed"
        job.exit_code = 1
    finally:
        job.end_time = time.monotonic()
        icon = "ok" if job.status == "completed" else "fail"
        print(
            f"\n[{job.job_id}] {icon} finished in {job.elapsed_sec():.1f}s"
            f" -- {job.command[:40]}",
            flush=True,
        )


# ── Pure routing ───────────────────────────────────────────────────────────────


def route_line(
    line: str,
    parse_args_fn: Callable[[list[str]], Any],
    handlers: dict[str, Callable[[Any], dict[str, Any]]],
) -> DispatchRoute:
    """Route one console input line to an action descriptor.

    No I/O, no side effects. The caller decides how to present each action and
    how to execute background / inline commands.
    """
    stripped = line.strip()
    if not stripped:
        return DispatchRoute(action=ROUTE_EMPTY)

    lower = stripped.lower()

    if lower in {"/exit", "exit", "quit", "/quit"}:
        return DispatchRoute(action=ROUTE_EXIT)
    if lower == "/help":
        return DispatchRoute(action=ROUTE_HELP)
    if lower == "/jobs":
        return DispatchRoute(action=ROUTE_JOBS)
    if lower.startswith("/focus"):
        parts = stripped.split(None, 1)
        job_id = parts[1].strip() if len(parts) > 1 else ""
        return DispatchRoute(action=ROUTE_FOCUS, job_id=job_id)
    if lower == "/clear":
        return DispatchRoute(action=ROUTE_CLEAR)
    if stripped.startswith("/"):
        token = stripped.split()[0]
        return DispatchRoute(action=ROUTE_UNKNOWN_META, message=token)

    try:
        tokens = shlex.split(stripped)
    except ValueError as exc:
        return DispatchRoute(action=ROUTE_PARSE_ERROR, message=str(exc))

    try:
        args = parse_args_fn(tokens)
    except SystemExit:
        return DispatchRoute(action=ROUTE_ARG_ERROR, message="")
    except Exception as exc:
        return DispatchRoute(action=ROUTE_ARG_ERROR, message=str(exc))

    command = str(getattr(args, "command", "") or "")
    handler = handlers.get(command)
    if handler is None:
        return DispatchRoute(action=ROUTE_UNKNOWN_CMD, message=command)

    json_output = bool(getattr(args, "json", False))
    if command in LONG_RUNNING_COMMANDS:
        return DispatchRoute(
            action=ROUTE_BG_JOB,
            command=command,
            args=args,
            handler=handler,
            json_output=json_output,
        )
    return DispatchRoute(
        action=ROUTE_INLINE,
        command=command,
        args=args,
        handler=handler,
        json_output=json_output,
    )


# ── Plain-text dispatch ────────────────────────────────────────────────────────


def dispatch_line(
    line: str,
    session: ConsoleSession,
    parse_args_fn: Callable[[list[str]], Any],
    handlers: dict[str, Callable[[Any], dict[str, Any]]],
) -> bool:
    """Process one console input line. Returns False to exit the session."""
    route = route_line(line, parse_args_fn, handlers)

    if route.action == ROUTE_EMPTY:
        return True
    if route.action == ROUTE_EXIT:
        return False
    if route.action == ROUTE_HELP:
        _print_console_help()
        return True
    if route.action == ROUTE_JOBS:
        _print_jobs(session)
        return True
    if route.action == ROUTE_FOCUS:
        job_id = route.job_id
        if not job_id:
            jobs = session.all_jobs()
            job = jobs[-1] if jobs else None
        else:
            job = session.get_job(job_id)
        if job is None:
            print("No job found." if not job_id else f"Unknown job: {job_id}")
        else:
            print(f"[{job.job_id}] {job.status} {job.elapsed_sec():.1f}s — {job.command}")
        return True
    if route.action == ROUTE_CLEAR:
        print("\033[2J\033[H", end="", flush=True)
        return True
    if route.action == ROUTE_UNKNOWN_META:
        print(f"Unknown console command: {route.message}  (type /help)")
        return True
    if route.action == ROUTE_PARSE_ERROR:
        print(f"Parse error: {route.message}")
        return True
    if route.action == ROUTE_ARG_ERROR:
        if route.message:
            print(f"Error parsing command: {route.message}")
        return True
    if route.action == ROUTE_UNKNOWN_CMD:
        print(f"Unknown command: {route.message!r}  (type /help)")
        return True
    if route.action == ROUTE_BG_JOB:
        job = session.add_job(line.strip())
        thread = threading.Thread(
            target=_run_job_thread,
            args=(job, route.handler, route.args),
            kwargs={"json_output": route.json_output},
            daemon=True,
            name=f"console-{job.job_id}",
        )
        job.thread = thread
        thread.start()
        print(f"[{job.job_id}] {route.command} started in background  (check: /jobs)", flush=True)
        return True
    if route.action == ROUTE_INLINE:
        from pojo_lens_agents.command_dispatch import print_payload
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError, PromotionBlockedError

        try:
            payload = route.handler(route.args)
            print_payload(payload, as_json=route.json_output)
        except PromotionBlockedError as exc:
            print(f"[error] {exc}", file=sys.stderr)
        except OrchestratorError as exc:
            print(f"[error] {exc}", file=sys.stderr)
        except Exception as exc:
            print(f"[error] unexpected: {exc}", file=sys.stderr)
        return True
    return True


# ── REPL loop ──────────────────────────────────────────────────────────────────


def run_console_session(
    args: Any,
    *,
    handlers: dict[str, Callable[[Any], dict[str, Any]]],
    parse_args_fn: Callable[[list[str]], Any],
    banner: str = CONSOLE_BANNER,
    prompt: str = CONSOLE_PROMPT,
) -> int:
    session = ConsoleSession()
    print(banner)
    try:
        while True:
            try:
                line = input(prompt)
            except EOFError:
                print()
                break
            except KeyboardInterrupt:
                print("  (use /exit to quit)")
                continue
            if not dispatch_line(line, session, parse_args_fn, handlers):
                break
    finally:
        running = [j for j in session.all_jobs() if j.status == "running"]
        if running:
            print(f"Waiting for {len(running)} background job(s)...", flush=True)
            for job in running:
                if job.thread is not None:
                    job.thread.join(timeout=30)
    print("Goodbye.")
    return 0
