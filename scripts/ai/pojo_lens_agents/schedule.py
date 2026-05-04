from __future__ import annotations

import fnmatch
import json
import os
import signal
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

_APSCHEDULER_IMPORT_ERROR: Exception | None = None
_WATCHDOG_IMPORT_ERROR: Exception | None = None

try:
    from apscheduler.schedulers.blocking import BlockingScheduler
    from apscheduler.triggers.cron import CronTrigger
except ImportError as exc:
    _APSCHEDULER_IMPORT_ERROR = exc
    BlockingScheduler = None  # type: ignore[assignment,misc]
    CronTrigger = None  # type: ignore[assignment,misc]

try:
    import watchdog.events
    import watchdog.observers
except ImportError as exc:
    _WATCHDOG_IMPORT_ERROR = exc


class ScheduleError(RuntimeError):
    pass


# ── path helpers ───────────────────────────────────────────────────────────────

def _pid_file(runtime_root: str) -> Path:
    return Path(runtime_root) / "schedule.pid"


def _log_file(runtime_root: str) -> Path:
    return Path(runtime_root) / "schedule.log"


def _status_file(runtime_root: str) -> Path:
    return Path(runtime_root) / "schedule-status.json"


# ── low-level I/O ──────────────────────────────────────────────────────────────

def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _append_log(log_path: Path, message: str) -> None:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8") as f:
        f.write(f"{_now_iso()} {message}\n")


def _write_status(status_path: Path, data: dict[str, Any]) -> None:
    status_path.parent.mkdir(parents=True, exist_ok=True)
    tmp = status_path.with_suffix(".tmp")
    try:
        tmp.write_text(json.dumps(data), encoding="utf-8")
        os.replace(tmp, status_path)
    except Exception:
        tmp.unlink(missing_ok=True)
        raise


def _is_process_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
        return True
    except (ProcessLookupError, PermissionError):
        return False
    except OSError:
        return False


# ── dep guards ─────────────────────────────────────────────────────────────────

def _guard_cron() -> None:
    if _APSCHEDULER_IMPORT_ERROR is not None:
        raise ScheduleError(
            f"apscheduler is required for --cron. "
            f"Install with: pip install 'pojolens-agents[schedule]'. "
            f"Error: {_APSCHEDULER_IMPORT_ERROR}"
        )


def _guard_watchdog() -> None:
    if _WATCHDOG_IMPORT_ERROR is not None:
        raise ScheduleError(
            f"watchdog is required for --on-change. "
            f"Install with: pip install 'pojolens-agents[schedule]'. "
            f"Error: {_WATCHDOG_IMPORT_ERROR}"
        )


# ── run trigger ────────────────────────────────────────────────────────────────

def _find_orchestrator_script() -> Path:
    for parent in Path(__file__).resolve().parents:
        candidate = parent / "scripts" / "ai" / "claude-orchestrator.py"
        if candidate.is_file():
            return candidate
    raise ScheduleError("Cannot locate claude-orchestrator.py in repo tree")


def _trigger_run(plan: str, runtime_root: str, extra_args: list[str]) -> dict[str, Any]:
    script = _find_orchestrator_script()
    cmd = [sys.executable, str(script), "run", plan, "--runtime-root", runtime_root, *extra_args]
    result = subprocess.run(cmd, capture_output=True, text=True)
    return {
        "returncode": result.returncode,
        "stdout": result.stdout.strip(),
        "stderr": result.stderr.strip(),
    }


def _build_extra_args(args: Any) -> list[str]:
    extra: list[str] = []
    if getattr(args, "agents", ""):
        extra += ["--agents", str(args.agents)]
    if getattr(args, "max_parallel", None) is not None:
        extra += ["--max-parallel", str(args.max_parallel)]
    if getattr(args, "claude_bin", ""):
        extra += ["--provider-bin", str(args.claude_bin)]
    return extra


# ── cron loop ──────────────────────────────────────────────────────────────────

def _run_cron_loop(
    plan: str,
    cron_expr: str,
    runtime_root: str,
    log_path: Path,
    status_path: Path,
    extra_args: list[str],
) -> None:
    _guard_cron()
    try:
        trigger = CronTrigger.from_crontab(cron_expr)
    except Exception as exc:
        raise ScheduleError(f"Invalid cron expression {cron_expr!r}: {exc}") from exc

    scheduler = BlockingScheduler()

    def _fire() -> None:
        ts = _now_iso()
        _append_log(log_path, f"TRIGGER plan={plan}")
        _write_status(status_path, {"status": "running", "lastTrigger": ts, "plan": plan})
        result = _trigger_run(plan, runtime_root, extra_args)
        outcome = "completed" if result["returncode"] == 0 else "failed"
        _append_log(log_path, f"RUN {outcome} rc={result['returncode']}")
        _write_status(status_path, {
            "status": "idle",
            "lastTrigger": ts,
            "lastOutcome": outcome,
            "lastReturncode": result["returncode"],
            "plan": plan,
        })

    scheduler.add_job(_fire, trigger)
    scheduler.start()  # blocks until KeyboardInterrupt / SIGTERM


# ── file-watch loop ────────────────────────────────────────────────────────────

_WATCH_DEBOUNCE_SEC = 5.0


def _pattern_matches(path: str, pattern: str) -> bool:
    p = Path(path)
    return fnmatch.fnmatch(path, pattern) or fnmatch.fnmatch(p.name, pattern)


