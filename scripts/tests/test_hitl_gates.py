import json
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


class AlwaysModeGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hitl = load_orchestrator_module().hitl_layer

    def _policy(self, mode):
        return self.hitl.HitlPolicy(enabled=True, mode=mode)

    def test_always_fires_on_batch_0(self):
        self.assertTrue(
            self.hitl.should_trigger_hitl_gate(self._policy("always"), batch_index=0, failed_task_ids=[])
        )

    def test_always_fires_on_batch_1(self):
        self.assertTrue(
            self.hitl.should_trigger_hitl_gate(self._policy("always"), batch_index=1, failed_task_ids=[])
        )

    def test_always_fires_on_batch_5(self):
        self.assertTrue(
            self.hitl.should_trigger_hitl_gate(self._policy("always"), batch_index=5, failed_task_ids=[])
        )

    def test_batch_mode_fires_on_every_index(self):
        for idx in (0, 1, 2, 10):
            self.assertTrue(
                self.hitl.should_trigger_hitl_gate(self._policy("batch"), batch_index=idx, failed_task_ids=[]),
                f"batch mode should fire on batch_index={idx}",
            )

    def test_on_failure_no_failures_returns_false(self):
        self.assertFalse(
            self.hitl.should_trigger_hitl_gate(self._policy("on-failure"), batch_index=2, failed_task_ids=[])
        )

    def test_on_failure_with_failures_returns_true(self):
        self.assertTrue(
            self.hitl.should_trigger_hitl_gate(self._policy("on-failure"), batch_index=2, failed_task_ids=["t1"])
        )

    def test_disabled_never_fires(self):
        disabled = self.hitl.HitlPolicy(enabled=False, mode="always")
        self.assertFalse(
            self.hitl.should_trigger_hitl_gate(disabled, batch_index=1, failed_task_ids=[])
        )


class StaleSentinelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hitl = load_orchestrator_module().hitl_layer

    def _write_sentinel(self, tmp_dir, content):
        p = pathlib.Path(tmp_dir) / "hitl-gate.lock"
        p.write_text(content, encoding="utf-8")
        return p

    def test_matching_gate_id_json_approve(self):
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, json.dumps({"gateId": "gate-001", "action": "approve"}))
            self.assertEqual("approve", self.hitl._sentinel_action(p, expected_gate_id="gate-001"))

    def test_matching_gate_id_json_abort(self):
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, json.dumps({"gateId": "gate-001", "action": "abort"}))
            self.assertEqual("abort", self.hitl._sentinel_action(p, expected_gate_id="gate-001"))

    def test_mismatched_gate_id_returns_none(self):
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, json.dumps({"gateId": "gate-001", "action": "approve"}))
            self.assertIsNone(self.hitl._sentinel_action(p, expected_gate_id="gate-002"))

    def test_json_no_gate_id_field_with_expected_id_reads_action(self):
        # JSON without gateId claim — cannot tell if stale, so read action
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, json.dumps({"action": "approve"}))
            self.assertEqual("approve", self.hitl._sentinel_action(p, expected_gate_id="gate-001"))

    def test_no_expected_gate_id_reads_action_regardless(self):
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, json.dumps({"gateId": "gate-999", "action": "approve"}))
            self.assertEqual("approve", self.hitl._sentinel_action(p, expected_gate_id=None))

    def test_plain_text_approve_backwards_compat(self):
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, "approve")
            self.assertEqual("approve", self.hitl._sentinel_action(p, expected_gate_id="gate-001"))

    def test_plain_text_abort_backwards_compat(self):
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, "abort")
            self.assertEqual("abort", self.hitl._sentinel_action(p, expected_gate_id="gate-001"))

    def test_json_no_action_field_returns_none(self):
        with tempfile.TemporaryDirectory() as d:
            p = self._write_sentinel(d, json.dumps({"gateId": "gate-001", "mode": "always"}))
            self.assertIsNone(self.hitl._sentinel_action(p, expected_gate_id="gate-001"))

    def test_missing_file_returns_none(self):
        p = pathlib.Path(tempfile.mkdtemp()) / "nonexistent.lock"
        self.assertIsNone(self.hitl._sentinel_action(p, expected_gate_id="gate-001"))

    def test_stale_json_gate_id_ignored_in_polling(self):
        # Simulate: disk has old gate-001 approve; new gate gate-002 fires with write_text injection.
        # Polling should ignore stale file until operator updates it.
        with tempfile.TemporaryDirectory() as d:
            hitl = self.hitl
            stale_path = pathlib.Path(d) / "hitl-gate.lock"
            stale_path.write_text(
                json.dumps({"gateId": "gate-001", "action": "approve"}), encoding="utf-8"
            )
            context = hitl.HitlGateContext(
                gate_id="gate-002",
                mode="always",
                batch_index=2,
                completed_batch_task_ids=["t1"],
                failed_task_ids=[],
                pending_task_ids=["t2"],
                run_dir=pathlib.Path(d),
                dry_run=False,
            )
            calls = [0]

            def fake_sleep(s):
                calls[0] += 1
                if calls[0] == 1:
                    # After first poll (stale ignored), operator writes correct gate
                    stale_path.write_text(
                        json.dumps({"gateId": "gate-002", "action": "approve"}), encoding="utf-8"
                    )

            captured_writes = {}

            def fake_write_text(path, text):
                captured_writes[str(path)] = text

            import io
            decision = hitl.wait_for_hitl_decision(
                context,
                write_text=fake_write_text,
                sleep=fake_sleep,
                output_stream=io.StringIO(),
            )
            self.assertTrue(decision.approved)
            self.assertEqual("sentinel", decision.source)
            self.assertEqual(1, calls[0])


