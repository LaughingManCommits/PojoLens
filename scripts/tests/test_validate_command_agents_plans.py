import json
import pathlib
import tempfile
import unittest
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandAgentsPlansTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_load_agents_requires_planner_agent(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            agents_path = pathlib.Path(tempdir) / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
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
                "required agent 'planner' is missing",
            ):
                orchestrator.load_agents(agents_path)

    def test_load_agents_preserves_skills(self):
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
                                "skills": ["caveman"],
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )

            agents = orchestrator.load_agents(agents_path)

            self.assertEqual(["caveman"], agents["planner"].skills)

    def test_load_agents_preserves_output_profile(self):
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
                                "outputProfile": "lean",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )

            agents = orchestrator.load_agents(agents_path)

            self.assertEqual("lean", agents["planner"].output_profile)

    def test_load_agents_rejects_unknown_skill_when_registry_exists(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            registry_path = temp_path / "skills" / "registry.json"
            registry_path.parent.mkdir(parents=True)
            registry_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "skills": {
                            "known": {
                                "description": "Known skill.",
                                "promptFile": "known/SKILL.md",
                            }
                        },
                    }
                ),
                encoding="utf-8",
            )
            (temp_path / "skills" / "known").mkdir()
            (temp_path / "skills" / "known" / "SKILL.md").write_text("Known skill.\n", encoding="utf-8")
            agents_path = temp_path / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "Plan",
                                "prompt": "Return JSON only.",
                                "skills": ["missing"],
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
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
                "unknown skills \\['missing'\\]",
            ):
                orchestrator.load_agents(agents_path)

    def test_load_agents_supports_prompt_file(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            prompt_path = temp_path / "agents" / "planner" / "prompt.md"
            prompt_path.parent.mkdir(parents=True)
            prompt_path.write_text("Return JSON only from markdown.\n", encoding="utf-8")
            agents_path = temp_path / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "Plan",
                                "promptFile": "agents/planner/prompt.md",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )

            agents = orchestrator.load_agents(agents_path)

            self.assertEqual("Return JSON only from markdown.", agents["planner"].prompt)

    def test_load_agents_rejects_prompt_and_prompt_file_together(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            prompt_path = temp_path / "planner.md"
            prompt_path.write_text("Return JSON only.\n", encoding="utf-8")
            agents_path = temp_path / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "Plan",
                                "prompt": "Inline prompt.",
                                "promptFile": "planner.md",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
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
                "define only one of 'prompt' or 'promptFile'",
            ):
                orchestrator.load_agents(agents_path)

    def test_load_agents_rejects_missing_prompt_file(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            agents_path = temp_path / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "Plan",
                                "promptFile": "missing.md",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
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
                "prompt file 'missing.md' does not exist",
            ):
                orchestrator.load_agents(agents_path)

    def test_load_agents_rejects_oversized_prompt_file(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            prompt_path = temp_path / "agents" / "planner" / "prompt.md"
            prompt_path.parent.mkdir(parents=True)
            prompt_path.write_text("A" * (orchestrator.AGENT_PROMPT_FAIL_BYTES + 1), encoding="utf-8")
            agents_path = temp_path / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "Plan",
                                "promptFile": "agents/planner/prompt.md",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
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
                "exceeds the hard cap",
            ):
                orchestrator.load_agents(agents_path)

    def test_load_agents_rejects_oversized_skill_prompt_file(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            registry_path = temp_path / "skills" / "registry.json"
            registry_path.parent.mkdir(parents=True)
            registry_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "skills": {
                            "huge": {
                                "description": "Huge skill.",
                                "promptFile": "huge/SKILL.md",
                            }
                        },
                    }
                ),
                encoding="utf-8",
            )
            (temp_path / "skills" / "huge").mkdir()
            (temp_path / "skills" / "huge" / "SKILL.md").write_text(
                "B" * (orchestrator.SKILL_PROMPT_FAIL_BYTES + 1),
                encoding="utf-8",
            )
            agents_path = temp_path / "agents.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "Plan",
                                "prompt": "Return JSON only.",
                                "skills": ["huge"],
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
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
                "exceeds the hard cap",
            ):
                orchestrator.load_agents(agents_path)

    def test_load_task_plan_rejects_unknown_agent(self):
        orchestrator = self.orchestrator
        planner = orchestrator.AgentDefinition(
            name="planner",
            description="Plan",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="Analyze",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        with tempfile.TemporaryDirectory() as tempdir:
            plan_path = pathlib.Path(tempdir) / "plan.json"
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "unknown-agent",
                        "goal": "Reject unknown task agents.",
                        "sharedContext": {
                            "summary": "Unknown agent test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect",
                                "title": "Inspect",
                                "agent": "ghost",
                                "prompt": "Inspect guidance.",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                orchestrator.OrchestratorError,
                "unknown agent 'ghost'",
            ):
                orchestrator.load_task_plan(plan_path, {"planner": planner, "analyst": analyst})

    def test_load_task_plan_preserves_task_skills(self):
        orchestrator = self.orchestrator
        planner = orchestrator.AgentDefinition(
            name="planner",
            description="Plan",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="Analyze",
            prompt="Return JSON only.",
            skills=["caveman"],
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            registry_path = temp_path / "skills" / "registry.json"
            registry_path.parent.mkdir(parents=True)
            registry_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "skills": {
                            "caveman": {
                                "description": "Compression skill.",
                                "promptFile": "caveman/SKILL.md",
                            },
                            "docs": {
                                "description": "Docs skill.",
                                "promptFile": "docs/SKILL.md",
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )
            (temp_path / "skills" / "caveman").mkdir()
            (temp_path / "skills" / "caveman" / "SKILL.md").write_text("Caveman.\n", encoding="utf-8")
            (temp_path / "skills" / "docs").mkdir()
            (temp_path / "skills" / "docs" / "SKILL.md").write_text("Docs.\n", encoding="utf-8")
            plan_path = temp_path / "plan.json"
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "task-skills",
                        "goal": "Keep task skills.",
                        "sharedContext": {
                            "summary": "Task skill test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect",
                                "title": "Inspect",
                                "agent": "analyst",
                                "prompt": "Inspect guidance.",
                                "skills": ["docs"],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            plan = orchestrator.load_task_plan(plan_path, {"planner": planner, "analyst": analyst})

            self.assertEqual(["docs"], plan.tasks[0].skills)

    def test_load_task_plan_preserves_output_profile(self):
        orchestrator = self.orchestrator
        planner = orchestrator.AgentDefinition(
            name="planner",
            description="Plan",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="Analyze",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        with tempfile.TemporaryDirectory() as tempdir:
            plan_path = pathlib.Path(tempdir) / "plan.json"
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "task-output-profile",
                        "goal": "Keep task output profile.",
                        "sharedContext": {
                            "summary": "Task output profile test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect",
                                "title": "Inspect",
                                "agent": "analyst",
                                "prompt": "Inspect guidance.",
                                "outputProfile": "lean",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            plan = orchestrator.load_task_plan(plan_path, {"planner": planner, "analyst": analyst})

            self.assertEqual("lean", plan.tasks[0].output_profile)

    def test_load_task_plan_rejects_dependency_cycle(self):
        orchestrator = self.orchestrator
        planner = orchestrator.AgentDefinition(
            name="planner",
            description="Plan",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="Analyze",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        with tempfile.TemporaryDirectory() as tempdir:
            plan_path = pathlib.Path(tempdir) / "plan.json"
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "dependency-cycle",
                        "goal": "Reject dependency cycles.",
                        "sharedContext": {
                            "summary": "Cycle test.",
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
                                "dependsOn": ["inspect-b"],
                            },
                            {
                                "id": "inspect-b",
                                "title": "Inspect B",
                                "agent": "analyst",
                                "prompt": "Inspect B.",
                                "dependsOn": ["inspect-a"],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                orchestrator.OrchestratorError,
                "Task plan contains a dependency cycle",
            ):
                orchestrator.load_task_plan(plan_path, {"planner": planner, "analyst": analyst})

    def test_load_task_plan_rejects_legacy_files_field(self):
        orchestrator = self.orchestrator
        planner = orchestrator.AgentDefinition(
            name="planner",
            description="Plan",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="Analyze",
            prompt="Return JSON only.",
            model_profile="simple",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        with tempfile.TemporaryDirectory() as tempdir:
            plan_path = pathlib.Path(tempdir) / "plan.json"
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "legacy-files",
                        "goal": "Reject legacy scope fields.",
                        "sharedContext": {
                            "summary": "Legacy scope test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect",
                                "title": "Inspect",
                                "agent": "analyst",
                                "prompt": "Inspect guidance.",
                                "files": ["README.md"],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                orchestrator.OrchestratorError,
                "legacy 'files' was replaced by 'readPaths' and 'writePaths'",
            ):
                orchestrator.load_task_plan(plan_path, {"planner": planner, "analyst": analyst})

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
            resolved_skills=[],
            branch_context_id="inspect",
            branch_parent_context_ids=[],
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
            follow_up_tasks=[],
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
        self.assertIn("context: `inspect`", summary)

    def test_dependency_summary_includes_key_notes_from_dependencies(self):
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
            resolved_skills=[],
            branch_context_id="inspect",
            branch_parent_context_ids=[],
            status="completed",
            summary="Inspected the coordinator rules.",
            workspace_mode="copy",
            workspace_path="workspace",
            started_at="2026-04-04T00:00:00+00:00",
            finished_at="2026-04-04T00:00:01+00:00",
            files_touched=[],
            actual_files_touched=[],
            protected_path_violations=[],
            validation_commands=[],
            follow_ups=["Open one follow-up issue."],
            follow_up_tasks=[],
            notes=[
                "Rule one is protected-path enforcement.",
                "Rule two is copy mode by default.",
                "Rule three is coordinator-owned review.",
            ],
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

        self.assertIn("key notes:", summary)
        self.assertIn("Rule one is protected-path enforcement.", summary)
        self.assertIn("... (1 more notes omitted)", summary)
        self.assertIn("next: Open one follow-up issue.", summary)

    def test_dependency_summary_marks_unknown_notes_and_follow_ups(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="review",
            title="Review",
            agent="reviewer",
            prompt="Review the dependency output.",
            depends_on=["inspect"],
        )
        dependency_record = make_task_run_record(
            orchestrator,
            orchestrator.TaskDefinition(
                id="inspect",
                title="Inspect",
                agent="analyst",
                prompt="Inspect the coordinator.",
            ),
            status="blocked",
            summary="Could not finish inspection because the worker lost tool access.",
        )
        dependency_record.unknown_fields = ["notes", "followUps"]

        summary = orchestrator.dependency_summary({"inspect": dependency_record}, task)

        self.assertIn("key notes: unknown", summary)
        self.assertIn("next: unknown", summary)

    def test_dependency_summary_for_reviewer_includes_diff_preview(self):
        orchestrator = self.orchestrator
        reviewer_task = orchestrator.TaskDefinition(
            id="review",
            title="Review",
            agent="reviewer",
            prompt="Review the dependency output.",
            depends_on=["implement"],
        )
        implementer_task = orchestrator.TaskDefinition(
            id="implement",
            title="Implement",
            agent="implementer",
            prompt="Make a small change.",
        )
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            workspace_root = temp_path / "workspace"
            repo_root.mkdir()
            workspace_root.mkdir()
            (repo_root / "foo.txt").write_text("old\nline\n", encoding="utf-8")
            (workspace_root / "foo.txt").write_text("new\nline\n", encoding="utf-8")
            orchestrator.ROOT = repo_root
            try:
                dependency_record = make_task_run_record(
                    orchestrator,
                    implementer_task,
                    status="completed",
                    summary="Updated foo.",
                    workspace_mode="copy",
                    workspace_path=str(workspace_root),
                )
                dependency_record.files_touched = ["foo.txt"]
                dependency_record.actual_files_touched = ["foo.txt"]
                summary = orchestrator.dependency_summary(
                    {"implement": dependency_record},
                    reviewer_task,
                )
            finally:
                orchestrator.ROOT = old_root

        self.assertIn("changed files:", summary)
        self.assertIn("`foo.txt` (modified, +1/-1)", summary)
        self.assertIn("diff preview:", summary)
        self.assertIn("-old", summary)
        self.assertIn("+new", summary)


if __name__ == "__main__":
    unittest.main()
