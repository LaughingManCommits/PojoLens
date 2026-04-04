import importlib.util
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


if __name__ == "__main__":
    unittest.main()
