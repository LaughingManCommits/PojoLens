import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
from types import SimpleNamespace


def load_orchestrator_module():
    root = pathlib.Path(__file__).resolve().parents[2]
    module_path = root / "scripts" / "claude-orchestrator.py"
    spec = importlib.util.spec_from_file_location("claude_orchestrator", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load orchestrator module from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ClaudeCommandTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

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
            files=["AGENTS.md", "ai/orchestrator/README.md"],
            validation=["scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="prompt-budget",
            goal="Keep prompts bounded.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt-budget dry-run validation.",
                constraints=["Keep the task small."],
                files=["AGENTS.md"],
                validation=[],
            ),
            tasks=[task],
        )

        with tempfile.TemporaryDirectory() as tempdir:
            record = orchestrator.execute_task(
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
            )

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
                goal="Inspect the orchestrator prompt contract.",
                name="prompt-budget-check",
                files=["scripts/claude-orchestrator.py"],
                constraints=["Keep the plan compact."],
                validation=["scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json"],
                out="",
            )
        )

        self.assertIn("promptSections", payload)
        self.assertIn("promptBudget", payload)
        self.assertFalse(payload["promptBudget"]["exceeded"])
        self.assertGreater(len(payload["promptSections"]), 0)

    def test_dependency_summary_truncates_long_dependency_output(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="review",
            title="Review",
            agent="reviewer",
            prompt="Review the dependency output.",
            depends_on=["inspect"],
        )
        dependency_record = orchestrator.TaskRunRecord(
            id="inspect",
            title="Inspect",
            agent="analyst",
            status="completed",
            summary="very long summary " * 40,
            workspace_mode="copy",
            workspace_path="workspace",
            started_at="2026-04-04T00:00:00+00:00",
            finished_at="2026-04-04T00:00:01+00:00",
            files_touched=[],
            actual_files_touched=[],
            protected_path_violations=[],
            validation_commands=[],
            follow_ups=[],
            notes=[],
            model="claude-haiku-4-5",
            model_profile="simple",
            prompt_chars=0,
            prompt_estimated_tokens=0,
            prompt_sections=[],
            prompt_budget=orchestrator.PromptBudgetResult(
                max_chars=None,
                max_estimated_tokens=None,
                exceeded=False,
                violations=[],
            ),
            usage=None,
            return_code=None,
            prompt_path="",
            command_path="",
            stdout_path=None,
            stderr_path=None,
            result_path=None,
        )

        summary = orchestrator.dependency_summary({"inspect": dependency_record}, task)

        self.assertIn("...", summary)
        self.assertIn("inspect", summary)

    def test_select_parallel_ready_batch_serializes_overlapping_write_tasks(self):
        orchestrator = self.orchestrator
        implementer = orchestrator.AgentDefinition(
            name="implementer",
            description="impl",
            prompt="Return JSON only.",
            model_profile="balanced",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read", "Edit", "Write"],
            disallowed_tools=[],
        )
        reviewer = orchestrator.AgentDefinition(
            name="reviewer",
            description="review",
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
        task_a = orchestrator.TaskDefinition(
            id="edit-alpha",
            title="Edit alpha",
            agent="implementer",
            prompt="Edit alpha.",
            files=["src/alpha/FileA.java"],
        )
        task_b = orchestrator.TaskDefinition(
            id="edit-alpha-child",
            title="Edit alpha child",
            agent="implementer",
            prompt="Edit alpha child.",
            files=["src/alpha"],
        )
        task_c = orchestrator.TaskDefinition(
            id="review-docs",
            title="Review docs",
            agent="reviewer",
            prompt="Review docs.",
            files=["README.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="parallel-safety",
            goal="Serialize overlapping write tasks.",
            shared_context=orchestrator.SharedContext(
                summary="Parallel safety test.",
                constraints=[],
                files=[],
                validation=[],
            ),
            tasks=[task_a, task_b, task_c],
        )

        batch = orchestrator.select_parallel_ready_batch(
            plan,
            [task_a, task_b, task_c],
            {"implementer": implementer, "reviewer": reviewer},
            max_parallel=2,
        )

        batch_ids = {task.id for task in batch}
        self.assertIn("review-docs", batch_ids)
        self.assertEqual(2, len(batch_ids))
        self.assertFalse({"edit-alpha", "edit-alpha-child"}.issubset(batch_ids))

    def test_apply_workspace_audit_marks_protected_path_violation(self):
        orchestrator = self.orchestrator
        record = orchestrator.TaskRunRecord(
            id="edit-docs",
            title="Edit docs",
            agent="implementer",
            status="completed",
            summary="Completed safely.",
            workspace_mode="copy",
            workspace_path="workspace",
            started_at="2026-04-04T00:00:00+00:00",
            finished_at="2026-04-04T00:00:01+00:00",
            files_touched=[],
            actual_files_touched=[],
            protected_path_violations=[],
            validation_commands=[],
            follow_ups=[],
            notes=[],
            model="claude-sonnet-4-6",
            model_profile="balanced",
            prompt_chars=10,
            prompt_estimated_tokens=3,
            prompt_sections=[],
            prompt_budget=orchestrator.PromptBudgetResult(
                max_chars=None,
                max_estimated_tokens=None,
                exceeded=False,
                violations=[],
            ),
            usage=None,
            return_code=0,
            prompt_path="prompt.txt",
            command_path="command.json",
            stdout_path=None,
            stderr_path=None,
            result_path="result.json",
        )

        audited = orchestrator.apply_workspace_audit(
            record,
            reported_files=["ai/state/current-state.md"],
            actual_files=[],
        )

        self.assertEqual("failed", audited.status)
        self.assertIn("ai/state/current-state.md", audited.protected_path_violations)
        self.assertIn("Protected-path violation", audited.summary)

    def test_prepare_workspace_copy_is_sparse(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            (repo_root / "ai").mkdir(parents=True)
            (repo_root / "docs").mkdir(parents=True)
            (repo_root / "nested").mkdir(parents=True)
            (repo_root / "AGENTS.md").write_text("root guidance\n", encoding="utf-8")
            (repo_root / "ai" / "AGENTS.md").write_text("ai guidance\n", encoding="utf-8")
            (repo_root / "docs" / "guide.md").write_text("guide\n", encoding="utf-8")
            (repo_root / "notes.txt").write_text("notes\n", encoding="utf-8")
            (repo_root / "nested" / "hinted.txt").write_text("hinted\n", encoding="utf-8")
            (repo_root / "nested" / "ignored.txt").write_text("ignored\n", encoding="utf-8")
            (repo_root / "large.bin").write_bytes(
                b"x" * (orchestrator.MAX_HYDRATED_FILE_BYTES + 1)
            )
            orchestrator.ROOT = repo_root
            try:
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="sparse-copy",
                    goal="Hydrate only hinted files.",
                    shared_context=orchestrator.SharedContext(
                        summary="Sparse workspace test.",
                        constraints=[],
                        files=["docs/guide.md", "nested", "large.bin"],
                        validation=[],
                    ),
                    tasks=[],
                )
                task = orchestrator.TaskDefinition(
                    id="copy-task",
                    title="Copy task",
                    agent="analyst",
                    prompt="Inspect sparse workspace.",
                    files=["notes.txt", "nested/hinted.txt"],
                    workspace_mode="copy",
                )

                workspace = orchestrator.prepare_workspace(
                    plan,
                    task,
                    "copy",
                    temp_path / "workspace",
                    temp_path / "runtime",
                )
            finally:
                orchestrator.ROOT = old_root

            copied_files = sorted(
                str(path.relative_to(workspace)).replace("\\", "/")
                for path in workspace.rglob("*")
                if path.is_file()
            )

        self.assertEqual(
            ["AGENTS.md", "ai/AGENTS.md", "docs/guide.md", "nested/hinted.txt", "notes.txt"],
            copied_files,
        )

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
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual("run-1", payload["runId"])
        self.assertEqual(1, payload["taskCount"])
        self.assertEqual(1, payload["tasks"][0]["diffStats"]["filesChanged"])
        self.assertEqual("modified", payload["tasks"][0]["files"][0]["status"])

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

    def test_promote_run_rejects_duplicate_file_ownership(self):
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
                with self.assertRaisesRegex(
                    orchestrator.OrchestratorError,
                    "also changed by task 'edit-foo-a'",
                ):
                    orchestrator.promote_run(
                        SimpleNamespace(
                            run_ref=str(run_dir),
                            selected_tasks=[],
                            dry_run=True,
                        )
                    )
            finally:
                orchestrator.ROOT = old_root

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
                            "files": [],
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
                            "files": [],
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
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertEqual(["retry-b"], payload["requestedTaskIds"])
        self.assertEqual(["inspect-a", "retry-b"], payload["retriedTaskIds"])
        self.assertEqual([], payload["seededTaskIds"])
        self.assertEqual({"planned": 2}, payload["statusCounts"])

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


if __name__ == "__main__":
    unittest.main()
