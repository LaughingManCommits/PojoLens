"""WP62: Hard Budget Cap Enforcement — regression tests."""
from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest
from dataclasses import dataclass
from typing import Any
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import governance, run_summary
from pojo_lens_agents.orchestrator_contracts import (
    EXIT_BLOCKED,
    EXIT_BUDGET_EXCEEDED,
    EXIT_SUCCESS,
    EXIT_WORKER_FAILURE,
)
from pojo_lens_agents.command_dispatch import _worker_run_exit_code


# ---------------------------------------------------------------------------
# Shared test fixtures
# ---------------------------------------------------------------------------

@dataclass
class Record:
    id: str
    status: str = "completed"
    model: str | None = "model-a"
    prompt_estimated_tokens: int = 0
    usage: dict[str, Any] | None = None
    stdout_bytes: int = 0
    stderr_bytes: int = 0
    result_bytes: int = 0


@dataclass
class Policy:
    run_budget_usd: float | None = None
    budget_behavior: str = "stop"
    max_task_stdout_bytes: int | None = None
    max_task_stderr_bytes: int | None = None
    max_task_result_bytes: int | None = None
    artifact_behavior: str = "warn"


def _make_record(task_id: str, cost: float = 0.0, status: str = "completed") -> Record:
    return Record(
        task_id,
        status=status,
        usage={"inputTokens": 1, "outputTokens": 1, "totalCostUsd": cost} if cost > 0 else None,
    )


def _eval_gov(records, run_budget_usd, budget_behavior="stop", *, max_result=None):
    return governance.evaluate_run_governance(
        records,
        Policy(run_budget_usd=run_budget_usd, budget_behavior=budget_behavior, max_task_result_bytes=max_result),
        serialize_run_policy=lambda p: {"runBudgetUsd": p.run_budget_usd},
        top_task_limit=3,
    )


# ---------------------------------------------------------------------------
# 1. Governance — budget alert detection
# ---------------------------------------------------------------------------

class BudgetGovernanceTest(unittest.TestCase):
    def test_no_budget_configured_no_alert(self):
        records = {"a": _make_record("a", cost=100.0)}
        result = _eval_gov(records, run_budget_usd=None)
        self.assertEqual("ok", result["status"])
        self.assertEqual(0, result["alertCount"])
        self.assertFalse(result["shouldStopScheduling"])

    def test_cost_below_budget_no_alert(self):
        records = {"a": _make_record("a", cost=0.49)}
        result = _eval_gov(records, run_budget_usd=0.50)
        self.assertEqual("ok", result["status"])
        self.assertFalse(result["shouldStopScheduling"])

    def test_cost_equals_budget_fires_stop(self):
        records = {"a": _make_record("a", cost=0.50)}
        result = _eval_gov(records, run_budget_usd=0.50)
        self.assertEqual("stop", result["status"])
        self.assertTrue(result["shouldStopScheduling"])
        budget_alerts = [a for a in result["blockingAlerts"] if a.get("kind") == "budget"]
        self.assertEqual(1, len(budget_alerts))
        self.assertAlmostEqual(0.50, budget_alerts[0]["actualUsd"], places=5)
        self.assertAlmostEqual(0.50, budget_alerts[0]["limitUsd"], places=5)

    def test_cost_exceeds_budget_fires_stop(self):
        records = {"a": _make_record("a", cost=1.25)}
        result = _eval_gov(records, run_budget_usd=0.50)
        self.assertEqual("stop", result["status"])
        self.assertTrue(result["shouldStopScheduling"])
        budget_alerts = [a for a in result["blockingAlerts"] if a.get("kind") == "budget"]
        self.assertEqual(1, len(budget_alerts))

    def test_warn_mode_does_not_block_scheduling(self):
        records = {"a": _make_record("a", cost=2.0)}
        result = _eval_gov(records, run_budget_usd=0.50, budget_behavior="warn")
        self.assertEqual("warn", result["status"])
        self.assertFalse(result["shouldStopScheduling"])
        warn_alerts = [a for a in result["alerts"] if a.get("kind") == "budget"]
        self.assertEqual(1, len(warn_alerts))

    def test_budget_alert_kind_field(self):
        records = {"a": _make_record("a", cost=1.0)}
        result = _eval_gov(records, run_budget_usd=0.50)
        alert = result["blockingAlerts"][0]
        self.assertEqual("budget", alert["kind"])
        self.assertEqual("stop", alert["severity"])


# ---------------------------------------------------------------------------
# 2. Exit codes
# ---------------------------------------------------------------------------

