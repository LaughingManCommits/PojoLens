from __future__ import annotations

import asyncio
import importlib.util
import io
import platform
import sys
import time
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import MagicMock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

TEXTUAL_AVAILABLE = importlib.util.find_spec("textual") is not None

if TEXTUAL_AVAILABLE:
    from textual.widgets import Static
    from pojo_lens_agents.tui_console import (
        CommandInput,
        ConsoleApp,
        _ExitConfirmModal,
        _ThreadLocalStdout,
    )
    from pojo_lens_agents.console import ConsoleJob, ConsoleSession
    from pojo_lens_agents.orchestrator_contracts import OrchestratorError, PromotionBlockedError


# ── Test fixtures ─────────────────────────────────────────────────────────────


def _make_parse_fn():
    """Minimal parse_args_fn: command = first token, json = False."""
    def parse(tokens):
        if not tokens:
            raise SystemExit(2)
        ns = Namespace()
        ns.command = tokens[0]
        ns.json = False
        return ns
    return parse


def _make_handlers():
    return {
        "status":    lambda args: {"_consoleText": "STATUS: ok", "statusCounts": {}},
        "inventory": lambda args: {"_consoleText": "INVENTORY: empty", "runs": []},
        "plan":      lambda args: {"_consoleText": "PLAN: generated"},
        "run":       lambda args: {"statusCounts": {"completed": 1}},
        "resume":    lambda args: {"statusCounts": {"completed": 1}},
        "retry":     lambda args: {"statusCounts": {"completed": 1}},
    }


def _make_app():
    return ConsoleApp(handlers=_make_handlers(), parse_args_fn=_make_parse_fn())


def _make_running_job(session: "ConsoleSession", command: str = "run plan.json") -> "ConsoleJob":
    """Inject a running job directly into a session without spawning a thread."""
    with session._lock:
        session._counter += 1
        job = ConsoleJob(
            job_id=f"job-{session._counter}",
            command=command,
            status="running",
            start_time=time.monotonic(),
        )
        session._jobs[job.job_id] = job
    return job


def _raise_orchestrator_error(args: object) -> dict:
    raise OrchestratorError("orchestrator failure")


def _raise_promo_blocked(args: object) -> dict:
    raise PromotionBlockedError("promotion blocked")


def _raise_runtime(args: object) -> dict:
    raise RuntimeError("unexpected crash")


# ── Base class: Win32 handle restoration + asyncio runner ────────────────────


class _TuiTestBase(unittest.TestCase):
    """Save/restore Python streams and Win32 STD handles around each Textual test.

    Textual's headless run_test() on Windows corrupts OS-level stdin/stdout/
    stderr handles, which breaks any subsequent subprocess.Popen calls.
    """

    def _run(self, coro) -> None:
        asyncio.run(coro)

    def setUp(self) -> None:
        self._saved_stdin  = sys.stdin
        self._saved_stdout = sys.stdout
        self._saved_stderr = sys.stderr
        if platform.system() == "Windows":
            import ctypes
            k32 = ctypes.windll.kernel32
            self._win_stdin  = k32.GetStdHandle(-10)
            self._win_stdout = k32.GetStdHandle(-11)
            self._win_stderr = k32.GetStdHandle(-12)

    def tearDown(self) -> None:
        sys.stdin  = self._saved_stdin
        sys.stdout = self._saved_stdout
        sys.stderr = self._saved_stderr
        if platform.system() == "Windows":
            import ctypes
            k32 = ctypes.windll.kernel32
            k32.SetStdHandle(-10, self._win_stdin)
            k32.SetStdHandle(-11, self._win_stdout)
            k32.SetStdHandle(-12, self._win_stderr)


