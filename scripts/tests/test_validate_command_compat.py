import json
import pathlib
import tempfile
import unittest
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandCompatTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_coerce_task_run_record_rejects_compat_worker_validation_mode(self):
        orchestrator = self.orchestrator
        payload = {
            "id": "inspect",
            "title": "Inspect",
            "agent": "analyst",
            "status": "completed",
            "summary": "Inspection done.",
            "workspace_mode": "copy",
            "workspace_path": "",
            "started_at": "2026-04-04T00:00:00+00:00",
            "finished_at": "2026-04-04T00:00:01+00:00",
            "files_touched": [],
            "actual_files_touched": [],
            "protected_path_violations": [],
            "validation_commands": [],
            "follow_ups": [],
            "notes": [],
            "model": "claude-haiku-4-5",
            "model_profile": "simple",
            "prompt_chars": 1,
            "prompt_estimated_tokens": 1,
            "prompt_sections": [],
            "prompt_budget": {
                "max_chars": None,
                "max_estimated_tokens": None,
                "exceeded": False,
                "violations": [],
            },
            "usage": None,
            "return_code": 0,
            "prompt_path": "",
            "command_path": "",
            "stdout_path": None,
            "stderr_path": None,
            "result_path": None,
            "worker_validation_mode": "compat",
        }

        with self.assertRaisesRegex(
            orchestrator.OrchestratorError,
            "workerValidationMode='compat' was removed from the live worker contract",
        ):
            orchestrator.coerce_task_run_record(payload, location="test")

    def test_load_agents_rejects_compat_worker_validation_mode(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            agents_path = pathlib.Path(tempdir) / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "Plan",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "workerValidationMode": "compat",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                orchestrator.OrchestratorError,
                "workerValidationMode='compat' was removed from the live worker contract",
            ):
                orchestrator.load_agents(agents_path)

    def test_worker_prompt_renders_summary_only_materialization_section(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
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
        task = orchestrator.TaskDefinition(
            id="add-feature",
            title="Add feature",
            agent="implementer",
            prompt="Implement the feature.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="materialization-section-test",
            goal="Prove materialization section rendering.",
            shared_context=orchestrator.SharedContext(
                summary="Regression test for materialization prompt section.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            dependency_materialization_mode="summary-only",
        )

        self.assertIn("Dependency materialization:", rendered.text)
        self.assertIn("Mode: `summary-only`", rendered.text)
        self.assertIn("Applied layers: none", rendered.text)
        section_names = [s.name for s in rendered.sections]
        self.assertIn("dependency_materialization", section_names)

    def test_run_loaded_plan_manifest_records_dependency_materialization_mode(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="implementer",
                description="implementation",
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
            task = orchestrator.TaskDefinition(
                id="do-work",
                title="Do work",
                agent="implementer",
                prompt="Make the change.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="materialization-manifest-test",
                goal="Prove dependencyMaterializationMode in manifest record.",
                shared_context=orchestrator.SharedContext(
                    summary="Manifest materialization field regression.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            def fake_execute_task(
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
                    summary="Completed.",
                    workspace_path=str(workspaces_dir / task.id),
                    dependency_materialization_mode="summary-only",
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"implementer": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        record = tasks_by_id["do-work"]
        self.assertIn("dependency_materialization_mode", record)
        self.assertEqual(
            orchestrator.DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
            record["dependency_materialization_mode"],
        )

    def test_worker_prompt_renders_apply_reviewed_materialization_section_with_applied_layers(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read", "Edit", "Write"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="build-downstream",
            title="Build downstream",
            agent="implementer",
            prompt="Continue from upstream state.",
            depends_on=["seed-upstream"],
            write_paths=["src/feature.txt"],
            dependency_materialization="apply-reviewed",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="apply-reviewed-prompt-section-test",
            goal="Prove apply-reviewed materialization prompt path.",
            shared_context=orchestrator.SharedContext(
                summary="Regression test for apply-reviewed prompt section rendering.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )
        layer = orchestrator.DependencyLayerRecord(
            task_id="seed-upstream",
            workspace_mode="copy",
            workspace_path="/tmp/seed-upstream-workspace",
            operations=[
                orchestrator.DependencyLayerOperation(
                    path="src/feature.txt",
                    action="modified",
                ),
            ],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            dependency_materialization_mode="apply-reviewed",
            dependency_layers_applied=[layer],
        )

        self.assertIn("Dependency materialization:", rendered.text)
        self.assertIn("Mode: `apply-reviewed`", rendered.text)
        self.assertIn("Applied layers:", rendered.text)
        self.assertIn("seed-upstream (1 ops from copy)", rendered.text)
        self.assertNotIn("Mode: `summary-only`", rendered.text)
        section_names = [s.name for s in rendered.sections]
        self.assertIn("dependency_materialization", section_names)

    def test_validate_command_reports_apply_reviewed_dependency_materialization(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "planning",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "effort": "high",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "implementer": {
                                "description": "implementation",
                                "prompt": "Return JSON only.",
                                "modelProfile": "balanced",
                                "effort": "high",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "workerValidationMode": "intents-only",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read", "Edit", "Write"],
                                "timeoutSec": 30,
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "validate-apply-reviewed-materialization",
                        "goal": "Prove apply-reviewed materialization is visible in validate output.",
                        "sharedContext": {
                            "summary": "Dependency materialization validate test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "seed-upstream",
                                "title": "Seed upstream",
                                "agent": "planner",
                                "prompt": "Seed the feature.",
                            },
                            {
                                "id": "build-downstream",
                                "title": "Build downstream",
                                "agent": "implementer",
                                "prompt": "Continue from upstream state.",
                                "dependsOn": ["seed-upstream"],
                                "writePaths": ["src/feature.txt"],
                                "dependencyMaterialization": "apply-reviewed",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            payload = orchestrator.validate_command(
                SimpleNamespace(
                    agents=str(agents_path),
                    task_plan=str(plan_path),
                )
            )

        tasks_by_id = {t["id"]: t for t in payload["tasks"]}
        self.assertEqual(
            "summary-only",
            tasks_by_id["seed-upstream"]["dependencyMaterialization"],
        )
        self.assertEqual(
            "apply-reviewed",
            tasks_by_id["build-downstream"]["dependencyMaterialization"],
        )


if __name__ == "__main__":
    unittest.main()
