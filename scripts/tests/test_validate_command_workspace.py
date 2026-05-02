import pathlib
import subprocess
import tempfile
import unittest

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandWorkspaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

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
            write_paths=["src/alpha/FileA.java"],
        )
        task_b = orchestrator.TaskDefinition(
            id="edit-alpha-child",
            title="Edit alpha child",
            agent="implementer",
            prompt="Edit alpha child.",
            write_paths=["src/alpha"],
        )
        task_c = orchestrator.TaskDefinition(
            id="review-docs",
            title="Review docs",
            agent="reviewer",
            prompt="Review docs.",
            read_paths=["README.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="parallel-safety",
            goal="Serialize overlapping write tasks.",
            shared_context=orchestrator.SharedContext(
                summary="Parallel safety test.",
                constraints=[],
                read_paths=[],
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
            branch_context_id="edit-docs",
            branch_parent_context_ids=[],
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
            declared_write_scope=["docs"],
        )

        self.assertEqual("failed", audited.status)
        self.assertIn("ai/state/current-state.md", audited.protected_path_violations)
        self.assertIn("Protected-path violation", audited.summary)

    def test_apply_workspace_audit_marks_write_scope_violation(self):
        orchestrator = self.orchestrator
        record = make_task_run_record(
            orchestrator,
            orchestrator.TaskDefinition(
                id="edit-docs",
                title="Edit docs",
                agent="implementer",
                prompt="Edit docs.",
                write_paths=["docs"],
            ),
            status="completed",
            summary="Completed safely.",
            workspace_path="workspace",
        )

        audited = orchestrator.apply_workspace_audit(
            record,
            reported_files=["src/App.java"],
            actual_files=["src/App.java"],
            declared_write_scope=["docs"],
        )

        self.assertEqual("failed", audited.status)
        self.assertEqual(["src/App.java"], audited.write_scope_violations)
        self.assertIn("Write-scope violation", audited.summary)

    def test_validate_scope_contract_requires_write_paths_for_write_capable_tasks(self):
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
        plan = orchestrator.TaskPlan(
            version=1,
            name="missing-write-scope",
            goal="Reject implicit write scope.",
            shared_context=orchestrator.SharedContext(
                summary="Scope validation test.",
                constraints=[],
                read_paths=["README.md"],
                validation=[],
            ),
            tasks=[
                orchestrator.TaskDefinition(
                    id="edit-readme",
                    title="Edit README",
                    agent="implementer",
                    prompt="Edit the readme.",
                    read_paths=["README.md"],
                )
            ],
        )

        with self.assertRaisesRegex(
            orchestrator.OrchestratorError,
            "write-capable tasks must declare non-empty writePaths",
        ):
            orchestrator.validate_scope_contract(plan, {"implementer": implementer})

    def test_validate_scope_contract_rejects_copy_directory_read_paths(self):
        orchestrator = self.orchestrator
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
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
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            (repo_root / "docs").mkdir(parents=True)
            (repo_root / "AGENTS.md").write_text("root guidance\n", encoding="utf-8")
            (repo_root / "ai").mkdir(parents=True)
            (repo_root / "ai" / "AGENTS.md").write_text("ai guidance\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="bad-copy-read-path",
                    goal="Reject directory read paths.",
                    shared_context=orchestrator.SharedContext(
                        summary="Scope validation test.",
                        constraints=[],
                        read_paths=["docs"],
                        validation=[],
                    ),
                    tasks=[
                        orchestrator.TaskDefinition(
                            id="inspect-docs",
                            title="Inspect docs",
                            agent="analyst",
                            prompt="Inspect docs.",
                        )
                    ],
                )

                with self.assertRaisesRegex(
                    orchestrator.OrchestratorError,
                    "copy readPaths must be concrete files, not directories",
                ):
                    orchestrator.validate_scope_contract(plan, {"analyst": analyst})
            finally:
                orchestrator.ROOT = old_root

    def test_validate_scope_contract_rejects_apply_reviewed_without_dependencies(self):
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
        task = orchestrator.TaskDefinition(
            id="implement",
            title="Implement",
            agent="implementer",
            prompt="Implement the change.",
            write_paths=["README.md"],
            dependency_materialization="apply-reviewed",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="missing-materialization-dependency",
            goal="Reject apply-reviewed without dependencies.",
            shared_context=orchestrator.SharedContext(
                summary="Materialization validation test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        with self.assertRaisesRegex(
            orchestrator.OrchestratorError,
            "dependencyMaterialization='apply-reviewed' requires non-empty dependsOn",
        ):
            orchestrator.validate_scope_contract(plan, {"implementer": implementer})

    def test_validate_scope_contract_rejects_apply_reviewed_for_repo_workspace(self):
        orchestrator = self.orchestrator
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
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
        upstream = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect first.",
        )
        downstream = orchestrator.TaskDefinition(
            id="implement",
            title="Implement",
            agent="implementer",
            prompt="Implement from dependency state.",
            depends_on=["inspect"],
            write_paths=["README.md"],
            workspace_mode="repo",
            dependency_materialization="apply-reviewed",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="repo-materialization",
            goal="Reject apply-reviewed in repo mode.",
            shared_context=orchestrator.SharedContext(
                summary="Materialization validation test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[upstream, downstream],
        )

        with self.assertRaisesRegex(
            orchestrator.OrchestratorError,
            "dependencyMaterialization='apply-reviewed' is not allowed for workspaceMode='repo'",
        ):
            orchestrator.validate_scope_contract(
                plan,
                {"analyst": analyst, "implementer": implementer},
            )

    def test_validate_scope_contract_rejects_apply_reviewed_with_repo_dependency(self):
        orchestrator = self.orchestrator
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="repo",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
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
        upstream = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect first.",
        )
        downstream = orchestrator.TaskDefinition(
            id="implement",
            title="Implement",
            agent="implementer",
            prompt="Implement from dependency state.",
            depends_on=["inspect"],
            write_paths=["README.md"],
            dependency_materialization="apply-reviewed",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="repo-dependency-materialization",
            goal="Reject non-reviewable dependency workspaces.",
            shared_context=orchestrator.SharedContext(
                summary="Materialization validation test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[upstream, downstream],
        )

        with self.assertRaisesRegex(
            orchestrator.OrchestratorError,
            "dependency 'inspect' resolves to workspaceMode='repo'",
        ):
            orchestrator.validate_scope_contract(
                plan,
                {"analyst": analyst, "implementer": implementer},
            )

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
                        read_paths=["docs/guide.md"],
                        validation=[],
                    ),
                    tasks=[],
                )
                task = orchestrator.TaskDefinition(
                    id="copy-task",
                    title="Copy task",
                    agent="analyst",
                    prompt="Inspect sparse workspace.",
                    read_paths=["notes.txt", "nested/hinted.txt"],
                    workspace_mode="copy",
                )

                workspace = orchestrator.prepare_workspace(
                    plan,
                    task,
                    "copy",
                    temp_path / "workspace",
                    temp_path / "runtime",
                    {},
                )
            finally:
                orchestrator.ROOT = old_root

            workspace = workspace.workspace_path
            copied_files = sorted(
                str(path.relative_to(workspace)).replace("\\", "/")
                for path in workspace.rglob("*")
                if path.is_file()
            )

        self.assertEqual(["docs/guide.md", "nested/hinted.txt", "notes.txt"], copied_files)

    def test_prepare_workspace_worktree_surfaces_git_failure(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_ensure_clean = orchestrator.ensure_clean_for_worktrees
        old_subprocess_run = orchestrator.subprocess.run
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            workspace_path = runtime_root / "workspaces" / "edit-worktree"
            repo_root.mkdir()
            runtime_root.mkdir()
            orchestrator.ROOT = repo_root
            task = orchestrator.TaskDefinition(
                id="edit-worktree",
                title="Edit worktree",
                agent="implementer",
                prompt="Edit from worktree.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="worktree-failure",
                goal="Surface worktree creation failures.",
                shared_context=orchestrator.SharedContext(
                    summary="Worktree failure test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            def fake_run(command, cwd, capture_output, text, check):
                return subprocess.CompletedProcess(command, 1, stdout="", stderr="git add failed")

            orchestrator.ensure_clean_for_worktrees = lambda: None
            orchestrator.subprocess.run = fake_run
            try:
                with self.assertRaisesRegex(
                    orchestrator.OrchestratorError,
                    "failed to create worktree: git add failed",
                ):
                    orchestrator.prepare_workspace(
                        plan,
                        task,
                        "worktree",
                        workspace_path,
                        runtime_root,
                        {},
                    )
            finally:
                orchestrator.ROOT = old_root
                orchestrator.ensure_clean_for_worktrees = old_ensure_clean
                orchestrator.subprocess.run = old_subprocess_run

    def test_prepare_workspace_copy_materializes_reviewed_dependency_layers(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            upstream_workspace = temp_path / "upstream-workspace"
            runtime_root = temp_path / "runtime"
            downstream_workspace = temp_path / "workspace"
            (repo_root / "ai").mkdir(parents=True)
            (repo_root / "src").mkdir(parents=True)
            (upstream_workspace / "src").mkdir(parents=True)
            (repo_root / "AGENTS.md").write_text("root guidance\n", encoding="utf-8")
            (repo_root / "ai" / "AGENTS.md").write_text("ai guidance\n", encoding="utf-8")
            (repo_root / "src" / "feature.txt").write_text("base\n", encoding="utf-8")
            (upstream_workspace / "src" / "feature.txt").write_text("reviewed\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                upstream = orchestrator.TaskDefinition(
                    id="edit-upstream",
                    title="Edit upstream",
                    agent="implementer",
                    prompt="Edit the feature.",
                    write_paths=["src/feature.txt"],
                    workspace_mode="copy",
                )
                downstream = orchestrator.TaskDefinition(
                    id="edit-downstream",
                    title="Edit downstream",
                    agent="implementer",
                    prompt="Continue from upstream state.",
                    depends_on=["edit-upstream"],
                    write_paths=["src/feature.txt"],
                    workspace_mode="copy",
                    dependency_materialization="apply-reviewed",
                )
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="materialize-reviewed-layer",
                    goal="Layer reviewed dependency state into the downstream workspace.",
                    shared_context=orchestrator.SharedContext(
                        summary="Materialization workspace prep test.",
                        constraints=[],
                        read_paths=[],
                        validation=[],
                    ),
                    tasks=[upstream, downstream],
                )
                dependency_record = make_task_run_record(
                    orchestrator,
                    upstream,
                    status="completed",
                    summary="Edited the feature.",
                    workspace_path=str(upstream_workspace),
                    files_touched=["src/feature.txt"],
                )

                preparation = orchestrator.prepare_workspace(
                    plan,
                    downstream,
                    "copy",
                    downstream_workspace,
                    runtime_root,
                    {"edit-upstream": dependency_record},
                )
            finally:
                orchestrator.ROOT = old_root

            layered_text = (preparation.workspace_path / "src" / "feature.txt").read_text(
                encoding="utf-8"
            )

        self.assertEqual("reviewed\n", layered_text)
        self.assertEqual(1, len(preparation.dependency_layers_applied))
        self.assertEqual("edit-upstream", preparation.dependency_layers_applied[0].task_id)
        self.assertEqual(
            ["src/feature.txt"],
            [operation.path for operation in preparation.dependency_layers_applied[0].operations],
        )

    def test_prepare_workspace_copy_rejects_ambiguous_dependency_materialization(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            workspace_path = temp_path / "workspace"
            dep_a_workspace = temp_path / "dep-a"
            dep_b_workspace = temp_path / "dep-b"
            (repo_root / "ai").mkdir(parents=True)
            (repo_root / "src").mkdir(parents=True)
            (dep_a_workspace / "src").mkdir(parents=True)
            (dep_b_workspace / "src").mkdir(parents=True)
            (repo_root / "AGENTS.md").write_text("root guidance\n", encoding="utf-8")
            (repo_root / "ai" / "AGENTS.md").write_text("ai guidance\n", encoding="utf-8")
            (repo_root / "src" / "feature.txt").write_text("base\n", encoding="utf-8")
            (dep_a_workspace / "src" / "feature.txt").write_text("dep a\n", encoding="utf-8")
            (dep_b_workspace / "src" / "feature.txt").write_text("dep b\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                dep_a = orchestrator.TaskDefinition(
                    id="edit-a",
                    title="Edit A",
                    agent="implementer",
                    prompt="Edit from branch A.",
                    write_paths=["src/feature.txt"],
                    workspace_mode="copy",
                )
                dep_b = orchestrator.TaskDefinition(
                    id="edit-b",
                    title="Edit B",
                    agent="implementer",
                    prompt="Edit from branch B.",
                    write_paths=["src/feature.txt"],
                    workspace_mode="copy",
                )
                downstream = orchestrator.TaskDefinition(
                    id="merge-downstream",
                    title="Merge downstream",
                    agent="implementer",
                    prompt="Merge dependency output.",
                    depends_on=["edit-a", "edit-b"],
                    write_paths=["src/feature.txt"],
                    workspace_mode="copy",
                    dependency_materialization="apply-reviewed",
                )
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="ambiguous-materialization",
                    goal="Reject overlapping dependency layers.",
                    shared_context=orchestrator.SharedContext(
                        summary="Materialization conflict test.",
                        constraints=[],
                        read_paths=[],
                        validation=[],
                    ),
                    tasks=[dep_a, dep_b, downstream],
                )
                dep_a_record = make_task_run_record(
                    orchestrator,
                    dep_a,
                    status="completed",
                    summary="Edited feature in A.",
                    workspace_path=str(dep_a_workspace),
                    files_touched=["src/feature.txt"],
                )
                dep_b_record = make_task_run_record(
                    orchestrator,
                    dep_b,
                    status="completed",
                    summary="Edited feature in B.",
                    workspace_path=str(dep_b_workspace),
                    files_touched=["src/feature.txt"],
                )

                with self.assertRaisesRegex(
                    orchestrator.OrchestratorError,
                    "dependency materialization is ambiguous for 'src/feature.txt'",
                ):
                    orchestrator.prepare_workspace(
                        plan,
                        downstream,
                        "copy",
                        workspace_path,
                        runtime_root,
                        {"edit-a": dep_a_record, "edit-b": dep_b_record},
                    )
            finally:
                orchestrator.ROOT = old_root

    def test_snapshot_workspace_files_hashes_files_and_ignores_internal_dirs(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            workspace = pathlib.Path(tempdir)
            (workspace / "src").mkdir()
            (workspace / "src" / "main.txt").write_text("main\n", encoding="utf-8")
            (workspace / "__pycache__").mkdir()
            (workspace / "__pycache__" / "ignored.pyc").write_bytes(b"cache")

            snapshots = orchestrator.snapshot_workspace_files(workspace)
            expected_hash = orchestrator.file_sha256(workspace / "src" / "main.txt")

        self.assertEqual(["src/main.txt"], sorted(snapshots))
        self.assertEqual(expected_hash, snapshots["src/main.txt"])


if __name__ == "__main__":
    unittest.main()
