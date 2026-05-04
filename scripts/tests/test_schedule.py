from __future__ import annotations

import json
import os
import signal
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, call, patch

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from ai.pojo_lens_agents.schedule import (
    _APSCHEDULER_IMPORT_ERROR,
    _WATCHDOG_IMPORT_ERROR,
    _append_log,
    _build_extra_args,
    _is_process_alive,
    _pattern_matches,
    _pid_file,
    _status_file,
    _trigger_run,
    _write_status,
    ScheduleError,
    get_schedule_status,
    stop_schedule,
)


# ── helpers ────────────────────────────────────────────────────────────────────

def _tmpdir() -> tempfile.TemporaryDirectory:
    return tempfile.TemporaryDirectory()


def _args(**kwargs) -> SimpleNamespace:
    defaults = {
        "task_plan": "ai/orchestrator/tasks/my-plan.json",
        "runtime_root": ".claude-orchestrator",
        "agents": "",
        "max_parallel": None,
        "claude_bin": "",
        "cron": "",
        "on_change": "",
        "once": False,
    }
    defaults.update(kwargs)
    return SimpleNamespace(**defaults)


# ── path helpers ───────────────────────────────────────────────────────────────

class TestPathHelpers(unittest.TestCase):
    def test_pid_file_in_runtime_root(self):
        assert _pid_file("/tmp/rt") == Path("/tmp/rt") / "schedule.pid"

    def test_status_file_in_runtime_root(self):
        assert _status_file("/tmp/rt") == Path("/tmp/rt") / "schedule-status.json"


# ── atomic status write ────────────────────────────────────────────────────────

class TestWriteStatus(unittest.TestCase):
    def test_writes_valid_json(self):
        with _tmpdir() as td:
            path = Path(td) / "schedule-status.json"
            _write_status(path, {"status": "idle", "plan": "p.json"})

            data = json.loads(path.read_text())
            assert data["status"] == "idle"
            assert data["plan"] == "p.json"

    def test_atomic_no_tmp_left(self):
        with _tmpdir() as td:
            path = Path(td) / "schedule-status.json"
            _write_status(path, {"x": 1})
            tmp = path.with_suffix(".tmp")
            assert not tmp.exists()


# ── append log ────────────────────────────────────────────────────────────────

class TestAppendLog(unittest.TestCase):
    def test_appends_multiple_lines(self):
        with _tmpdir() as td:
            path = Path(td) / "schedule.log"
            _append_log(path, "START pid=1")
            _append_log(path, "STOP")

            lines = path.read_text().splitlines()
            assert len(lines) == 2
            assert "START pid=1" in lines[0]
            assert "STOP" in lines[1]

    def test_creates_parent_dirs(self):
        with _tmpdir() as td:
            path = Path(td) / "sub" / "schedule.log"
            _append_log(path, "MSG")
            assert path.exists()


# ── pattern matching ──────────────────────────────────────────────────────────

class TestPatternMatching(unittest.TestCase):
    def test_full_path_glob(self):
        assert _pattern_matches("/repo/src/Foo.java", "*.java")

    def test_nested_glob(self):
        assert _pattern_matches("/repo/src/com/example/Foo.java", "*.java")

    def test_no_match(self):
        assert not _pattern_matches("/repo/src/Foo.py", "*.java")

    def test_prefix_path_glob(self):
        assert _pattern_matches("/repo/src/Foo.java", "/repo/src/*.java")

    def test_name_only_match(self):
        assert _pattern_matches("/some/deep/path/Config.java", "Config.java")


# ── build extra args ──────────────────────────────────────────────────────────

class TestBuildExtraArgs(unittest.TestCase):
    def test_all_options(self):
        args = _args(agents="agents.json", max_parallel=4, claude_bin="claude")
        result = _build_extra_args(args)
        assert "--agents" in result
        assert "agents.json" in result
        assert "--max-parallel" in result
        assert "4" in result
        assert "--provider-bin" in result
        assert "claude" in result

    def test_empty_options(self):
        args = _args(agents="", max_parallel=None, claude_bin="")
        assert _build_extra_args(args) == []


# ── dep guards ────────────────────────────────────────────────────────────────

