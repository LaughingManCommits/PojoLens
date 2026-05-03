"""
Textual TUI operator console for pojolens-agents.

Retro cyberpunk aesthetic: neon green on dark, heavy borders,
job queue panel, command history, thread-safe stdout capture.
"""
from __future__ import annotations

import io
import json
import shlex
import sys
import threading
import time
from typing import Any, Callable

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import App, ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.events import Key
    from textual.reactive import reactive
    from textual.screen import ModalScreen
    from textual.widgets import Button, Footer, Header, Input, Label, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    ModalScreen = object  # type: ignore[assignment,misc]

from pojo_lens_agents.command_dispatch import _worker_run_exit_code
from pojo_lens_agents.console import (
    CONSOLE_BANNER,
    LONG_RUNNING_COMMANDS,
    ConsoleJob,
    ConsoleSession,
)
from pojo_lens_agents.orchestrator_contracts import OrchestratorError, PromotionBlockedError


# ── Thread-local stdout router ─────────────────────────────────────────────────

class _ThreadLocalStdout:
    """Replaces sys.stdout; routes each thread's print() to its own sink."""

    _local: threading.local = threading.local()
    _real: Any = None
    _installed: bool = False

    @classmethod
    def install(cls) -> None:
        if cls._installed:
            return
        cls._real = sys.stdout
        sys.stdout = cls()  # type: ignore[assignment]
        cls._installed = True

    def write(self, text: str) -> int:
        sink = getattr(self._local, "sink", None)
        if sink is not None:
            return sink.write(text)
        return self._real.write(text)  # type: ignore[union-attr]

    def flush(self) -> None:
        sink = getattr(self._local, "sink", None)
        (sink if sink is not None else self._real).flush()

    @property
    def encoding(self) -> str:
        return getattr(self._real, "encoding", "utf-8")

    @classmethod
    def set_sink(cls, buf: io.StringIO) -> None:
        cls._local.sink = buf

    @classmethod
    def clear_sink(cls) -> None:
        cls._local.sink = None


# ── History-aware Input ────────────────────────────────────────────────────────

class CommandInput(Input):
    """Input widget that delegates up/down to the app's history navigation."""

    def on_key(self, event: Key) -> None:
        if event.key == "up":
            self.app.history_prev()  # type: ignore[attr-defined]
            event.prevent_default()
            event.stop()
        elif event.key == "down":
            self.app.history_next()  # type: ignore[attr-defined]
            event.prevent_default()
            event.stop()


# ── Exit confirmation modal ────────────────────────────────────────────────────

class _ExitConfirmModal(ModalScreen):  # type: ignore[type-arg]
    """Warns operator about running background jobs before exit."""

    DEFAULT_CSS = """
    _ExitConfirmModal {
        align: center middle;
    }
    #dialog {
        width: 54;
        height: 9;
        border: heavy #ff2244;
        background: #050508;
        padding: 1 2;
    }
    #modal-title {
        color: #ff2244;
        text-style: bold;
        margin-bottom: 1;
    }
    #modal-msg {
        color: #a0ffa0;
        margin-bottom: 1;
    }
    #modal-btns {
        height: auto;
        align: center middle;
    }
    Button {
        margin: 0 1;
    }
    """

    def __init__(self, running_count: int) -> None:
        super().__init__()
        self._count = running_count

    def compose(self) -> ComposeResult:
        with Container(id="dialog"):
            yield Static("!! JOBS STILL RUNNING !!", id="modal-title")
            yield Static(
                f"{self._count} job(s) active. Exit and abandon?",
                id="modal-msg",
            )
            with Horizontal(id="modal-btns"):
                yield Button("EXIT", variant="error", id="confirm-yes")
                yield Button("STAY", variant="primary", id="confirm-no")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        self.dismiss(event.button.id == "confirm-yes")


# ── Main app ───────────────────────────────────────────────────────────────────

