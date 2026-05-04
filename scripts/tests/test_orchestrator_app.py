"""Regression tests for WP72: Orchestrator Core Coverage."""
from __future__ import annotations

import argparse
import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


# ---------------------------------------------------------------------------
# 1. OTEL endpoint validation — _assert_otel_endpoint
# ---------------------------------------------------------------------------

class OtelEndpointValidationTest(unittest.TestCase):
    """_assert_otel_endpoint rejects non-HTTP/HTTPS URLs before a run starts."""

    def _fn(self):
        from pojo_lens_agents.orchestrator_app import _assert_otel_endpoint
        return _assert_otel_endpoint

    def test_valid_http_accepted(self):
        self._fn()("http://collector.local:4318/v1/traces")

    def test_valid_https_accepted(self):
        self._fn()("https://otel.example.com/v1/traces")

    def test_none_accepted(self):
        self._fn()(None)

    def test_empty_string_accepted(self):
        self._fn()("")

    def test_bad_scheme_raises(self):
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError
        with self.assertRaises(OrchestratorError):
            self._fn()("grpc://collector:4317")

    def test_missing_netloc_raises(self):
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError
        with self.assertRaises(OrchestratorError):
            self._fn()("http://")

    def test_ftp_scheme_raises(self):
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError
        with self.assertRaises(OrchestratorError):
            self._fn()("ftp://files.example.com")

    def test_error_message_includes_offending_value(self):
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError
        with self.assertRaises(OrchestratorError) as ctx:
            self._fn()("grpc://bad-endpoint:4317")
        self.assertIn("grpc://bad-endpoint:4317", str(ctx.exception))

    def test_run_plan_raises_before_run_ops_when_otel_bad(self):
        """run_plan validates endpoint before delegating to run_ops_layer."""
        from pojo_lens_agents import orchestrator_app
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError

        args = mock.MagicMock()
        args.otel_endpoint = "grpc://bad"
        args.tui = False
        args.watch = False
        args.json = False

        # run_ops_layer.run_plan must NOT be called — error fires first
        with mock.patch("pojo_lens_agents.run_ops.run_plan") as mock_run_ops:
            with self.assertRaises(OrchestratorError) as ctx:
                orchestrator_app.run_plan(args)
        mock_run_ops.assert_not_called()
        self.assertIn("grpc://bad", str(ctx.exception))


# ---------------------------------------------------------------------------
# 2. wizard_command — dep injection keys
# ---------------------------------------------------------------------------

class WizardCommandDepsKeysTest(unittest.TestCase):
    """orchestrator_app.wizard_command passes the expected deps dict to wizard_layer."""

    EXPECTED_KEYS = {
        "root", "textual_available", "slugify", "write_json", "error_factory",
        "load_agents", "ensure_claude_available", "claude_command",
        "agent_payload_for_claude", "run_subprocess", "extract_json_payload",
        "inventory_handler", "validate_handler", "run_handler", "resume_handler",
        "retry_handler", "status_handler", "review_handler", "diff_run_handler",
        "promote_handler", "validate_run_handler", "default_task_timeout_sec",
    }

    def _capture_deps(self):
        """Calls orchestrator_app.wizard_command and returns the deps dict passed to wizard."""
        from pojo_lens_agents import orchestrator_app

        captured: dict = {}

        def fake_wizard_command(args, *, deps):
            captured.update(deps)
            return {
                "interactive": False,
                "usedTextual": False,
                "mode": "plan",
                "steps": [],
                "nextActions": [],
            }

        with mock.patch("pojo_lens_agents.wizard.wizard_command", fake_wizard_command):
            args = mock.MagicMock()
            args.runtime_root = "."
            args.json = True
            args.watch = False
            args.tui = False
            args.claude_bin = "claude"
            args.resume_run_ref = ""
            args.retry_run_ref = ""
            try:
                orchestrator_app.wizard_command(args)
            except Exception:
                pass

        return captured

    def test_all_expected_keys_present(self):
        deps = self._capture_deps()
        missing = self.EXPECTED_KEYS - set(deps.keys())
        self.assertEqual(set(), missing, f"Missing deps keys: {missing}")

    def test_exact_key_set_matches(self):
        deps = self._capture_deps()
        self.assertEqual(self.EXPECTED_KEYS, set(deps.keys()))

    def test_default_task_timeout_sec_is_numeric(self):
        deps = self._capture_deps()
        self.assertIsInstance(deps["default_task_timeout_sec"], (int, float))
        self.assertGreater(deps["default_task_timeout_sec"], 0)

    def test_root_is_path_instance(self):
        deps = self._capture_deps()
        self.assertIsInstance(deps["root"], Path)

    def test_error_factory_is_orchestrator_error(self):
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError
        deps = self._capture_deps()
        self.assertIs(deps["error_factory"], OrchestratorError)