class TestDepGuards(unittest.TestCase):
    @unittest.skipUnless(_APSCHEDULER_IMPORT_ERROR is not None, "apscheduler installed")
    def test_cron_guard_raises_when_missing(self):
        from ai.pojo_lens_agents.schedule import _guard_cron
        with self.assertRaises(ScheduleError) as ctx:
            _guard_cron()
        assert "apscheduler" in str(ctx.exception)
        assert "pojolens-agents[schedule]" in str(ctx.exception)

    @unittest.skipUnless(_WATCHDOG_IMPORT_ERROR is not None, "watchdog installed")
    def test_watchdog_guard_raises_when_missing(self):
        from ai.pojo_lens_agents.schedule import _guard_watchdog
        with self.assertRaises(ScheduleError) as ctx:
            _guard_watchdog()
        assert "watchdog" in str(ctx.exception)
        assert "pojolens-agents[schedule]" in str(ctx.exception)


# ── stop ──────────────────────────────────────────────────────────────────────

class TestStopSchedule(unittest.TestCase):
    def test_stop_when_no_pid_file(self):
        with _tmpdir() as td:
            result = stop_schedule(td)
            assert result["status"] == "not-running"

    def test_stop_when_pid_not_alive(self):
        with _tmpdir() as td:
            pid_path = _pid_file(td)
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text("99999999")  # very likely not alive
            with patch("ai.pojo_lens_agents.schedule._is_process_alive", return_value=False):
                result = stop_schedule(td)
            assert result["status"] == "not-running"
            assert not pid_path.exists()

    def test_stop_sends_sigterm_and_removes_pid(self):
        with _tmpdir() as td:
            pid_path = _pid_file(td)
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text("12345")
            with patch("ai.pojo_lens_agents.schedule._is_process_alive", return_value=True), \
                 patch("os.kill") as mock_kill:
                result = stop_schedule(td)
            mock_kill.assert_called_once_with(12345, signal.SIGTERM)
            assert result["status"] == "stopped"
            assert result["pid"] == 12345
            assert not pid_path.exists()

    def test_stop_returns_error_on_invalid_pid_file(self):
        with _tmpdir() as td:
            pid_path = _pid_file(td)
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text("not-a-number")
            result = stop_schedule(td)
            assert result["status"] == "error"
            assert "Cannot read" in result["message"]


# ── status ────────────────────────────────────────────────────────────────────

class TestGetScheduleStatus(unittest.TestCase):
    def test_not_running_no_files(self):
        with _tmpdir() as td:
            result = get_schedule_status(td)
            assert result["running"] is False
            assert result["pid"] is None

    def test_running_when_pid_alive(self):
        with _tmpdir() as td:
            pid_path = _pid_file(td)
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text(str(os.getpid()))
            result = get_schedule_status(td)
            assert result["running"] is True
            assert result["pid"] == os.getpid()

    def test_not_running_when_pid_dead(self):
        with _tmpdir() as td:
            pid_path = _pid_file(td)
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text("99999999")
            with patch("ai.pojo_lens_agents.schedule._is_process_alive", return_value=False):
                result = get_schedule_status(td)
            assert result["running"] is False
            assert result["pid"] is None

    def test_last_status_merged_into_result(self):
        with _tmpdir() as td:
            status_path = _status_file(td)
            status_path.parent.mkdir(parents=True, exist_ok=True)
            _write_status(status_path, {"status": "idle", "lastOutcome": "completed", "plan": "p.json"})
            result = get_schedule_status(td)
            assert result["lastOutcome"] == "completed"
            assert result["plan"] == "p.json"

    def test_pid_not_duplicated_from_status_file(self):
        with _tmpdir() as td:
            status_path = _status_file(td)
            status_path.parent.mkdir(parents=True, exist_ok=True)
            _write_status(status_path, {"pid": 99, "status": "idle"})
            result = get_schedule_status(td)
            # pid from status file must not override the live pid check
            assert "pid" in result
            assert result["pid"] is None  # no live pid_file present


# ── trigger run subprocess ─────────────────────────────────────────────────────

class TestTriggerRun(unittest.TestCase):
    def test_calls_subprocess_with_correct_args(self):
        fake_result = MagicMock(returncode=0, stdout="done", stderr="")
        with patch("ai.pojo_lens_agents.schedule._find_orchestrator_script",
                   return_value=Path("/repo/scripts/ai/claude-orchestrator.py")), \
             patch("subprocess.run", return_value=fake_result) as mock_run:
            result = _trigger_run("plan.json", "/rt", ["--max-parallel", "2"])

        cmd = mock_run.call_args[0][0]
        assert sys.executable in cmd[0]
        assert "run" in cmd
        assert "plan.json" in cmd
        assert "--runtime-root" in cmd
        assert "/rt" in cmd
        assert "--max-parallel" in cmd
        assert "2" in cmd
        assert result["returncode"] == 0

    def test_returns_failed_returncode(self):
        fake_result = MagicMock(returncode=1, stdout="", stderr="error")
        with patch("ai.pojo_lens_agents.schedule._find_orchestrator_script",
                   return_value=Path("/repo/scripts/ai/claude-orchestrator.py")), \
             patch("subprocess.run", return_value=fake_result):
            result = _trigger_run("plan.json", "/rt", [])
        assert result["returncode"] == 1