class ExitCodeTest(unittest.TestCase):
    def test_no_issues_success(self):
        self.assertEqual(EXIT_SUCCESS, _worker_run_exit_code({}))

    def test_failed_tasks_exit_worker_failure(self):
        self.assertEqual(EXIT_WORKER_FAILURE, _worker_run_exit_code({"failed": 1}))

    def test_budget_exceeded_exit_budget_exceeded(self):
        self.assertEqual(EXIT_BUDGET_EXCEEDED, _worker_run_exit_code({"blocked": 2}, budget_exceeded=True))

    def test_budget_exceeded_takes_priority_over_blocked(self):
        # Budget stop should be more specific than plain blocked
        code = _worker_run_exit_code({"blocked": 3}, budget_exceeded=True)
        self.assertEqual(EXIT_BUDGET_EXCEEDED, code)
        self.assertNotEqual(EXIT_BLOCKED, code)

    def test_failed_takes_priority_over_budget_exceeded(self):
        # A run with failures AND budget stop: failures win
        code = _worker_run_exit_code({"failed": 1, "blocked": 2}, budget_exceeded=True)
        self.assertEqual(EXIT_WORKER_FAILURE, code)

    def test_blocked_without_budget_exit_blocked(self):
        self.assertEqual(EXIT_BLOCKED, _worker_run_exit_code({"blocked": 1}))

    def test_exit_budget_exceeded_constant_is_8(self):
        self.assertEqual(8, EXIT_BUDGET_EXCEEDED)


# ---------------------------------------------------------------------------
# 3. Lifecycle state — derive_run_lifecycle_state
# ---------------------------------------------------------------------------

class BudgetLifecycleStateTest(unittest.TestCase):
    def _derive(self, *, has_failures=False, has_blocked=False, is_resumable=False, budget_exceeded=False):
        return run_summary.derive_run_lifecycle_state(
            records=[],
            summary_base={
                "hasFailures": has_failures,
                "hasBlocked": has_blocked,
                "isResumable": is_resumable,
                "budgetExceeded": budget_exceeded,
            },
            promotion_readiness={"allowed": False, "filesPromotable": 0},
            approval_summary={
                "reviewRecorded": False,
                "validationRecorded": False,
                "validationPassed": None,
                "validationExecutionScope": None,
                "validationGeneratedAt": None,
                "promotionRecorded": False,
                "promotionAllowed": None,
                "promotionApplied": False,
                "promotionGeneratedAt": None,
                "promotionFilesPromoted": None,
                "promotionSummaryPath": None,
                "reviewSummaryPath": None,
                "validationSummaryPath": None,
            },
        )

    def test_budget_exceeded_with_blocked_returns_budget_exceeded_state(self):
        state, reason = self._derive(has_blocked=True, budget_exceeded=True)
        self.assertEqual("budget_exceeded", state)
        self.assertIn("runBudgetUsd", reason)

    def test_budget_exceeded_without_blocked_falls_through_to_completed(self):
        # Edge: budget flag set but no blocked tasks (all ran before cap hit) → completed path
        state, _ = self._derive(has_blocked=False, budget_exceeded=True)
        self.assertEqual("completed", state)

    def test_failed_takes_priority_over_budget_exceeded(self):
        state, _ = self._derive(has_failures=True, has_blocked=True, budget_exceeded=True)
        self.assertEqual("failed", state)

    def test_plain_blocked_no_budget(self):
        state, _ = self._derive(has_blocked=True, budget_exceeded=False)
        self.assertEqual("blocked", state)

    def test_resumable_no_budget(self):
        state, _ = self._derive(is_resumable=True)
        self.assertEqual("awaiting_execution", state)

    def test_no_issues_completed(self):
        state, _ = self._derive()
        self.assertEqual("completed", state)


# ---------------------------------------------------------------------------
# 4. summarize_run_manifest — budget_exceeded flag and lifecycle state
# ---------------------------------------------------------------------------

class BudgetManifestSummaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from scripts.tests.test_claude_orchestrator_helpers import load_orchestrator_module
        cls.orchestrator = load_orchestrator_module()

    def _write_manifest(self, run_dir, tasks_dict, run_governance=None):
        manifest_path = run_dir / "manifest.json"
        payload = {"runId": "run-budget-test", "tasks": tasks_dict}
        if run_governance is not None:
            payload["runGovernance"] = run_governance
        self.orchestrator.write_json(manifest_path, payload)
        return manifest_path, payload

    def _blocked_task_dict(self, task_id):
        return {
            "id": task_id,
            "title": task_id,
            "agent": "implementer",
            "status": "blocked",
            "summary": "Budget cap exceeded; task was not run.",
            "workspace_mode": "copy",
            "workspace_path": "",
            "started_at": "2026-05-01T00:00:00+00:00",
            "finished_at": "2026-05-01T00:00:01+00:00",
            "files_touched": [],
            "actual_files_touched": [],
            "protected_path_violations": [],
            "validation_commands": [],
            "follow_ups": [],
            "notes": [],
            "model": None,
            "model_profile": None,
            "prompt_chars": 0,
            "prompt_estimated_tokens": 0,
            "prompt_sections": [],
            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
            "usage": None,
            "return_code": None,
            "prompt_path": "",
            "command_path": "",
            "stdout_path": None,
            "stderr_path": None,
            "result_path": None,
        }

    def _run_governance_budget_stop(self, actual_usd=1.5, limit_usd=0.5):
        return {
            "status": "stop",
            "alertCount": 1,
            "blockingAlertCount": 1,
            "alerts": [{"kind": "budget", "severity": "stop", "actualUsd": actual_usd, "limitUsd": limit_usd}],
            "blockingAlerts": [{"kind": "budget", "severity": "stop", "actualUsd": actual_usd, "limitUsd": limit_usd}],
            "shouldStopScheduling": True,
        }

    def test_budget_exceeded_lifecycle_state_in_summary(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tmpdir:
            run_dir = pathlib.Path(tmpdir) / "run"
            run_dir.mkdir()
            manifest_path, manifest = self._write_manifest(
                run_dir,
                {"t2": self._blocked_task_dict("t2")},
                run_governance=self._run_governance_budget_stop(),
            )
            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)
        self.assertEqual("budget_exceeded", summary["lifecycleState"])

    def test_budget_exceeded_flag_in_flags(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tmpdir:
            run_dir = pathlib.Path(tmpdir) / "run"
            run_dir.mkdir()
            manifest_path, manifest = self._write_manifest(
                run_dir,
                {"t2": self._blocked_task_dict("t2")},
                run_governance=self._run_governance_budget_stop(),
            )
            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)
        self.assertIn("budget-exceeded", summary["flags"])
        self.assertIn("state:budget_exceeded", summary["flags"])

    def test_no_budget_governance_no_flag(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tmpdir:
            run_dir = pathlib.Path(tmpdir) / "run"
            run_dir.mkdir()
            manifest_path, manifest = self._write_manifest(
                run_dir,
                {"t2": self._blocked_task_dict("t2")},
                run_governance={"status": "ok", "alertCount": 0, "blockingAlertCount": 0, "alerts": [], "blockingAlerts": [], "shouldStopScheduling": False},
            )
            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)
        self.assertNotIn("budget-exceeded", summary["flags"])
        self.assertEqual("blocked", summary["lifecycleState"])

    def test_governance_warn_mode_no_budget_exceeded_state(self):
        """Warn-mode budget alerts are not blocking — lifecycle should NOT be budget_exceeded."""
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tmpdir:
            run_dir = pathlib.Path(tmpdir) / "run"
            run_dir.mkdir()
            warn_gov = {
                "status": "warn",
                "alertCount": 1,
                "blockingAlertCount": 0,
                "alerts": [{"kind": "budget", "severity": "warn", "actualUsd": 1.0, "limitUsd": 0.5}],
                "blockingAlerts": [],
                "shouldStopScheduling": False,
            }
            manifest_path, manifest = self._write_manifest(
                run_dir,
                {"t2": self._blocked_task_dict("t2")},
                run_governance=warn_gov,
            )
            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)
        self.assertNotIn("budget-exceeded", summary["flags"])
        self.assertNotEqual("budget_exceeded", summary["lifecycleState"])


# ---------------------------------------------------------------------------
# 5. run_plan estimate path — budget warning
# ---------------------------------------------------------------------------

