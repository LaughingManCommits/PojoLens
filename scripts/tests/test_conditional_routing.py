"""Regression tests for WP64: Conditional Task Routing.

Covers:
- _check_follow_up_condition logic
- TaskDefinitionModel mutual-requirement validation for conditionField/conditionValue
- Integration: _inject_follow_up_tasks with condition met / not met
"""
from __future__ import annotations

import json
import pathlib
import tempfile
import unittest

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


def _load_run_ops():
    import importlib
    return importlib.import_module("pojo_lens_agents.run_ops")


def _load_model():
    import importlib
    return importlib.import_module("pojo_lens_agents.orchestrator_models").TaskDefinitionModel


# ---------------------------------------------------------------------------
# Unit tests: _check_follow_up_condition
# ---------------------------------------------------------------------------

class CheckFollowUpConditionTest(unittest.TestCase):
    """Unit tests for run_ops._check_follow_up_condition."""

    def _fn(self):
        return _load_run_ops()._check_follow_up_condition

    def _task(self, condition_field=None, condition_value=None):
        from types import SimpleNamespace
        return SimpleNamespace(condition_field=condition_field, condition_value=condition_value)

    def _record(self, **kwargs):
        from types import SimpleNamespace
        return SimpleNamespace(**kwargs)

    def test_no_condition_always_true(self):
        fn = self._fn()
        met, actual = fn(self._task(), self._record(status="blocked"))
        self.assertTrue(met)
        self.assertIsNone(actual)

    def test_status_match_exact(self):
        fn = self._fn()
        met, actual = fn(
            self._task(condition_field="status", condition_value="blocked"),
            self._record(status="blocked"),
        )
        self.assertTrue(met)
        self.assertEqual("blocked", actual)

    def test_status_no_match(self):
        fn = self._fn()
        met, actual = fn(
            self._task(condition_field="status", condition_value="blocked"),
            self._record(status="completed"),
        )
        self.assertFalse(met)
        self.assertEqual("completed", actual)

    def test_summary_substring_match(self):
        fn = self._fn()
        met, _ = fn(
            self._task(condition_field="summary", condition_value="FAIL"),
            self._record(summary="All tests FAIL on line 42."),
        )
        self.assertTrue(met)

    def test_case_insensitive_match(self):
        fn = self._fn()
        met, _ = fn(
            self._task(condition_field="status", condition_value="BLOCKED"),
            self._record(status="blocked"),
        )
        self.assertTrue(met)

    def test_unknown_field_returns_false(self):
        fn = self._fn()
        met, actual = fn(
            self._task(condition_field="nonexistent_field", condition_value="anything"),
            self._record(status="completed"),
        )
        self.assertFalse(met)
        self.assertIsNone(actual)

    def test_field_value_none_returns_false(self):
        fn = self._fn()
        met, actual = fn(
            self._task(condition_field="model", condition_value="opus"),
            self._record(model=None),
        )
        self.assertFalse(met)
        self.assertIsNone(actual)

    def test_summary_no_match(self):
        fn = self._fn()
        met, _ = fn(
            self._task(condition_field="summary", condition_value="FAIL"),
            self._record(summary="All checks passed."),
        )
        self.assertFalse(met)


# ---------------------------------------------------------------------------
# Pydantic model validation: conditionField/conditionValue mutual requirement
# ---------------------------------------------------------------------------

class ConditionModelValidationTest(unittest.TestCase):
    """TaskDefinitionModel rejects mismatched conditionField/conditionValue."""

    def _base(self):
        return {
            "id": "fix-task",
            "title": "Fix it",
            "agent": "implementer",
            "prompt": "Do the fix.",
        }

    def test_both_absent_valid(self):
        model = _load_model().model_validate(self._base())
        self.assertIsNone(model.condition_field)
        self.assertIsNone(model.condition_value)

    def test_both_present_valid(self):
        data = {**self._base(), "conditionField": "status", "conditionValue": "blocked"}
        model = _load_model().model_validate(data)
        self.assertEqual("status", model.condition_field)
        self.assertEqual("blocked", model.condition_value)

    def test_only_condition_field_rejected(self):
        from pydantic import ValidationError
        data = {**self._base(), "conditionField": "status"}
        with self.assertRaises(ValidationError) as ctx:
            _load_model().model_validate(data)
        self.assertIn("conditionField and conditionValue", str(ctx.exception))

    def test_only_condition_value_rejected(self):
        from pydantic import ValidationError
        data = {**self._base(), "conditionValue": "blocked"}
        with self.assertRaises(ValidationError) as ctx:
            _load_model().model_validate(data)
        self.assertIn("conditionField and conditionValue", str(ctx.exception))


# ---------------------------------------------------------------------------
# Integration tests: full _inject_follow_up_tasks with conditional routing
# ---------------------------------------------------------------------------

