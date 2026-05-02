import json
import pathlib
import tempfile
import unittest
from dataclasses import asdict
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandRunLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_review_run_reports_diff_summary(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            workspace_root.mkdir()
            run_dir.mkdir()
            (repo_root / "foo.txt").write_text("old\nline\n", encoding="utf-8")
            (workspace_root / "foo.txt").write_text("new\nline\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "run-1",
                        "tasks": {
                            "edit-foo": {
                                "id": "edit-foo",
                                "title": "Edit foo",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Changed foo.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_root),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["foo.txt"],
                                "actual_files_touched": ["foo.txt"],
                                "protected_path_violations": [],
                                "validation_commands": [],
                                "follow_ups": [],
                                "notes": [],
                                "model": "claude-sonnet-4-6",
                                "model_profile": "balanced",
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
                payload = orchestrator.review_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        context_lines=3,
                    )
                )
                updated_manifest = orchestrator.read_json(manifest_path)
                review_summary_exists = pathlib.Path(
                    updated_manifest["coordinatorReview"]["summaryPath"]
                ).exists()
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("run-1", payload["runId"])
        self.assertEqual(1, payload["taskCount"])
        self.assertEqual(1, payload["tasks"][0]["diffStats"]["filesChanged"])
        self.assertEqual("modified", payload["tasks"][0]["files"][0]["status"])
        self.assertEqual(1, payload["summary"]["changedTaskCount"])
        self.assertEqual(1, payload["summary"]["changedFileCount"])
        self.assertIn("coordinatorReview", updated_manifest)
        self.assertTrue(review_summary_exists)

    def test_export_patch_writes_patch_file(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            workspace_root.mkdir()
            run_dir.mkdir()
            (repo_root / "foo.txt").write_text("old\nline\n", encoding="utf-8")
            (workspace_root / "foo.txt").write_text("new\nline\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "run-2",
                        "tasks": {
                            "edit-foo": {
                                "id": "edit-foo",
                                "title": "Edit foo",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Changed foo.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_root),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["foo.txt"],
                                "actual_files_touched": ["foo.txt"],
                                "protected_path_violations": [],
                                "validation_commands": [],
                                "follow_ups": [],
                                "notes": [],
                                "model": "claude-sonnet-4-6",
                                "model_profile": "balanced",
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
                output = temp_path / "review.patch"
                payload = orchestrator.export_patch(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        context_lines=3,
                        out=str(output),
                    )
                )
                patch_text = output.read_text(encoding="utf-8")
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("run-2", payload["runId"])
        self.assertTrue(patch_text.startswith("--- a/foo.txt"))
        self.assertIn("+new", patch_text)
        self.assertEqual(1, payload["filesChanged"])

    def test_promote_run_applies_modified_added_and_deleted_files(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            workspace_root.mkdir()
            run_dir.mkdir()
            (repo_root / "foo.txt").write_text("old\nline\n", encoding="utf-8")
            (repo_root / "delete.txt").write_text("delete me\n", encoding="utf-8")
            (workspace_root / "foo.txt").write_text("new\nline\n", encoding="utf-8")
            (workspace_root / "add.txt").write_text("added\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "run-3",
                        "tasks": {
                            "edit-files": {
                                "id": "edit-files",
                                "title": "Edit files",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Changed multiple files.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_root),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["foo.txt", "add.txt", "delete.txt"],
                                "actual_files_touched": ["foo.txt", "add.txt", "delete.txt"],
                                "protected_path_violations": [],
                                "validation_commands": [],
                                "follow_ups": [],
                                "notes": [],
                                "model": "claude-sonnet-4-6",
                                "model_profile": "balanced",
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
                payload = orchestrator.promote_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        dry_run=False,
                    )
                )
                updated_manifest = orchestrator.read_json(manifest_path)
                foo_text = (repo_root / "foo.txt").read_text(encoding="utf-8")
                add_text = (repo_root / "add.txt").read_text(encoding="utf-8")
                deleted_exists = (repo_root / "delete.txt").exists()
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("run-3", payload["runId"])
        self.assertEqual(3, payload["filesPromoted"])
        self.assertEqual({"added": 1, "modified": 1, "deleted": 1}, payload["operationCounts"])
        self.assertEqual("new\nline\n", foo_text)
        self.assertEqual("added\n", add_text)
        self.assertFalse(deleted_exists)
        self.assertIn("coordinatorPromotion", updated_manifest)
        self.assertEqual(3, updated_manifest["coordinatorPromotion"]["filesPromoted"])

    def test_promote_run_dry_run_reports_duplicate_file_ownership(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_a = temp_path / "workspace-a"
            workspace_b = temp_path / "workspace-b"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            workspace_a.mkdir()
            workspace_b.mkdir()
            run_dir.mkdir()
            (repo_root / "foo.txt").write_text("old\n", encoding="utf-8")
            (workspace_a / "foo.txt").write_text("new from a\n", encoding="utf-8")
            (workspace_b / "foo.txt").write_text("new from b\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "run-4",
                        "tasks": {
                            "edit-foo-a": {
                                "id": "edit-foo-a",
                                "title": "Edit foo A",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Changed foo from A.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_a),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["foo.txt"],
                                "actual_files_touched": ["foo.txt"],
                                "protected_path_violations": [],
                                "validation_commands": [],
                                "follow_ups": [],
                                "notes": [],
                                "model": "claude-sonnet-4-6",
                                "model_profile": "balanced",
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
                            },
                            "edit-foo-b": {
                                "id": "edit-foo-b",
                                "title": "Edit foo B",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Changed foo from B.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_b),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["foo.txt"],
                                "actual_files_touched": ["foo.txt"],
                                "protected_path_violations": [],
                                "validation_commands": [],
                                "follow_ups": [],
                                "notes": [],
                                "model": "claude-sonnet-4-6",
                                "model_profile": "balanced",
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
                            },
                        },
                    },
                )
                payload = orchestrator.promote_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertFalse(payload["promotionAllowed"])
        self.assertIn("also changed by task 'edit-foo-a'", "\n".join(payload["blockedReasons"]))

    def test_promote_run_dry_run_dedupes_identical_duplicate_file_ownership(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_a = temp_path / "workspace-a"
            workspace_b = temp_path / "workspace-b"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            workspace_a.mkdir()
            workspace_b.mkdir()
            run_dir.mkdir()
            (repo_root / "foo.txt").write_text("old\n", encoding="utf-8")
            (workspace_a / "foo.txt").write_text("new shared\n", encoding="utf-8")
            (workspace_b / "foo.txt").write_text("new shared\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "run-4b",
                        "tasks": {
                            "edit-foo-a": {
                                "id": "edit-foo-a",
                                "title": "Edit foo A",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Changed foo from A.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_a),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["foo.txt"],
                                "actual_files_touched": ["foo.txt"],
                                "protected_path_violations": [],
                                "validation_commands": [],
                                "follow_ups": [],
                                "notes": [],
                                "model": "claude-sonnet-4-6",
                                "model_profile": "balanced",
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
                            },
                            "edit-foo-b": {
                                "id": "edit-foo-b",
                                "title": "Edit foo B",
                                "agent": "reviewer",
                                "status": "completed",
                                "summary": "Materialized matching foo change.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_b),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["foo.txt"],
                                "actual_files_touched": ["foo.txt"],
                                "protected_path_violations": [],
                                "validation_commands": [],
                                "follow_ups": [],
                                "notes": [],
                                "model": "claude-sonnet-4-6",
                                "model_profile": "balanced",
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
                            },
                        },
                    },
                )
                payload = orchestrator.promote_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertTrue(payload["promotionAllowed"])
        self.assertEqual(1, payload["filesPromotable"])
        self.assertEqual(["edit-foo-a"], payload["promotableTaskIds"])
        reviewer_payload = next(task for task in payload["tasks"] if task["id"] == "edit-foo-b")
        self.assertEqual(["foo.txt"], reviewer_payload["dedupedDuplicatePaths"])
        self.assertEqual(0, reviewer_payload["filesPromotableSelected"])

    def test_retry_run_seeds_completed_dependency_and_replans_failed_task(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "previous-run"
            workspaces_dir = runtime_root / "workspaces" / "previous-run"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
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
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                            }
                        },
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "retry-plan",
                        "goal": "Retry failed tasks.",
                        "sharedContext": {
                            "summary": "Retry test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect-a",
                                "title": "Inspect A",
                                "agent": "analyst",
                                "prompt": "Inspect A.",
                            },
                            {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "prompt": "Retry B.",
                                "dependsOn": ["inspect-a"],
                            },
                        ],
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "previous-run",
                        "planPath": str(plan_path),
                        "agentsPath": str(agents_path),
                        "runtimeRoot": str(runtime_root),
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "tasks": {
                            "inspect-a": {
                                "id": "inspect-a",
                                "title": "Inspect A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Completed A.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspaces_dir / "inspect-a"),
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
                            },
                            "retry-b": {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "status": "failed",
                                "summary": "B failed.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspaces_dir / "retry-b"),
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
                                "return_code": 1,
                                "prompt_path": "",
                                "command_path": "",
                                "stdout_path": None,
                                "stderr_path": None,
                                "result_path": None,
                            },
                        },
                    },
                )
                payload = orchestrator.retry_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        agents="",
                        claude_bin="claude",
                        runtime_root="",
                        max_parallel=2,
                        selected_tasks=[],
                        continue_on_error=False,
                        worker_validation_mode="",
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("previous-run", payload["retryOfRunId"])
        self.assertEqual(["retry-b"], payload["requestedTaskIds"])
        self.assertEqual(["retry-b"], payload["retriedTaskIds"])
        self.assertEqual(["inspect-a"], payload["seededTaskIds"])
        self.assertEqual({"completed": 1, "planned": 1}, payload["statusCounts"])

    def test_retry_run_inherits_worker_validation_mode_from_manifest(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_loaded_plan = orchestrator.run_loaded_plan
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "previous-run"
            workspaces_dir = runtime_root / "workspaces" / "previous-run"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
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
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                            }
                        },
                    }
                ),
                encoding="utf-8",
            )
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "retry-plan",
                        "goal": "Retry failed tasks.",
                        "sharedContext": {
                            "summary": "Retry test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "prompt": "Retry B.",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            captured: dict[str, object] = {}

            def fake_run_loaded_plan(
                plan_path_arg,
                agents_path_arg,
                agents,
                plan,
                *,
                claude_bin,
                runtime_root,
                max_parallel,
                continue_on_error,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
                initial_records=None,
                retry_of_run_id=None,
                requested_task_ids=None,
                retried_task_ids=None,
                existing_run_id=None,
                existing_run_dir=None,
                existing_workspaces_dir=None,
                write_plan_snapshot=True,
                max_task_retries=None,
                **kwargs,
            ):
                captured["worker_validation_mode"] = worker_validation_mode
                captured["effort_override"] = effort_override
                return {
                    "runId": "retry-run",
                    "plan": plan.name,
                    "goal": plan.goal,
                    "dryRun": dry_run,
                    "workerValidationMode": worker_validation_mode,
                    "effortOverride": effort_override,
                    "runtimeRoot": str(runtime_root),
                    "runDir": str(runtime_root / "runs" / "retry-run"),
                    "workspacesDir": str(runtime_root / "workspaces" / "retry-run"),
                    "statusCounts": {"planned": 1},
                    "usageTotals": {
                        "tasksWithUsage": 0,
                        "promptEstimatedTokens": 0,
                        "inputTokens": 0,
                        "outputTokens": 0,
                        "cacheReadInputTokens": 0,
                        "cacheCreationInputTokens": 0,
                        "totalCostUsd": 0.0,
                        "perModel": {},
                    },
                    "tasks": [],
                    "requestedTaskIds": list(requested_task_ids or []),
                    "retriedTaskIds": list(retried_task_ids or []),
                    "seededTaskIds": sorted(initial_records or {}),
                }

            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "previous-run",
                        "planPath": str(plan_path),
                        "agentsPath": str(agents_path),
                        "runtimeRoot": str(runtime_root),
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "workerValidationMode": "intents-only",
                        "workerValidationModeOverride": None,
                        "tasks": {
                            "retry-b": {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "status": "failed",
                                "summary": "B failed.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspaces_dir / "retry-b"),
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
                                "return_code": 1,
                                "prompt_path": "",
                                "command_path": "",
                                "stdout_path": None,
                                "stderr_path": None,
                                "result_path": None,
                            }
                        },
                    },
                )
                orchestrator.run_loaded_plan = fake_run_loaded_plan
                payload = orchestrator.retry_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        agents="",
                        claude_bin="claude",
                        runtime_root="",
                        max_parallel=2,
                        selected_tasks=[],
                        continue_on_error=False,
                        worker_validation_mode="",
                        effort="low",
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.run_loaded_plan = old_run_loaded_plan
                orchestrator.ROOT = old_root

        self.assertEqual("intents-only", captured["worker_validation_mode"])
        self.assertEqual("intents-only", payload["workerValidationMode"])
        self.assertEqual("low", captured["effort_override"])
        self.assertEqual("low", payload["effortOverride"])

    def test_retry_run_drops_legacy_compat_manifest_mode(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_loaded_plan = orchestrator.run_loaded_plan
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "previous-run"
            workspaces_dir = runtime_root / "workspaces" / "previous-run"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
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
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
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
                        "name": "retry-plan",
                        "goal": "Retry failed tasks.",
                        "sharedContext": {
                            "summary": "Retry test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "prompt": "Retry B.",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            captured: dict[str, object] = {}

            def fake_run_loaded_plan(
                plan_path_arg,
                agents_path_arg,
                agents,
                plan,
                *,
                claude_bin,
                runtime_root,
                max_parallel,
                continue_on_error,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
                initial_records=None,
                retry_of_run_id=None,
                requested_task_ids=None,
                retried_task_ids=None,
                existing_run_id=None,
                existing_run_dir=None,
                existing_workspaces_dir=None,
                write_plan_snapshot=True,
                max_task_retries=None,
                **kwargs,
            ):
                captured["worker_validation_mode"] = worker_validation_mode
                return {
                    "runId": "retry-run",
                    "plan": plan.name,
                    "goal": plan.goal,
                    "dryRun": dry_run,
                    "workerValidationMode": worker_validation_mode,
                    "runtimeRoot": str(runtime_root),
                    "runDir": str(runtime_root / "runs" / "retry-run"),
                    "workspacesDir": str(runtime_root / "workspaces" / "retry-run"),
                    "statusCounts": {"planned": 1},
                    "usageTotals": {
                        "tasksWithUsage": 0,
                        "promptEstimatedTokens": 0,
                        "inputTokens": 0,
                        "outputTokens": 0,
                        "cacheReadInputTokens": 0,
                        "cacheCreationInputTokens": 0,
                        "totalCostUsd": 0.0,
                        "perModel": {},
                    },
                    "tasks": [],
                    "requestedTaskIds": list(requested_task_ids or []),
                    "retriedTaskIds": list(retried_task_ids or []),
                    "seededTaskIds": sorted(initial_records or {}),
                }

            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "previous-run",
                        "planPath": str(plan_path),
                        "agentsPath": str(agents_path),
                        "runtimeRoot": str(runtime_root),
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "workerValidationMode": "compat",
                        "tasks": {
                            "retry-b": {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "status": "failed",
                                "summary": "B failed.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspaces_dir / "retry-b"),
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
                                "return_code": 1,
                                "prompt_path": "",
                                "command_path": "",
                                "stdout_path": None,
                                "stderr_path": None,
                                "result_path": None,
                            }
                        },
                    },
                )
                orchestrator.run_loaded_plan = fake_run_loaded_plan
                orchestrator.retry_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        agents="",
                        claude_bin="claude",
                        runtime_root="",
                        max_parallel=2,
                        selected_tasks=[],
                        continue_on_error=False,
                        worker_validation_mode="",
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.run_loaded_plan = old_run_loaded_plan
                orchestrator.ROOT = old_root

        self.assertIsNone(captured["worker_validation_mode"])

    def test_retry_run_retries_unfinished_dependency_when_selected_task_depends_on_it(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "previous-run"
            workspaces_dir = runtime_root / "workspaces" / "previous-run"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
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
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                            }
                        },
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "retry-plan",
                        "goal": "Retry failed tasks.",
                        "sharedContext": {
                            "summary": "Retry test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect-a",
                                "title": "Inspect A",
                                "agent": "analyst",
                                "prompt": "Inspect A.",
                            },
                            {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "prompt": "Retry B.",
                                "dependsOn": ["inspect-a"],
                            },
                        ],
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "previous-run",
                        "planPath": str(plan_path),
                        "agentsPath": str(agents_path),
                        "runtimeRoot": str(runtime_root),
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "tasks": {
                            "inspect-a": {
                                "id": "inspect-a",
                                "title": "Inspect A",
                                "agent": "analyst",
                                "status": "failed",
                                "summary": "A failed.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspaces_dir / "inspect-a"),
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
                                "return_code": 1,
                                "prompt_path": "",
                                "command_path": "",
                                "stdout_path": None,
                                "stderr_path": None,
                                "result_path": None,
                            },
                            "retry-b": {
                                "id": "retry-b",
                                "title": "Retry B",
                                "agent": "analyst",
                                "status": "blocked",
                                "summary": "B blocked.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspaces_dir / "retry-b"),
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
                                "return_code": None,
                                "prompt_path": "",
                                "command_path": "",
                                "stdout_path": None,
                                "stderr_path": None,
                                "result_path": None,
                            },
                        },
                    },
                )
                payload = orchestrator.retry_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        agents="",
                        claude_bin="claude",
                        runtime_root="",
                        max_parallel=2,
                        selected_tasks=["retry-b"],
                        continue_on_error=False,
                        worker_validation_mode="",
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual(["retry-b"], payload["requestedTaskIds"])
        self.assertEqual(["inspect-a", "retry-b"], payload["retriedTaskIds"])
        self.assertEqual([], payload["seededTaskIds"])
        self.assertEqual({"planned": 2}, payload["statusCounts"])

    def test_resume_run_reuses_existing_run_and_preserves_unselected_tasks(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_loaded_plan = orchestrator.run_loaded_plan
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "same-run"
            workspaces_dir = runtime_root / "workspaces" / "same-run"
            repo_root.mkdir()
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            selected_plan_path = run_dir / "selected-plan.json"
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
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                            },
                        },
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )
            plan_payload = {
                "version": 1,
                "name": "resume-plan",
                "goal": "Resume in place.",
                "sharedContext": {
                    "summary": "Resume test.",
                    "constraints": [],
                    "readPaths": [],
                    "validation": [],
                },
                "tasks": [
                    {
                        "id": "inspect-a",
                        "title": "Inspect A",
                        "agent": "analyst",
                        "prompt": "Inspect A.",
                    },
                    {
                        "id": "retry-b",
                        "title": "Retry B",
                        "agent": "analyst",
                        "prompt": "Retry B.",
                        "dependsOn": ["inspect-a"],
                    },
                    {
                        "id": "later-c",
                        "title": "Later C",
                        "agent": "analyst",
                        "prompt": "Later C.",
                    },
                ],
            }
            plan_path.write_text(json.dumps(plan_payload, indent=2) + "\n", encoding="utf-8")
            selected_plan_path.write_text(json.dumps(plan_payload, indent=2) + "\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            captured: dict[str, object] = {}

            def fake_run_loaded_plan(
                plan_path_arg,
                agents_path_arg,
                agents,
                plan,
                *,
                claude_bin,
                runtime_root,
                max_parallel,
                continue_on_error,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
                initial_records=None,
                retry_of_run_id=None,
                requested_task_ids=None,
                retried_task_ids=None,
                existing_run_id=None,
                existing_run_dir=None,
                existing_workspaces_dir=None,
                write_plan_snapshot=True,
                max_task_retries=None,
                **kwargs,
            ):
                captured["plan_path"] = pathlib.Path(plan_path_arg)
                captured["effort_override"] = effort_override
                captured["plan_task_ids"] = [task.id for task in plan.tasks]
                captured["requested_task_ids"] = list(requested_task_ids or [])
                captured["initial_record_statuses"] = {
                    task_id: record.status for task_id, record in sorted((initial_records or {}).items())
                }
                captured["initial_record_summaries"] = {
                    task_id: record.summary for task_id, record in sorted((initial_records or {}).items())
                }
                captured["existing_run_id"] = existing_run_id
                captured["existing_run_dir"] = str(existing_run_dir)
                captured["existing_workspaces_dir"] = str(existing_workspaces_dir)
                captured["write_plan_snapshot"] = write_plan_snapshot
                return {
                    "runId": existing_run_id,
                    "plan": plan.name,
                    "goal": plan.goal,
                    "dryRun": dry_run,
                    "workerValidationMode": worker_validation_mode or "intents-only",
                    "effortOverride": effort_override,
                    "runtimeRoot": str(runtime_root),
                    "runDir": str(existing_run_dir),
                    "workspacesDir": str(existing_workspaces_dir),
                    "statusCounts": {"completed": 1, "failed": 1, "planned": 1},
                    "usageTotals": {
                        "tasksWithUsage": 0,
                        "promptEstimatedTokens": 0,
                        "inputTokens": 0,
                        "outputTokens": 0,
                        "cacheReadInputTokens": 0,
                        "cacheCreationInputTokens": 0,
                        "totalCostUsd": 0.0,
                        "perModel": {},
                    },
                    "tasks": [],
                }

            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "same-run",
                        "planPath": str(plan_path),
                        "agentsPath": str(agents_path),
                        "runtimeRoot": str(runtime_root),
                        "runDir": str(run_dir),
                        "workspacesDir": str(workspaces_dir),
                        "workerValidationMode": "intents-only",
                        "workerValidationModeOverride": None,
                        "tasks": {
                            "inspect-a": asdict(
                                make_task_run_record(
                                    orchestrator,
                                    orchestrator.TaskDefinition(
                                        id="inspect-a",
                                        title="Inspect A",
                                        agent="analyst",
                                        prompt="Inspect A.",
                                    ),
                                    status="completed",
                                    summary="Completed A.",
                                    workspace_path=str(workspaces_dir / "inspect-a"),
                                )
                            ),
                            "retry-b": asdict(
                                make_task_run_record(
                                    orchestrator,
                                    orchestrator.TaskDefinition(
                                        id="retry-b",
                                        title="Retry B",
                                        agent="analyst",
                                        prompt="Retry B.",
                                        depends_on=["inspect-a"],
                                    ),
                                    status="failed",
                                    summary="B failed.",
                                    workspace_path=str(workspaces_dir / "retry-b"),
                                )
                            ),
                        },
                    },
                )
                orchestrator.run_loaded_plan = fake_run_loaded_plan
                payload = orchestrator.resume_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        agents="",
                        claude_bin="claude",
                        max_parallel=2,
                        selected_tasks=["retry-b"],
                        continue_on_error=False,
                        worker_validation_mode="",
                        effort="medium",
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.run_loaded_plan = old_run_loaded_plan
                orchestrator.ROOT = old_root

        self.assertEqual(selected_plan_path.resolve(), captured["plan_path"])
        self.assertEqual(["inspect-a", "retry-b", "later-c"], captured["plan_task_ids"])
        self.assertEqual(["retry-b"], captured["requested_task_ids"])
        self.assertEqual(
            {"inspect-a": "completed", "later-c": "planned"},
            captured["initial_record_statuses"],
        )
        self.assertIn("not selected for this resume", captured["initial_record_summaries"]["later-c"])
        self.assertEqual("same-run", captured["existing_run_id"])
        self.assertEqual(str(run_dir), captured["existing_run_dir"])
        self.assertEqual(str(workspaces_dir), captured["existing_workspaces_dir"])
        self.assertFalse(captured["write_plan_snapshot"])
        self.assertEqual("medium", captured["effort_override"])
        self.assertEqual("same-run", payload["runId"])
        self.assertEqual("medium", payload["effortOverride"])
        self.assertEqual(["retry-b"], payload["requestedTaskIds"])
        self.assertEqual(["retry-b"], payload["resumedTaskIds"])
        self.assertEqual(["inspect-a", "later-c"], payload["preservedTaskIds"])
        self.assertTrue(payload["resumedInPlace"])


if __name__ == "__main__":
    unittest.main()
