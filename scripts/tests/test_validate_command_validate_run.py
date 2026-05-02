import pathlib
import sys
import tempfile
import unittest
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import load_orchestrator_module


class ValidateCommandValidateRunTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_validate_run_dedupes_worker_suggested_commands_in_dry_run(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            cmd_one = f"\"{sys.executable}\" -c \"print('one')\""
            cmd_two = f"\"{sys.executable}\" -c \"print('two')\""
            cmd_three = f"\"{sys.executable}\" -c \"print('three')\""
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-1",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done A.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-a"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": [cmd_one, cmd_two],
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
                            "task-b": {
                                "id": "task-b",
                                "title": "Task B",
                                "agent": "reviewer",
                                "status": "completed",
                                "summary": "Done B.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-b"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": [cmd_two, cmd_three],
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
                        },
                    },
                )
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=True,
                    )
                )
                updated_manifest = orchestrator.read_json(manifest_path)
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("validate-run-1", payload["runId"])
        self.assertEqual([cmd_one, cmd_two, cmd_three], payload["suggestedCommands"])
        self.assertEqual(3, payload["commandCount"])
        self.assertEqual({"planned": 3}, payload["statusCounts"])
        self.assertTrue(payload["allPassed"])
        self.assertEqual(3, payload["acceptedCommandCount"])
        self.assertEqual(0, payload["rejectedCommandCount"])
        self.assertIn("coordinatorValidation", updated_manifest)
        self.assertEqual(
            [cmd_one, cmd_two, cmd_three],
            updated_manifest["coordinatorValidation"]["suggestedCommands"],
        )

    def test_validate_run_adds_docs_consistency_helper_for_docs_only_changes(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-docs-helper",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Updated README.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-a"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": ["examples/spring-boot-starter-quickstart/README.md"],
                                "actual_files_touched": ["examples/spring-boot-starter-quickstart/README.md"],
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
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertIn("scripts/docs/check-doc-consistency.ps1", payload["suggestedCommands"])
        helper = next(command for command in payload["commands"] if command["sourceKind"] == "coordinator-helper")
        self.assertEqual("planned", helper["status"])
        self.assertEqual("docs-consistency", helper["helperKind"])

    def test_validate_run_executes_commands_and_stops_after_failure(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            success_cmd = f"\"{sys.executable}\" -c \"print('ok')\""
            fail_cmd = f"\"{sys.executable}\" -c \"import sys; print('bad'); sys.exit(2)\""
            skipped_cmd = f"\"{sys.executable}\" -c \"print('skip-me')\""
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-2",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-a"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": [success_cmd, fail_cmd, skipped_cmd],
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
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=False,
                    )
                )
                updated_manifest = orchestrator.read_json(manifest_path)
                first_stdout_exists = pathlib.Path(payload["commands"][0]["stdoutPath"]).exists()
                second_stderr_exists = pathlib.Path(payload["commands"][1]["stderrPath"]).exists()
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual({"completed": 1, "failed": 1, "skipped": 1}, payload["statusCounts"])
        self.assertFalse(payload["allPassed"])
        self.assertEqual("completed", payload["commands"][0]["status"])
        self.assertEqual("argv", payload["commands"][0]["executionKind"])
        self.assertEqual("tool", payload["commands"][0]["normalizedIntent"]["kind"])
        self.assertEqual("failed", payload["commands"][1]["status"])
        self.assertEqual("skipped", payload["commands"][2]["status"])
        self.assertTrue(first_stdout_exists)
        self.assertTrue(second_stderr_exists)
        self.assertIsNone(payload["commands"][2]["stdoutPath"])
        self.assertIn("coordinatorValidation", updated_manifest)
        self.assertEqual(
            {"completed": 1, "failed": 1, "skipped": 1},
            updated_manifest["coordinatorValidation"]["statusCounts"],
        )

    def test_validate_run_excludes_blocked_tasks_by_default(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-3",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-completed": {
                                "id": "task-completed",
                                "title": "Task Completed",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-a"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": ["scripts/ai/refresh-ai-memory.ps1 -Check"],
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
                            "task-blocked": {
                                "id": "task-blocked",
                                "title": "Task Blocked",
                                "agent": "reviewer",
                                "status": "blocked",
                                "summary": "Blocked.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-b"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": ["py -3 -m py_compile scripts/ai/claude-orchestrator.py"],
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
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual(["completed"], payload["includedStatuses"])
        self.assertEqual(["task-completed"], payload["includedTaskIds"])
        self.assertEqual(["task-blocked"], payload["excludedTaskIds"])
        self.assertEqual(["scripts/ai/refresh-ai-memory.ps1 -Check"], payload["suggestedCommands"])
        self.assertEqual(1, payload["commandCount"])
        blocked_task = next(task for task in payload["tasks"] if task["id"] == "task-blocked")
        self.assertFalse(blocked_task["includedForValidation"])
        self.assertIn("excluded by the current validation policy", blocked_task["excludedReason"])

    def test_validate_run_rejects_unsafe_commands_by_default(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            safe_cmd = "scripts/ai/refresh-ai-memory.ps1 -Check"
            unsafe_cmd = "grep -n 'workers must not' ai/orchestrator/README.md | grep state"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-4",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-a"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": [safe_cmd, unsafe_cmd],
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
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual(2, payload["commandCount"])
        self.assertEqual(1, payload["acceptedCommandCount"])
        self.assertEqual(1, payload["rejectedCommandCount"])
        self.assertEqual({"planned": 1, "rejected": 1}, payload["statusCounts"])
        self.assertFalse(payload["allPassed"])
        rejected = next(command for command in payload["commands"] if command["status"] == "rejected")
        self.assertFalse(rejected["policyAccepted"])
        self.assertIn("shell composition", rejected["policyReason"])

    def test_validate_run_can_reject_legacy_raw_commands_with_intents_only(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            raw_cmd = "scripts/docs/check-doc-consistency.ps1"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-intents-only",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-a"),
                                "started_at": "2026-04-04T00:00:00+00:00",
                                "finished_at": "2026-04-04T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": [raw_cmd],
                                "validation_intents": [
                                    {
                                        "kind": "repo-script",
                                        "entrypoint": "scripts/ai/refresh-ai-memory.ps1",
                                        "args": ["-Check"],
                                    }
                                ],
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
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        allow_unsafe_commands=False,
                        intents_only=True,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertTrue(payload["intentsOnly"])
        self.assertEqual(1, payload["legacyValidationCommandCount"])
        self.assertEqual(1, payload["includedLegacyValidationCommandCount"])
        self.assertEqual(["task-a"], payload["legacyValidationCommandTaskIds"])
        self.assertEqual(["task-a"], payload["includedLegacyValidationCommandTaskIds"])
        self.assertEqual(1, payload["intentsOnlyRejectedCommandCount"])
        self.assertEqual({"planned": 1, "rejected": 1}, payload["statusCounts"])
        rejected = next(command for command in payload["commands"] if command["sourceKind"] == "command")
        self.assertTrue(rejected["compatibilityOnly"])
        self.assertTrue(rejected["intentsOnlyRejected"])
        self.assertTrue(rejected["qualityPolicyAccepted"])
        self.assertFalse(rejected["policyAccepted"])
        self.assertIn("compatibility-only under --intents-only", rejected["policyReason"])
        planned = next(command for command in payload["commands"] if command["sourceKind"] == "intent")
        self.assertEqual("planned", planned["status"])

    def test_validate_run_executes_structured_validation_intent(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            repo_root.mkdir()
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-5",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "analyst",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "copy",
                                "workspace_path": str(run_dir / "workspace-a"),
                                "started_at": "2026-04-05T00:00:00+00:00",
                                "finished_at": "2026-04-05T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_intents": [
                                    {
                                        "kind": "tool",
                                        "entrypoint": "py",
                                        "args": ["-3", "-c", "print('ok-from-intent')"],
                                    }
                                ],
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
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=False,
                    )
                )
                stdout_exists = pathlib.Path(payload["commands"][0]["stdoutPath"]).exists()
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual(1, payload["commandCount"])
        self.assertEqual("intent", payload["commands"][0]["sourceKind"])
        self.assertEqual("completed", payload["commands"][0]["status"])
        self.assertEqual(1, len(payload["suggestedValidationIntents"]))
        self.assertTrue(stdout_exists)

    def test_validate_run_task_workspace_scope_keeps_same_command_per_workspace(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            workspace_a = temp_path / "workspace-a"
            workspace_b = temp_path / "workspace-b"
            repo_root.mkdir()
            run_dir.mkdir()
            workspace_a.mkdir()
            workspace_b.mkdir()
            command = f"\"{sys.executable}\" -c \"print('same-command')\""
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-workspace-dedupe",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Done A.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_a),
                                "started_at": "2026-04-06T00:00:00+00:00",
                                "finished_at": "2026-04-06T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": [command],
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
                            "task-b": {
                                "id": "task-b",
                                "title": "Task B",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Done B.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_b),
                                "started_at": "2026-04-06T00:00:00+00:00",
                                "finished_at": "2026-04-06T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_commands": [command],
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
                        },
                    },
                )
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        execution_scope="task-workspace",
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("task-workspace", payload["executionScope"])
        self.assertEqual(2, payload["commandCount"])
        self.assertEqual({"planned": 2}, payload["statusCounts"])
        self.assertEqual(
            [str(workspace_a.resolve()), str(workspace_b.resolve())],
            [command_payload["cwd"] for command_payload in payload["commands"]],
        )
        self.assertEqual([["task-a"], ["task-b"]], [command_payload["taskIds"] for command_payload in payload["commands"]])

    def test_validate_run_task_workspace_scope_executes_in_task_workspace(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            workspace_root = temp_path / "workspace-a"
            repo_root.mkdir()
            run_dir.mkdir()
            workspace_root.mkdir()
            (workspace_root / "marker.txt").write_text("workspace-marker\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            orchestrator.ROOT = repo_root
            try:
                orchestrator.write_json(
                    manifest_path,
                    {
                        "runId": "validate-run-workspace-exec",
                        "runDir": str(run_dir),
                        "tasks": {
                            "task-a": {
                                "id": "task-a",
                                "title": "Task A",
                                "agent": "implementer",
                                "status": "completed",
                                "summary": "Done.",
                                "workspace_mode": "copy",
                                "workspace_path": str(workspace_root),
                                "started_at": "2026-04-06T00:00:00+00:00",
                                "finished_at": "2026-04-06T00:00:01+00:00",
                                "files_touched": [],
                                "actual_files_touched": [],
                                "protected_path_violations": [],
                                "validation_intents": [
                                    {
                                        "kind": "tool",
                                        "entrypoint": sys.executable,
                                        "args": [
                                            "-c",
                                            "from pathlib import Path; print(Path('marker.txt').read_text(encoding='utf-8').strip())",
                                        ],
                                    }
                                ],
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
                payload = orchestrator.validate_run(
                    SimpleNamespace(
                        run_ref=str(run_dir),
                        selected_tasks=[],
                        include_statuses=[],
                        execution_scope="task-workspace",
                        allow_unsafe_commands=False,
                        continue_on_error=False,
                        timeout_sec=60,
                        dry_run=False,
                    )
                )
                stdout_text = pathlib.Path(payload["commands"][0]["stdoutPath"]).read_text(encoding="utf-8")
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("task-workspace", payload["executionScope"])
        self.assertEqual(1, payload["commandCount"])
        self.assertEqual("completed", payload["commands"][0]["status"])
        self.assertEqual(str(workspace_root.resolve()), payload["commands"][0]["cwd"])
        self.assertEqual("workspace-marker", stdout_text.strip())


if __name__ == "__main__":
    unittest.main()