# ── _ThreadLocalStdout unit tests ─────────────────────────────────────────────


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class ThreadLocalStdoutTest(unittest.TestCase):
    """Unit tests for _ThreadLocalStdout routing logic.

    These run without Textual's headless machinery; they manipulate class-level
    state directly so each test must save/restore to avoid cross-test pollution.
    """

    def setUp(self) -> None:
        self._old_real      = _ThreadLocalStdout._real
        self._old_installed = _ThreadLocalStdout._installed
        _ThreadLocalStdout.clear_sink()

    def tearDown(self) -> None:
        _ThreadLocalStdout._real      = self._old_real
        _ThreadLocalStdout._installed = self._old_installed
        _ThreadLocalStdout.clear_sink()

    def test_write_routes_to_sink_when_set(self):
        buf = io.StringIO()
        _ThreadLocalStdout.set_sink(buf)
        _ThreadLocalStdout().write("hello")
        self.assertEqual("hello", buf.getvalue())

    def test_write_falls_back_to_real_when_no_sink(self):
        fake_real = io.StringIO()
        _ThreadLocalStdout._real = fake_real
        _ThreadLocalStdout.clear_sink()
        _ThreadLocalStdout().write("world")
        self.assertEqual("world", fake_real.getvalue())

    def test_sink_shadows_real(self):
        fake_real = io.StringIO()
        _ThreadLocalStdout._real = fake_real
        buf = io.StringIO()
        _ThreadLocalStdout.set_sink(buf)
        _ThreadLocalStdout().write("sink-only")
        self.assertEqual("sink-only", buf.getvalue())
        self.assertEqual("", fake_real.getvalue())

    def test_clear_sink_restores_fallback_to_real(self):
        fake_real = io.StringIO()
        _ThreadLocalStdout._real = fake_real
        buf = io.StringIO()
        _ThreadLocalStdout.set_sink(buf)
        _ThreadLocalStdout.clear_sink()
        _ThreadLocalStdout().write("after-clear")
        self.assertEqual("after-clear", fake_real.getvalue())
        self.assertEqual("", buf.getvalue())

    def test_encoding_delegates_to_real(self):
        fake_real = MagicMock()
        fake_real.encoding = "utf-8"
        _ThreadLocalStdout._real = fake_real
        self.assertEqual("utf-8", _ThreadLocalStdout().encoding)

    def test_encoding_defaults_utf8_when_real_lacks_attribute(self):
        fake_real = MagicMock(spec=[])
        _ThreadLocalStdout._real = fake_real
        self.assertEqual("utf-8", _ThreadLocalStdout().encoding)

    def test_install_is_idempotent(self):
        _ThreadLocalStdout._real      = io.StringIO()
        _ThreadLocalStdout._installed = True
        old_stdout = sys.stdout
        _ThreadLocalStdout.install()
        self.assertIs(old_stdout, sys.stdout)

    def test_flush_routes_to_sink(self):
        buf = MagicMock()
        _ThreadLocalStdout.set_sink(buf)
        _ThreadLocalStdout().flush()
        buf.flush.assert_called_once()

    def test_flush_falls_back_to_real(self):
        fake_real = MagicMock()
        _ThreadLocalStdout._real = fake_real
        _ThreadLocalStdout.clear_sink()
        _ThreadLocalStdout().flush()
        fake_real.flush.assert_called_once()