def _run_watch_loop(
    plan: str,
    pattern: str,
    runtime_root: str,
    log_path: Path,
    status_path: Path,
    extra_args: list[str],
) -> None:
    _guard_watchdog()
    import watchdog.events
    import watchdog.observers

    last_trigger: list[float] = [0.0]  # mutable cell — avoids nonlocal in nested class

    class _Handler(watchdog.events.FileSystemEventHandler):
        def on_any_event(self, event: watchdog.events.FileSystemEvent) -> None:
            if event.is_directory:
                return
            src = str(getattr(event, "src_path", ""))
            if not _pattern_matches(src, pattern):
                return
            now = time.monotonic()
            if now - last_trigger[0] < _WATCH_DEBOUNCE_SEC:
                return
            last_trigger[0] = now
            ts = _now_iso()
            _append_log(log_path, f"TRIGGER file={src}")
            _write_status(status_path, {"status": "running", "lastTrigger": ts, "plan": plan})
            result = _trigger_run(plan, runtime_root, extra_args)
            outcome = "completed" if result["returncode"] == 0 else "failed"
            _append_log(log_path, f"RUN {outcome} rc={result['returncode']}")
            _write_status(status_path, {
                "status": "idle",
                "lastTrigger": ts,
                "lastOutcome": outcome,
                "lastReturncode": result["returncode"],
                "plan": plan,
            })

    try:
        watch_root = str(_find_orchestrator_script().parents[2])
    except ScheduleError:
        watch_root = str(Path.cwd())

    observer = watchdog.observers.Observer()
    observer.schedule(_Handler(), watch_root, recursive=True)
    observer.start()
    try:
        while True:
            time.sleep(1)
    except (KeyboardInterrupt, SystemExit):
        observer.stop()
    observer.join()


# ── public API ─────────────────────────────────────────────────────────────────

def start_schedule(args: Any) -> dict[str, Any]:
    runtime_root = str(getattr(args, "runtime_root", ".claude-orchestrator"))
    plan = str(getattr(args, "task_plan", "") or "")
    cron_expr = str(getattr(args, "cron", "") or "")
    watch_pattern = str(getattr(args, "on_change", "") or "")
    once = bool(getattr(args, "once", False))

    if not plan:
        raise ScheduleError("Plan path is required")
    if not any([cron_expr, watch_pattern, once]):
        raise ScheduleError("Specify --cron, --on-change, or --once")

    pid_path = _pid_file(runtime_root)
    log_path = _log_file(runtime_root)
    status_path = _status_file(runtime_root)

    if pid_path.exists():
        try:
            existing_pid = int(pid_path.read_text(encoding="utf-8").strip())
            if _is_process_alive(existing_pid):
                raise ScheduleError(
                    f"Scheduler already running (PID {existing_pid}). "
                    "Use 'schedule stop' first."
                )
        except ValueError:
            pass
        pid_path.unlink(missing_ok=True)

    pid = os.getpid()
    pid_path.parent.mkdir(parents=True, exist_ok=True)
    pid_path.write_text(str(pid), encoding="utf-8")
    extra_args = _build_extra_args(args)
    _append_log(log_path, f"START pid={pid} plan={plan}")

    try:
        if once:
            ts = _now_iso()
            _append_log(log_path, f"TRIGGER (once) plan={plan}")
            result = _trigger_run(plan, runtime_root, extra_args)
            outcome = "completed" if result["returncode"] == 0 else "failed"
            _append_log(log_path, f"RUN {outcome} rc={result['returncode']}")
            return {"status": outcome, "plan": plan, "returncode": result["returncode"]}
        elif cron_expr:
            _append_log(log_path, f"CRON {cron_expr!r}")
            _write_status(status_path, {"status": "idle", "pid": pid, "plan": plan, "cron": cron_expr})
            _run_cron_loop(plan, cron_expr, runtime_root, log_path, status_path, extra_args)
        else:
            _append_log(log_path, f"WATCH {watch_pattern!r}")
            _write_status(status_path, {"status": "idle", "pid": pid, "plan": plan, "watch": watch_pattern})
            _run_watch_loop(plan, watch_pattern, runtime_root, log_path, status_path, extra_args)
    finally:
        pid_path.unlink(missing_ok=True)
        _append_log(log_path, "STOP")

    return {"status": "stopped", "plan": plan}


def stop_schedule(runtime_root: str) -> dict[str, Any]:
    pid_path = _pid_file(runtime_root)
    if not pid_path.exists():
        return {"status": "not-running", "message": "No schedule.pid found"}

    try:
        pid = int(pid_path.read_text(encoding="utf-8").strip())
    except (ValueError, OSError) as exc:
        return {"status": "error", "message": f"Cannot read PID file: {exc}"}

    if not _is_process_alive(pid):
        pid_path.unlink(missing_ok=True)
        return {"status": "not-running", "message": f"PID {pid} is not alive; PID file removed"}

    try:
        os.kill(pid, signal.SIGTERM)
    except OSError as exc:
        return {"status": "error", "message": f"Cannot signal PID {pid}: {exc}"}

    pid_path.unlink(missing_ok=True)
    return {"status": "stopped", "pid": pid}


def get_schedule_status(runtime_root: str) -> dict[str, Any]:
    pid_path = _pid_file(runtime_root)
    status_path = _status_file(runtime_root)

    running = False
    pid: int | None = None
    if pid_path.exists():
        try:
            pid = int(pid_path.read_text(encoding="utf-8").strip())
            running = _is_process_alive(pid)
        except (ValueError, OSError):
            pass

    last_status: dict[str, Any] = {}
    if status_path.exists():
        try:
            last_status = json.loads(status_path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            pass

    return {
        "running": running,
        "pid": pid if running else None,
        **{k: v for k, v in last_status.items() if k != "pid"},
    }