class EstimateBudgetWarningTest(unittest.TestCase):
    def _run_estimate(self, total_max_usd, run_budget_usd):
        from pojo_lens_agents import run_ops
        from pojo_lens_agents.orchestrator_contracts import RunPolicy, TaskPlan, SharedContext

        plan = TaskPlan(
            version=1,
            name="test-plan",
            goal="test",
            shared_context=SharedContext(summary="", constraints=[], read_paths=[], validation=[]),
            tasks=[],
            run_policy=RunPolicy(run_budget_usd=run_budget_usd),
        )
        mock_args = mock.MagicMock()
        mock_args.estimate = True
        mock_args.agents = "/fake/agents.json"
        mock_args.task_plan = "/fake/plan.json"
        mock_args.selected_tasks = []

        def fake_load_agents(_path):
            return {}

        def fake_load_plan(_path, _agents):
            return plan

        def fake_selected_plan(loaded, selected):
            return loaded

        def fake_output_profiles(_plan, _agents):
            return {}

        def fake_output_profile_sources(_plan, _agents):
            return {}

        def fake_efforts(_plan, _agents, **_kw):
            return {}

        def fake_effort_sources(_plan, _agents):
            return {}

        def fake_models(_plan, _agents):
            return {}

        def fake_model_profiles(_plan, _agents):
            return {}

        def fake_skills(_task, _agent):
            return []

        def fake_complex_models(_profiles):
            return []

        def fake_topology(_plan, _agents):
            return {"warnings": [], "warningCount": 0}

        def fake_serialize_policy(p):
            return {"runBudgetUsd": p.run_budget_usd}

        def fake_pricing():
            return {}

        def fake_estimate(_plan, _agents, **_kw):
            return {"totalMaxUsd": total_max_usd, "warnings": []}

        return run_ops.run_plan(
            mock_args,
            load_agents=fake_load_agents,
            load_task_plan=fake_load_plan,
            selected_plan=fake_selected_plan,
            run_loaded_plan_fn=None,
            effective_plan_output_profiles=fake_output_profiles,
            effective_plan_output_profile_sources=fake_output_profile_sources,
            effective_plan_efforts=fake_efforts,
            effective_plan_effort_sources=fake_effort_sources,
            effective_plan_models=fake_models,
            effective_plan_model_profiles=fake_model_profiles,
            effective_task_skills=fake_skills,
            complex_model_task_ids=fake_complex_models,
            analyze_plan_topology=fake_topology,
            serialize_run_policy=fake_serialize_policy,
            load_model_pricing=fake_pricing,
            estimate_plan_cost=fake_estimate,
        )

    def test_estimate_under_budget_no_warning(self):
        payload = self._run_estimate(total_max_usd=0.30, run_budget_usd=0.50)
        self.assertIsNone(payload.get("estimateBudgetWarning"))

    def test_estimate_over_budget_warning_present(self):
        payload = self._run_estimate(total_max_usd=0.75, run_budget_usd=0.50)
        warning = payload.get("estimateBudgetWarning")
        self.assertIsNotNone(warning)
        self.assertIn("0.7500", warning)
        self.assertIn("0.5000", warning)

    def test_estimate_no_budget_configured_no_warning(self):
        payload = self._run_estimate(total_max_usd=999.0, run_budget_usd=None)
        self.assertIsNone(payload.get("estimateBudgetWarning"))

    def test_estimate_exactly_at_budget_no_warning(self):
        # totalMaxUsd == runBudgetUsd: strictly > check, so no warning at equality
        payload = self._run_estimate(total_max_usd=0.50, run_budget_usd=0.50)
        self.assertIsNone(payload.get("estimateBudgetWarning"))

    def test_estimate_payload_is_estimated_only(self):
        payload = self._run_estimate(total_max_usd=0.1, run_budget_usd=0.5)
        self.assertTrue(payload.get("estimatedOnly"))


# ---------------------------------------------------------------------------
# 6. dispatch_main — budget_exceeded propagates through payload
# ---------------------------------------------------------------------------

class DispatchBudgetExitCodeTest(unittest.TestCase):
    def test_dispatch_main_budget_exceeded_payload_returns_exit_8(self):
        from pojo_lens_agents.command_dispatch import dispatch_main
        import io
        import sys

        captured = io.StringIO()
        args = mock.MagicMock()
        args.command = "run"
        args.json = False

        def fake_run_handler(_args):
            return {
                "statusCounts": {"blocked": 3},
                "budgetExceeded": True,
                "_consoleText": "run complete",
            }

        with mock.patch("sys.stdout", captured):
            exit_code = dispatch_main(args, {"run": fake_run_handler})
        self.assertEqual(EXIT_BUDGET_EXCEEDED, exit_code)

    def test_dispatch_main_no_budget_blocked_returns_exit_5(self):
        from pojo_lens_agents.command_dispatch import dispatch_main
        import io

        args = mock.MagicMock()
        args.command = "run"
        args.json = False

        def fake_run_handler(_args):
            return {"statusCounts": {"blocked": 3}, "budgetExceeded": False}

        with mock.patch("sys.stdout", io.StringIO()):
            exit_code = dispatch_main(args, {"run": fake_run_handler})
        self.assertEqual(EXIT_BLOCKED, exit_code)


if __name__ == "__main__":
    unittest.main()
