import pathlib
import tempfile
import unittest

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class HitlGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def _agent(self):
        orchestrator = self.orchestrator
        return orchestrator.AgentDefinition(
            name="analyst",
            description="Analyze.",
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

    def _plan(self, tasks, *, run_policy=None):
        orchestrator = self.orchestrator
        return orchestrator.TaskPlan(
            version=1,
            name="hitl-proof",
            goal="Prove HITL gates.",
            shared_context=orchestrator.SharedContext(
                summary="HITL test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=tasks,
            run_policy=run_policy or orchestrator.RunPolicy(),
        )

    def test_hitl_auto_approve_emits_gate_and_approval_events(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            plan_path = temp_path / "plan.json"
            agents_path = temp_path / "agents.json"
            plan_path.write_text("{}", encoding="utf-8")
            agents_path.write_text("{}", encoding="utf-8")
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="A.",
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Done.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": self._agent()},
                    self._plan([task]),
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    hitl=True,
                    hitl_mode="batch",
                    hitl_auto_approve=True,
                )
                sentinel_exists = (pathlib.Path(payload["runDir"]) / "hitl-gate.lock").exists()
            finally:
                orchestrator.execute_task = old_execute_task

        phases = [event["phase"] for event in payload["events"]]
        self.assertIn("hitl-gate", phases)
        self.assertIn("hitl-approved", phases)
        self.assertEqual({"completed": 1}, payload["statusCounts"])
        self.assertTrue(sentinel_exists)

    def test_hitl_abort_blocks_pending_tasks_and_records_event(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        old_wait = orchestrator.hitl_layer.wait_for_hitl_decision
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            plan_path = temp_path / "plan.json"
            agents_path = temp_path / "agents.json"
            plan_path.write_text("{}", encoding="utf-8")
            agents_path.write_text("{}", encoding="utf-8")
            task_a = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="inspect-b",
                title="Inspect B",
                agent="analyst",
                prompt="B.",
                depends_on=["inspect-a"],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Done.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            def fake_wait(context, *, auto_approve=False, write_text=None):
                return orchestrator.hitl_layer.HitlDecision(
                    approved=False,
                    action="abort",
                    reason="Test abort.",
                    source="test",
                    sentinel_path=str(context.run_dir / "hitl-gate.lock"),
                )

            orchestrator.execute_task = fake_execute_task
            orchestrator.hitl_layer.wait_for_hitl_decision = fake_wait
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": self._agent()},
                    self._plan([task_a, task_b]),
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    hitl=True,
                    hitl_mode="always",
                )
            finally:
                orchestrator.execute_task = old_execute_task
                orchestrator.hitl_layer.wait_for_hitl_decision = old_wait

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        phases = [event["phase"] for event in payload["events"]]
        self.assertIn("hitl-aborted", phases)
        self.assertEqual("blocked", tasks_by_id["inspect-b"]["status"])
        self.assertIn("HITL gate 'gate-001' aborted", tasks_by_id["inspect-b"]["summary"])
        self.assertEqual({"completed": 1, "blocked": 1}, payload["statusCounts"])


if __name__ == "__main__":
    unittest.main()