# ---------------------------------------------------------------------------
# 3. --json flag suppresses interactive mode in wizard
# ---------------------------------------------------------------------------

class WizardJsonFlagSuppressesInteractiveTest(unittest.TestCase):
    """wizard.wizard_command sets interactive=False when args.json=True."""

    def _make_plan_root(self):
        td = tempfile.TemporaryDirectory()
        root = Path(td.name)
        tasks_dir = root / "ai" / "orchestrator" / "tasks"
        tasks_dir.mkdir(parents=True)
        plan_path = tasks_dir / "test-plan.json"
        plan_path.write_text(
            '{"version":1,"name":"test-plan","goal":"Test goal","tasks":[{"id":"t1"}]}',
            encoding="utf-8",
        )
        return td, root, str(plan_path.resolve())

    def _make_deps(self, root: Path):
        return {
            "root": root,
            "textual_available": lambda: False,
            "slugify": lambda t: t[:48],
            "write_json": lambda p, d: None,
            "error_factory": RuntimeError,
            "load_agents": lambda p: {},
            "ensure_claude_available": lambda b: None,
            "claude_command": lambda *a, **k: [],
            "agent_payload_for_claude": lambda a: {},
            "run_subprocess": lambda *a, **k: mock.MagicMock(stdout="", stderr="", returncode=0),
            "extract_json_payload": lambda t: {},
            "inventory_handler": lambda a: {"runs": []},
            "validate_handler": lambda a: {
                "planName": "test-plan",
                "taskCount": 1,
                "topology": {"maxParallelWidth": 1},
                "tasks": [{"id": "t1"}],
            },
            "run_handler": lambda a: {
                "runId": "run-test",
                "runDir": str(root / ".claude-orchestrator" / "runs" / "run-test"),
                "dryRun": True,
                "statusCounts": {"planned": 1},
                "usageTotals": {"totalCostUsd": 0.0},
            },
            "resume_handler": lambda a: {},
            "retry_handler": lambda a: {},
            "status_handler": lambda a: {},
            "review_handler": lambda a: {},
            "diff_run_handler": lambda a: {},
            "promote_handler": lambda a: {},
            "validate_run_handler": lambda a: {},
            "default_task_timeout_sec": 30,
        }

    def test_json_true_makes_interactive_false(self):
        from pojo_lens_agents import wizard

        td, root, _plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)

        payload = wizard.wizard_command(
            argparse.Namespace(
                json=True,
                watch=False,
                tui=False,
                dry_run=True,
                plan="",
                goal="",
                goal_words=[],
                resume_run_ref="",
                retry_run_ref="",
                runtime_root=str(root / ".claude-orchestrator"),
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                max_parallel=1,
                planner_agent="planner",
                verbose=False,
            ),
            deps=self._make_deps(root),
        )

        self.assertFalse(payload["interactive"])

    def test_json_false_with_non_tty_stdin_also_makes_interactive_false(self):
        """Even without --json, non-tty stdin/stderr in tests → interactive=False."""
        from pojo_lens_agents import wizard

        td, root, _plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)

        # In a test process stdin/stderr are not real ttys, so interactive=False
        # regardless of --json; verify the payload reflects that.
        payload = wizard.wizard_command(
            argparse.Namespace(
                json=False,
                watch=False,
                tui=False,
                dry_run=True,
                plan="",
                goal="",
                goal_words=[],
                resume_run_ref="",
                retry_run_ref="",
                runtime_root=str(root / ".claude-orchestrator"),
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                max_parallel=1,
                planner_agent="planner",
                verbose=False,
            ),
            deps=self._make_deps(root),
        )

        self.assertFalse(payload["interactive"])


# ---------------------------------------------------------------------------
# 4. dispatch_main — error propagation to CLI exit codes
# ---------------------------------------------------------------------------

