import json
import pathlib
import sys
import tempfile
import unittest
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandTopologyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_analyze_plan_topology_flags_read_only_review_flow(self):
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
        inspect_task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the docs.",
        )
        review_task = orchestrator.TaskDefinition(
            id="review",
            title="Review",
            agent="reviewer",
            prompt="Review the findings.",
            depends_on=["inspect"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="read-only-review",
            goal="Show read-only review topology.",
            shared_context=orchestrator.SharedContext(
                summary="Read-only review topology test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[inspect_task, review_task],
        )

        topology = orchestrator.analyze_plan_topology(
            plan,
            {"analyst": analyst, "reviewer": reviewer},
        )

        self.assertEqual(2, topology["taskCount"])
        self.assertEqual({"analyst": 1, "reviewer": 1}, topology["agentCounts"])
        self.assertEqual(2, topology["readOnlyTaskCount"])
        self.assertEqual(0, topology["writeTaskCount"])
        self.assertEqual(2, topology["batchCount"])
        self.assertEqual([1, 1], topology["batchSizes"])
        self.assertEqual(1, topology["maxDependencyHops"])
        self.assertEqual({"inspect": 0, "review": 1}, topology["taskDependencyHops"])
        self.assertEqual(1, topology["warningCount"])
        self.assertEqual("read-only-review-optional", topology["warnings"][0]["kind"])

    def test_analyze_plan_topology_allows_single_reviewer_only_plan(self):
        orchestrator = self.orchestrator
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
        review_task = orchestrator.TaskDefinition(
            id="review",
            title="Review",
            agent="reviewer",
            prompt="Review the docs directly.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="reviewer-only",
            goal="Allow a direct reviewer task without warning.",
            shared_context=orchestrator.SharedContext(
                summary="Reviewer-only topology test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[review_task],
        )

        topology = orchestrator.analyze_plan_topology(
            plan,
            {"reviewer": reviewer},
        )

        self.assertEqual(1, topology["taskCount"])
        self.assertEqual({"reviewer": 1}, topology["agentCounts"])
        self.assertEqual(0, topology["warningCount"])
        self.assertEqual([], topology["warnings"])

    def test_analyze_plan_topology_flags_docs_validation_missing(self):
        orchestrator = self.orchestrator
        implementer = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read", "Edit"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="edit-docs",
            title="Edit docs",
            agent="implementer",
            prompt="Improve docs.",
            write_paths=["docs/guide.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="docs-validation-missing",
            goal="Warn when docs-only plans skip docs validation.",
            shared_context=orchestrator.SharedContext(
                summary="Docs plan.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        topology = orchestrator.analyze_plan_topology(plan, {"implementer": implementer})

        warning = next(item for item in topology["warnings"] if item["kind"] == "docs-validation-missing")
        self.assertEqual(["edit-docs"], warning["taskIds"])
        self.assertIn("check-doc-consistency", warning["message"])

    def test_analyze_plan_topology_flags_reviewer_prompt_budget_risk(self):
        orchestrator = self.orchestrator
        implementer = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
            prompt="Return JSON only.",
            model_profile="balanced",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            max_prompt_estimated_tokens=1200,
            allowed_tools=["Read", "Edit"],
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
            max_prompt_estimated_tokens=1600,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task_a = orchestrator.TaskDefinition(
            id="task-a",
            title="Task A",
            agent="implementer",
            prompt="Implement A.",
            write_paths=["a.txt"],
        )
        task_b = orchestrator.TaskDefinition(
            id="task-b",
            title="Task B",
            agent="implementer",
            prompt="Implement B.",
            write_paths=["b.txt"],
        )
        review_task = orchestrator.TaskDefinition(
            id="review",
            title="Review",
            agent="reviewer",
            prompt="Review both changes.",
            depends_on=["task-a", "task-b"],
            dependency_materialization="apply-reviewed",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="review-budget-risk",
            goal="Warn about reviewer prompt budget inheritance.",
            shared_context=orchestrator.SharedContext(
                summary="Budget warning test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task_a, task_b, review_task],
        )

        topology = orchestrator.analyze_plan_topology(
            plan,
            {"implementer": implementer, "reviewer": reviewer},
        )

        warning = next(
            item for item in topology["warnings"] if item["kind"] == "reviewer-prompt-budget-risk"
        )
        self.assertEqual(["task-a", "task-b", "review"], warning["taskIds"])
        self.assertIn("inherits 1600 prompt-token budget", warning["message"])

    def test_analyze_plan_topology_flags_agent_prompt_size_warning(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="X" * (orchestrator.AGENT_PROMPT_WARN_BYTES + 1),
            prompt_path="ai/orchestrator/agents/analyst/prompt.md",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect guidance.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="agent-prompt-size-warning",
            goal="Warn on oversized always-loaded role prompts.",
            shared_context=orchestrator.SharedContext(
                summary="Agent prompt warning test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        topology = orchestrator.analyze_plan_topology(plan, {"analyst": agent})

        warning = next(item for item in topology["warnings"] if item["kind"] == "agent-prompt-size-warning")
        self.assertEqual(["inspect"], warning["taskIds"])
        self.assertIn("warning threshold", warning["message"])

    def test_analyze_plan_topology_flags_skill_prompt_size_warning(self):
        orchestrator = self.orchestrator
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            skills=["bigskill"],
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect guidance.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="skill-prompt-size-warning",
            goal="Warn on oversized skill text.",
            shared_context=orchestrator.SharedContext(
                summary="Skill prompt warning test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )
        plan_support_module = sys.modules["pojo_lens_agents.plan_support"]
        old_default_agents_path = plan_support_module.DEFAULT_AGENTS_PATH
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            registry_root = temp_path / "ai" / "orchestrator"
            (registry_root / "skills" / "bigskill").mkdir(parents=True)
            (registry_root / "skills" / "registry.json").write_text(
                json.dumps(
                    {
                        "version": 1,
                        "skills": {
                            "bigskill": {
                                "description": "Large skill.",
                                "promptFile": "bigskill/SKILL.md",
                            }
                        },
                    }
                ),
                encoding="utf-8",
            )
            (registry_root / "skills" / "bigskill" / "SKILL.md").write_text(
                "S" * (orchestrator.SKILL_PROMPT_WARN_BYTES + 1),
                encoding="utf-8",
            )
            plan_support_module.DEFAULT_AGENTS_PATH = registry_root / "agents.json"
            try:
                topology = orchestrator.analyze_plan_topology(plan, {"analyst": analyst})
            finally:
                plan_support_module.DEFAULT_AGENTS_PATH = old_default_agents_path

        warning = next(item for item in topology["warnings"] if item["kind"] == "skill-prompt-size-warning")
        self.assertEqual(["inspect"], warning["taskIds"])
        self.assertIn("Keep skills narrow", warning["message"])

    def test_analyze_plan_topology_flags_resolved_skills_count_warning(self):
        orchestrator = self.orchestrator
        analyst = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            skills=["one", "two", "three", "four", "five"],
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect guidance.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="resolved-skills-warning",
            goal="Warn on stacked skills.",
            shared_context=orchestrator.SharedContext(
                summary="Resolved skills warning test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        topology = orchestrator.analyze_plan_topology(plan, {"analyst": analyst})

        warning = next(item for item in topology["warnings"] if item["kind"] == "resolved-skills-count-warning")
        self.assertEqual(["inspect"], warning["taskIds"])
        self.assertIn("warning threshold", warning["message"])

    def test_validate_command_reports_effective_worker_validation_sources(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "planning",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "effort": "high",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "analyst": {
                                "description": "analysis",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "effort": "high",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "workerValidationMode": "intents-only",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "implementer": {
                                "description": "implementation",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "effort": "high",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "workerValidationMode": "intents-only",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
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
                        "name": "validate-worker-validation",
                        "goal": "Inspect effective validation-mode sources.",
                        "sharedContext": {
                            "summary": "Validation summary test.",
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
                            },
                            {
                                "id": "implement",
                                "title": "Implement",
                                "agent": "implementer",
                                "prompt": "Implement change.",
                                "workerValidationMode": "intents-only",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            payload = orchestrator.validate_command(
                SimpleNamespace(
                    agents=str(agents_path),
                    task_plan=str(plan_path),
                )
            )

        self.assertEqual(
            {"analyst": "intents-only", "implementer": "intents-only", "planner": None},
            payload["agentWorkerValidationModes"],
        )
        self.assertEqual(
            {"inspect": "intents-only", "implement": "intents-only"},
            payload["taskWorkerValidationModes"],
        )
        self.assertEqual(
            {"inspect": "agent", "implement": "task"},
            payload["taskWorkerValidationModeSources"],
        )
        self.assertEqual(
            {
                "inspect": orchestrator.MODEL_PROFILE_TO_MODEL["simple"],
                "implement": orchestrator.MODEL_PROFILE_TO_MODEL["simple"],
            },
            payload["taskModels"],
        )
        self.assertEqual(
            {"inspect": "simple", "implement": "simple"},
            payload["taskModelProfiles"],
        )
        self.assertEqual([], payload["complexModelTaskIds"])
        self.assertEqual(0, payload["complexModelTaskCount"])
        self.assertEqual(
            [
                {
                    "id": "inspect",
                    "agent": "analyst",
                    "skills": [],
                    "resolvedSkills": [],
                    "model": orchestrator.MODEL_PROFILE_TO_MODEL["simple"],
                    "modelProfile": "simple",
                    "effort": "high",
                    "effortSource": "agent",
                    "readPaths": [],
                    "writePaths": [],
                    "dependencyMaterialization": "summary-only",
                    "workerValidationMode": "intents-only",
                    "workerValidationModeSource": "agent",
                },
                {
                    "id": "implement",
                    "agent": "implementer",
                    "skills": [],
                    "resolvedSkills": [],
                    "model": orchestrator.MODEL_PROFILE_TO_MODEL["simple"],
                    "modelProfile": "simple",
                    "effort": "high",
                    "effortSource": "agent",
                    "readPaths": [],
                    "writePaths": [],
                    "dependencyMaterialization": "summary-only",
                    "workerValidationMode": "intents-only",
                    "workerValidationModeSource": "task",
                },
            ],
            payload["tasks"],
        )

    def test_validate_command_reports_complex_model_tasks(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "planning",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "analyst": {
                                "description": "analysis",
                                "prompt": "Return JSON only.",
                                "modelProfile": "balanced",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
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
                        "name": "complex-model-visibility",
                        "goal": "Expose exceptional model choices.",
                        "sharedContext": {
                            "summary": "Complex model summary test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect",
                                "title": "Inspect",
                                "agent": "analyst",
                                "prompt": "Inspect the coordinator.",
                            },
                            {
                                "id": "deep-design",
                                "title": "Deep design",
                                "agent": "analyst",
                                "prompt": "Reason about architecture tradeoffs.",
                                "modelProfile": "complex",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            payload = orchestrator.validate_command(
                SimpleNamespace(
                    agents=str(agents_path),
                    task_plan=str(plan_path),
                )
            )

        self.assertEqual(
            {
                "inspect": orchestrator.MODEL_PROFILE_TO_MODEL["balanced"],
                "deep-design": orchestrator.MODEL_PROFILE_TO_MODEL["complex"],
            },
            payload["taskModels"],
        )
        self.assertEqual(
            {"inspect": "balanced", "deep-design": "complex"},
            payload["taskModelProfiles"],
        )
        self.assertEqual(["deep-design"], payload["complexModelTaskIds"])
        self.assertEqual(1, payload["complexModelTaskCount"])

    def test_validate_command_reports_topology_summary_and_warnings(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "planning",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "analyst": {
                                "description": "analysis",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "implementer": {
                                "description": "implementation",
                                "prompt": "Return JSON only.",
                                "modelProfile": "balanced",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read", "Edit"],
                                "timeoutSec": 30,
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
                        "name": "topology-summary",
                        "goal": "Expose lean-plan topology warnings.",
                        "sharedContext": {
                            "summary": "Topology summary test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "inspect",
                                "title": "Inspect",
                                "agent": "analyst",
                                "prompt": "Inspect the code.",
                            },
                            {
                                "id": "implement",
                                "title": "Implement",
                                "agent": "implementer",
                                "prompt": "Implement the change.",
                                "dependsOn": ["inspect"],
                                "writePaths": ["scripts/tests/test_claude_orchestrator.py"],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            payload = orchestrator.validate_command(
                SimpleNamespace(
                    agents=str(agents_path),
                    task_plan=str(plan_path),
                )
            )

        self.assertEqual({"analyst": 1, "implementer": 1}, payload["topology"]["agentCounts"])
        self.assertEqual(1, payload["topology"]["writeTaskCount"])
        self.assertEqual(1, payload["topology"]["readOnlyTaskCount"])
        self.assertEqual(2, payload["topology"]["batchCount"])
        self.assertEqual(1, payload["topology"]["maxParallelWidth"])
        self.assertEqual(1, payload["topology"]["maxDependencyHops"])
        self.assertEqual({"inspect": 0, "implement": 1}, payload["topology"]["taskDependencyHops"])
        self.assertEqual(1, payload["topology"]["warningCount"])
        self.assertEqual(
            "single-write-task-upstream-analyst",
            payload["topology"]["warnings"][0]["kind"],
        )

    def test_validate_command_rejects_compat_worker_validation_mode(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
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
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
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
                        "name": "reject-compat",
                        "goal": "Reject compat workers.",
                        "sharedContext": {
                            "summary": "Compat rejection test.",
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
                                "workerValidationMode": "compat",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                orchestrator.OrchestratorError,
                "workerValidationMode='compat' was removed from the live worker contract",
            ):
                orchestrator.validate_command(
                    SimpleNamespace(
                        agents=str(agents_path),
                        task_plan=str(plan_path),
                    )
                )


if __name__ == "__main__":
    unittest.main()