# ── _capture unit tests ────────────────────────────────────────────────────────


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class CaptureTest(unittest.TestCase):
    """Test _capture: stdout interception, result pass-through, sink lifecycle."""

    def setUp(self) -> None:
        self._old_stdout    = sys.stdout
        self._old_real      = _ThreadLocalStdout._real
        self._old_installed = _ThreadLocalStdout._installed
        _ThreadLocalStdout.clear_sink()
        # Ensure _ThreadLocalStdout is installed so print() routes through it.
        if not _ThreadLocalStdout._installed:
            _ThreadLocalStdout._real      = sys.stdout
            sys.stdout                    = _ThreadLocalStdout()
            _ThreadLocalStdout._installed = True

    def tearDown(self) -> None:
        sys.stdout                    = self._old_stdout
        _ThreadLocalStdout._real      = self._old_real
        _ThreadLocalStdout._installed = self._old_installed
        _ThreadLocalStdout.clear_sink()

    def test_capture_intercepts_print(self):
        text, _ = ConsoleApp._capture(lambda: print("captured line"))
        self.assertIn("captured line", text)

    def test_capture_returns_fn_result(self):
        _, result = ConsoleApp._capture(lambda: 42)
        self.assertEqual(42, result)

    def test_capture_multiple_prints(self):
        def multi():
            print("first")
            print("second")
            return "done"
        text, result = ConsoleApp._capture(multi)
        self.assertIn("first", text)
        self.assertIn("second", text)
        self.assertEqual("done", result)

    def test_capture_clears_sink_after_success(self):
        ConsoleApp._capture(lambda: print("x"))
        self.assertIsNone(getattr(_ThreadLocalStdout._local, "sink", None))

    def test_capture_clears_sink_after_exception(self):
        def boom():
            print("before error")
            raise ValueError("intentional")
        with self.assertRaises(ValueError):
            ConsoleApp._capture(boom)
        self.assertIsNone(getattr(_ThreadLocalStdout._local, "sink", None))


# ── _payload_text unit tests ───────────────────────────────────────────────────


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class PayloadTextTest(unittest.TestCase):
    """Test _payload_text: _consoleText path, JSON path, flag override."""

    def test_console_text_returned_when_present_and_not_json(self):
        payload = {"_consoleText": "human readable", "status": "ok"}
        self.assertEqual("human readable", ConsoleApp._payload_text(payload, as_json=False))

    def test_json_dumped_when_no_console_text(self):
        import json
        payload = {"status": "ok", "count": 3}
        data = json.loads(ConsoleApp._payload_text(payload, as_json=False))
        self.assertEqual("ok", data["status"])
        self.assertEqual(3, data["count"])

    def test_json_dumped_when_as_json_true_even_with_console_text(self):
        import json
        payload = {"_consoleText": "human readable", "status": "ok"}
        data = json.loads(ConsoleApp._payload_text(payload, as_json=True))
        self.assertNotIn("_consoleText", data)
        self.assertEqual("ok", data["status"])

    def test_json_output_excludes_console_text_key(self):
        result = ConsoleApp._payload_text({"_consoleText": "text", "x": 1}, as_json=True)
        self.assertNotIn("_consoleText", result)

    def test_whitespace_only_console_text_falls_through_to_json(self):
        import json
        payload = {"_consoleText": "   ", "status": "ok"}
        data = json.loads(ConsoleApp._payload_text(payload, as_json=False))
        self.assertEqual("ok", data["status"])


