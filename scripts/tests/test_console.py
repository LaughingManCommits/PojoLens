from __future__ import annotations

import sys
import threading
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from ai.pojo_lens_agents.console import (
    LONG_RUNNING_COMMANDS,
    ConsoleJob,
    ConsoleSession,
    _print_console_help,
    _print_jobs,
    dispatch_line,
    run_console_session,
)


# ---------------------------------------------------------------------------
# ConsoleSession
# ---------------------------------------------------------------------------


class ConsoleSessionTest(unittest.TestCase):
    def test_add_job_assigns_unique_ids(self):
        session = ConsoleSession()
        j1 = session.add_job("run plan.json")
        j2 = session.add_job("resume run-1")
        self.assertEqual(j1.job_id, "job-1")
        self.assertEqual(j2.job_id, "job-2")
        self.assertNotEqual(j1.job_id, j2.job_id)

    def test_add_job_status_is_running(self):
        session = ConsoleSession()
        job = session.add_job("run plan.json")
        self.assertEqual(job.status, "running")

    def test_all_jobs_returns_all(self):
        session = ConsoleSession()
        session.add_job("run plan.json")
        session.add_job("resume x")
        self.assertEqual(len(session.all_jobs()), 2)

    def test_get_job_found(self):
        session = ConsoleSession()
        job = session.add_job("run plan.json")
        found = session.get_job(job.job_id)
        self.assertIs(found, job)

    def test_get_job_missing_returns_none(self):
        session = ConsoleSession()
        self.assertIsNone(session.get_job("job-99"))

    def test_elapsed_sec_running(self):
        session = ConsoleSession()
        job = session.add_job("run plan.json")
        elapsed = job.elapsed_sec()
        self.assertGreaterEqual(elapsed, 0.0)

    def test_elapsed_sec_completed(self):
        session = ConsoleSession()
        job = session.add_job("run plan.json")
        job.start_time = time.monotonic() - 5.0
        job.end_time = time.monotonic()
        self.assertGreaterEqual(job.elapsed_sec(), 4.9)


# ---------------------------------------------------------------------------
# ConsoleJob dataclass
# ---------------------------------------------------------------------------


class ConsoleJobTest(unittest.TestCase):
    def test_elapsed_sec_no_end_time(self):
        job = ConsoleJob(
            job_id="job-1",
            command="run plan.json",
            status="running",
            start_time=time.monotonic() - 2.0,
        )
        self.assertGreaterEqual(job.elapsed_sec(), 1.9)

    def test_elapsed_sec_with_end_time(self):
        t = time.monotonic()
        job = ConsoleJob(
            job_id="job-1",
            command="run plan.json",
            status="completed",
            start_time=t - 10.0,
            end_time=t,
        )
        self.assertAlmostEqual(job.elapsed_sec(), 10.0, delta=0.1)


# ---------------------------------------------------------------------------
# _print_console_help / _print_jobs
# ---------------------------------------------------------------------------


class PrintHelpTest(unittest.TestCase):
    def test_prints_without_error(self):
        with mock.patch("builtins.print") as mock_print:
            _print_console_help()
        self.assertTrue(mock_print.called)

    def test_includes_exit_and_help(self):
        lines: list[str] = []
        with mock.patch("builtins.print", side_effect=lambda *a, **kw: lines.extend(a)):
            _print_console_help()
        combined = "\n".join(str(x) for x in lines)
        self.assertIn("/exit", combined)
        self.assertIn("/help", combined)
        self.assertIn("[bg]", combined)