class AlwaysModeMultiBatchTest(unittest.TestCase):
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

    def _plan(self, tasks):
        orchestrator = self.orchestrator
        return orchestrator.TaskPlan(
            version=1,
            name="always-multi",
            goal="Prove always mode fires every batch.",
            shared_context=orchestrator.SharedContext(
                summary="HITL always test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=tasks,
            run_policy=orchestrator.RunPolicy(),
        )

    def test_always_mode_fires_on_every_batch(self):
        orchestrator = self.orchestrator
        old_execute = orchestrator.execute_task
        old_wait = orchestrator.hitl_layer.wait_for_hitl_decision

        gate_ids_seen = []

        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            plan_path = temp_path / "plan.json"
            agents_path = temp_path / "agents.json"
            plan_path.write_text("{}", encoding="utf-8")
            agents_path.write_text("{}", encoding="utf-8")

            # 3 tasks in a chain: A → B → C, each its own batch
            task_a = orchestrator.TaskDefinition(id="t-a", title="A", agent="analyst", prompt="A.")
            task_b = orchestrator.TaskDefinition(id="t-b", title="B", agent="analyst", prompt="B.", depends_on=["t-a"])
            task_c = orchestrator.TaskDefinition(id="t-c", title="C", agent="analyst", prompt="C.", depends_on=["t-b"])

            async def fake_execute_task(
                run_dir, runtime_root, workspaces_dir, plan, agents, task, dependency_records,
                *, claude_bin, agents_json, dry_run, worker_validation_mode=None, effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator, task, status="completed", summary="Done.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            def counting_wait(context, *, auto_approve=False, write_text=None):
                gate_ids_seen.append(context.gate_id)
                return orchestrator.hitl_layer.HitlDecision(
                    approved=True, action="approve", reason="test auto", source="test",
                    sentinel_path=str(context.run_dir / "hitl-gate.lock"),
                )

            orchestrator.execute_task = fake_execute_task
            orchestrator.hitl_layer.wait_for_hitl_decision = counting_wait
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path, agents_path,
                    {"analyst": self._agent()},
                    self._plan([task_a, task_b, task_c]),
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    hitl=True,
                    hitl_mode="always",
                )
            finally:
                orchestrator.execute_task = old_execute
                orchestrator.hitl_layer.wait_for_hitl_decision = old_wait

        self.assertEqual({"completed": 3}, payload["statusCounts"])
        # Gate fires after each batch: gate-001, gate-002, gate-003
        self.assertEqual(3, len(gate_ids_seen), f"expected 3 gate firings, got {gate_ids_seen}")
        self.assertIn("gate-001", gate_ids_seen)
        self.assertIn("gate-002", gate_ids_seen)
        self.assertIn("gate-003", gate_ids_seen)


if __name__ == "__main__":
    unittest.main()