# ── Headless _dispatch tests ───────────────────────────────────────────────────


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class ConsoleAppDispatchTest(_TuiTestBase):
    """Test _dispatch routing and Rich-markup output via headless run_test()."""

    def test_dispatch_help_writes_help_sections(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/help")
                await pilot.pause(0.1)
                all_text = "\n".join(written)
                self.assertIn("CONSOLE COMMANDS", all_text)
                self.assertIn("/exit", all_text)
                self.assertIn("ORCHESTRATOR COMMANDS", all_text)
                self.assertIn("/jobs", all_text)
        self._run(scenario())

    def test_dispatch_clear_calls_write_banner(self):
        # _write_banner writes directly to RichLog (not via _write), so spy on it.
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                banner_calls = [0]
                original_banner = app._write_banner
                def spy_banner():
                    banner_calls[0] += 1
                    original_banner()
                app._write_banner = spy_banner
                app._dispatch("/clear")
                await pilot.pause(0.1)
                self.assertEqual(1, banner_calls[0])
        self._run(scenario())

    def test_dispatch_jobs_with_no_jobs(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/jobs")
                await pilot.pause(0.1)
                self.assertTrue(any("no background jobs" in w for w in written))
        self._run(scenario())

    def test_dispatch_jobs_with_running_job(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                _make_running_job(app._session, "run plan.json")
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/jobs")
                await pilot.pause(0.1)
                all_text = "\n".join(written)
                self.assertIn("job-1", all_text)
                self.assertIn("running", all_text)
        self._run(scenario())

    def test_dispatch_focus_no_args_no_jobs(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/focus")
                await pilot.pause(0.1)
                self.assertTrue(any("no job found" in w for w in written))
        self._run(scenario())

    def test_dispatch_focus_no_args_returns_last_job(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                _make_running_job(app._session, "run plan.json")
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/focus")
                await pilot.pause(0.1)
                self.assertTrue(any("job-1" in w for w in written))
        self._run(scenario())

    def test_dispatch_focus_with_known_job_id(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                _make_running_job(app._session, "run plan.json")
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/focus job-1")
                await pilot.pause(0.1)
                all_text = "\n".join(written)
                self.assertIn("job-1", all_text)
                self.assertIn("running", all_text)
        self._run(scenario())

    def test_dispatch_focus_with_unknown_job_id(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/focus job-999")
                await pilot.pause(0.1)
                self.assertTrue(
                    any("job-999" in w or "unknown" in w for w in written)
                )
        self._run(scenario())

    def test_dispatch_unknown_slash_command(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("/xyz_does_not_exist")
                await pilot.pause(0.1)
                self.assertTrue(any("unknown command" in w for w in written))
        self._run(scenario())

    def test_dispatch_shlex_parse_error(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("run 'unterminated string")
                await pilot.pause(0.1)
                self.assertTrue(any("parse error" in w for w in written))
        self._run(scenario())

    def test_dispatch_unknown_command(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("notacommand arg1")
                await pilot.pause(0.1)
                self.assertTrue(any("unknown command" in w for w in written))
        self._run(scenario())

    def test_dispatch_empty_line_produces_no_output(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("   ")
                await pilot.pause(0.1)
                self.assertEqual([], written)
        self._run(scenario())

    def test_dispatch_exit_calls_app_exit(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                exit_calls: list[object] = []
                app.exit = lambda v=None: exit_calls.append(v)
                app._dispatch("/exit")
                await pilot.pause(0.3)
                self.assertEqual([0], exit_calls)
        self._run(scenario())


# ── Headless routing + worker tests ───────────────────────────────────────────


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class ConsoleAppRoutingTest(_TuiTestBase):
    """Test bg-job vs inline routing, worker success/failure, and busy flag."""

    def test_run_command_creates_bg_job_and_completes(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._dispatch("run plan.json")
                await pilot.pause(0.6)
                jobs = app._session.all_jobs()
                self.assertEqual(1, len(jobs))
                self.assertEqual("completed", jobs[0].status)
                self.assertEqual(0, jobs[0].exit_code)
        self._run(scenario())

    def test_resume_command_is_background_job(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._dispatch("resume run-001")
                await pilot.pause(0.6)
                jobs = app._session.all_jobs()
                self.assertEqual(1, len(jobs))
                self.assertTrue(jobs[0].command.startswith("resume"))
                self.assertEqual("completed", jobs[0].status)
        self._run(scenario())

    def test_retry_command_is_background_job(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._dispatch("retry run-001")
                await pilot.pause(0.6)
                jobs = app._session.all_jobs()
                self.assertEqual(1, len(jobs))
                self.assertTrue(jobs[0].command.startswith("retry"))
        self._run(scenario())

    def test_bg_job_writes_completion_message(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("run plan.json")
                await pilot.pause(0.6)
                all_text = "\n".join(written)
                self.assertIn("job-1", all_text)
                self.assertTrue("OK" in all_text or "!!" in all_text)
        self._run(scenario())

    def test_bg_job_queued_message_written(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("run plan.json")
                await pilot.pause(0.1)
                self.assertTrue(any("queued" in w or "job-1" in w for w in written))
        self._run(scenario())

    def test_status_command_is_inline_and_clears_busy(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._dispatch("status run-001")
                await pilot.pause(0.5)
                self.assertFalse(app._busy)
                self.assertEqual(0, len(app._session.all_jobs()))
        self._run(scenario())

    def test_inline_success_writes_console_text(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("status run-001")
                await pilot.pause(0.5)
                self.assertTrue(any("STATUS" in w for w in written))
        self._run(scenario())

    def test_bg_job_orchestrator_error_sets_failed(self):
        async def scenario():
            handlers = {**_make_handlers(), "run": _raise_orchestrator_error}
            app = ConsoleApp(handlers=handlers, parse_args_fn=_make_parse_fn())
            async with app.run_test() as pilot:
                app._dispatch("run plan.json")
                await pilot.pause(0.6)
                jobs = app._session.all_jobs()
                self.assertEqual(1, len(jobs))
                self.assertEqual("failed", jobs[0].status)
                self.assertEqual(1, jobs[0].exit_code)
        self._run(scenario())

    def test_bg_job_promotion_blocked_sets_failed(self):
        async def scenario():
            handlers = {**_make_handlers(), "run": _raise_promo_blocked}
            app = ConsoleApp(handlers=handlers, parse_args_fn=_make_parse_fn())
            async with app.run_test() as pilot:
                app._dispatch("run plan.json")
                await pilot.pause(0.6)
                jobs = app._session.all_jobs()
                self.assertEqual("failed", jobs[0].status)
        self._run(scenario())

    def test_bg_job_unexpected_exception_sets_failed(self):
        async def scenario():
            handlers = {**_make_handlers(), "run": _raise_runtime}
            app = ConsoleApp(handlers=handlers, parse_args_fn=_make_parse_fn())
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("run plan.json")
                await pilot.pause(0.6)
                self.assertEqual("failed", app._session.all_jobs()[0].status)
                self.assertTrue(any("CRASH" in w for w in written))
        self._run(scenario())

    def test_inline_orchestrator_error_clears_busy_and_writes_error(self):
        async def scenario():
            handlers = {**_make_handlers(), "status": _raise_orchestrator_error}
            app = ConsoleApp(handlers=handlers, parse_args_fn=_make_parse_fn())
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("status run-001")
                await pilot.pause(0.5)
                self.assertFalse(app._busy)
                self.assertTrue(any("ERROR" in w for w in written))
        self._run(scenario())

    def test_inline_promotion_blocked_clears_busy_and_writes_blocked(self):
        async def scenario():
            handlers = {**_make_handlers(), "status": _raise_promo_blocked}
            app = ConsoleApp(handlers=handlers, parse_args_fn=_make_parse_fn())
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("status run-001")
                await pilot.pause(0.5)
                self.assertFalse(app._busy)
                self.assertTrue(any("BLOCKED" in w for w in written))
        self._run(scenario())

    def test_inline_unexpected_exception_clears_busy_and_writes_unexpected(self):
        async def scenario():
            handlers = {**_make_handlers(), "status": _raise_runtime}
            app = ConsoleApp(handlers=handlers, parse_args_fn=_make_parse_fn())
            async with app.run_test() as pilot:
                written: list[str] = []
                app._write = lambda t: written.append(t)
                app._dispatch("status run-001")
                await pilot.pause(0.5)
                self.assertFalse(app._busy)
                self.assertTrue(any("UNEXPECTED" in w for w in written))
        self._run(scenario())

    def test_busy_while_inline_command_runs(self):
        """_set_busy(True) must be in effect while the inline worker thread runs."""
        busy_during: list[bool] = []

        async def scenario():
            import threading
            barrier = threading.Event()
            released = threading.Event()

            def slow_handler(args):
                barrier.set()           # signal: inside handler
                released.wait(timeout=2)  # block until test releases
                return {"_consoleText": "done"}

            handlers = {**_make_handlers(), "status": slow_handler}
            app = ConsoleApp(handlers=handlers, parse_args_fn=_make_parse_fn())
            async with app.run_test() as pilot:
                app._dispatch("status run-001")
                await pilot.pause(0.1)
                barrier.wait(timeout=2)      # wait until inside handler
                busy_during.append(app._busy)
                released.set()
                await pilot.pause(0.5)
                self.assertTrue(busy_during[0], "app must be busy while inline handler runs")
                self.assertFalse(app._busy,     "app must not be busy after handler returns")

        self._run(scenario())


# ── History navigation tests ───────────────────────────────────────────────────


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class ConsoleAppHistoryTest(_TuiTestBase):
    """Test command history prev/next navigation."""

    def test_history_prev_sets_last_command(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._history     = ["cmd1", "cmd2", "cmd3"]
                app._history_pos = -1
                app.history_prev()
                self.assertEqual("cmd3", app.query_one("#cmd-input", CommandInput).value)
        self._run(scenario())

    def test_history_prev_walks_backwards(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._history     = ["cmd1", "cmd2", "cmd3"]
                app._history_pos = -1
                app.history_prev()  # cmd3
                app.history_prev()  # cmd2
                app.history_prev()  # cmd1
                self.assertEqual("cmd1", app.query_one("#cmd-input", CommandInput).value)
        self._run(scenario())

    def test_history_prev_clamps_at_oldest(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._history     = ["only"]
                app._history_pos = -1
                app.history_prev()  # → only
                app.history_prev()  # stays clamped at oldest
                self.assertEqual("only", app.query_one("#cmd-input", CommandInput).value)
                self.assertEqual(0, app._history_pos)
        self._run(scenario())

    def test_history_next_advances_forward(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._history     = ["cmd1", "cmd2", "cmd3"]
                app._history_pos = 0  # sitting at oldest
                app.history_next()    # → cmd2
                self.assertEqual("cmd2", app.query_one("#cmd-input", CommandInput).value)
                self.assertEqual(1, app._history_pos)
        self._run(scenario())

    def test_history_next_past_newest_clears_input(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._history     = ["cmd1", "cmd2"]
                app._history_pos = 1  # at newest
                app.history_next()    # → past end
                self.assertEqual("", app.query_one("#cmd-input", CommandInput).value)
                self.assertEqual(-1, app._history_pos)
        self._run(scenario())

    def test_history_next_at_minus_one_is_noop(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._history     = ["cmd1"]
                app._history_pos = -1
                cmd = app.query_one("#cmd-input", CommandInput)
                cmd.value = "current"
                app.history_next()
                self.assertEqual("current", cmd.value)
                self.assertEqual(-1, app._history_pos)
        self._run(scenario())

    def test_history_prev_empty_history_is_noop(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                app._history     = []
                app._history_pos = -1
                cmd = app.query_one("#cmd-input", CommandInput)
                cmd.value = "current"
                app.history_prev()
                self.assertEqual("current", cmd.value)
        self._run(scenario())

    def test_submit_appends_to_history_and_resets_position(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                cmd = app.query_one("#cmd-input", CommandInput)
                cmd.value = "inventory"
                await pilot.press("enter")
                await pilot.pause(0.5)
                self.assertIn("inventory", app._history)
                self.assertEqual(-1, app._history_pos)
        self._run(scenario())


# ── _ExitConfirmModal tests ────────────────────────────────────────────────────
#
# ModalScreen has no run_test() — it cannot be run standalone.
# Compose/query tests push the modal from a ConsoleApp.
# Dismiss-value tests call on_button_pressed directly (no Textual loop needed).


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class ExitConfirmModalTest(_TuiTestBase):
    """Test _ExitConfirmModal composition and button dismissal values."""

    # ── Direct handler tests (no Textual loop required) ───────────────────────

    def _make_button_event(self, button_id: str) -> "object":
        from unittest.mock import MagicMock as MM
        btn = MM()
        btn.id = button_id
        event = MM()
        event.button = btn
        return event

    def test_confirm_yes_dismisses_true(self):
        modal = _ExitConfirmModal(1)
        dismissed: list[bool] = []
        modal.dismiss = lambda v: dismissed.append(v)
        modal.on_button_pressed(self._make_button_event("confirm-yes"))
        self.assertEqual([True], dismissed)

    def test_confirm_no_dismisses_false(self):
        modal = _ExitConfirmModal(2)
        dismissed: list[bool] = []
        modal.dismiss = lambda v: dismissed.append(v)
        modal.on_button_pressed(self._make_button_event("confirm-no"))
        self.assertEqual([False], dismissed)

    def test_unknown_button_id_dismisses_false(self):
        modal = _ExitConfirmModal(1)
        dismissed: list[bool] = []
        modal.dismiss = lambda v: dismissed.append(v)
        modal.on_button_pressed(self._make_button_event("other-btn"))
        self.assertEqual([False], dismissed)

    def test_modal_stores_count(self):
        modal = _ExitConfirmModal(7)
        self.assertEqual(7, modal._count)

    # ── Compose / render tests (via ConsoleApp host) ───────────────────────────

    def test_modal_compose_shows_running_count(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                modal = _ExitConfirmModal(3)
                app.push_screen(modal)
                await pilot.pause(0.2)
                msg = app.query_one("#modal-msg", Static)
                self.assertIn("3", str(msg.renderable))
        self._run(scenario())

    def test_modal_title_shows_running_warning(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                modal = _ExitConfirmModal(1)
                app.push_screen(modal)
                await pilot.pause(0.2)
                title = app.query_one("#modal-title", Static)
                self.assertIn("RUNNING", str(title.renderable).upper())
        self._run(scenario())


# ── ConsoleApp exit-flow tests ─────────────────────────────────────────────────


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class ConsoleAppExitTest(_TuiTestBase):
    """Test _exit_flow and action_request_quit under no-jobs and with-jobs cases."""

    def test_exit_flow_no_jobs_calls_exit_immediately(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                exit_calls: list[object] = []
                app.exit = lambda v=None: exit_calls.append(v)
                await app._exit_flow()
                self.assertEqual([0], exit_calls)
        self._run(scenario())

    def test_exit_flow_with_running_jobs_shows_modal(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                _make_running_job(app._session)
                pushed: list[object] = []

                async def fake_push_screen_wait(screen):
                    pushed.append(screen)
                    return False  # operator chose STAY

                app.push_screen_wait = fake_push_screen_wait
                exit_calls: list[object] = []
                app.exit = lambda v=None: exit_calls.append(v)
                await app._exit_flow()
                self.assertEqual(1, len(pushed))
                self.assertIsInstance(pushed[0], _ExitConfirmModal)
                self.assertEqual([], exit_calls)  # not exited
        self._run(scenario())

    def test_exit_flow_with_jobs_confirmed_exits(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                _make_running_job(app._session)

                async def fake_push_screen_wait(screen):
                    return True  # operator confirmed EXIT

                app.push_screen_wait = fake_push_screen_wait
                exit_calls: list[object] = []
                app.exit = lambda v=None: exit_calls.append(v)
                await app._exit_flow()
                self.assertEqual([0], exit_calls)
        self._run(scenario())

    def test_action_request_quit_delegates_to_exit_flow(self):
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                exit_calls: list[object] = []
                app.exit = lambda v=None: exit_calls.append(v)
                await app.action_request_quit()
                self.assertEqual([0], exit_calls)
        self._run(scenario())

    def test_exit_flow_counts_only_running_jobs(self):
        """Completed jobs must not trigger the modal."""
        async def scenario():
            app = _make_app()
            async with app.run_test() as pilot:
                job = _make_running_job(app._session)
                job.status = "completed"  # mark as done
                exit_calls: list[object] = []
                app.exit = lambda v=None: exit_calls.append(v)
                await app._exit_flow()
                self.assertEqual([0], exit_calls)
        self._run(scenario())


if __name__ == "__main__":
    unittest.main()
