import pathlib
import tempfile
import unittest

from pydantic import ValidationError

from pojo_lens_agents.orchestrator_models import (
    CoordinatorCheckpointModel,
    DependencyOutputModel,
    RunManifestModel,
    TaskPlanModel,
)
from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class OrchestratorModelsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_task_plan_model_round_trips_tracked_plan_contract(self):
        payload = {
            "version": 1,
            "name": "typed-contract-proof",
            "goal": "Prove typed task plan parsing.",
            "runPolicy": {
                "runBudgetUsd": 0.25,
                "budgetBehavior": "warn",
                "maxTaskResultBytes": 4096,
                "artifactBehavior": "stop",
            },
            "sharedContext": {
                "summary": "Shared context.",
                "constraints": ["Keep JSON stable."],
                "readPaths": ["TODO.md"],
                "validation": ["scripts/docs/check-doc-consistency.ps1"],
            },
            "tasks": [
                {
                    "id": "inspect-contract",
                    "title": "Inspect contract",
                    "agent": "analyst",
                    "prompt": "Inspect the contract.",
                    "readPaths": ["TODO.md"],
                    "dependsOn": [],
                    "modelProfile": "simple",
                    "outputProfile": "lean",
                    "workerValidationMode": "intents-only",
                }
            ],
        }

        model = TaskPlanModel.model_validate(payload)
        dumped = model.model_dump(by_alias=True)

        self.assertEqual("typed-contract-proof", dumped["name"])
        self.assertEqual(["TODO.md"], dumped["sharedContext"]["readPaths"])
        self.assertEqual([], dumped["tasks"][0]["dependsOn"])
        self.assertEqual("lean", dumped["tasks"][0]["outputProfile"])

    def test_task_plan_model_rejects_invalid_task_id_before_runtime(self):
        payload = {
            "version": 1,
            "name": "bad",
            "goal": "Bad.",
            "sharedContext": {
                "summary": "Shared context.",
                "constraints": [],
                "readPaths": [],
                "validation": [],
            },
            "tasks": [
                {
                    "id": "Bad Id",
                    "title": "Bad",
                    "agent": "analyst",
                    "prompt": "Bad.",
                }
            ],
        }

        with self.assertRaisesRegex(ValidationError, "must match"):
            TaskPlanModel.model_validate(payload)

    def test_run_manifest_model_validates_generated_manifest_payload(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="Analyze",
            prompt="Return JSON only.",
            model_profile="simple",
            output_profile="standard",
        )
        task = orchestrator.TaskDefinition(
            id="inspect-contract",
            title="Inspect contract",
            agent="analyst",
            prompt="Inspect.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="manifest-contract",
            goal="Validate manifest contract.",
            shared_context=orchestrator.SharedContext(
                summary="Shared.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Done.",
            usage={
                "inputTokens": 10,
                "outputTokens": 2,
                "totalCostUsd": 0.01,
            },
        )
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            payload = orchestrator.manifest_payload(
                "run-typed-contract",
                temp_path / "plan.json",
                temp_path / "agents.json",
                {"analyst": agent},
                temp_path / "runtime",
                temp_path / "run",
                temp_path / "workspaces",
                plan,
                {record.id: record},
                dry_run=False,
            )

        model = RunManifestModel.model_validate(payload)

        self.assertEqual("run-typed-contract", model.run_id)
        self.assertEqual("completed", model.tasks["inspect-contract"].status)
        self.assertEqual(0.01, model.tasks["inspect-contract"].usage["totalCostUsd"])

    def test_task_record_coercion_preserves_retry_attempt_fields(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="retry-task",
            title="Retry task",
            agent="analyst",
            prompt="Retry.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Retried.",
        )
        payload = {
            **orchestrator.asdict(record),
            "attempt": 3,
            "attempt_errors": [
                {
                    "attempt": 1,
                    "status": "failed",
                    "failureKind": "transient",
                }
            ],
        }

        coerced = orchestrator.coerce_task_run_record(payload, location="test")

        self.assertEqual(3, coerced.attempt)
        self.assertEqual("transient", coerced.attempt_errors[0]["failureKind"])

    def test_dependency_output_and_checkpoint_models_cover_handoff_edges(self):
        dependency = DependencyOutputModel.model_validate(
            {
                "task_id": "inspect-contract",
                "branch_context_id": "inspect-contract",
                "status": "completed",
                "summary": "Done.",
                "followUps": [],
                "changedFiles": ["README.md"],
                "diffPreview": "diff --git a/README.md b/README.md",
            }
        )
        checkpoint = CoordinatorCheckpointModel.model_validate(
            {
                "summaryPath": "run/review/summary.json",
                "status": "ready",
                "taskCount": 1,
            }
        )

        self.assertEqual(["README.md"], dependency.changed_files)
        self.assertEqual("run/review/summary.json", checkpoint.summary_path)


if __name__ == "__main__":
    unittest.main()
