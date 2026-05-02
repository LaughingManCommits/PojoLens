import pathlib
import tempfile
import unittest
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ReviewerFindingsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def _make_reviewer_task(self):
        return self.orchestrator.TaskDefinition(
            id="review-impl",
            title="Review impl",
            agent="reviewer",
            prompt="Review the changes.",
        )

    def _make_impl_task(self):
        return self.orchestrator.TaskDefinition(
            id="edit-src",
            title="Edit src",
            agent="implementer",
            prompt="Edit src.",
            write_paths=["src/Foo.java"],
        )

    def test_coerce_worker_result_parses_findings(self):
        orchestrator = self.orchestrator
        payload = orchestrator.coerce_worker_result(
            {
                "status": "completed",
                "summary": "Review done.",
                "filesTouched": [],
                "validationIntents": [],
                "followUps": [],
                "notes": [],
                "findings": [
                    {"severity": "info", "message": "Looks good."},
                    {"severity": "warn", "message": "Minor style issue."},
                ],
            }
        )
        self.assertEqual([{"severity": "info", "message": "Looks good."}, {"severity": "warn", "message": "Minor style issue."}], payload["findings"])

    def test_coerce_worker_result_rejects_invalid_finding_severity(self):
        orchestrator = self.orchestrator
        with self.assertRaises(Exception) as ctx:
            orchestrator.coerce_worker_result(
                {
                    "status": "completed",
                    "summary": "Done.",
                    "filesTouched": [],
                    "validationIntents": [],
                    "followUps": [],
                    "notes": [],
                    "findings": [{"severity": "critical", "message": "Bad."}],
                }
            )
        self.assertIn("severity", str(ctx.exception))

    def test_coerce_worker_result_null_findings_returns_empty(self):
        orchestrator = self.orchestrator
        payload = orchestrator.coerce_worker_result(
            {
                "status": "completed",
                "summary": "Done.",
                "filesTouched": [],
                "validationIntents": [],
                "followUps": [],
                "notes": [],
                "findings": None,
            }
        )
        self.assertEqual([], payload["findings"])

    def test_coerce_worker_result_missing_findings_returns_empty(self):
        orchestrator = self.orchestrator
        payload = orchestrator.coerce_worker_result(
            {
                "status": "completed",
                "summary": "Done.",
                "filesTouched": [],
                "validationIntents": [],
                "followUps": [],
                "notes": [],
            }
        )
        self.assertEqual([], payload["findings"])

    def test_plan_promotion_blocks_on_reviewer_blocking_finding(self):
        orchestrator = self.orchestrator
        reviewer_task = self._make_reviewer_task()
        reviewer_record = make_task_run_record(
            orchestrator,
            reviewer_task,
            status="completed",
            summary="Review identified a blocking issue.",
            reviewer_findings=[
                orchestrator.ReviewFinding(severity="block", message="Missing null check in Foo.process()."),
            ],
        )
        with self.assertRaises(orchestrator.PromotionBlockedError) as ctx:
            orchestrator.plan_promotion([reviewer_record])
        self.assertIn("reviewer blocked promotion", str(ctx.exception))
        self.assertIn("Missing null check", str(ctx.exception))

    def test_plan_promotion_allows_warn_severity_finding(self):
        orchestrator = self.orchestrator
        reviewer_task = self._make_reviewer_task()
        reviewer_record = make_task_run_record(
            orchestrator,
            reviewer_task,
            status="completed",
            summary="Review done; minor style notes only.",
            reviewer_findings=[
                orchestrator.ReviewFinding(severity="warn", message="Style nit: rename variable."),
                orchestrator.ReviewFinding(severity="info", message="Good coverage."),
            ],
        )
        task_payloads, operations, counts = orchestrator.plan_promotion([reviewer_record])
        self.assertEqual(1, len(task_payloads))
        self.assertEqual(0, len(operations))

    def test_plan_promotion_blocks_on_block_but_not_warn(self):
        orchestrator = self.orchestrator
        reviewer_task = self._make_reviewer_task()
        reviewer_record = make_task_run_record(
            orchestrator,
            reviewer_task,
            status="completed",
            summary="Mixed findings.",
            reviewer_findings=[
                orchestrator.ReviewFinding(severity="warn", message="Style nit."),
                orchestrator.ReviewFinding(severity="block", message="Null pointer in hot path."),
            ],
        )
        with self.assertRaises(orchestrator.PromotionBlockedError) as ctx:
            orchestrator.plan_promotion([reviewer_record])
        self.assertIn("reviewer blocked promotion", str(ctx.exception))

    def test_summarize_promotion_readiness_reflects_reviewer_blocking(self):
        orchestrator = self.orchestrator
        reviewer_task = self._make_reviewer_task()
        reviewer_record = make_task_run_record(
            orchestrator,
            reviewer_task,
            status="completed",
            summary="Blocked by reviewer.",
            reviewer_findings=[
                orchestrator.ReviewFinding(severity="block", message="Critical logic error."),
            ],
        )
        readiness = orchestrator.summarize_promotion_readiness([reviewer_record])
        self.assertFalse(readiness["allowed"])
        self.assertTrue(readiness["reviewerFindingsBlocked"])
        self.assertTrue(any("reviewer blocked" in r for r in readiness["blockedReasons"]))

    def test_summarize_promotion_readiness_warn_not_blocked(self):
        orchestrator = self.orchestrator
        reviewer_task = self._make_reviewer_task()
        reviewer_record = make_task_run_record(
            orchestrator,
            reviewer_task,
            status="completed",
            summary="Warn only.",
            reviewer_findings=[
                orchestrator.ReviewFinding(severity="warn", message="Style nit."),
            ],
        )
        readiness = orchestrator.summarize_promotion_readiness([reviewer_record])
        self.assertTrue(readiness["allowed"])
        self.assertFalse(readiness["reviewerFindingsBlocked"])

    def test_derive_run_lifecycle_state_review_blocked(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            run_dir = pathlib.Path(tempdir) / "run"
            run_dir.mkdir()
            workspace = pathlib.Path(tempdir) / "ws"
            workspace.mkdir()
            (workspace / "src").mkdir()
            (workspace / "src" / "Foo.java").write_text("class Foo {}", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.write_json(
                manifest_path,
                {
                    "runId": "run-wp37",
                    "tasks": {
                        "edit-src": {
                            "id": "edit-src",
                            "title": "Edit src",
                            "agent": "implementer",
                            "status": "completed",
                            "summary": "Added Foo.",
                            "workspace_mode": "copy",
                            "workspace_path": str(workspace),
                            "started_at": "2026-05-01T00:00:00+00:00",
                            "finished_at": "2026-05-01T00:00:01+00:00",
                            "files_touched": ["src/Foo.java"],
                            "actual_files_touched": ["src/Foo.java"],
                            "protected_path_violations": [],
                            "validation_commands": [],
                            "follow_ups": [],
                            "notes": [],
                            "model": "claude-sonnet-4-6",
                            "model_profile": "balanced",
                            "prompt_chars": 1,
                            "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
                            "usage": None,
                            "return_code": 0,
                            "prompt_path": "",
                            "command_path": "",
                            "stdout_path": None,
                            "stderr_path": None,
                            "result_path": None,
                        },
                        "review-impl": {
                            "id": "review-impl",
                            "title": "Review impl",
                            "agent": "reviewer",
                            "status": "completed",
                            "summary": "Blocked: missing null check.",
                            "workspace_mode": "copy",
                            "workspace_path": str(workspace),
                            "started_at": "2026-05-01T00:01:00+00:00",
                            "finished_at": "2026-05-01T00:01:01+00:00",
                            "files_touched": [],
                            "actual_files_touched": [],
                            "protected_path_violations": [],
                            "validation_commands": [],
                            "follow_ups": [],
                            "notes": [],
                            "reviewer_findings": [{"severity": "block", "message": "Missing null check in Foo.process()."}],
                            "model": "claude-haiku-4-5-20251001",
                            "model_profile": "simple",
                            "prompt_chars": 1,
                            "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
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
            manifest_path, manifest = orchestrator.load_run_manifest(str(run_dir))
            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)

        self.assertEqual("review-blocked", summary["lifecycleState"])
        self.assertIn("review-blocked", summary["flags"])
        self.assertIn("blocking findings", summary["lifecycleStateReason"])

    def test_task_review_summary_includes_reviewer_findings(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            run_dir = pathlib.Path(tempdir) / "run"
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            orchestrator.write_json(
                manifest_path,
                {
                    "runId": "r1",
                    "tasks": {
                        "review-impl": {
                            "id": "review-impl",
                            "title": "Review impl",
                            "agent": "reviewer",
                            "status": "completed",
                            "summary": "Review with findings.",
                            "workspace_mode": "copy",
                            "workspace_path": str(run_dir),
                            "started_at": "2026-05-01T00:01:00+00:00",
                            "finished_at": "2026-05-01T00:01:01+00:00",
                            "files_touched": [],
                            "actual_files_touched": [],
                            "protected_path_violations": [],
                            "validation_commands": [],
                            "follow_ups": [],
                            "notes": [],
                            "reviewer_findings": [
                                {"severity": "warn", "message": "Style nit."},
                                {"severity": "block", "message": "Critical bug."},
                            ],
                            "model": "claude-haiku-4-5-20251001",
                            "model_profile": "simple",
                            "prompt_chars": 1,
                            "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
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
            review_payload = orchestrator.review_run(
                SimpleNamespace(run_ref=str(run_dir), selected_tasks=[], context_lines=3)
            )

        task_findings = review_payload["tasks"][0]["reviewerFindings"]
        self.assertEqual(2, len(task_findings))
        self.assertEqual("warn", task_findings[0]["severity"])
        self.assertEqual("block", task_findings[1]["severity"])

    def test_task_review_summary_flags_mojibake_in_docs_text(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            repo_root.mkdir()
            workspace_root.mkdir()
            (repo_root / "README.md").write_text("hello\n", encoding="utf-8")
            (workspace_root / "README.md").write_text("salary â‰¥ 100k\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                record = make_task_run_record(
                    orchestrator,
                    self._make_impl_task(),
                    status="completed",
                    summary="Updated README.",
                    workspace_path=str(workspace_root),
                    files_touched=["README.md"],
                    actual_files_touched=["README.md"],
                )
                payload, _ = orchestrator.task_review_summary(record, context_lines=3)
            finally:
                orchestrator.ROOT = old_root

        findings = payload["textQualityFindings"]
        self.assertEqual(1, len(findings))
        self.assertEqual("mojibake", findings[0]["kind"])
        self.assertEqual("block", findings[0]["severity"])
        self.assertIn("README.md", findings[0]["message"])

    def test_task_review_summary_warns_on_new_non_ascii_docs_text(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            repo_root.mkdir()
            workspace_root.mkdir()
            (repo_root / "guide.md").write_text("plain ascii\n", encoding="utf-8")
            (workspace_root / "guide.md").write_text("cafe é\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                record = make_task_run_record(
                    orchestrator,
                    self._make_impl_task(),
                    status="completed",
                    summary="Updated guide.",
                    workspace_path=str(workspace_root),
                    files_touched=["guide.md"],
                    actual_files_touched=["guide.md"],
                )
                payload, _ = orchestrator.task_review_summary(record, context_lines=3)
            finally:
                orchestrator.ROOT = old_root

        findings = payload["textQualityFindings"]
        self.assertEqual(1, len(findings))
        self.assertEqual("non-ascii-doc-text", findings[0]["kind"])
        self.assertEqual("warn", findings[0]["severity"])

    def test_plan_promotion_blocks_on_text_quality_guardrail(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            repo_root.mkdir()
            workspace_root.mkdir()
            (repo_root / "README.md").write_text("hello\n", encoding="utf-8")
            (workspace_root / "README.md").write_text("salary â‰¥ 100k\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                record = make_task_run_record(
                    orchestrator,
                    self._make_impl_task(),
                    status="completed",
                    summary="Updated README.",
                    workspace_path=str(workspace_root),
                    files_touched=["README.md"],
                    actual_files_touched=["README.md"],
                )
                with self.assertRaises(orchestrator.PromotionBlockedError) as ctx:
                    orchestrator.plan_promotion([record])
            finally:
                orchestrator.ROOT = old_root

        self.assertIn("text-quality guardrails blocked promotion", str(ctx.exception))

    def test_summarize_promotion_readiness_reflects_text_quality_block(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            repo_root.mkdir()
            workspace_root.mkdir()
            (repo_root / "README.md").write_text("hello\n", encoding="utf-8")
            (workspace_root / "README.md").write_text("salary â‰¥ 100k\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                record = make_task_run_record(
                    orchestrator,
                    self._make_impl_task(),
                    status="completed",
                    summary="Updated README.",
                    workspace_path=str(workspace_root),
                    files_touched=["README.md"],
                    actual_files_touched=["README.md"],
                )
                readiness = orchestrator.summarize_promotion_readiness([record])
            finally:
                orchestrator.ROOT = old_root

        self.assertFalse(readiness["allowed"])
        self.assertTrue(readiness["textQualityBlocked"])
        self.assertTrue(any("text-quality guardrails blocked promotion" in item for item in readiness["blockedReasons"]))

    def test_derive_run_lifecycle_state_text_quality_blocked(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            run_dir = pathlib.Path(tempdir) / "run"
            run_dir.mkdir()
            workspace = pathlib.Path(tempdir) / "ws"
            workspace.mkdir()
            (workspace / "README.md").write_text("salary â‰¥ 100k\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.write_json(
                manifest_path,
                {
                    "runId": "run-wp38",
                    "tasks": {
                        "edit-docs": {
                            "id": "edit-docs",
                            "title": "Edit docs",
                            "agent": "implementer",
                            "status": "completed",
                            "summary": "Edited docs.",
                            "workspace_mode": "copy",
                            "workspace_path": str(workspace),
                            "started_at": "2026-05-01T00:00:00+00:00",
                            "finished_at": "2026-05-01T00:00:01+00:00",
                            "files_touched": ["README.md"],
                            "actual_files_touched": ["README.md"],
                            "protected_path_violations": [],
                            "validation_commands": [],
                            "follow_ups": [],
                            "notes": [],
                            "model": "claude-sonnet-4-6",
                            "model_profile": "balanced",
                            "prompt_chars": 1,
                            "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
                            "usage": None,
                            "return_code": 0,
                            "prompt_path": "",
                            "command_path": "",
                            "stdout_path": None,
                            "stderr_path": None,
                            "result_path": None
                        }
                    },
                },
            )
            old_root = orchestrator.ROOT
            orchestrator.ROOT = pathlib.Path(tempdir)
            try:
                manifest_path, manifest = orchestrator.load_run_manifest(str(run_dir))
                summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("review-blocked", summary["lifecycleState"])
        self.assertIn("Text-quality guardrails blocked promotion", summary["lifecycleStateReason"])
        self.assertIn("text-quality-blocked", summary["flags"])

    def test_worker_result_schema_allows_findings(self):
        orchestrator = self.orchestrator
        schema = orchestrator.WORKER_RESULT_SCHEMA
        self.assertIn("findings", schema["properties"])
        finding_schema = schema["properties"]["findings"]
        self.assertIn("null", finding_schema["type"])
        self.assertIn("array", finding_schema["type"])
        self.assertNotIn("findings", schema["required"])


if __name__ == "__main__":
    unittest.main()
