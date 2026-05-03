import asyncio
import contextlib
import importlib.util
import io
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
from dataclasses import asdict
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    load_cli_module,
    make_task_run_record,
)

# --- slice imports ---
from scripts.tests.test_validate_command_topology import ValidateCommandTopologyTest
from scripts.tests.test_validate_command_agents_plans import ValidateCommandAgentsPlansTest
from scripts.tests.test_validate_command_prompts_workers import ValidateCommandPromptsWorkersTest
from scripts.tests.test_validate_command_validation_commands import ValidateCommandValidationCommandsTest
from scripts.tests.test_validate_command_workspace import ValidateCommandWorkspaceTest
from scripts.tests.test_validate_command_execute_run import ValidateCommandExecuteRunTest
from scripts.tests.test_validate_command_run_lifecycle import ValidateCommandRunLifecycleTest
from scripts.tests.test_validate_command_reporting import ValidateCommandReportingTest
from scripts.tests.test_validate_command_validate_run import ValidateCommandValidateRunTest
from scripts.tests.test_validate_command_compat import ValidateCommandCompatTest
from scripts.tests.test_reviewer_findings import ReviewerFindingsTest


class ClaudeCommandTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_agent_payload_for_claude_can_limit_to_selected_agent(self):
        orchestrator = self.orchestrator
        payload = json.loads(
            orchestrator.agent_payload_for_claude(
                {
                    "planner": orchestrator.AgentDefinition(
                        name="planner",
                        description="Plan work.",
                        prompt="Planner prompt.",
                        skills=["caveman"],
                        model_profile="simple",
                    ),
                    "analyst": orchestrator.AgentDefinition(
                        name="analyst",
                        description="Analyze work.",
                        prompt="Analyst prompt.",
                        model_profile="simple",
                    ),
                },
                selected_names=["planner", "analyst"],
            )
        )

        self.assertEqual(["analyst", "planner"], sorted(payload))
        self.assertEqual(["caveman"], payload["planner"]["skills"])
        self.assertNotIn("skills", payload["analyst"])

    def test_agent_payload_for_claude_can_override_skills_per_task(self):
        orchestrator = self.orchestrator
        payload = json.loads(
            orchestrator.agent_payload_for_claude(
                {
                    "analyst": orchestrator.AgentDefinition(
                        name="analyst",
                        description="Analyze work.",
                        prompt="Analyst prompt.",
                        skills=["caveman"],
                        model_profile="simple",
                    ),
                },
                selected_names=["analyst"],
                resolved_skills_by_name={"analyst": ["docs", "caveman"]},
            )
        )

        self.assertEqual(["docs", "caveman"], payload["analyst"]["skills"])

    def test_variadic_tool_flags_do_not_consume_prompt(self):
        command = self.orchestrator.claude_command(
            "claude",
            '{"analyst":{"description":"d","prompt":"p"}}',
            "analyst",
            "prompt text",
            '{"type":"object"}',
            model="claude-haiku-4-5",
            effort="high",
            permission_mode="dontAsk",
            allowed_tools=["Read", "Grep", "Glob"],
            disallowed_tools=[],
            max_budget_usd=None,
        )

        self.assertIn("--allowed-tools", command)
        allowed_index = command.index("--allowed-tools")
        self.assertEqual("Read,Grep,Glob", command[allowed_index + 1])
        self.assertEqual(["--", "prompt text"], command[-2:])

    def test_disallowed_tools_are_compacted_to_one_argument(self):
        command = self.orchestrator.claude_command(
            "claude",
            '{"reviewer":{"description":"d","prompt":"p"}}',
            "reviewer",
            "review prompt",
            '{"type":"object"}',
            model=None,
            effort=None,
            permission_mode=None,
            allowed_tools=[],
            disallowed_tools=["Edit", "Write"],
            max_budget_usd=1.25,
        )

        self.assertIn("--disallowed-tools", command)
        denied_index = command.index("--disallowed-tools")
        self.assertEqual("Edit,Write", command[denied_index + 1])
        self.assertIn("--max-budget-usd", command)
        self.assertEqual(["--", "review prompt"], command[-2:])

    def test_summarize_run_manifest_flags_unexpectedly_verbose_lean_task(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            run_dir = temp_path / "run"
            run_dir.mkdir()
            manifest_path = run_dir / "manifest.json"
            record = make_task_run_record(
                orchestrator,
                orchestrator.TaskDefinition(
                    id="tighten-docs",
                    title="Tighten docs",
                    agent="docs-implementer",
                    prompt="Tighten docs.",
                ),
                status="completed",
                summary="S" * orchestrator.LEAN_MAX_WORKER_SUMMARY_CHARS,
                workspace_path=str(temp_path / "workspace"),
                result_path=str(run_dir / "tasks" / "tighten-docs" / "worker-result.json"),
                result_bytes=orchestrator.LEAN_VERBOSE_RESULT_BYTES + 1,
            )
            record.output_profile = "lean"
            record.output_profile_source = "agent"
            manifest = {
                "runId": "run-x",
                "generatedAt": "2026-05-02T00:00:00+00:00",
                "runDir": str(run_dir),
                "workspacesDir": str(temp_path / "workspaces"),
                "plan": {"name": "cheap-proof", "goal": "cheap proof", "taskIds": ["tighten-docs"]},
                "tasks": {"tighten-docs": asdict(record)},
                "taskOutputProfiles": {"tighten-docs": "lean"},
                "taskOutputProfileSources": {"tighten-docs": "agent"},
                "topology": {"batchCount": 1, "maxParallelWidth": 1, "warningCount": 0},
                "usageTotals": {},
                "runGovernance": {},
                "events": [],
            }
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)
            evaluation = orchestrator.evaluate_loaded_run_quality(
                manifest_path,
                manifest,
                selected_tasks=[],
            )

        self.assertEqual(["tighten-docs"], summary["unexpectedlyVerboseTaskIds"])
        self.assertIn("verbose", summary["flags"])
        check = next(item for item in evaluation["checks"] if item["name"] == "output-discipline")
        self.assertEqual("warn", check["status"])


class ConsoleEntrypointTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cli = load_cli_module()

    def test_extract_repo_root_removes_global_option_anywhere(self):
        repo_root, remaining = self.cli.extract_repo_root(
            [
                "validate",
                "--repo-root",
                "C:/data/pojolens",
                "ai/orchestrator/tasks/example-parallel.json",
                "--json",
            ]
        )

        self.assertEqual("C:/data/pojolens", repo_root)
        self.assertEqual(
            ["validate", "ai/orchestrator/tasks/example-parallel.json", "--json"],
            remaining,
        )

    def test_console_entrypoint_forwards_to_existing_validate_command(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        stdout_buffer = io.StringIO()
        with contextlib.redirect_stdout(stdout_buffer):
            exit_code = self.cli.main(
                [
                    "--repo-root",
                    str(root),
                    "validate",
                    "ai/orchestrator/tasks/example-parallel.json",
                    "--json",
                ]
            )

        self.assertEqual(0, exit_code)
        payload = json.loads(stdout_buffer.getvalue())
        self.assertEqual("example-parallel", payload["planName"])
        self.assertEqual(["inspect-memory-contract", "inspect-runtime-contract"], payload["taskIds"])
        self.assertEqual(2, payload["topology"]["maxParallelWidth"])
        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual(
            ["caveman", "orchestrator"],
            tasks_by_id["inspect-runtime-contract"]["resolvedSkills"],
        )


class ExitCodeConstantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_exit_code_constants_are_defined(self):
        o = self.orchestrator
        self.assertEqual(0, o.EXIT_SUCCESS)
        self.assertEqual(1, o.EXIT_ERROR)
        self.assertEqual(2, o.EXIT_BOOTSTRAP)
        self.assertEqual(3, o.EXIT_VALIDATION)
        self.assertEqual(4, o.EXIT_WORKER_FAILURE)
        self.assertEqual(5, o.EXIT_BLOCKED)
        self.assertEqual(6, o.EXIT_UNSAFE_PROMOTION)
        self.assertEqual(7, o.EXIT_CRASH)

    def test_worker_run_exit_code_success_when_no_failures(self):
        o = self.orchestrator
        self.assertEqual(o.EXIT_SUCCESS, o._worker_run_exit_code({"completed": 3}))

    def test_worker_run_exit_code_failure_when_failed_present(self):
        o = self.orchestrator
        self.assertEqual(o.EXIT_WORKER_FAILURE, o._worker_run_exit_code({"completed": 2, "failed": 1}))

    def test_worker_run_exit_code_blocked_when_only_blocked(self):
        o = self.orchestrator
        self.assertEqual(o.EXIT_BLOCKED, o._worker_run_exit_code({"completed": 1, "blocked": 1}))

    def test_worker_run_exit_code_failure_takes_priority_over_blocked(self):
        o = self.orchestrator
        self.assertEqual(o.EXIT_WORKER_FAILURE, o._worker_run_exit_code({"failed": 1, "blocked": 1}))

    def test_worker_run_exit_code_empty_counts_is_success(self):
        o = self.orchestrator
        self.assertEqual(o.EXIT_SUCCESS, o._worker_run_exit_code({}))

    def test_promotion_blocked_error_is_subclass_of_orchestrator_error(self):
        o = self.orchestrator
        self.assertTrue(issubclass(o.PromotionBlockedError, o.OrchestratorError))

    def test_validation_error_is_subclass_of_orchestrator_error(self):
        o = self.orchestrator
        self.assertTrue(issubclass(o.ValidationError, o.OrchestratorError))

    def test_main_returns_validation_exit_code_for_invalid_plan(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        cli = load_cli_module()
        import tempfile
        with tempfile.NamedTemporaryFile(suffix=".json", mode="w", delete=False, encoding="utf-8") as f:
            json.dump({"version": 99, "bad": True}, f)
            bad_plan = f.name
        try:
            stderr_buf = io.StringIO()
            with contextlib.redirect_stderr(stderr_buf):
                exit_code = cli.main([
                    "--repo-root", str(root),
                    "validate", bad_plan, "--json",
                ])
            self.assertEqual(cli.EXIT_BOOTSTRAP, 2)
            self.assertIn(exit_code, {1, 3})
        finally:
            pathlib.Path(bad_plan).unlink(missing_ok=True)

    def test_main_returns_unsafe_promotion_exit_code_when_promotion_blocked(self):
        o = self.orchestrator
        import tempfile
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            run_dir = temp_path / "run"
            workspace_a = temp_path / "workspace-a"
            workspace_b = temp_path / "workspace-b"
            run_dir.mkdir()
            workspace_a.mkdir()
            workspace_b.mkdir()
            (workspace_a / "shared.txt").write_text("from a\n", encoding="utf-8")
            (workspace_b / "shared.txt").write_text("from b\n", encoding="utf-8")
            manifest_path = run_dir / "manifest.json"
            old_root = o.ROOT
            repo_root = temp_path / "repo"
            repo_root.mkdir()
            o.ROOT = repo_root
            try:
                o.write_json(manifest_path, {
                    "runId": "run-x",
                    "tasks": {
                        "task-a": {
                            "id": "task-a", "title": "A", "agent": "implementer",
                            "status": "completed", "summary": "done",
                            "workspace_mode": "copy", "workspace_path": str(workspace_a),
                            "started_at": "2026-04-30T00:00:00+00:00",
                            "finished_at": "2026-04-30T00:00:01+00:00",
                            "files_touched": ["shared.txt"],
                            "actual_files_touched": ["shared.txt"],
                            "protected_path_violations": [], "write_scope_violations": [],
                            "validation_commands": [], "validation_intents": [],
                            "follow_ups": [], "notes": [],
                            "model": "claude-haiku-4-5-20251001", "model_profile": "simple",
                            "prompt_chars": 1, "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
                            "usage": None, "return_code": 0,
                            "prompt_path": "", "command_path": "",
                            "stdout_path": None, "stderr_path": None, "result_path": None,
                            "stdout_bytes": 0, "stderr_bytes": 0, "result_bytes": 0,
                            "dependency_materialization_mode": "summary-only",
                            "dependency_layers_applied": [], "unknown_fields": [],
                            "worker_validation_mode": "intents-only",
                            "worker_validation_mode_source": None,
                        },
                        "task-b": {
                            "id": "task-b", "title": "B", "agent": "implementer",
                            "status": "completed", "summary": "done",
                            "workspace_mode": "copy", "workspace_path": str(workspace_b),
                            "started_at": "2026-04-30T00:00:00+00:00",
                            "finished_at": "2026-04-30T00:00:01+00:00",
                            "files_touched": ["shared.txt"],
                            "actual_files_touched": ["shared.txt"],
                            "protected_path_violations": [], "write_scope_violations": [],
                            "validation_commands": [], "validation_intents": [],
                            "follow_ups": [], "notes": [],
                            "model": "claude-haiku-4-5-20251001", "model_profile": "simple",
                            "prompt_chars": 1, "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
                            "usage": None, "return_code": 0,
                            "prompt_path": "", "command_path": "",
                            "stdout_path": None, "stderr_path": None, "result_path": None,
                            "stdout_bytes": 0, "stderr_bytes": 0, "result_bytes": 0,
                            "dependency_materialization_mode": "summary-only",
                            "dependency_layers_applied": [], "unknown_fields": [],
                            "worker_validation_mode": "intents-only",
                            "worker_validation_mode_source": None,
                        },
                    },
                })
                with self.assertRaises(o.PromotionBlockedError):
                    o.plan_promotion(o.selected_run_records({"tasks": {
                        "task-a": {"id": "task-a", "title": "A", "agent": "implementer",
                            "status": "completed", "summary": "done",
                            "workspace_mode": "copy", "workspace_path": str(workspace_a),
                            "started_at": "2026-04-30T00:00:00+00:00",
                            "finished_at": "2026-04-30T00:00:01+00:00",
                            "files_touched": ["shared.txt"], "actual_files_touched": ["shared.txt"],
                            "protected_path_violations": [], "write_scope_violations": [],
                            "validation_commands": [], "validation_intents": [],
                            "follow_ups": [], "notes": [],
                            "model": "claude-haiku-4-5-20251001", "model_profile": "simple",
                            "prompt_chars": 1, "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
                            "usage": None, "return_code": 0,
                            "prompt_path": "", "command_path": "",
                            "stdout_path": None, "stderr_path": None, "result_path": None,
                            "stdout_bytes": 0, "stderr_bytes": 0, "result_bytes": 0,
                            "dependency_materialization_mode": "summary-only",
                            "dependency_layers_applied": [], "unknown_fields": [],
                            "worker_validation_mode": "intents-only", "worker_validation_mode_source": None},
                        "task-b": {"id": "task-b", "title": "B", "agent": "implementer",
                            "status": "completed", "summary": "done",
                            "workspace_mode": "copy", "workspace_path": str(workspace_b),
                            "started_at": "2026-04-30T00:00:00+00:00",
                            "finished_at": "2026-04-30T00:00:01+00:00",
                            "files_touched": ["shared.txt"], "actual_files_touched": ["shared.txt"],
                            "protected_path_violations": [], "write_scope_violations": [],
                            "validation_commands": [], "validation_intents": [],
                            "follow_ups": [], "notes": [],
                            "model": "claude-haiku-4-5-20251001", "model_profile": "simple",
                            "prompt_chars": 1, "prompt_estimated_tokens": 1,
                            "prompt_sections": [],
                            "prompt_budget": {"max_chars": None, "max_estimated_tokens": None, "exceeded": False, "violations": []},
                            "usage": None, "return_code": 0,
                            "prompt_path": "", "command_path": "",
                            "stdout_path": None, "stderr_path": None, "result_path": None,
                            "stdout_bytes": 0, "stderr_bytes": 0, "result_bytes": 0,
                            "dependency_materialization_mode": "summary-only",
                            "dependency_layers_applied": [], "unknown_fields": [],
                            "worker_validation_mode": "intents-only", "worker_validation_mode_source": None},
                    }}, []))
            finally:
                o.ROOT = old_root


class GlobalOptionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def _parse(self, *args):
        return self.orchestrator.parse_args.__wrapped__(*args) if hasattr(
            self.orchestrator.parse_args, "__wrapped__"
        ) else self.orchestrator.parse_args()

    def _parse_argv(self, argv):
        import sys as _sys
        old = _sys.argv[:]
        _sys.argv = ["claude-orchestrator"] + list(argv)
        try:
            return self.orchestrator.parse_args()
        finally:
            _sys.argv = old

    def test_verbose_flag_accepted_by_validate(self):
        args = self._parse_argv(["validate", "-v"])
        self.assertTrue(args.verbose)

    def test_verbose_flag_long_form_accepted_by_run(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--verbose"])
        self.assertTrue(args.verbose)

    def test_verbose_defaults_to_false_on_inventory(self):
        args = self._parse_argv(["inventory"])
        self.assertFalse(args.verbose)

    def test_json_accepted_by_status(self):
        args = self._parse_argv(["status", "some/run/dir", "--json"])
        self.assertTrue(args.json)

    def test_provider_bin_accepted_by_validate(self):
        args = self._parse_argv(["validate", "--provider-bin", "anthropic-cli"])
        self.assertEqual("anthropic-cli", args.claude_bin)

    def test_no_args_defaults_to_wizard(self):
        args = self._parse_argv([])
        self.assertEqual("wizard", args.command)

    def test_natural_language_args_route_to_wizard_goal(self):
        args = self._parse_argv(["add pagination to the employee endpoint"])
        self.assertEqual("wizard", args.command)
        self.assertEqual("add pagination to the employee endpoint", args.goal)

    def test_provider_bin_accepted_by_run(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--provider-bin", "my-cli"])
        self.assertEqual("my-cli", args.claude_bin)

    def test_claude_bin_legacy_alias_still_works(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--claude-bin", "legacy-claude"])
        self.assertEqual("legacy-claude", args.claude_bin)

    def test_hitl_flags_accepted_by_run(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--hitl", "--hitl-mode", "on-failure", "--hitl-auto-approve"])
        self.assertTrue(args.hitl)
        self.assertEqual("on-failure", args.hitl_mode)
        self.assertTrue(args.hitl_auto_approve)

    def test_hitl_flags_accepted_by_resume(self):
        args = self._parse_argv(["resume", "some/run/dir", "--hitl", "--hitl-mode", "always", "--hitl-auto-approve"])
        self.assertTrue(args.hitl)
        self.assertEqual("always", args.hitl_mode)
        self.assertTrue(args.hitl_auto_approve)

    def test_otel_endpoint_flag_accepted_by_run(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--otel-endpoint", "http://collector:4318/v1/traces"])
        self.assertEqual("http://collector:4318/v1/traces", args.otel_endpoint)

    def test_estimate_flag_accepted_by_run(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--estimate"])
        self.assertTrue(args.estimate)

    def test_follow_up_mode_flag_accepted_by_run(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--follow-up-mode", "inject"])
        self.assertEqual("inject", args.follow_up_mode)

    def test_follow_up_mode_flag_accepted_by_resume(self):
        args = self._parse_argv(["resume", "some/run/dir", "--follow-up-mode", "ignore"])
        self.assertEqual("ignore", args.follow_up_mode)

    def test_tui_flag_accepted_by_run(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--tui"])
        self.assertTrue(args.tui)

    def test_tui_flag_accepted_by_resume(self):
        args = self._parse_argv(["resume", "some/run/dir", "--tui"])
        self.assertTrue(args.tui)

    def test_tui_flag_accepted_by_retry(self):
        args = self._parse_argv(["retry", "some/run/dir", "--tui"])
        self.assertTrue(args.tui)

    def test_wizard_resume_flag_accepted(self):
        args = self._parse_argv(["wizard", "--resume", "some/run/dir", "--dry-run", "--json"])
        self.assertEqual("some/run/dir", args.resume_run_ref)
        self.assertTrue(args.dry_run)
        self.assertTrue(args.json)

    def test_otel_endpoint_flag_accepted_by_export_trace(self):
        args = self._parse_argv(["export-trace", "some/run/dir", "--otel-endpoint", "http://collector:4318/v1/traces"])
        self.assertEqual("http://collector:4318/v1/traces", args.otel_endpoint)

    def test_dry_run_accepted_by_validate(self):
        args = self._parse_argv(["validate", "--dry-run"])
        self.assertTrue(args.dry_run)

    def test_dry_run_accepted_by_review(self):
        args = self._parse_argv(["review", "some/run/dir", "--dry-run"])
        self.assertTrue(args.dry_run)

    def test_dry_run_accepted_by_inventory(self):
        args = self._parse_argv(["inventory", "--dry-run"])
        self.assertTrue(args.dry_run)

    def test_dry_run_accepted_by_cleanup(self):
        args = self._parse_argv(["cleanup", "some/run/dir", "--dry-run"])
        self.assertTrue(args.dry_run)

    def test_dry_run_accepted_by_export_patch(self):
        args = self._parse_argv(["export-patch", "some/run/dir", "--dry-run"])
        self.assertTrue(args.dry_run)

    def test_diff_run_accepts_task_and_path_filters(self):
        args = self._parse_argv(
            [
                "diff-run",
                "some/run/dir",
                "--task",
                "task-a",
                "--tasks",
                "task-b,task-c",
                "--path",
                "src/**",
                "--paths",
                "docs/**,README.md",
                "--stat",
                "--json",
            ]
        )
        self.assertEqual("some/run/dir", args.run_ref)
        self.assertEqual(["task-a"], args.selected_tasks)
        self.assertEqual("task-b,task-c", args.selected_task_csv)
        self.assertEqual(["src/**"], args.path_filters)
        self.assertEqual("docs/**,README.md", args.path_filters_csv)
        self.assertTrue(args.stat)
        self.assertTrue(args.json)

    def test_resolve_tui_mode_auto_enables_when_interactive_and_textual_available(self):
        enabled, watch, warning = self.orchestrator._resolve_tui_mode(
            requested=False,
            watch=False,
            json_output=False,
            stderr_isatty=True,
            textual_available=True,
        )

        self.assertTrue(enabled)
        self.assertFalse(watch)
        self.assertIsNone(warning)

    def test_resolve_tui_mode_falls_back_to_watch_when_textual_missing(self):
        enabled, watch, warning = self.orchestrator._resolve_tui_mode(
            requested=True,
            watch=False,
            json_output=False,
            stderr_isatty=True,
            textual_available=False,
        )

        self.assertFalse(enabled)
        self.assertTrue(watch)
        self.assertIn("falling back to --watch", warning)

    def test_resolve_tui_mode_ignores_tui_when_json_requested(self):
        enabled, watch, warning = self.orchestrator._resolve_tui_mode(
            requested=True,
            watch=False,
            json_output=True,
            stderr_isatty=True,
            textual_available=True,
        )

        self.assertFalse(enabled)
        self.assertFalse(watch)
        self.assertIn("--tui is ignored", warning)

    def test_dry_run_accepted_by_export_trace(self):
        args = self._parse_argv(["export-trace", "some/run/dir", "--dry-run"])
        self.assertTrue(args.dry_run)

    def test_json_accepted_by_cleanup(self):
        args = self._parse_argv(["cleanup", "some/run/dir", "--json"])
        self.assertTrue(args.json)

    def test_max_parallel_default_is_two(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan])
        self.assertEqual(2, args.max_parallel)

    def test_max_parallel_can_be_overridden(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        plan = str(root / "ai" / "orchestrator" / "tasks" / "example-parallel.json")
        args = self._parse_argv(["run", plan, "--max-parallel", "4"])
        self.assertEqual(4, args.max_parallel)


class SlopLoggingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_run_subprocess_emits_slop_progress_to_stderr(self):
        orchestrator = self.orchestrator
        stderr_buffer = io.StringIO()
        stderr_buffer.isatty = lambda: True
        original_interval = orchestrator.SLOP_PROGRESS_INTERVAL_SEC
        orchestrator.SLOP_PROGRESS_INTERVAL_SEC = 0.05
        try:
            with contextlib.redirect_stderr(stderr_buffer):
                completed = orchestrator.run_subprocess(
                    [
                        sys.executable,
                        "-c",
                        "import time; time.sleep(0.12); print('ok-from-worker')",
                    ],
                    cwd=pathlib.Path(__file__).resolve().parents[2],
                    timeout_sec=2,
                    progress_action=orchestrator.task_wait_action(
                        orchestrator.TaskDefinition(
                            id="worker-one",
                            title="Worker One",
                            agent="analyst",
                            prompt="Wait briefly.",
                        )
                    ),
                )
        finally:
            orchestrator.SLOP_PROGRESS_INTERVAL_SEC = original_interval

        self.assertEqual(0, completed.returncode)
        self.assertIn("ok-from-worker", completed.stdout)
        stderr_text = stderr_buffer.getvalue()
        self.assertIn("[WORKER-ONE][FLOW] Slopsloshing .", stderr_text)
        self.assertIn("[WORKER-ONE][FLOW] Slopsloshing ..", stderr_text)
        self.assertIn("(waiting for analyst)", stderr_text)

    def test_validation_wait_action_shortens_long_commands(self):
        orchestrator = self.orchestrator
        action = orchestrator.validation_wait_action(
            "python -c \"print('alpha'); print('beta'); print('gamma'); print('delta'); print('epsilon')\"",
            "command",
        )

        self.assertEqual("VALIDATE-RUN", action.actor)
        self.assertEqual("PROCESS", action.phase)
        self.assertEqual("Slopcrunching", action.phrase)
        self.assertIn("running validation command", action.reason)
        self.assertIn("...", action.reason)


class PromptBudgetTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_execute_task_dry_run_fails_when_prompt_budget_is_exceeded(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            max_prompt_estimated_tokens=10,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect-budget",
            title="Inspect budget",
            agent="analyst",
            prompt="Review the orchestrator guidance and summarize the most important contract details.",
            read_paths=["AGENTS.md", "ai/orchestrator/README.md"],
            validation=["scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="prompt-budget",
            goal="Keep prompts bounded.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt-budget dry-run validation.",
                constraints=["Keep the task small."],
                read_paths=["AGENTS.md"],
                validation=[],
            ),
            tasks=[task],
        )

        with tempfile.TemporaryDirectory() as tempdir:
            record = asyncio.run(orchestrator.execute_task(
                pathlib.Path(tempdir) / "run",
                pathlib.Path(tempdir) / "runtime",
                pathlib.Path(tempdir) / "workspaces",
                plan,
                {"analyst": agent},
                task,
                {},
                claude_bin="claude",
                agents_json="{}",
                dry_run=True,
            ))

        self.assertEqual("failed", record.status)
        self.assertTrue(record.prompt_budget.exceeded)
        self.assertIn("Prompt budget exceeded", record.summary)
        self.assertGreater(len(record.prompt_sections), 0)

    def test_plan_dry_run_reports_prompt_sections_and_budget(self):
        orchestrator = self.orchestrator
        root = pathlib.Path(__file__).resolve().parents[2]
        payload = orchestrator.plan_with_claude(
            SimpleNamespace(
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                planner_agent="planner",
                dry_run=True,
                claude_bin="claude",
                effort="medium",
                goal="Inspect the orchestrator prompt contract.",
                name="prompt-budget-check",
                files=["scripts/ai/claude-orchestrator.py"],
                constraints=["Keep the plan compact."],
                validation=["scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json"],
                out="",
            )
        )

        self.assertIn("promptSections", payload)
        self.assertIn("promptBudget", payload)
        self.assertFalse(payload["promptBudget"]["exceeded"])
        self.assertGreater(len(payload["promptSections"]), 0)
        self.assertIn("Prefer the smallest actor set", payload["prompt"])
        self.assertIn("structured-intent-only", payload["prompt"])
        planner_agents_index = payload["command"].index("--agents")
        planner_agents_payload = json.loads(payload["command"][planner_agents_index + 1])
        self.assertEqual(["planner"], sorted(planner_agents_payload))
        self.assertEqual("medium", payload["effort"])
        self.assertIn("--effort", payload["command"])
        self.assertEqual("medium", payload["command"][payload["command"].index("--effort") + 1])


if __name__ == "__main__":
    unittest.main()
