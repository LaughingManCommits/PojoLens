import os
import pathlib
import subprocess
import tempfile
import unittest
from dataclasses import asdict
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandReportingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_inventory_runs_summarizes_newest_runs_first(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            old_run_dir = runtime_root / "runs" / "old-run"
            new_run_dir = runtime_root / "runs" / "new-run"
            old_workspaces_dir = runtime_root / "workspaces" / "old-run"
            new_workspaces_dir = runtime_root / "workspaces" / "new-run"
            old_run_dir.mkdir(parents=True)
            new_run_dir.mkdir(parents=True)
            old_workspaces_dir.mkdir(parents=True)
            new_workspaces_dir.mkdir(parents=True)
            orchestrator.write_json(
                old_run_dir / "manifest.json",
                {
                    "runId": "old-run",
                    "generatedAt": "2026-04-01T10:00:00+00:00",
                    "dryRun": False,
                    "runDir": str(old_run_dir),
                    "workspacesDir": str(old_workspaces_dir),
                    "plan": {"name": "old-plan", "goal": "Old goal", "taskIds": ["task-a"]},
                    "taskEfforts": {"task-a": "medium"},
                    "taskEffortSources": {"task-a": "task"},
                    "usageTotals": {"promptEstimatedTokens": 123, "totalCostUsd": 0.12},
                    "events": [
                        {"ts": "2026-04-01T10:00:00+00:00", "phase": "run-start"},
                        {"ts": "2026-04-01T10:01:00+00:00", "phase": "run-finished"},
                    ],
                    "tasks": {
                        "task-a": asdict(
                            make_task_run_record(
                                orchestrator,
                                orchestrator.TaskDefinition(
                                    id="task-a",
                                    title="Task A",
                                    agent="analyst",
                                    prompt="A.",
                                ),
                                status="completed",
                                summary="Done.",
                                workspace_path=str(old_workspaces_dir / "task-a"),
                            )
                        )
                    },
                },
            )
            orchestrator.write_json(
                new_run_dir / "manifest.json",
                {
                    "runId": "new-run",
                    "generatedAt": "2026-04-07T10:00:00+00:00",
                    "dryRun": False,
                    "runDir": str(new_run_dir),
                    "workspacesDir": str(new_workspaces_dir),
                    "plan": {"name": "new-plan", "goal": "New goal", "taskIds": ["task-b"]},
                    "taskEfforts": {"task-b": "high"},
                    "taskEffortSources": {"task-b": "agent"},
                    "usageTotals": {"promptEstimatedTokens": 456, "totalCostUsd": 0.34},
                    "events": [
                        {"ts": "2026-04-07T10:00:00+00:00", "phase": "run-start"},
                        {
                            "ts": "2026-04-07T10:01:00+00:00",
                            "phase": "batch-ready",
                            "taskIds": ["task-b"],
                        },
                        {
                            "ts": "2026-04-07T10:02:00+00:00",
                            "phase": "task-failed",
                            "taskId": "task-b",
                        },
                    ],
                    "tasks": {
                        "task-b": asdict(
                            make_task_run_record(
                                orchestrator,
                                orchestrator.TaskDefinition(
                                    id="task-b",
                                    title="Task B",
                                    agent="analyst",
                                    prompt="B.",
                                ),
                                status="failed",
                                summary="Failed.",
                                workspace_path=str(new_workspaces_dir / "task-b"),
                            )
                        )
                    },
                },
            )

            payload = orchestrator.inventory_runs(
                SimpleNamespace(
                    runtime_root=str(runtime_root),
                    limit=20,
                )
            )

        self.assertEqual(2, payload["runCount"])
        self.assertEqual(2, payload["shownRunCount"])
        self.assertEqual(1, payload["completedRunCount"])
        self.assertEqual(1, payload["resumableRunCount"])
        self.assertEqual(["new-run", "old-run"], [run["runId"] for run in payload["runs"]])
        self.assertEqual(["task-b"], payload["runs"][0]["resumeCandidateTaskIds"])
        self.assertEqual({"failed": 1}, payload["runs"][0]["statusCounts"])
        self.assertEqual([], payload["runs"][1]["resumeCandidateTaskIds"])
        self.assertEqual(3, payload["runs"][0]["traceSummary"]["eventCount"])
        self.assertEqual("task-failed", payload["runs"][0]["traceSummary"]["latestPhase"])
        self.assertEqual({"run-start": 1, "batch-ready": 1, "task-failed": 1}, payload["runs"][0]["traceSummary"]["phaseCounts"])
        self.assertEqual({"high": 1}, payload["runs"][0]["effortCounts"])
        self.assertEqual("failed", payload["runs"][0]["lifecycleState"])
        self.assertIn("state:failed", payload["runs"][0]["flags"])
        self.assertIn("failed", payload["runs"][0]["flags"])
        self.assertIn("resumable", payload["runs"][0]["flags"])
        self.assertFalse(payload["runs"][0]["promotionReady"])

    def test_status_run_reports_compact_task_and_promotion_summary(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            run_dir = temp_path / "run"
            workspaces_dir = temp_path / "workspaces"
            repo_root.mkdir()
            workspace_root.mkdir()
            run_dir.mkdir()
            workspaces_dir.mkdir()
            (repo_root / "foo.txt").write_text("old\n", encoding="utf-8")
            (workspace_root / "foo.txt").write_text("new\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "status-run",
                        "generatedAt": "2026-04-07T10:00:00+00:00",
                        "dryRun": False,
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "plan": {"name": "status-plan", "goal": "Status goal", "taskIds": ["task-a"]},
                        "taskEfforts": {"task-a": "medium"},
                        "taskEffortSources": {"task-a": "override"},
                        "usageTotals": {"promptEstimatedTokens": 456, "totalCostUsd": 0.34},
                        "events": [
                            {"ts": "2026-04-07T10:00:00+00:00", "phase": "run-start"},
                            {
                                "ts": "2026-04-07T10:01:00+00:00",
                                "phase": "task-finished",
                                "taskId": "task-a",
                            },
                            {"ts": "2026-04-07T10:02:00+00:00", "phase": "run-finished"},
                        ],
                        "tasks": {
                            "task-a": asdict(
                                make_task_run_record(
                                    orchestrator,
                                    orchestrator.TaskDefinition(
                                        id="task-a",
                                        title="Task A",
                                        agent="implementer",
                                        prompt="A.",
                                    ),
                                    status="completed",
                                    summary="Done.",
                                    workspace_path=str(workspace_root),
                                    files_touched=["foo.txt"],
                                    actual_files_touched=["foo.txt"],
                                )
                            )
                        },
                    },
                )
                payload = orchestrator.status_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("status-run", payload["run"]["runId"])
        self.assertEqual(3, payload["traceSummary"]["eventCount"])
        self.assertEqual("run-finished", payload["traceSummary"]["latestPhase"])
        self.assertEqual(["task-a"], payload["traceSummary"]["taskIdsReferenced"])
        self.assertEqual(1, payload["branchSummary"]["contextCount"])
        self.assertEqual("task-a", payload["tasks"][0]["branchContextId"])
        self.assertEqual("medium", payload["tasks"][0]["effort"])
        self.assertEqual("override", payload["tasks"][0]["effortSource"])
        self.assertEqual({"medium": 1}, payload["run"]["effortCounts"])
        self.assertEqual("awaiting_review", payload["run"]["lifecycleState"])
        self.assertIn("state:awaiting_review", payload["run"]["flags"])
        self.assertTrue(payload["run"]["promotionReady"])
        self.assertIn("promotion-ready", payload["run"]["flags"])
        self.assertEqual(1, payload["taskCount"])
        self.assertEqual(1, payload["tasks"][0]["filesChanged"])

    def test_summarize_run_manifest_derives_awaiting_validation_and_promotion_states(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "state-run"
            workspaces_dir = runtime_root / "workspaces" / "state-run"
            repo_root = temp_path / "repo"
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            repo_root.mkdir()
            old_root = orchestrator.ROOT
            orchestrator.ROOT = repo_root
            (repo_root / "foo.txt").write_text("old\n", encoding="utf-8")
            task_workspace = workspaces_dir / "task-a"
            task_workspace.mkdir(parents=True)
            (task_workspace / "foo.txt").write_text("new\n", encoding="utf-8")
            task = orchestrator.TaskDefinition(
                id="task-a",
                title="Task A",
                agent="implementer",
                prompt="A.",
            )
            manifest_path = run_dir / "manifest.json"
            base_task_payload = asdict(
                make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Done.",
                    workspace_path=str(task_workspace),
                    files_touched=["foo.txt"],
                    actual_files_touched=["foo.txt"],
                )
            )
            try:
                base_manifest = {
                    "runId": "state-run",
                    "generatedAt": "2026-04-07T10:00:00+00:00",
                    "dryRun": False,
                    "runDir": str(run_dir),
                    "workspacesDir": str(workspaces_dir),
                    "plan": {"name": "state-plan", "goal": "State goal", "taskIds": ["task-a"]},
                    "tasks": {"task-a": base_task_payload},
                }
                orchestrator.write_json(manifest_path, base_manifest)
                awaiting_review_summary, _ = orchestrator.summarize_run_manifest(
                    manifest_path,
                    orchestrator.read_json(manifest_path),
                )
                orchestrator.write_json(
                    manifest_path,
                    base_manifest
                    | {
                        "coordinatorReview": {
                            "summaryPath": str(run_dir / "review" / "summary.json"),
                        },
                    },
                )
                review_recorded_summary, _ = orchestrator.summarize_run_manifest(
                    manifest_path,
                    orchestrator.read_json(manifest_path),
                )
                orchestrator.write_json(
                    manifest_path,
                    base_manifest
                    | {
                        "coordinatorReview": {
                            "summaryPath": str(run_dir / "review" / "summary.json"),
                        },
                        "coordinatorValidation": {
                            "allPassed": False,
                            "statusCounts": {"failed": 1},
                        },
                    },
                )
                awaiting_validation_summary, _ = orchestrator.summarize_run_manifest(
                    manifest_path,
                    orchestrator.read_json(manifest_path),
                )
                orchestrator.write_json(
                    manifest_path,
                    base_manifest
                    | {
                        "coordinatorReview": {
                            "summaryPath": str(run_dir / "review" / "summary.json"),
                        },
                        "coordinatorValidation": {
                            "allPassed": True,
                            "statusCounts": {"completed": 1},
                        },
                    },
                )
                awaiting_promotion_summary, _ = orchestrator.summarize_run_manifest(
                    manifest_path,
                    orchestrator.read_json(manifest_path),
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("awaiting_review", awaiting_review_summary["lifecycleState"])
        self.assertEqual("awaiting_validation", review_recorded_summary["lifecycleState"])
        self.assertEqual("awaiting_validation", awaiting_validation_summary["lifecycleState"])
        self.assertTrue(review_recorded_summary["approvalSummary"]["reviewRecorded"])
        self.assertFalse(review_recorded_summary["approvalSummary"]["validationRecorded"])
        self.assertIn("state:awaiting_validation", awaiting_validation_summary["flags"])
        self.assertEqual("awaiting_promotion", awaiting_promotion_summary["lifecycleState"])
        self.assertIn("state:awaiting_promotion", awaiting_promotion_summary["flags"])

    def test_summarize_run_manifest_requires_repo_validation_after_promotion(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "state-run"
            workspaces_dir = runtime_root / "workspaces" / "state-run"
            repo_root = temp_path / "repo"
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            repo_root.mkdir()
            old_root = orchestrator.ROOT
            orchestrator.ROOT = repo_root
            (repo_root / "foo.txt").write_text("old\n", encoding="utf-8")
            task_workspace = workspaces_dir / "task-a"
            task_workspace.mkdir(parents=True)
            (task_workspace / "foo.txt").write_text("new\n", encoding="utf-8")
            task = orchestrator.TaskDefinition(
                id="task-a",
                title="Task A",
                agent="implementer",
                prompt="A.",
            )
            manifest_path = run_dir / "manifest.json"
            base_task_payload = asdict(
                make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Done.",
                    workspace_path=str(task_workspace),
                    files_touched=["foo.txt"],
                    actual_files_touched=["foo.txt"],
                )
            )
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "state-run",
                        "generatedAt": "2026-04-07T10:00:00+00:00",
                        "dryRun": False,
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "plan": {"name": "state-plan", "goal": "State goal", "taskIds": ["task-a"]},
                        "tasks": {"task-a": base_task_payload},
                        "coordinatorReview": {
                            "summaryPath": str(run_dir / "review" / "summary.json"),
                        },
                        "coordinatorValidation": {
                            "generatedAt": "2026-04-07T10:02:00+00:00",
                            "executionScope": "task-workspace",
                            "allPassed": True,
                            "statusCounts": {"completed": 1},
                        },
                        "coordinatorPromotion": {
                            "generatedAt": "2026-04-07T10:03:00+00:00",
                            "promotionAllowed": True,
                            "dryRun": False,
                            "filesPromoted": 1,
                            "filesPromotable": 1,
                            "summaryPath": str(run_dir / "promotion" / "summary.json"),
                        },
                    },
                )
                awaiting_repo_validation_summary, _ = orchestrator.summarize_run_manifest(
                    manifest_path,
                    orchestrator.read_json(manifest_path),
                )
                orchestrator.write_json(
                    manifest_path,
                    orchestrator.read_json(manifest_path)
                    | {
                        "coordinatorValidation": {
                            "generatedAt": "2026-04-07T10:04:00+00:00",
                            "executionScope": "repo",
                            "allPassed": True,
                            "statusCounts": {"completed": 1},
                        },
                    },
                )
                completed_summary, _ = orchestrator.summarize_run_manifest(
                    manifest_path,
                    orchestrator.read_json(manifest_path),
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("awaiting_validation", awaiting_repo_validation_summary["lifecycleState"])
        self.assertIn("repo-scope coordinator validation", awaiting_repo_validation_summary["lifecycleStateReason"])
        self.assertEqual("task-workspace", awaiting_repo_validation_summary["approvalSummary"]["validationExecutionScope"])
        self.assertEqual("completed", completed_summary["lifecycleState"])
        self.assertEqual("repo", completed_summary["approvalSummary"]["validationExecutionScope"])

    def test_evaluate_run_reports_branch_lineage_and_quality_warnings(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            workspaces_dir = temp_path / "workspaces"
            repo_root.mkdir()
            run_dir.mkdir()
            workspaces_dir.mkdir()
            (repo_root / "foo.txt").write_text("old\n", encoding="utf-8")
            (workspaces_dir / "task-a").mkdir()
            (workspaces_dir / "task-b").mkdir()
            ((workspaces_dir / "task-a") / "foo.txt").write_text("new\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            task_a = orchestrator.TaskDefinition(
                id="task-a",
                title="Task A",
                agent="analyst",
                prompt="A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="task-b",
                title="Task B",
                agent="reviewer",
                prompt="B.",
                depends_on=["task-a"],
            )
            try:
                orchestrator.write_json(
                    run_dir / "manifest.json",
                    {
                        "runId": "eval-run",
                        "generatedAt": "2026-04-07T10:00:00+00:00",
                        "dryRun": False,
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "plan": {"name": "eval-plan", "goal": "Eval goal", "taskIds": ["task-a", "task-b"]},
                        "taskEfforts": {"task-a": "high", "task-b": "high"},
                        "taskEffortSources": {"task-a": "agent", "task-b": "agent"},
                        "taskModelProfiles": {"task-a": "simple", "task-b": "simple"},
                        "topology": {
                            "reviewerTaskCount": 1,
                            "writeTaskCount": 0,
                            "readOnlyTaskIds": ["task-a", "task-b"],
                            "warnings": [
                                {
                                    "kind": "read-only-review-optional",
                                    "taskIds": ["task-b"],
                                    "message": "Reviewer hop is optional.",
                                }
                            ],
                        },
                        "events": [
                            {
                                "ts": "2026-04-07T10:00:00+00:00",
                                "phase": "run-start",
                                "taskIds": ["task-a", "task-b"],
                                "branchContextIds": ["task-a", "task-b<-task-a"],
                                "details": {"resume": False, "retryOfRunId": None, "seededTaskIds": []},
                            },
                            {
                                "ts": "2026-04-07T10:01:00+00:00",
                                "phase": "task-finished",
                                "taskId": "task-a",
                                "branchContextId": "task-a",
                            },
                            {
                                "ts": "2026-04-07T10:02:00+00:00",
                                "phase": "task-finished",
                                "taskId": "task-b",
                                "branchContextId": "task-b<-task-a",
                                "parentTaskIds": ["task-a"],
                            },
                        ],
                        "tasks": {
                            "task-a": asdict(
                                make_task_run_record(
                                    orchestrator,
                                    task_a,
                                    status="completed",
                                    summary="Done.",
                                    workspace_path=str(workspaces_dir / "task-a"),
                                    files_touched=["foo.txt"],
                                    actual_files_touched=["foo.txt"],
                                )
                            ),
                            "task-b": asdict(
                                make_task_run_record(
                                    orchestrator,
                                    task_b,
                                    status="completed",
                                    summary="Reviewed.",
                                    branch_context_id="task-b<-task-a",
                                    branch_parent_context_ids=["task-a"],
                                    workspace_path=str(workspaces_dir / "task-b"),
                                )
                            )
                            | {"validation_commands": ["mvn test"]},
                        },
                    },
                )
                payload = orchestrator.evaluate_run_quality(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("warn", payload["status"])
        self.assertEqual("warn", payload["scoreSummary"]["status"])
        self.assertEqual({"pass": 3, "warn": 4}, payload["scoreSummary"]["statusCounts"])
        self.assertEqual(7, payload["scoreSummary"]["totalChecks"])
        self.assertEqual(71.4, payload["scoreSummary"]["scorePercent"])
        self.assertTrue(payload["scoreSummary"]["promotionReady"])
        self.assertFalse(payload["scoreSummary"]["resumable"])
        self.assertEqual(2, payload["scoreSummary"]["taskCount"])
        self.assertEqual("warn", payload["benchmarkDimensions"]["decompositionQuality"]["status"])
        self.assertEqual("pass", payload["benchmarkDimensions"]["retryCorrectness"]["status"])
        self.assertEqual("pass", payload["benchmarkDimensions"]["reviewPromotionAccuracy"]["status"])
        self.assertEqual("warn", payload["benchmarkDimensions"]["parallelEfficiency"]["status"])
        self.assertEqual(3, payload["traceSummary"]["eventCount"])
        self.assertEqual(2, payload["branchSummary"]["contextCount"])
        self.assertEqual(
            ["task-b<-task-a"],
            payload["branchSummary"]["leafContextIds"],
        )
        by_name = {check["name"]: check for check in payload["checks"]}
        self.assertEqual("warn", by_name["over-delegation"]["status"])
        self.assertEqual("warn", by_name["reviewer-hops"]["status"])
        self.assertEqual("warn", by_name["validation-suggestions"]["status"])
        self.assertEqual("warn", by_name["effort-fit"]["status"])
        self.assertEqual("pass", by_name["output-discipline"]["status"])
        self.assertEqual("pass", by_name["retry-resume-contract"]["status"])

    def test_evaluate_run_corpus_aggregates_scores_and_dimensions(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            run_one_dir = runtime_root / "runs" / "run-one"
            run_two_dir = runtime_root / "runs" / "run-two"
            workspaces_one = runtime_root / "workspaces" / "run-one"
            workspaces_two = runtime_root / "workspaces" / "run-two"
            run_one_dir.mkdir(parents=True)
            run_two_dir.mkdir(parents=True)
            workspaces_one.mkdir(parents=True)
            workspaces_two.mkdir(parents=True)
            orchestrator.write_json(
                run_one_dir / "manifest.json",
                {
                    "runId": "run-one",
                    "generatedAt": "2026-04-07T10:00:00+00:00",
                    "dryRun": True,
                    "runDir": str(run_one_dir),
                    "workspacesDir": str(workspaces_one),
                    "plan": {"name": "eval-a", "goal": "A", "taskIds": ["task-a", "task-b"]},
                    "taskEfforts": {"task-a": "high", "task-b": "high"},
                    "taskEffortSources": {"task-a": "agent", "task-b": "agent"},
                    "taskModelProfiles": {"task-a": "balanced", "task-b": "balanced"},
                    "topology": {
                        "reviewerTaskCount": 1,
                        "writeTaskCount": 0,
                        "readOnlyTaskIds": ["task-a", "task-b"],
                        "batchCount": 2,
                        "maxParallelWidth": 1,
                        "warnings": [
                            {
                                "kind": "read-only-review-optional",
                                "taskIds": ["task-b"],
                                "message": "Reviewer hop is optional.",
                            }
                        ],
                    },
                    "events": [
                        {"ts": "2026-04-07T10:00:00+00:00", "phase": "run-start"},
                        {"ts": "2026-04-07T10:01:00+00:00", "phase": "run-finished"},
                    ],
                    "tasks": {
                        "task-a": asdict(
                            make_task_run_record(
                                orchestrator,
                                orchestrator.TaskDefinition(id="task-a", title="Task A", agent="analyst", prompt="A."),
                                status="planned",
                                summary="A.",
                                workspace_path=str(workspaces_one / "task-a"),
                            )
                        ),
                        "task-b": asdict(
                            make_task_run_record(
                                orchestrator,
                                orchestrator.TaskDefinition(
                                    id="task-b",
                                    title="Task B",
                                    agent="reviewer",
                                    prompt="B.",
                                    depends_on=["task-a"],
                                ),
                                status="planned",
                                summary="B.",
                                branch_context_id="task-b<-task-a",
                                branch_parent_context_ids=["task-a"],
                                workspace_path=str(workspaces_one / "task-b"),
                            )
                        ),
                    },
                },
            )
            orchestrator.write_json(
                run_two_dir / "manifest.json",
                {
                    "runId": "run-two",
                    "generatedAt": "2026-04-08T10:00:00+00:00",
                    "dryRun": True,
                    "runDir": str(run_two_dir),
                    "workspacesDir": str(workspaces_two),
                    "plan": {"name": "eval-b", "goal": "B", "taskIds": ["task-c"]},
                    "taskEfforts": {"task-c": "medium"},
                    "taskEffortSources": {"task-c": "task"},
                    "taskModelProfiles": {"task-c": "balanced"},
                    "topology": {
                        "reviewerTaskCount": 0,
                        "writeTaskCount": 0,
                        "readOnlyTaskIds": ["task-c"],
                        "batchCount": 1,
                        "maxParallelWidth": 1,
                        "warnings": [],
                    },
                    "events": [
                        {"ts": "2026-04-08T10:00:00+00:00", "phase": "run-start"},
                        {"ts": "2026-04-08T10:01:00+00:00", "phase": "run-finished"},
                    ],
                    "tasks": {
                        "task-c": asdict(
                            make_task_run_record(
                                orchestrator,
                                orchestrator.TaskDefinition(id="task-c", title="Task C", agent="analyst", prompt="C."),
                                status="planned",
                                summary="C.",
                                workspace_path=str(workspaces_two / "task-c"),
                            )
                        ),
                    },
                },
            )

            payload = orchestrator.evaluate_run_corpus(
                SimpleNamespace(
                    runtime_root=str(runtime_root),
                    limit=20,
                )
            )

        self.assertEqual(2, payload["runCount"])
        self.assertEqual(2, payload["shownRunCount"])
        self.assertEqual({"pass": 1, "warn": 1}, payload["statusCounts"])
        self.assertEqual({"pass": 1, "warn": 1}, payload["scoreStatusCounts"])
        self.assertEqual(89.3, payload["averageScorePercent"])
        self.assertEqual({"pass": 1, "warn": 1}, payload["dimensionStatusCounts"]["decompositionQuality"])
        self.assertEqual({"pass": 2}, payload["dimensionStatusCounts"]["retryCorrectness"])
        self.assertEqual({"pass": 2}, payload["dimensionStatusCounts"]["reviewPromotionAccuracy"])
        self.assertEqual({"pass": 1, "warn": 1}, payload["dimensionStatusCounts"]["parallelEfficiency"])
        self.assertEqual(["run-one", "run-two"], sorted(run["runId"] for run in payload["runs"]))

    def test_prune_runs_removes_only_old_completed_runs_by_default(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            repo_root.mkdir()
            orchestrator.ROOT = repo_root
            now = orchestrator.datetime.now(orchestrator.timezone.utc)
            old_completed_at = (now - orchestrator.timedelta(days=14)).isoformat()
            old_failed_at = (now - orchestrator.timedelta(days=13)).isoformat()
            recent_completed_at = (now - orchestrator.timedelta(days=1)).isoformat()

            def write_run(run_id: str, generated_at: str, status: str) -> tuple[pathlib.Path, pathlib.Path]:
                run_dir = runtime_root / "runs" / run_id
                workspaces_dir = runtime_root / "workspaces" / run_id
                task_workspace = workspaces_dir / "task-a"
                manifest_path = run_dir / "manifest.json"
                run_dir.mkdir(parents=True)
                task_workspace.mkdir(parents=True)
                record = make_task_run_record(
                    orchestrator,
                    orchestrator.TaskDefinition(
                        id="task-a",
                        title="Task A",
                        agent="analyst",
                        prompt="A.",
                    ),
                    status=status,
                    summary=status,
                    workspace_path=str(task_workspace),
                )
                record.started_at = generated_at
                record.finished_at = generated_at
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": run_id,
                        "generatedAt": generated_at,
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "plan": {"name": run_id, "goal": run_id, "taskIds": ["task-a"]},
                        "tasks": {
                            "task-a": asdict(record)
                        },
                    },
                )
                generated_dt = orchestrator.parse_iso_datetime(generated_at)
                if generated_dt is not None:
                    os.utime(manifest_path, (generated_dt.timestamp(), generated_dt.timestamp()))
                return run_dir, workspaces_dir

            old_completed_run_dir, old_completed_workspaces = write_run(
                "old-completed",
                old_completed_at,
                "completed",
            )
            old_failed_run_dir, old_failed_workspaces = write_run(
                "old-failed",
                old_failed_at,
                "failed",
            )
            recent_completed_run_dir, recent_completed_workspaces = write_run(
                "recent-completed",
                recent_completed_at,
                "completed",
            )

            try:
                payload = orchestrator.prune_runs(
                    SimpleNamespace(
                        runtime_root=str(runtime_root),
                        older_than_days=7.0,
                        keep=0,
                        include_incomplete=False,
                        continue_on_error=False,
                        dry_run=False,
                    )
                )
            finally:
                orchestrator.ROOT = old_root
            old_completed_exists = old_completed_run_dir.exists()
            old_completed_workspaces_exist = old_completed_workspaces.exists()
            old_failed_exists = old_failed_run_dir.exists()
            old_failed_workspaces_exist = old_failed_workspaces.exists()
            recent_completed_exists = recent_completed_run_dir.exists()
            recent_completed_workspaces_exist = recent_completed_workspaces.exists()

        self.assertEqual(["old-completed"], payload["removedRunIds"])
        self.assertEqual(["old-failed"], payload["skippedIncompleteRunIds"])
        self.assertEqual(["recent-completed"], payload["skippedRecentRunIds"])
        self.assertFalse(old_completed_exists)
        self.assertFalse(old_completed_workspaces_exist)
        self.assertTrue(old_failed_exists)
        self.assertTrue(old_failed_workspaces_exist)
        self.assertTrue(recent_completed_exists)
        self.assertTrue(recent_completed_workspaces_exist)

    def test_cleanup_run_removes_run_and_workspace_directories(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "cleanup-run"
            workspaces_dir = runtime_root / "workspaces" / "cleanup-run"
            workspace_path = workspaces_dir / "task-a"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            workspace_path.mkdir(parents=True)
            (run_dir / "manifest.json").write_text("{}", encoding="utf-8")
            (workspace_path / "foo.txt").write_text("temp\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "cleanup-run",
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_path),
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
                            }
                        },
                    },
                )
                payload = orchestrator.cleanup_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("cleanup-run", payload["runId"])
        self.assertTrue(payload["removedRunDir"])
        self.assertTrue(payload["removedWorkspacesDir"])
        self.assertEqual([], payload["removedWorktrees"])
        self.assertFalse(run_dir.exists())
        self.assertFalse(workspaces_dir.exists())

    def test_cleanup_run_raises_when_worktree_remove_fails(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_subprocess_run = orchestrator.subprocess.run
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "cleanup-worktree-remove-fail"
            workspaces_dir = runtime_root / "workspaces" / "cleanup-worktree-remove-fail"
            worktree_path = workspaces_dir / "task-a"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            worktree_path.mkdir(parents=True)
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "cleanup-worktree-remove-fail",
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "worktree",
                                "workspace_path": str(worktree_path),
                                "started_at": "2026-04-06T00:00:00+00:00",
                                "finished_at": "2026-04-06T00:00:01+00:00",
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
                            }
                        },
                    },
                )

                def fake_run(command, cwd, capture_output, text, check):
                    return subprocess.CompletedProcess(command, 1, stdout="", stderr="remove failed")

                orchestrator.subprocess.run = fake_run
                with self.assertRaisesRegex(
                    orchestrator.OrchestratorError,
                    "Failed to remove detached worktree '.*remove failed",
                ):
                    orchestrator.cleanup_run(
                        SimpleNamespace(
                            run_ref=str(run_dir),
                        )
                    )
            finally:
                orchestrator.ROOT = old_root
                orchestrator.subprocess.run = old_subprocess_run

    def test_cleanup_run_raises_when_worktree_prune_fails(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_subprocess_run = orchestrator.subprocess.run
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "cleanup-worktree-prune-fail"
            workspaces_dir = runtime_root / "workspaces" / "cleanup-worktree-prune-fail"
            worktree_path = workspaces_dir / "task-a"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            worktree_path.mkdir(parents=True)
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "cleanup-worktree-prune-fail",
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "worktree",
                                "workspace_path": str(worktree_path),
                                "started_at": "2026-04-06T00:00:00+00:00",
                                "finished_at": "2026-04-06T00:00:01+00:00",
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
                            }
                        },
                    },
                )

                def fake_run(command, cwd, capture_output, text, check):
                    if command[:3] == ["git", "worktree", "remove"]:
                        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")
                    if command[:3] == ["git", "worktree", "prune"]:
                        return subprocess.CompletedProcess(command, 1, stdout="", stderr="prune failed")
                    return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

                orchestrator.subprocess.run = fake_run
                with self.assertRaisesRegex(
                    orchestrator.OrchestratorError,
                    "Failed to prune worktree metadata: prune failed",
                ):
                    orchestrator.cleanup_run(
                        SimpleNamespace(
                            run_ref=str(run_dir),
                        )
                    )
            finally:
                orchestrator.ROOT = old_root
                orchestrator.subprocess.run = old_subprocess_run


if __name__ == "__main__":
    unittest.main()