class PrintJobsTest(unittest.TestCase):
    def test_no_jobs_prints_message(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            _print_jobs(session)
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn("No background jobs", output)

    def test_with_jobs_prints_table(self):
        session = ConsoleSession()
        job = session.add_job("run plan.json")
        job.status = "completed"
        job.end_time = job.start_time + 3.0
        with mock.patch("builtins.print") as mock_print:
            _print_jobs(session)
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn("job-1", output)
        self.assertIn("completed", output)
        self.assertIn("run plan.json", output)


# ---------------------------------------------------------------------------
# dispatch_line — console meta-commands
# ---------------------------------------------------------------------------


def _fake_parse_args(tokens):
    cmd = tokens[0] if tokens else "wizard"
    return SimpleNamespace(command=cmd, json=False)


def _fake_handlers():
    return {
        "status": lambda args: {"status": "ok"},
        "inventory": lambda args: {"runs": []},
        "run": lambda args: {"statusCounts": {"completed": 1}, "runId": "r-1", "runDir": "/tmp/r-1"},
        "resume": lambda args: {"statusCounts": {"completed": 1}, "runId": "r-2", "runDir": "/tmp/r-2"},
        "retry": lambda args: {"statusCounts": {"completed": 1}, "runId": "r-3", "runDir": "/tmp/r-3"},
        "wizard": lambda args: {"mode": "plan", "steps": [], "statusCounts": {}},
    }


class DispatchLineExitTest(unittest.TestCase):
    def test_slash_exit_returns_false(self):
        session = ConsoleSession()
        result = dispatch_line("/exit", session, _fake_parse_args, _fake_handlers())
        self.assertFalse(result)

    def test_bare_exit_returns_false(self):
        session = ConsoleSession()
        result = dispatch_line("exit", session, _fake_parse_args, _fake_handlers())
        self.assertFalse(result)

    def test_quit_returns_false(self):
        session = ConsoleSession()
        result = dispatch_line("quit", session, _fake_parse_args, _fake_handlers())
        self.assertFalse(result)

    def test_exit_case_insensitive(self):
        session = ConsoleSession()
        result = dispatch_line("EXIT", session, _fake_parse_args, _fake_handlers())
        self.assertFalse(result)


class DispatchLineEmptyTest(unittest.TestCase):
    def test_empty_line_returns_true(self):
        session = ConsoleSession()
        result = dispatch_line("", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(result)

    def test_whitespace_only_returns_true(self):
        session = ConsoleSession()
        result = dispatch_line("   ", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(result)


class DispatchLineHelpTest(unittest.TestCase):
    def test_slash_help_returns_true(self):
        session = ConsoleSession()
        with mock.patch("builtins.print"):
            result = dispatch_line("/help", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(result)

    def test_slash_help_calls_print(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            dispatch_line("/help", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(mock_print.called)


class DispatchLineJobsTest(unittest.TestCase):
    def test_slash_jobs_returns_true(self):
        session = ConsoleSession()
        with mock.patch("builtins.print"):
            result = dispatch_line("/jobs", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(result)

    def test_slash_jobs_prints_output(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            dispatch_line("/jobs", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(mock_print.called)


class DispatchLineFocusTest(unittest.TestCase):
    def test_focus_no_jobs_prints_no_job(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            result = dispatch_line("/focus", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(result)
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn("No job", output)

    def test_focus_with_job_id(self):
        session = ConsoleSession()
        job = session.add_job("run plan.json")
        job.status = "running"
        with mock.patch("builtins.print") as mock_print:
            dispatch_line(f"/focus {job.job_id}", session, _fake_parse_args, _fake_handlers())
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn(job.job_id, output)

    def test_focus_unknown_job_id(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            dispatch_line("/focus job-99", session, _fake_parse_args, _fake_handlers())
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn("Unknown job", output)

    def test_focus_no_arg_uses_last_job(self):
        session = ConsoleSession()
        j1 = session.add_job("run a.json")
        j2 = session.add_job("run b.json")
        with mock.patch("builtins.print") as mock_print:
            dispatch_line("/focus", session, _fake_parse_args, _fake_handlers())
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn(j2.job_id, output)


class DispatchLineClearTest(unittest.TestCase):
    def test_slash_clear_returns_true(self):
        session = ConsoleSession()
        with mock.patch("builtins.print"):
            result = dispatch_line("/clear", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(result)

    def test_slash_clear_prints_escape(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            dispatch_line("/clear", session, _fake_parse_args, _fake_handlers())
        call_args = mock_print.call_args_list
        self.assertTrue(
            any(c.args and "\033[2J" in c.args[0] for c in call_args)
        )


class DispatchLineUnknownSlashTest(unittest.TestCase):
    def test_unknown_slash_command_returns_true(self):
        session = ConsoleSession()
        with mock.patch("builtins.print"):
            result = dispatch_line("/florp", session, _fake_parse_args, _fake_handlers())
        self.assertTrue(result)

    def test_unknown_slash_command_prints_hint(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            dispatch_line("/florp", session, _fake_parse_args, _fake_handlers())
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn("Unknown console command", output)
        self.assertIn("/florp", output)


# ---------------------------------------------------------------------------
# dispatch_line — orchestrator command routing
# ---------------------------------------------------------------------------


class DispatchLineInlineCommandTest(unittest.TestCase):
    def test_inline_command_calls_handler(self):
        session = ConsoleSession()
        called: dict[str, object] = {}

        def fake_status(args):
            called["args"] = args
            return {"status": "ok", "_consoleText": "run ok"}

        handlers = {"status": fake_status}
        with mock.patch("builtins.print"):
            result = dispatch_line("status /tmp/run", session, _fake_parse_args, handlers)
        self.assertTrue(result)
        self.assertIn("args", called)

    def test_inline_command_no_background_job(self):
        session = ConsoleSession()
        handlers = {"inventory": lambda args: {"runs": []}}
        with mock.patch("builtins.print"):
            dispatch_line("inventory", session, _fake_parse_args, handlers)
        self.assertEqual(len(session.all_jobs()), 0)

    def test_unknown_command_prints_hint(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            result = dispatch_line("frobnicate", session, _fake_parse_args, {})
        self.assertTrue(result)
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn("Unknown command", output)


class DispatchLineBackgroundJobTest(unittest.TestCase):
    def _wait_for_job(self, job: ConsoleJob, timeout: float = 5.0) -> None:
        deadline = time.monotonic() + timeout
        while job.status == "running" and time.monotonic() < deadline:
            time.sleep(0.01)

    def test_run_command_creates_background_job(self):
        session = ConsoleSession()
        barrier = threading.Event()

        def slow_run(args):
            barrier.wait(timeout=5)
            return {"statusCounts": {"completed": 1}, "runId": "r-1", "runDir": "/tmp/r-1"}

        handlers = {"run": slow_run}
        with mock.patch("builtins.print"):
            result = dispatch_line("run plan.json", session, _fake_parse_args, handlers)
        self.assertTrue(result)
        jobs = session.all_jobs()
        self.assertEqual(len(jobs), 1)
        self.assertEqual(jobs[0].status, "running")
        barrier.set()

    def test_run_job_completes_with_success(self):
        session = ConsoleSession()

        def fast_run(args):
            return {"statusCounts": {"completed": 2}, "runId": "r-x", "runDir": "/tmp/r-x"}

        handlers = {"run": fast_run}
        with mock.patch("builtins.print"):
            dispatch_line("run plan.json", session, _fake_parse_args, handlers)
        jobs = session.all_jobs()
        self.assertEqual(len(jobs), 1)
        self._wait_for_job(jobs[0])
        self.assertEqual(jobs[0].status, "completed")

    def test_run_job_completes_with_failure_on_worker_failure(self):
        session = ConsoleSession()

        def failed_run(args):
            return {"statusCounts": {"failed": 1}, "runId": "r-f", "runDir": "/tmp/r-f"}

        handlers = {"run": failed_run}
        with mock.patch("builtins.print"):
            dispatch_line("run plan.json", session, _fake_parse_args, handlers)
        jobs = session.all_jobs()
        self._wait_for_job(jobs[0])
        self.assertEqual(jobs[0].status, "failed")

    def test_resume_and_retry_are_background_commands(self):
        self.assertIn("resume", LONG_RUNNING_COMMANDS)
        self.assertIn("retry", LONG_RUNNING_COMMANDS)
        self.assertIn("run", LONG_RUNNING_COMMANDS)

    def test_wizard_is_a_background_command(self):
        self.assertIn("wizard", LONG_RUNNING_COMMANDS)

    def test_status_is_not_a_background_command(self):
        self.assertNotIn("status", LONG_RUNNING_COMMANDS)


class DispatchLineParseErrorTest(unittest.TestCase):
    def test_shlex_error_prints_message_and_continues(self):
        session = ConsoleSession()
        with mock.patch("builtins.print") as mock_print:
            result = dispatch_line('run "unclosed', session, _fake_parse_args, {})
        self.assertTrue(result)
        output = " ".join(str(c.args[0]) for c in mock_print.call_args_list)
        self.assertIn("Parse error", output)

    def test_argparse_system_exit_is_swallowed(self):
        session = ConsoleSession()

        def bad_parse(tokens):
            raise SystemExit(2)

        with mock.patch("builtins.print"):
            result = dispatch_line("run", session, bad_parse, {})
        self.assertTrue(result)

    def test_parse_exception_is_swallowed(self):
        session = ConsoleSession()

        def bad_parse(tokens):
            raise ValueError("oops")

        with mock.patch("builtins.print"):
            result = dispatch_line("run plan.json", session, bad_parse, {})
        self.assertTrue(result)


class DispatchLineHandlerExceptionTest(unittest.TestCase):
    def test_orchestrator_error_prints_to_stderr_and_continues(self):
        session = ConsoleSession()
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError

        def bad_handler(args):
            raise OrchestratorError("plan not found")

        handlers = {"status": bad_handler}
        with mock.patch("builtins.print"), mock.patch("sys.stderr"):
            result = dispatch_line("status /tmp/x", session, _fake_parse_args, handlers)
        self.assertTrue(result)

    def test_unexpected_exception_prints_to_stderr_and_continues(self):
        session = ConsoleSession()

        def boom(args):
            raise RuntimeError("kaboom")

        handlers = {"status": boom}
        with mock.patch("builtins.print"), mock.patch("sys.stderr"):
            result = dispatch_line("status /tmp/x", session, _fake_parse_args, handlers)
        self.assertTrue(result)


# ---------------------------------------------------------------------------
# run_console_session lifecycle
# ---------------------------------------------------------------------------


class RunConsoleSessionTest(unittest.TestCase):
    def test_eoferror_exits_gracefully(self):
        with mock.patch("builtins.input", side_effect=EOFError), \
             mock.patch("builtins.print"):
            exit_code = run_console_session(
                None,
                handlers={},
                parse_args_fn=_fake_parse_args,
            )
        self.assertEqual(exit_code, 0)

    def test_slash_exit_ends_session(self):
        with mock.patch("builtins.input", side_effect=["/exit"]), \
             mock.patch("builtins.print"):
            exit_code = run_console_session(
                None,
                handlers={},
                parse_args_fn=_fake_parse_args,
            )
        self.assertEqual(exit_code, 0)

    def test_prints_banner_on_start(self):
        printed: list[str] = []
        with mock.patch("builtins.input", side_effect=["/exit"]), \
             mock.patch("builtins.print", side_effect=lambda *a, **kw: printed.extend(a)):
            run_console_session(
                None,
                handlers={},
                parse_args_fn=_fake_parse_args,
                banner="TEST-BANNER",
            )
        self.assertIn("TEST-BANNER", printed)

    def test_prints_goodbye_on_exit(self):
        printed: list[str] = []
        with mock.patch("builtins.input", side_effect=["/exit"]), \
             mock.patch("builtins.print", side_effect=lambda *a, **kw: printed.extend(a)):
            run_console_session(
                None,
                handlers={},
                parse_args_fn=_fake_parse_args,
            )
        self.assertIn("Goodbye.", printed)

    def test_keyboard_interrupt_continues(self):
        # First call raises KeyboardInterrupt, second call provides /exit
        with mock.patch("builtins.input", side_effect=[KeyboardInterrupt, "/exit"]), \
             mock.patch("builtins.print"):
            exit_code = run_console_session(
                None,
                handlers={},
                parse_args_fn=_fake_parse_args,
            )
        self.assertEqual(exit_code, 0)

    def test_waits_for_running_background_jobs(self):
        started = threading.Event()
        finished = threading.Event()

        def slow_run(args):
            started.set()
            finished.wait(timeout=5)
            return {"statusCounts": {"completed": 1}, "runId": "r-w", "runDir": "/tmp/r-w"}

        inputs = iter(["run plan.json", "/exit"])

        def fake_input(prompt):
            return next(inputs)

        with mock.patch("builtins.input", side_effect=fake_input), \
             mock.patch("builtins.print"):
            # Start the session in a thread so we can signal the background job
            result: dict[str, object] = {}

            def _run():
                result["code"] = run_console_session(
                    None,
                    handlers={"run": slow_run},
                    parse_args_fn=_fake_parse_args,
                )

            t = threading.Thread(target=_run)
            t.start()
            started.wait(timeout=3)
            finished.set()
            t.join(timeout=10)

        self.assertEqual(result.get("code"), 0)

    def test_multiple_commands_in_sequence(self):
        called: list[str] = []

        def fake_status(args):
            called.append("status")
            return {"status": "ok", "_consoleText": "ok"}

        def fake_inventory(args):
            called.append("inventory")
            return {"runs": [], "_consoleText": "empty"}

        def parse_fn(tokens):
            cmd = tokens[0] if tokens else "wizard"
            return SimpleNamespace(command=cmd, json=False)

        handlers = {"status": fake_status, "inventory": fake_inventory}
        with mock.patch("builtins.input", side_effect=["status /tmp/r", "inventory", "/exit"]), \
             mock.patch("builtins.print"):
            run_console_session(None, handlers=handlers, parse_args_fn=parse_fn)
        self.assertEqual(called, ["status", "inventory"])


# ---------------------------------------------------------------------------
# Console command wired into cli_parser
# ---------------------------------------------------------------------------


class ConsoleSubcommandTest(unittest.TestCase):
    def test_console_subcommand_parses(self):
        from ai.pojo_lens_agents.cli_parser import parse_args
        args = parse_args(["console"])
        self.assertEqual(args.command, "console")

    def test_console_with_runtime_root(self):
        from ai.pojo_lens_agents.cli_parser import parse_args
        args = parse_args(["console", "--runtime-root", "/tmp/runs"])
        self.assertEqual(args.command, "console")
        self.assertEqual(args.runtime_root, "/tmp/runs")

    def test_console_with_provider_bin(self):
        from ai.pojo_lens_agents.cli_parser import parse_args
        args = parse_args(["console", "--provider-bin", "my-claude"])
        self.assertEqual(args.claude_bin, "my-claude")


# ---------------------------------------------------------------------------
# KNOWN_COMMANDS includes console
# ---------------------------------------------------------------------------


class KnownCommandsTest(unittest.TestCase):
    def test_console_in_known_commands(self):
        from ai.pojo_lens_agents.wizard import KNOWN_COMMANDS
        self.assertIn("console", KNOWN_COMMANDS)

    def test_preprocess_argv_passes_console_through(self):
        from ai.pojo_lens_agents.wizard import preprocess_argv
        result = preprocess_argv(["console"])
        self.assertEqual(result, ["console"])

    def test_preprocess_argv_passes_console_with_flags(self):
        from ai.pojo_lens_agents.wizard import preprocess_argv
        result = preprocess_argv(["console", "--runtime-root", "/tmp"])
        self.assertEqual(result, ["console", "--runtime-root", "/tmp"])


if __name__ == "__main__":
    unittest.main()