class DispatchMainErrorPropagationTest(unittest.TestCase):
    """dispatch_main maps exception types to the correct non-zero exit codes."""

    def test_unknown_command_returns_exit_error(self):
        from pojo_lens_agents.command_dispatch import dispatch_main
        from pojo_lens_agents.orchestrator_contracts import EXIT_ERROR

        args = mock.MagicMock()
        args.command = "nonexistent-command"
        args.json = False

        with mock.patch("sys.stderr", io.StringIO()):
            code = dispatch_main(args, {})
        self.assertEqual(EXIT_ERROR, code)

    def test_orchestrator_error_from_handler_returns_exit_error(self):
        from pojo_lens_agents.command_dispatch import dispatch_main
        from pojo_lens_agents.orchestrator_contracts import EXIT_ERROR, OrchestratorError

        args = mock.MagicMock()
        args.command = "run"
        args.json = False

        def bad_handler(_args):
            raise OrchestratorError("something went wrong")

        with mock.patch("sys.stderr", io.StringIO()):
            code = dispatch_main(args, {"run": bad_handler})
        self.assertEqual(EXIT_ERROR, code)

    def test_unexpected_exception_from_handler_returns_exit_crash(self):
        from pojo_lens_agents.command_dispatch import dispatch_main
        from pojo_lens_agents.orchestrator_contracts import EXIT_CRASH

        args = mock.MagicMock()
        args.command = "run"
        args.json = False

        def crashing_handler(_args):
            raise RuntimeError("unhandled crash")

        with mock.patch("sys.stderr", io.StringIO()):
            code = dispatch_main(args, {"run": crashing_handler})
        self.assertEqual(EXIT_CRASH, code)

    def test_promotion_blocked_error_returns_exit_unsafe_promotion(self):
        from pojo_lens_agents.command_dispatch import dispatch_main
        from pojo_lens_agents.orchestrator_contracts import EXIT_UNSAFE_PROMOTION, PromotionBlockedError

        args = mock.MagicMock()
        args.command = "promote"
        args.json = False

        def blocked_handler(_args):
            raise PromotionBlockedError("unsafe to promote")

        with mock.patch("sys.stderr", io.StringIO()):
            code = dispatch_main(args, {"promote": blocked_handler})
        self.assertEqual(EXIT_UNSAFE_PROMOTION, code)

    def test_otel_error_in_run_plan_maps_to_exit_error_via_dispatch(self):
        """Bad --otel-endpoint → OrchestratorError → EXIT_ERROR through dispatch_main."""
        from pojo_lens_agents import orchestrator_app
        from pojo_lens_agents.command_dispatch import dispatch_main
        from pojo_lens_agents.orchestrator_contracts import EXIT_ERROR

        args = mock.MagicMock()
        args.command = "run"
        args.json = False
        args.otel_endpoint = "grpc://bad"

        with mock.patch("sys.stderr", io.StringIO()):
            code = dispatch_main(args, {"run": orchestrator_app.run_plan})
        self.assertEqual(EXIT_ERROR, code)


# ---------------------------------------------------------------------------
# schedule_command dispatch
# ---------------------------------------------------------------------------

class ScheduleCommandDispatchTest(unittest.TestCase):
    """schedule_command delegates to start_schedule / stop_schedule / get_schedule_status."""

    def _cmd(self):
        from pojo_lens_agents.orchestrator_app import schedule_command
        return schedule_command

    def _args(self, sub, **kwargs):
        import argparse
        ns = argparse.Namespace(schedule_command=sub, runtime_root=".rt", **kwargs)
        return ns

    def test_start_delegates_to_start_schedule(self):
        with mock.patch("pojo_lens_agents.schedule.start_schedule", return_value={"status": "completed"}) as m:
            result = self._cmd()(self._args("start"))
        m.assert_called_once()
        assert result == {"status": "completed"}

    def test_stop_delegates_to_stop_schedule(self):
        with mock.patch("pojo_lens_agents.schedule.stop_schedule", return_value={"status": "stopped"}) as m:
            result = self._cmd()(self._args("stop"))
        m.assert_called_once_with(".rt")
        assert result == {"status": "stopped"}

    def test_status_delegates_to_get_schedule_status(self):
        with mock.patch("pojo_lens_agents.schedule.get_schedule_status", return_value={"running": False}) as m:
            result = self._cmd()(self._args("status"))
        m.assert_called_once_with(".rt")
        assert result["running"] is False

    def test_unknown_subcommand_raises(self):
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError
        with self.assertRaises(OrchestratorError):
            self._cmd()(self._args("bogus"))

    def test_schedule_in_build_handlers(self):
        from pojo_lens_agents.orchestrator_app import _build_handlers
        assert "schedule" in _build_handlers()


if __name__ == "__main__":
    unittest.main()