# ── cron expression validation (requires apscheduler) ────────────────────────

@unittest.skipIf(_APSCHEDULER_IMPORT_ERROR is not None, "apscheduler not installed")
class TestCronExpressionValidation(unittest.TestCase):
    def test_valid_cron_expr_parses(self):
        from apscheduler.triggers.cron import CronTrigger
        trigger = CronTrigger.from_crontab("0 2 * * *")
        assert trigger is not None

    def test_invalid_cron_raises_schedule_error(self):
        from ai.pojo_lens_agents.schedule import _run_cron_loop
        with self.assertRaises(ScheduleError) as ctx:
            _run_cron_loop("p.json", "not-valid-cron", "/rt", Path("/rt/s.log"), Path("/rt/s.json"), [])
        assert "Invalid cron" in str(ctx.exception)

    def test_nightly_cron_expr_valid(self):
        from apscheduler.triggers.cron import CronTrigger
        trigger = CronTrigger.from_crontab("0 2 * * *")
        assert trigger is not None

    def test_every_minute_cron_expr_valid(self):
        from apscheduler.triggers.cron import CronTrigger
        trigger = CronTrigger.from_crontab("* * * * *")
        assert trigger is not None


# ── start --once mode (mock subprocess) ───────────────────────────────────────

class TestStartOnce(unittest.TestCase):
    def test_once_triggers_run_and_returns_outcome(self):
        fake_result = MagicMock(returncode=0, stdout="ok", stderr="")
        args = _args(once=True)
        with _tmpdir() as td:
            args.runtime_root = td
            with patch("ai.pojo_lens_agents.schedule._find_orchestrator_script",
                       return_value=Path("/repo/scripts/ai/claude-orchestrator.py")), \
                 patch("subprocess.run", return_value=fake_result):
                result = (
                    __import__("ai.pojo_lens_agents.schedule", fromlist=["start_schedule"])
                    .start_schedule(args)
                )
        assert result["status"] == "completed"
        assert result["returncode"] == 0

    def test_once_failed_run_returns_failed(self):
        fake_result = MagicMock(returncode=1, stdout="", stderr="err")
        args = _args(once=True)
        with _tmpdir() as td:
            args.runtime_root = td
            with patch("ai.pojo_lens_agents.schedule._find_orchestrator_script",
                       return_value=Path("/repo/scripts/ai/claude-orchestrator.py")), \
                 patch("subprocess.run", return_value=fake_result):
                result = (
                    __import__("ai.pojo_lens_agents.schedule", fromlist=["start_schedule"])
                    .start_schedule(args)
                )
        assert result["status"] == "failed"
        assert result["returncode"] == 1

    def test_once_cleans_up_pid_file(self):
        fake_result = MagicMock(returncode=0, stdout="", stderr="")
        args = _args(once=True)
        with _tmpdir() as td:
            args.runtime_root = td
            with patch("ai.pojo_lens_agents.schedule._find_orchestrator_script",
                       return_value=Path("/repo/scripts/ai/claude-orchestrator.py")), \
                 patch("subprocess.run", return_value=fake_result):
                __import__("ai.pojo_lens_agents.schedule", fromlist=["start_schedule"]).start_schedule(args)
            assert not _pid_file(td).exists()

    def test_start_raises_when_already_running(self):
        from ai.pojo_lens_agents.schedule import start_schedule
        args = _args(once=True)
        with _tmpdir() as td:
            args.runtime_root = td
            pid_path = _pid_file(td)
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text(str(os.getpid()))
            with self.assertRaises(ScheduleError) as ctx:
                start_schedule(args)
            assert "already running" in str(ctx.exception)

    def test_start_raises_without_trigger(self):
        from ai.pojo_lens_agents.schedule import start_schedule
        args = _args(once=False, cron="", on_change="")
        with _tmpdir() as td:
            args.runtime_root = td
            with self.assertRaises(ScheduleError) as ctx:
                start_schedule(args)
            assert "Specify" in str(ctx.exception)


if __name__ == "__main__":
    unittest.main()