class ConsoleApp(App):
    """Retro cyberpunk operator console."""

    CSS = """
    ConsoleApp {
        background: #050508;
        color: #a0ffa0;
    }

    Header {
        background: #08080f;
        color: #00ff41;
        border-bottom: heavy #00ff41 30%;
    }

    #body {
        height: 1fr;
        layout: horizontal;
    }

    RichLog {
        width: 1fr;
        background: #060610;
        border-right: heavy #00ff41 25%;
        scrollbar-color: #00ff41 25%;
        scrollbar-background: #050508;
        padding: 0 1;
    }

    #jobs-panel {
        width: 34;
        background: #050510;
        padding: 1;
    }

    #jobs-title {
        text-style: bold;
        color: #00e5ff;
        text-align: center;
        margin-bottom: 1;
        border-bottom: solid #00e5ff 30%;
    }

    #jobs-empty {
        color: #2a4a3a;
        text-align: center;
        margin-top: 2;
        text-style: italic;
    }

    #jobs-list {
        color: #a0ffc0;
    }

    #sys-bar {
        height: 1;
        background: #080810;
        color: #1a4a2a;
        padding: 0 2;
    }

    #input-bar {
        height: 3;
        padding: 0 1;
        background: #04040c;
        border-top: heavy #00ff41 40%;
    }

    CommandInput {
        width: 1fr;
        background: #050510;
        color: #00ff41;
        border: solid #00ff41 35%;
    }

    CommandInput:focus {
        border: solid #00e5ff;
        color: #00e5ff;
        background: #05051a;
    }

    CommandInput:disabled {
        background: #080808;
        color: #1a3a1a;
        border: solid #1a3a1a;
        opacity: 70%;
    }

    #run-btn {
        min-width: 8;
        margin-left: 1;
        background: #001800;
        color: #00ff41;
        border: solid #00ff41 60%;
    }

    #run-btn:hover {
        background: #002800;
    }

    #run-btn:disabled {
        background: #080808;
        color: #1a3a1a;
        border: solid #1a3a1a;
    }

    Footer {
        background: #04040c;
        color: #2a5a3a;
        border-top: solid #00ff41 20%;
    }
    """

    BINDINGS = [
        Binding("ctrl+l", "clear_output", "CLR", show=True),
        Binding("ctrl+j", "toggle_jobs", "JOBS", show=True),
        Binding("escape", "focus_input", "FOCUS", show=False),
        Binding("q", "request_quit", "QUIT", show=True),
    ]

    show_jobs: reactive[bool] = reactive(True)

    def __init__(
        self,
        handlers: dict[str, Callable[[Any], dict[str, Any]]],
        parse_args_fn: Callable[[list[str]], Any],
    ) -> None:
        super().__init__()
        self._handlers = handlers
        self._parse_args_fn = parse_args_fn
        self._session = ConsoleSession()
        self._history: list[str] = []
        self._history_pos: int = -1
        self._busy = False
        _ThreadLocalStdout.install()

    # ── Composition ────────────────────────────────────────────────────────────

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        with Horizontal(id="body"):
            yield RichLog(id="output", markup=True, auto_scroll=True, highlight=False)
            with Container(id="jobs-panel"):
                yield Label("// JOB QUEUE //", id="jobs-title")
                yield Static("[ IDLE ]", id="jobs-empty")
                yield Static("", id="jobs-list")
        yield Static("", id="sys-bar")
        with Horizontal(id="input-bar"):
            yield CommandInput(
                placeholder="enter command... (/help for manual)",
                id="cmd-input",
            )
            yield Button("TX", variant="primary", id="run-btn")
        yield Footer()

    def on_mount(self) -> None:
        self.title = "POJOLENS // OPERATOR CONSOLE"
        self.sub_title = "SESSION ACTIVE"
        self.query_one("#cmd-input").focus()
        self._write_banner()
        self.set_interval(1.0, self._tick)

    # ── Reactive watchers ──────────────────────────────────────────────────────

    def watch_show_jobs(self, show: bool) -> None:
        self.query_one("#jobs-panel").display = show

    # ── Banner ─────────────────────────────────────────────────────────────────

    def _write_banner(self) -> None:
        log = self.query_one("#output", RichLog)
        log.write(
            "[bold #00ff41]"
            "╔══════════════════════════════════════════╗\n"
            "║  POJOLENS OPERATOR CONSOLE  //  v-OMEGA  ║\n"
            "╚══════════════════════════════════════════╝"
            "[/bold #00ff41]"
        )
        log.write(
            "[dim #00e5ff]  TYPE [bold]/help[/bold] FOR COMMANDS  //  "
            "[bold]/exit[/bold] TO DISCONNECT[/dim #00e5ff]\n"
        )

    # ── Tick / sys-bar ─────────────────────────────────────────────────────────

    def _tick(self) -> None:
        self._refresh_jobs_panel()
        running = sum(1 for j in self._session.all_jobs() if j.status == "running")
        total = len(self._session.all_jobs())
        status = (
            f"[#00ff41]JOBS: {total}  ACTIVE: {running}[/]"
            if total else "[dim #2a4a2a]STANDBY[/]"
        )
        self.query_one("#sys-bar", Static).update(status)

    # ── Jobs panel ─────────────────────────────────────────────────────────────

    def _refresh_jobs_panel(self) -> None:
        jobs = self._session.all_jobs()
        empty = self.query_one("#jobs-empty", Static)
        jobs_widget = self.query_one("#jobs-list", Static)

        if not jobs:
            empty.display = True
            jobs_widget.update("")
            return

        empty.display = False
        lines: list[str] = []
        for job in reversed(jobs):
            elapsed = f"{job.elapsed_sec():.1f}s"
            c = {"running": "#ffaa00", "completed": "#00ff41", "failed": "#ff2244"}.get(
                job.status, "#a0ffa0"
            )
            badge = {"running": ">>", "completed": "OK", "failed": "!!"}.get(job.status, "??")
            cmd_short = job.command[:22] + ("..." if len(job.command) > 22 else "")
            exit_part = f"  rc={job.exit_code}" if job.exit_code is not None else ""
            lines.append(
                f"[bold {c}][{badge}] {job.job_id}[/bold {c}]\n"
                f"  [{c}]{job.status}[/{c}]  {elapsed}{exit_part}\n"
                f"  [dim]{cmd_short}[/dim]"
            )
        jobs_widget.update("\n\n".join(lines))

    # ── History ────────────────────────────────────────────────────────────────

    def history_prev(self) -> None:
        if not self._history:
            return
        if self._history_pos == -1:
            self._history_pos = len(self._history) - 1
        elif self._history_pos > 0:
            self._history_pos -= 1
        self.query_one("#cmd-input", CommandInput).value = self._history[self._history_pos]

    def history_next(self) -> None:
        if self._history_pos == -1:
            return
        self._history_pos += 1
        cmd = self.query_one("#cmd-input", CommandInput)
        if self._history_pos >= len(self._history):
            self._history_pos = -1
            cmd.value = ""
        else:
            cmd.value = self._history[self._history_pos]

    # ── UI events ──────────────────────────────────────────────────────────────

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "run-btn":
            self._submit()

    def on_input_submitted(self, event: Input.Submitted) -> None:
        if event.input.id == "cmd-input":
            self._submit()

    # ── Actions ────────────────────────────────────────────────────────────────

    def action_clear_output(self) -> None:
        self.query_one("#output", RichLog).clear()
        self._write_banner()

    def action_toggle_jobs(self) -> None:
        self.show_jobs = not self.show_jobs

    def action_focus_input(self) -> None:
        self.query_one("#cmd-input").focus()

    async def action_request_quit(self) -> None:
        await self._exit_flow()

    # ── Output helper ──────────────────────────────────────────────────────────

    def _write(self, text: str) -> None:
        self.query_one("#output", RichLog).write(text)

    # ── Submit / dispatch ──────────────────────────────────────────────────────

    def _submit(self) -> None:
        if self._busy:
            self._write("[#ffaa00]>> BUSY — wait for inline command or use /jobs[/]")
            return
        cmd_input = self.query_one("#cmd-input", CommandInput)
        line = cmd_input.value
        cmd_input.value = ""
        if not line.strip():
            return
        self._history.append(line)
        self._history_pos = -1
        self._write(f"\n[bold #00ff41]>>[/bold #00ff41] [#a0ffc0]{line}[/]")
        self._dispatch(line)

    def _dispatch(self, line: str) -> None:
        stripped = line.strip()
        lower = stripped.lower()

        # ── Meta-commands ──────────────────────────────────────────────────────
        if lower in {"/exit", "exit", "quit", "/quit"}:
            self.run_worker(self._exit_flow())
            return

        if lower == "/help":
            self._show_help()
            return

        if lower == "/jobs":
            self._cmd_jobs()
            return

        if lower.startswith("/focus"):
            parts = stripped.split(None, 1)
            job_id = parts[1].strip() if len(parts) > 1 else ""
            jobs = self._session.all_jobs()
            job = (jobs[-1] if jobs else None) if not job_id else self._session.get_job(job_id)
            if job is None:
                self._write("[#ffaa00]>> no job found[/]" if not job_id else f"[#ffaa00]>> unknown: {job_id}[/]")
            else:
                c = {"running": "#ffaa00", "completed": "#00ff41", "failed": "#ff2244"}.get(
                    job.status, "#a0ffa0"
                )
                self._write(
                    f"[bold {c}][{job.job_id}][/] {job.status}  {job.elapsed_sec():.1f}s\n"
                    f"  {job.command}"
                )
            return

        if lower == "/clear":
            self.action_clear_output()
            return

        if stripped.startswith("/"):
            token = stripped.split()[0]
            self._write(f"[#ffaa00]>> unknown command: {token}  (type /help)[/]")
            return

        # ── Orchestrator commands ──────────────────────────────────────────────
        try:
            tokens = shlex.split(stripped)
        except ValueError as exc:
            self._write(f"[#ff2244]>> parse error: {exc}[/]")
            return

        try:
            args = self._parse_args_fn(tokens)
        except SystemExit:
            return
        except Exception as exc:
            self._write(f"[#ff2244]>> error parsing: {exc}[/]")
            return

        command = str(getattr(args, "command", "") or "")
        handler = self._handlers.get(command)
        if handler is None:
            self._write(f"[#ffaa00]>> unknown command: {command!r}  (type /help)[/]")
            return

        json_output = bool(getattr(args, "json", False))

        if command in LONG_RUNNING_COMMANDS:
            job = self._session.add_job(stripped)
            self._write(f"[#ffaa00]>> [{job.job_id}] queued :: {command}[/]  [dim](/jobs to monitor)[/]")
            self.run_worker(
                lambda j=job, h=handler, a=args, jo=json_output: self._bg_job_worker(j, h, a, jo),
                thread=True,
                name=job.job_id,
            )
        else:
            self._set_busy(True)
            self.run_worker(
                lambda h=handler, a=args, jo=json_output: self._inline_worker(h, a, jo),
                thread=True,
                name=f"inline-{command}",
            )

    # ── Workers ────────────────────────────────────────────────────────────────

    @staticmethod
    def _capture(fn: Callable[[], Any]) -> tuple[str, Any]:
        buf = io.StringIO()
        _ThreadLocalStdout.set_sink(buf)
        try:
            result = fn()
        finally:
            _ThreadLocalStdout.clear_sink()
        return buf.getvalue(), result

    @staticmethod
    def _payload_text(payload: dict[str, Any], as_json: bool) -> str:
        console_text = payload.get("_consoleText")
        if not as_json and isinstance(console_text, str) and console_text.strip():
            return console_text
        public = {k: v for k, v in payload.items() if k != "_consoleText"}
        return json.dumps(public, indent=2)

    def _inline_worker(self, handler: Callable, args: Any, json_output: bool) -> None:
        try:
            captured, payload = self._capture(lambda: handler(args))
            text = captured.strip() or self._payload_text(payload, json_output)
            self.call_from_thread(self._write, text if text.strip() else "[dim](no output)[/]")
        except PromotionBlockedError as exc:
            self.call_from_thread(self._write, f"[#ff2244]>> BLOCKED :: {exc}[/]")
        except OrchestratorError as exc:
            self.call_from_thread(self._write, f"[#ff2244]>> ERROR :: {exc}[/]")
        except Exception as exc:
            self.call_from_thread(self._write, f"[#ff2244]>> UNEXPECTED :: {exc}[/]")
        finally:
            self.call_from_thread(self._set_busy, False)

    def _bg_job_worker(
        self, job: ConsoleJob, handler: Callable, args: Any, json_output: bool
    ) -> None:
        try:
            captured, payload = self._capture(lambda: handler(args))
            text = captured.strip() or self._payload_text(payload, json_output)
            exit_code = _worker_run_exit_code(payload.get("statusCounts", {}))
            job.status = "completed" if exit_code == 0 else "failed"
            job.exit_code = exit_code
            if text.strip():
                self.call_from_thread(self._write, text)
        except PromotionBlockedError as exc:
            self.call_from_thread(self._write, f"[#ff2244]>> [{job.job_id}] BLOCKED :: {exc}[/]")
            job.status = "failed"
            job.exit_code = 1
        except OrchestratorError as exc:
            self.call_from_thread(self._write, f"[#ff2244]>> [{job.job_id}] ERROR :: {exc}[/]")
            job.status = "failed"
            job.exit_code = 1
        except Exception as exc:
            self.call_from_thread(self._write, f"[#ff2244]>> [{job.job_id}] CRASH :: {exc}[/]")
            job.status = "failed"
            job.exit_code = 1
        finally:
            job.end_time = time.monotonic()
            ok = job.status == "completed"
            c, badge = ("#00ff41", "OK") if ok else ("#ff2244", "!!")
            msg = (
                f"[bold {c}]>>[{badge}][/bold {c}] "
                f"[{c}][{job.job_id}][/]  {job.elapsed_sec():.1f}s  "
                f"[dim]{job.command[:40]}[/dim]"
            )
            self.call_from_thread(self._write, msg)
            self.call_from_thread(self._refresh_jobs_panel)

    def _set_busy(self, busy: bool) -> None:
        self._busy = busy
        self.query_one("#run-btn", Button).disabled = busy
        self.query_one("#cmd-input", CommandInput).disabled = busy
        if not busy:
            self.query_one("#cmd-input").focus()

    # ── /jobs inline display ───────────────────────────────────────────────────

    def _cmd_jobs(self) -> None:
        jobs = self._session.all_jobs()
        if not jobs:
            self._write("[dim]>> no background jobs[/]")
            return
        self._write("[bold #00e5ff]JOB       STATUS       ELAPSED    COMMAND[/]")
        self._write("[dim #2a4a3a]" + "-" * 54 + "[/]")
        for job in jobs:
            c = {"running": "#ffaa00", "completed": "#00ff41", "failed": "#ff2244"}.get(
                job.status, "#a0ffa0"
            )
            self._write(
                f"[{c}]{job.job_id:<10} {job.status:<12} {job.elapsed_sec():.1f}s[/{c}]"
                f"  [dim]{job.command[:32]}[/]"
            )

    # ── Help ───────────────────────────────────────────────────────────────────

    def _show_help(self) -> None:
        self._write(
            "\n[bold #00e5ff]╔══ CONSOLE COMMANDS ══╗[/bold #00e5ff]\n"
            "  [bold #00ff41]/help[/]             this manual\n"
            "  [bold #00ff41]/jobs[/]             list background jobs\n"
            "  [bold #00ff41]/focus[/] [dim][job-id][/]   inspect a job\n"
            "  [bold #00ff41]/clear[/]            clear output buffer\n"
            "  [bold #00ff41]/exit[/]             disconnect session\n"
            "\n[bold #00e5ff]╔══ ORCHESTRATOR COMMANDS ══╗[/bold #00e5ff]  "
            "[#ffaa00][bg][/] = background job\n"
            "  [#a0ffc0]wizard[/] [goal]          guided operator flow\n"
            "  [#a0ffc0]validate[/] [plan]         validate definitions\n"
            "  [#a0ffc0]plan[/] <goal>             generate task plan\n"
            "  [#a0ffc0]run[/] <plan>              execute plan        [#ffaa00][bg][/]\n"
            "  [#a0ffc0]resume[/] <run>            resume partial      [#ffaa00][bg][/]\n"
            "  [#a0ffc0]retry[/] <run>             retry failed        [#ffaa00][bg][/]\n"
            "  [#a0ffc0]status[/] <run>            show run status\n"
            "  [#a0ffc0]inventory[/]               list runs\n"
            "  [#a0ffc0]review[/] <run>            summarize changes\n"
            "  [#a0ffc0]diff-run[/] <run>          show diffs\n"
            "  [#a0ffc0]promote[/] <run>           apply to repo\n"
            "  [#a0ffc0]validate-run[/] <run>      post-promote check\n"
            "  [#a0ffc0]evaluate-run[/] <run>      quality score\n"
            "  [#a0ffc0]evaluate-corpus[/]         corpus quality score\n"
            "  [#a0ffc0]cleanup[/] <run>           remove artifacts\n"
            "  [#a0ffc0]prune[/]                   prune old runs\n"
            "  [#a0ffc0]summarize-ledger[/]         print ledger summary\n"
            "  [#a0ffc0]config show[/]             print config\n"
            "\n[dim #2a5a3a]CTRL+J :: toggle job queue  /  "
            "UP/DOWN :: command history  /  "
            "CTRL+L :: clear[/]\n"
        )

    # ── Exit flow (async — shows modal if jobs active) ─────────────────────────

    async def _exit_flow(self) -> None:
        running = [j for j in self._session.all_jobs() if j.status == "running"]
        if running:
            confirmed = await self.push_screen_wait(_ExitConfirmModal(len(running)))
            if not confirmed:
                return
        self.exit(0)


# ── Entry point ────────────────────────────────────────────────────────────────

def run_tui_console(
    args: Any,
    *,
    handlers: dict[str, Callable[[Any], dict[str, Any]]],
    parse_args_fn: Callable[[list[str]], Any],
) -> int:
    if TEXTUAL_IMPORT_ERROR is not None:
        raise RuntimeError(
            "TUI console requires textual. Install 'pojolens-agents[tui]'."
        ) from TEXTUAL_IMPORT_ERROR
    app = ConsoleApp(handlers=handlers, parse_args_fn=parse_args_fn)
    result = app.run()
    return result if isinstance(result, int) else 0