class ConditionalRoutingIntegrationTest(unittest.TestCase):
    """Integration: conditional follow-up task injection via run_loaded_plan."""

    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def _make_agents(self, orchestrator):
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        implementer = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
            prompt="Return JSON only.",
            model_profile="balanced",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read", "Write"],
            disallowed_tools=[],
        )
        return {"analyst": analyst, "implementer": implementer}

    def _make_plan(self, orchestrator):
        task = orchestrator.TaskDefinition(
            id="review-a",
            title="Review A",
            agent="analyst",
            prompt="Review and block if findings exist.",
        )
        return orchestrator.TaskPlan(
            version=1,
            name="conditional-routing",
            goal="Test conditional follow-up routing.",
            shared_context=orchestrator.SharedContext(
                summary="Conditional routing integration test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

    def _run_plan(self, orchestrator, agents, plan, task_status, follow_up_tasks):
        """Run the plan with a fake executor that returns given status and follow-up tasks."""
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")

            async def fake_execute_task(
                run_dir, runtime_root, workspaces_dir, plan, agents, task,
                dependency_records, *, claude_bin, agents_json, dry_run,
                worker_validation_mode=None, effort_override=None,
            ):
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status=task_status,
                    summary="Test run.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.follow_up_tasks = follow_up_tasks
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    agents,
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    follow_up_behavior_override="inject",
                )
            finally:
                orchestrator.execute_task = old_execute_task
        return payload

    def test_condition_met_injects_task(self):
        o = self.orchestrator
        agents = self._make_agents(o)
        plan = self._make_plan(o)
        payload = self._run_plan(
            o, agents, plan,
            task_status="blocked",
            follow_up_tasks=[{
                "id": "fix-task",
                "title": "Apply fix",
                "agent": "implementer",
                "prompt": "Fix the blocking issue.",
                "writePaths": ["docs/fix.md"],
                "conditionField": "status",
                "conditionValue": "blocked",
            }],
        )
        task_ids = [t["id"] for t in payload["tasks"]]
        self.assertIn("fix-task", task_ids)
        injected_events = [e for e in payload["events"] if e["phase"] == "task-injected"]
        self.assertEqual(["fix-task"], [e["taskId"] for e in injected_events])
        skipped_events = [e for e in payload["events"] if e["phase"] == "task-injection-skipped"]
        self.assertEqual([], skipped_events)

    def test_condition_not_met_skips_task(self):
        o = self.orchestrator
        agents = self._make_agents(o)
        plan = self._make_plan(o)
        payload = self._run_plan(
            o, agents, plan,
            task_status="completed",
            follow_up_tasks=[{
                "id": "fix-task",
                "title": "Apply fix",
                "agent": "implementer",
                "prompt": "Fix the blocking issue.",
                "writePaths": ["docs/fix.md"],
                "conditionField": "status",
                "conditionValue": "blocked",
            }],
        )
        task_ids = [t["id"] for t in payload["tasks"]]
        self.assertNotIn("fix-task", task_ids)
        injected_events = [e for e in payload["events"] if e["phase"] == "task-injected"]
        self.assertEqual([], injected_events)
        skipped_events = [e for e in payload["events"] if e["phase"] == "task-injection-skipped"]
        self.assertEqual(1, len(skipped_events))
        details = skipped_events[0]["details"]
        self.assertEqual("status", details["conditionField"])
        self.assertEqual("blocked", details["conditionValue"])
        self.assertEqual("completed", details["actualValue"])

    def test_no_condition_always_injects(self):
        o = self.orchestrator
        agents = self._make_agents(o)
        plan = self._make_plan(o)
        payload = self._run_plan(
            o, agents, plan,
            task_status="completed",
            follow_up_tasks=[{
                "id": "follow-up",
                "title": "Always follow",
                "agent": "implementer",
                "prompt": "Always run.",
                "writePaths": ["docs/output.md"],
            }],
        )
        task_ids = [t["id"] for t in payload["tasks"]]
        self.assertIn("follow-up", task_ids)
        injected_events = [e for e in payload["events"] if e["phase"] == "task-injected"]
        self.assertEqual(["follow-up"], [e["taskId"] for e in injected_events])

    def test_mixed_conditions_only_met_ones_inject(self):
        """Two follow-up tasks: one condition met, one not met → one injected, one skipped."""
        o = self.orchestrator
        agents = self._make_agents(o)
        plan = self._make_plan(o)
        payload = self._run_plan(
            o, agents, plan,
            task_status="blocked",
            follow_up_tasks=[
                {
                    "id": "fix-blocked",
                    "title": "Fix blocked",
                    "agent": "implementer",
                    "prompt": "Fix.",
                    "writePaths": ["docs/fix.md"],
                    "conditionField": "status",
                    "conditionValue": "blocked",
                },
                {
                    "id": "post-success",
                    "title": "Post success",
                    "agent": "implementer",
                    "prompt": "Post-success step.",
                    "writePaths": ["docs/post.md"],
                    "conditionField": "status",
                    "conditionValue": "completed",
                },
            ],
        )
        task_ids = [t["id"] for t in payload["tasks"]]
        self.assertIn("fix-blocked", task_ids)
        self.assertNotIn("post-success", task_ids)
        injected = [e["taskId"] for e in payload["events"] if e["phase"] == "task-injected"]
        skipped = [e for e in payload["events"] if e["phase"] == "task-injection-skipped"]
        self.assertEqual(["fix-blocked"], injected)
        self.assertEqual(1, len(skipped))

    def test_skipped_event_details_include_actual_value(self):
        """Skipped event details expose conditionField, conditionValue, and actualValue."""
        o = self.orchestrator
        agents = self._make_agents(o)
        plan = self._make_plan(o)
        payload = self._run_plan(
            o, agents, plan,
            task_status="completed",
            follow_up_tasks=[{
                "id": "never-runs",
                "title": "Never",
                "agent": "implementer",
                "prompt": "Blocked only.",
                "writePaths": ["docs/never.md"],
                "conditionField": "status",
                "conditionValue": "blocked",
            }],
        )
        skipped = [e for e in payload["events"] if e["phase"] == "task-injection-skipped"]
        self.assertEqual(1, len(skipped))
        d = skipped[0]["details"]
        self.assertEqual("status", d["conditionField"])
        self.assertEqual("blocked", d["conditionValue"])
        self.assertEqual("completed", d["actualValue"])
        self.assertEqual("review-a", d["emitterTaskId"])
        self.assertEqual(1, d["followUpIndex"])


if __name__ == "__main__":
    unittest.main()
