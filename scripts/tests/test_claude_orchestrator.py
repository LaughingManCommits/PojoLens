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
from dataclasses import asdict
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


def make_task_run_record(
    orchestrator,
    task,
    *,
    status,
    summary,
    agent_name=None,
    workspace_mode="copy",
    workspace_path="",
    files_touched=None,
    actual_files_touched=None,
    dependency_materialization_mode=None,
    dependency_layers_applied=None,
    usage=None,
    stdout_path=None,
    stderr_path=None,
    result_path=None,
    stdout_bytes=0,
    stderr_bytes=0,
    result_bytes=0,
):
    return orchestrator.TaskRunRecord(
        id=task.id,
        title=task.title,
        agent=agent_name or task.agent,
        status=status,
        summary=summary,
        workspace_mode=workspace_mode,
        workspace_path=workspace_path,
        started_at="2026-04-04T00:00:00+00:00",
        finished_at="2026-04-04T00:00:01+00:00",
        files_touched=list(files_touched or []),
        actual_files_touched=list(actual_files_touched or []),
        protected_path_violations=[],
        write_scope_violations=[],
        validation_commands=[],
        follow_ups=[],
        notes=[],
        model="claude-haiku-4-5",
        model_profile="simple",
        prompt_chars=1,
        prompt_estimated_tokens=1,
        prompt_sections=[],
        prompt_budget=orchestrator.PromptBudgetResult(
            max_chars=None,
            max_estimated_tokens=None,
            exceeded=False,
            violations=[],
        ),
        usage=usage,
        return_code=0 if status == "completed" else 1,
        prompt_path="",
        command_path="",
        stdout_path=stdout_path,
        stderr_path=stderr_path,
        result_path=result_path,
        stdout_bytes=stdout_bytes,
        stderr_bytes=stderr_bytes,
        result_bytes=result_bytes,
        dependency_materialization_mode=(
            dependency_materialization_mode
            or orchestrator.DEFAULT_DEPENDENCY_MATERIALIZATION_MODE
        ),
        dependency_layers_applied=list(dependency_layers_applied or []),
    )


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
                        model_profile="simple",
                    ),
                    "analyst": orchestrator.AgentDefinition(
                        name="analyst",
                        description="Analyze work.",
                        prompt="Analyst prompt.",
                        model_profile="simple",
                    ),
                },
                selected_names=["planner"],
            )
        )

        self.assertEqual(["planner"], sorted(payload))

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
            validation=["scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json"],
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
        self.assertIn("Prefer the smallest actor set", payload["prompt"])
        self.assertIn("structured-intent-only", payload["prompt"])
        planner_agents_index = payload["command"].index("--agents")
        planner_agents_payload = json.loads(payload["command"][planner_agents_index + 1])
        self.assertEqual(["planner"], sorted(planner_agents_payload))


class ValidateCommandTest(unittest.TestCase):
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
            {"inspect": "claude-haiku-4-5", "implement": "claude-haiku-4-5"},
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
                    "model": "claude-haiku-4-5",
                    "modelProfile": "simple",
                    "readPaths": [],
                    "writePaths": [],
                    "dependencyMaterialization": "summary-only",
                    "workerValidationMode": "intents-only",
                    "workerValidationModeSource": "agent",
                },
                {
                    "id": "implement",
                    "agent": "implementer",
                    "model": "claude-haiku-4-5",
                    "modelProfile": "simple",
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
            {"inspect": "claude-sonnet-4-6", "deep-design": "claude-opus-4-6"},
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
        self.assertNotIn("next:", summary)

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

    def test_intents_only_output_contract_lives_in_agent_prompt(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt=(
                "Return JSON only. Emit only structured `validationIntents`. "
                "Use `[]` for known-empty `filesTouched`."
            ),
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
            prompt="Inspect the coordinator.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="intent-only-worker-prompt",
            goal="Keep validation suggestions intent-only.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        agent_payload = orchestrator.agent_payload_for_claude(
            {"analyst": agent},
            selected_names=["analyst"],
        )

        self.assertNotIn("Emit only structured `validationIntents`", rendered.text)
        self.assertNotIn("Use `[]` for known-empty `filesTouched`", rendered.text)
        self.assertIn("Emit only structured `validationIntents`", agent_payload)
        self.assertIn("Use `[]` for known-empty `filesTouched`", agent_payload)

    def test_worker_prompt_minimal_mode_uses_task_local_read_paths_only(self):
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
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
            read_paths=["task-only.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="minimal-read-context",
            goal="Keep minimal worker prompts task-local.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=["shared.md"],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        self.assertIn("`task-only.md`", rendered.text)
        self.assertNotIn("`shared.md`", rendered.text)

    def test_worker_prompt_full_mode_includes_shared_read_paths(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="full",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
            read_paths=["task-only.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="full-read-context",
            goal="Keep full worker prompts inclusive.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=["shared.md"],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        self.assertIn("`task-only.md`", rendered.text)
        self.assertIn("`shared.md`", rendered.text)

    def test_worker_prompt_execution_context_omits_absolute_workspace_paths(self):
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
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="execution-context-shape",
            goal="Keep execution context cache-friendly.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )
        workspace = pathlib.Path("C:/tmp/workspace")

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            workspace,
            "- none",
            worker_validation_mode="intents-only",
        )

        self.assertIn("Current working directory: isolated task workspace", rendered.text)
        self.assertNotIn(str(workspace), rendered.text)
        self.assertIn("Contract:", rendered.text)

    def test_worker_prompt_rules_fit_without_truncation(self):
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
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="worker-rules-shape",
            goal="Keep worker rules compact and complete.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        worker_rules = next(
            section for section in rendered.sections if section.name == "worker_rules"
        )
        self.assertFalse(worker_rules.truncated)
        self.assertIn("Treat this prompt plus the declared workspace as the full contract.", rendered.text)
        self.assertLessEqual(worker_rules.item_count, 7)
        self.assertIn("Treat `writePaths` as the edit contract.", rendered.text)
        self.assertNotIn("Emit only structured `validationIntents`", rendered.text)
        self.assertNotIn("Use `[]` for known-empty", rendered.text)

    def test_coerce_worker_result_compacts_verbose_fields(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "completed",
            "summary": "Summary " * 80,
            "filesTouched": ["src/main/App.java", "src/main/App.java"],
            "validationIntents": [
                {
                    "kind": "repo-script",
                    "entrypoint": "scripts/check-doc-consistency.ps1",
                },
                {
                    "kind": "tool",
                    "entrypoint": "mvn",
                    "args": ["-q", "test"],
                },
                {
                    "kind": "tool",
                    "entrypoint": "py",
                    "args": ["-3", "-m", "unittest"],
                },
            ],
            "followUps": [
                "First follow-up item.",
                "Second follow-up item.",
                "Third follow-up item.",
                "Fourth follow-up item should be dropped.",
            ],
            "notes": [
                "Note one.",
                "Note two.",
                "Note three.",
                "Note four.",
                "Note five.",
                "Note six should be dropped.",
            ],
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual("completed", result["status"])
        self.assertLessEqual(len(result["summary"]), orchestrator.MAX_WORKER_SUMMARY_CHARS)
        self.assertEqual(["src/main/App.java"], result["filesTouched"])
        self.assertEqual(2, len(result["validationIntents"]))
        self.assertEqual([], result["validationCommands"])
        self.assertEqual(3, len(result["followUps"]))
        self.assertEqual(5, len(result["notes"]))

    def test_coerce_worker_result_preserves_unknown_null_fields(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "blocked",
            "summary": "A tool failure left some fields unknown.",
            "filesTouched": None,
            "validationIntents": [],
            "followUps": ["Retry after the tool comes back."],
            "notes": None,
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual([], result["filesTouched"])
        self.assertEqual([], result["validationCommands"])
        self.assertEqual(["Retry after the tool comes back."], result["followUps"])
        self.assertEqual([], result["notes"])
        self.assertEqual(
            ["filesTouched", "notes"],
            result["unknownFields"],
        )

    def test_coerce_worker_result_accepts_structured_validation_intents(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "completed",
            "summary": "Validation suggestions are structured.",
            "filesTouched": [],
            "validationIntents": [
                {
                    "kind": "repo-script",
                    "entrypoint": "scripts/check-doc-consistency.ps1",
                },
                {
                    "kind": "tool",
                    "entrypoint": "mvn",
                    "args": ["-q", "test"],
                },
            ],
            "followUps": [],
            "notes": [],
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual(2, len(result["validationIntents"]))
        self.assertEqual("repo-script", result["validationIntents"][0]["kind"])
        self.assertEqual("mvn", result["validationIntents"][1]["entrypoint"])

    def test_task_output_schema_json_requires_validation_intents_only(self):
        orchestrator = self.orchestrator

        schema = json.loads(orchestrator.task_output_schema_json("intents-only"))

        self.assertIn("validationIntents", schema["required"])
        self.assertNotIn("validationCommands", schema["properties"])

    def test_coerce_worker_result_rejects_invalid_status(self):
        orchestrator = self.orchestrator

        with self.assertRaisesRegex(orchestrator.OrchestratorError, "field 'status' must be one of"):
            orchestrator.coerce_worker_result(
                {
                    "status": "planned",
                    "summary": "bad status",
                    "filesTouched": [],
                    "validationIntents": [],
                    "followUps": [],
                    "notes": [],
                }
            )

    def test_validation_command_policy_rejects_shell_composition(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_command_policy(
            "grep -n 'foo' ai/orchestrator/README.md | grep bar"
        )

        self.assertFalse(policy["accepted"])
        self.assertIn("shell composition", policy["reason"])

    def test_validation_command_policy_accepts_repo_script(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_command_policy("scripts/refresh-ai-memory.ps1 -Check")

        self.assertTrue(policy["accepted"])
        self.assertEqual("scripts/refresh-ai-memory.ps1", policy["entrypoint"])
        self.assertEqual("repo-script", policy["intent"]["kind"])

    def test_validation_intent_policy_accepts_repo_script(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_intent_policy(
            orchestrator.ValidationIntent(
                kind="repo-script",
                entrypoint="scripts/check-doc-consistency.ps1",
                args=[],
            )
        )

        self.assertTrue(policy["accepted"])
        self.assertEqual("scripts/check-doc-consistency.ps1", policy["entrypoint"])

    def test_validation_intent_policy_rejects_unknown_tool(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_intent_policy(
            orchestrator.ValidationIntent(
                kind="tool",
                entrypoint="unknown-tool",
                args=["--flag"],
            )
        )

        self.assertFalse(policy["accepted"])
        self.assertIn("not an approved executable", policy["reason"])

    def test_collect_validation_commands_marks_unknown_validation_commands(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Inspection finished with partial data.",
        )
        record.unknown_fields = ["validationCommands"]

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertEqual([], command_payloads)
        self.assertEqual([], task_payloads[0]["validationCommands"])
        self.assertFalse(task_payloads[0]["validationCommandsKnown"])
        self.assertEqual(["validationCommands"], task_payloads[0]["unknownFields"])

    def test_collect_validation_commands_normalizes_safe_raw_command(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Inspection finished with one direct validation command.",
        )
        record.validation_commands = ["scripts/check-doc-consistency.ps1"]

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertEqual(1, task_payloads[0]["legacyValidationCommandCount"])
        self.assertTrue(task_payloads[0]["legacyValidationCommandsPresent"])
        self.assertEqual(1, len(command_payloads))
        self.assertEqual("command", command_payloads[0]["sourceKind"])
        self.assertTrue(command_payloads[0]["compatibilityOnly"])
        self.assertIsNone(command_payloads[0]["intent"])
        self.assertEqual("repo-script", command_payloads[0]["normalizedIntent"]["kind"])

    def test_collect_validation_commands_prefers_structured_intents_for_duplicates(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Inspection produced one structured validation suggestion.",
        )
        record.validation_intents = [
            orchestrator.ValidationIntent(
                kind="repo-script",
                entrypoint="scripts/check-doc-consistency.ps1",
                args=[],
            )
        ]
        record.validation_commands = ["scripts/check-doc-consistency.ps1"]

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertEqual(
            ["scripts/check-doc-consistency.ps1"],
            task_payloads[0]["renderedValidationIntents"],
        )
        self.assertEqual(1, len(command_payloads))
        self.assertEqual("intent", command_payloads[0]["sourceKind"])
        self.assertEqual(["inspect"], command_payloads[0]["taskIds"])

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

    def test_execute_task_fails_when_worker_stdout_is_not_json(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_subprocess = orchestrator.run_subprocess
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            runtime_root = temp_path / "runtime"
            workspaces_dir = temp_path / "workspaces"
            repo_root.mkdir()
            run_dir.mkdir()
            runtime_root.mkdir()
            workspaces_dir.mkdir()
            orchestrator.ROOT = repo_root
            orchestrator.run_subprocess = (
                lambda command, *, cwd, timeout_sec, progress_action=None: subprocess.CompletedProcess(
                    command,
                    0,
                    "worker narration only",
                    "",
                )
            )
            try:
                agent = orchestrator.AgentDefinition(
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
                task = orchestrator.TaskDefinition(
                    id="inspect-json",
                    title="Inspect JSON",
                    agent="analyst",
                    prompt="Inspect worker JSON output.",
                    workspace_mode="repo",
                )
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="worker-json-failure",
                    goal="Fail malformed worker output cleanly.",
                    shared_context=orchestrator.SharedContext(
                        summary="Worker JSON failure test.",
                        constraints=[],
                        read_paths=[],
                        validation=[],
                    ),
                    tasks=[task],
                )

                record = orchestrator.execute_task(
                    run_dir,
                    runtime_root,
                    workspaces_dir,
                    plan,
                    {"analyst": agent},
                    task,
                    {},
                    claude_bin="claude",
                    agents_json="{}",
                    dry_run=False,
                )
                stdout_text = pathlib.Path(record.stdout_path).read_text(encoding="utf-8")
                stderr_text = pathlib.Path(record.stderr_path).read_text(encoding="utf-8")
            finally:
                orchestrator.run_subprocess = old_run_subprocess
                orchestrator.ROOT = old_root

        self.assertEqual("failed", record.status)
        self.assertIn("standalone JSON payload", record.summary)
        self.assertEqual("worker narration only", stdout_text)
        self.assertIn("standalone JSON payload", stderr_text)
        self.assertIsNone(record.result_path)

    def test_execute_task_fails_when_intents_only_worker_returns_raw_validation_commands(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_subprocess = orchestrator.run_subprocess
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            runtime_root = temp_path / "runtime"
            workspaces_dir = temp_path / "workspaces"
            repo_root.mkdir()
            run_dir.mkdir()
            runtime_root.mkdir()
            workspaces_dir.mkdir()
            orchestrator.ROOT = repo_root
            orchestrator.run_subprocess = (
                lambda command, *, cwd, timeout_sec, progress_action=None: subprocess.CompletedProcess(
                    command,
                    0,
                    json.dumps(
                        {
                            "status": "completed",
                            "summary": "Worker returned a legacy command.",
                            "filesTouched": [],
                            "validationIntents": [],
                            "validationCommands": ["scripts/check-doc-consistency.ps1"],
                            "followUps": [],
                            "notes": [],
                        }
                    ),
                    "",
                )
            )
            try:
                agent = orchestrator.AgentDefinition(
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
                task = orchestrator.TaskDefinition(
                    id="inspect-json",
                    title="Inspect JSON",
                    agent="analyst",
                    prompt="Inspect worker JSON output.",
                    workspace_mode="repo",
                )
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="worker-intents-only-failure",
                    goal="Fail raw validationCommands under intents-only mode.",
                    shared_context=orchestrator.SharedContext(
                        summary="Worker intent-only failure test.",
                        constraints=[],
                        read_paths=[],
                        validation=[],
                    ),
                    tasks=[task],
                )

                record = orchestrator.execute_task(
                    run_dir,
                    runtime_root,
                    workspaces_dir,
                    plan,
                    {"analyst": agent},
                    task,
                    {},
                    claude_bin="claude",
                    agents_json="{}",
                    dry_run=False,
                    worker_validation_mode="intents-only",
                )
                stderr_text = pathlib.Path(record.stderr_path).read_text(encoding="utf-8")
            finally:
                orchestrator.run_subprocess = old_run_subprocess
                orchestrator.ROOT = old_root

        self.assertEqual("failed", record.status)
        self.assertIn("must not include raw validationCommands", record.summary)
        self.assertIn("must not include raw validationCommands", stderr_text)

    def test_run_loaded_plan_fail_fast_blocks_remaining_ready_tasks(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_fail = orchestrator.TaskDefinition(
                id="a-fail",
                title="A fails",
                agent="analyst",
                prompt="Fail.",
            )
            task_ready = orchestrator.TaskDefinition(
                id="c-ready",
                title="C ready",
                agent="analyst",
                prompt="Would have run next.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="fail-fast",
                goal="Stop scheduling after the first failure.",
                shared_context=orchestrator.SharedContext(
                    summary="Fail-fast test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_fail, task_ready],
            )
            executed_task_ids: list[str] = []

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="failed",
                    summary="Worker failed.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual(["a-fail"], executed_task_ids)
        self.assertEqual({"failed": 1, "blocked": 1}, payload["statusCounts"])
        self.assertEqual("blocked", tasks_by_id["c-ready"]["status"])
        self.assertIn(
            "Coordinator stopped scheduling new tasks after a worker failure.",
            tasks_by_id["c-ready"]["summary"],
        )

    def test_run_loaded_plan_stops_after_budget_limit_before_later_batch(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="inspect-b",
                title="Inspect B",
                agent="analyst",
                prompt="Inspect B.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="budget-stop",
                goal="Stop before the next batch after the run budget is reached.",
                shared_context=orchestrator.SharedContext(
                    summary="Budget stop test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
                run_policy=orchestrator.RunPolicy(
                    run_budget_usd=0.5,
                    budget_behavior="stop",
                ),
            )
            executed_task_ids: list[str] = []

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Completed with spend.",
                    workspace_path=str(workspaces_dir / task.id),
                    usage={
                        "inputTokens": 10,
                        "outputTokens": 5,
                        "cacheReadInputTokens": 0,
                        "cacheCreationInputTokens": 0,
                        "totalCostUsd": 0.75,
                        "modelUsage": {},
                    },
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual(["inspect-a"], executed_task_ids)
        self.assertEqual({"completed": 1, "blocked": 1}, payload["statusCounts"])
        self.assertEqual({"runBudgetUsd": 0.5, "budgetBehavior": "stop"}, payload["runPolicy"])
        self.assertEqual("stop", payload["runGovernance"]["status"])
        self.assertEqual(1, payload["runGovernance"]["blockingAlertCount"])
        self.assertEqual("blocked", tasks_by_id["inspect-b"]["status"])
        self.assertIn("Run policy stop triggered", tasks_by_id["inspect-b"]["summary"])
        self.assertEqual("inspect-a", payload["runGovernance"]["highestCostTasks"][0]["taskId"])

    def test_run_loaded_plan_warns_on_budget_limit_and_continues(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(id="inspect-a", title="Inspect A", agent="analyst", prompt="A.")
            task_b = orchestrator.TaskDefinition(id="inspect-b", title="Inspect B", agent="analyst", prompt="B.")
            plan = orchestrator.TaskPlan(
                version=1,
                name="budget-warn",
                goal="Warn on budget and continue.",
                shared_context=orchestrator.SharedContext(
                    summary="Budget warn test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
                run_policy=orchestrator.RunPolicy(
                    run_budget_usd=0.5,
                    budget_behavior="warn",
                ),
            )
            executed_task_ids: list[str] = []

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Completed.",
                    workspace_path=str(workspaces_dir / task.id),
                    usage={
                        "inputTokens": 10,
                        "outputTokens": 5,
                        "cacheReadInputTokens": 0,
                        "cacheCreationInputTokens": 0,
                        "totalCostUsd": 0.75 if task.id == "inspect-a" else 0.0,
                        "modelUsage": {},
                    },
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        self.assertEqual(["inspect-a", "inspect-b"], executed_task_ids)
        self.assertEqual({"completed": 2}, payload["statusCounts"])
        self.assertEqual("warn", payload["runGovernance"]["status"])
        self.assertEqual(1, payload["runGovernance"]["alertCount"])
        self.assertEqual(0, payload["runGovernance"]["blockingAlertCount"])

    def test_run_loaded_plan_stops_after_artifact_limit_before_later_batch(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(id="inspect-a", title="Inspect A", agent="analyst", prompt="A.")
            task_b = orchestrator.TaskDefinition(id="inspect-b", title="Inspect B", agent="analyst", prompt="B.")
            plan = orchestrator.TaskPlan(
                version=1,
                name="artifact-stop",
                goal="Stop before the next batch when task artifacts are too large.",
                shared_context=orchestrator.SharedContext(
                    summary="Artifact stop test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
                run_policy=orchestrator.RunPolicy(
                    max_task_stdout_bytes=10,
                    artifact_behavior="stop",
                ),
            )
            executed_task_ids: list[str] = []

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Completed with large stdout.",
                    workspace_path=str(workspaces_dir / task.id),
                    stdout_bytes=32 if task.id == "inspect-a" else 0,
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual(["inspect-a"], executed_task_ids)
        self.assertEqual("stop", payload["runGovernance"]["status"])
        self.assertEqual(32, payload["runGovernance"]["artifactTotals"]["totalBytes"])
        self.assertEqual("inspect-a", payload["runGovernance"]["artifactTotals"]["largestTasks"][0]["taskId"])
        self.assertEqual("blocked", tasks_by_id["inspect-b"]["status"])
        self.assertIn("Run policy stop triggered", tasks_by_id["inspect-b"]["summary"])

    def test_run_loaded_plan_blocks_dependents_after_failed_dependency(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_fail = orchestrator.TaskDefinition(
                id="a-fail",
                title="A fails",
                agent="analyst",
                prompt="Fail.",
            )
            task_blocked = orchestrator.TaskDefinition(
                id="b-needs-a",
                title="B needs A",
                agent="analyst",
                prompt="Wait for A.",
                depends_on=["a-fail"],
            )
            task_independent = orchestrator.TaskDefinition(
                id="c-independent",
                title="C independent",
                agent="analyst",
                prompt="Can still run.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="dependency-blocking",
                goal="Block dependents while letting independent tasks continue.",
                shared_context=orchestrator.SharedContext(
                    summary="Dependency blocking test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_fail, task_blocked, task_independent],
            )
            executed_task_ids: list[str] = []

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                executed_task_ids.append(task.id)
                status = "failed" if task.id == "a-fail" else "completed"
                summary = "Upstream failed." if task.id == "a-fail" else "Independent task completed."
                return make_task_run_record(
                    orchestrator,
                    task,
                    status=status,
                    summary=summary,
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=2,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual({"a-fail", "c-independent"}, set(executed_task_ids))
        self.assertEqual({"failed": 1, "blocked": 1, "completed": 1}, payload["statusCounts"])
        self.assertEqual("blocked", tasks_by_id["b-needs-a"]["status"])
        self.assertIn("Dependency failed or was blocked.", tasks_by_id["b-needs-a"]["summary"])
        self.assertEqual("completed", tasks_by_id["c-independent"]["status"])

    def test_run_loaded_plan_uses_tracked_worker_validation_modes(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                worker_validation_mode="intents-only",
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
            task_a = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="review-b",
                title="Review B",
                agent="reviewer",
                prompt="Review B.",
                worker_validation_mode="intents-only",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="tracked-worker-validation-mode",
                goal="Use tracked validation modes.",
                shared_context=orchestrator.SharedContext(
                    summary="Tracked mode test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
            )
            seen_modes: dict[str, str] = {}

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                seen_modes[task.id] = str(worker_validation_mode)
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.worker_validation_mode = orchestrator.resolved_worker_validation_mode(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                record.worker_validation_mode_source = orchestrator.resolved_worker_validation_mode_source(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst, "reviewer": reviewer},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=2,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task

        self.assertEqual({"inspect-a": "None", "review-b": "None"}, seen_modes)
        self.assertEqual("intents-only", payload["workerValidationMode"])
        self.assertIsNone(payload["workerValidationModeOverride"])
        self.assertEqual(
            {"inspect-a": "intents-only", "review-b": "intents-only"},
            payload["taskWorkerValidationModes"],
        )
        self.assertEqual(
            {"inspect-a": "agent", "review-b": "task"},
            payload["taskWorkerValidationModeSources"],
        )
        self.assertEqual(
            ["agent", "task"],
            [task["worker_validation_mode_source"] for task in payload["tasks"]],
        )

    def test_run_loaded_plan_worker_validation_override_beats_tracked_modes(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                worker_validation_mode="intents-only",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
                worker_validation_mode="intents-only",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="override-worker-validation-mode",
                goal="Override tracked validation modes.",
                shared_context=orchestrator.SharedContext(
                    summary="Override mode test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )
            seen_modes: list[str] = []

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                seen_modes.append(str(worker_validation_mode))
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.worker_validation_mode = orchestrator.resolved_worker_validation_mode(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                record.worker_validation_mode_source = orchestrator.resolved_worker_validation_mode_source(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    worker_validation_mode="intents-only",
                )
            finally:
                orchestrator.execute_task = old_execute_task

        self.assertEqual(["intents-only"], seen_modes)
        self.assertEqual("intents-only", payload["workerValidationMode"])
        self.assertEqual("intents-only", payload["workerValidationModeOverride"])
        self.assertEqual({"inspect-a": "intents-only"}, payload["taskWorkerValidationModes"])
        self.assertEqual({"inspect-a": "override"}, payload["taskWorkerValidationModeSources"])
        self.assertEqual({"inspect-a": "claude-haiku-4-5"}, payload["taskModels"])
        self.assertEqual({"inspect-a": "simple"}, payload["taskModelProfiles"])
        self.assertEqual([], payload["complexModelTaskIds"])
        self.assertEqual(0, payload["complexModelTaskCount"])
        self.assertEqual("override", payload["tasks"][0]["worker_validation_mode_source"])

    def test_run_loaded_plan_reports_complex_model_tasks_in_payload_and_manifest(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="balanced",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="deep-design",
                title="Deep design",
                agent="analyst",
                prompt="Handle the hardest architecture slice.",
                model_profile="complex",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="complex-model-run-visibility",
                goal="Expose complex model tasks in run output.",
                shared_context=orchestrator.SharedContext(
                    summary="Complex model run test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.model = orchestrator.resolved_model(task, agents[task.agent])
                record.model_profile = orchestrator.resolved_model_profile(task, agents[task.agent])
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task

            manifest = json.loads(
                (pathlib.Path(payload["runDir"]) / "manifest.json").read_text(encoding="utf-8")
            )

        self.assertEqual({"deep-design": "claude-opus-4-6"}, payload["taskModels"])
        self.assertEqual({"deep-design": "complex"}, payload["taskModelProfiles"])
        self.assertEqual(["deep-design"], payload["complexModelTaskIds"])
        self.assertEqual(1, payload["complexModelTaskCount"])
        self.assertEqual({"deep-design": "claude-opus-4-6"}, manifest["taskModels"])
        self.assertEqual({"deep-design": "complex"}, manifest["taskModelProfiles"])
        self.assertEqual(["deep-design"], manifest["complexModelTaskIds"])
        self.assertEqual(1, manifest["complexModelTaskCount"])

    def test_run_loaded_plan_and_summary_report_topology(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="topology-run-visibility",
                goal="Expose topology in run output.",
                shared_context=orchestrator.SharedContext(
                    summary="Topology run test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task

            manifest_path = pathlib.Path(payload["runDir"]) / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)

        self.assertEqual(1, payload["topology"]["taskCount"])
        self.assertEqual({"analyst": 1}, payload["topology"]["agentCounts"])
        self.assertEqual(1, payload["topology"]["batchCount"])
        self.assertEqual(1, payload["topology"]["maxParallelWidth"])
        self.assertEqual(0, payload["topology"]["warningCount"])
        self.assertEqual(1, manifest["topology"]["batchCount"])
        self.assertEqual({"inspect-a": 0}, manifest["topology"]["taskDependencyHops"])
        self.assertEqual(1, summary["topologyBatchCount"])
        self.assertEqual(1, summary["topologyMaxParallelWidth"])
        self.assertEqual(0, summary["topologyWarningCount"])

    def test_run_loaded_plan_can_reuse_existing_run_directories_without_overwriting_snapshot(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "same-run"
            workspaces_dir = runtime_root / "workspaces" / "same-run"
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            selected_plan_path = run_dir / "selected-plan.json"
            selected_plan_path.write_text("{\"sentinel\":true}\n", encoding="utf-8")
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="same-run-reuse",
                goal="Reuse an existing run directory.",
                shared_context=orchestrator.SharedContext(
                    summary="Existing run test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    existing_run_id="same-run",
                    existing_run_dir=run_dir,
                    existing_workspaces_dir=workspaces_dir,
                    write_plan_snapshot=False,
                )
            finally:
                orchestrator.execute_task = old_execute_task

            manifest = orchestrator.read_json(run_dir / "manifest.json")
            selected_plan_text = selected_plan_path.read_text(encoding="utf-8")

        self.assertEqual("same-run", payload["runId"])
        self.assertEqual(str(run_dir.resolve()), payload["runDir"])
        self.assertEqual(str(workspaces_dir.resolve()), payload["workspacesDir"])
        self.assertEqual("{\"sentinel\":true}\n", selected_plan_text)
        self.assertEqual("same-run", manifest["runId"])
        self.assertEqual(str(run_dir.resolve()), manifest["runDir"])
        self.assertEqual(str(workspaces_dir.resolve()), manifest["workspacesDir"])

    def test_run_loaded_plan_rejects_compat_worker_validation_override(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
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
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="reject-compat-run",
                goal="Reject compat workers before execution.",
                shared_context=orchestrator.SharedContext(
                    summary="Compat rejection test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            with self.assertRaisesRegex(
                orchestrator.OrchestratorError,
                "workerValidationMode='compat' was removed from the live worker contract",
            ):
                orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    worker_validation_mode="compat",
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
                initial_records=None,
                retry_of_run_id=None,
                requested_task_ids=None,
                retried_task_ids=None,
                existing_run_id=None,
                existing_run_dir=None,
                existing_workspaces_dir=None,
                write_plan_snapshot=True,
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
                        dry_run=True,
                    )
                )
            finally:
                orchestrator.run_loaded_plan = old_run_loaded_plan
                orchestrator.ROOT = old_root

        self.assertEqual("intents-only", captured["worker_validation_mode"])
        self.assertEqual("intents-only", payload["workerValidationMode"])

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
                initial_records=None,
                retry_of_run_id=None,
                requested_task_ids=None,
                retried_task_ids=None,
                existing_run_id=None,
                existing_run_dir=None,
                existing_workspaces_dir=None,
                write_plan_snapshot=True,
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
                initial_records=None,
                retry_of_run_id=None,
                requested_task_ids=None,
                retried_task_ids=None,
                existing_run_id=None,
                existing_run_dir=None,
                existing_workspaces_dir=None,
                write_plan_snapshot=True,
            ):
                captured["plan_path"] = pathlib.Path(plan_path_arg)
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
        self.assertEqual("same-run", payload["runId"])
        self.assertEqual(["retry-b"], payload["requestedTaskIds"])
        self.assertEqual(["retry-b"], payload["resumedTaskIds"])
        self.assertEqual(["inspect-a", "later-c"], payload["preservedTaskIds"])
        self.assertTrue(payload["resumedInPlace"])

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
                    "usageTotals": {"promptEstimatedTokens": 123, "totalCostUsd": 0.12},
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
                    "usageTotals": {"promptEstimatedTokens": 456, "totalCostUsd": 0.34},
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

    def test_prune_runs_removes_only_old_completed_runs_by_default(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            repo_root.mkdir()
            orchestrator.ROOT = repo_root

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
                "2026-03-01T10:00:00+00:00",
                "completed",
            )
            old_failed_run_dir, old_failed_workspaces = write_run(
                "old-failed",
                "2026-03-02T10:00:00+00:00",
                "failed",
            )
            recent_completed_run_dir, recent_completed_workspaces = write_run(
                "recent-completed",
                "2026-04-07T10:00:00+00:00",
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
                                "validation_commands": ["scripts/refresh-ai-memory.ps1 -Check"],
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
                                "validation_commands": ["py -3 -m py_compile scripts/claude-orchestrator.py"],
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
        self.assertEqual(["scripts/refresh-ai-memory.ps1 -Check"], payload["suggestedCommands"])
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
            safe_cmd = "scripts/refresh-ai-memory.ps1 -Check"
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
            raw_cmd = "scripts/check-doc-consistency.ps1"
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
                                        "entrypoint": "scripts/refresh-ai-memory.ps1",
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


    def test_coerce_task_run_record_rejects_compat_worker_validation_mode(self):
        orchestrator = self.orchestrator
        payload = {
            "id": "inspect",
            "title": "Inspect",
            "agent": "analyst",
            "status": "completed",
            "summary": "Inspection done.",
            "workspace_mode": "copy",
            "workspace_path": "",
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
            "worker_validation_mode": "compat",
        }

        with self.assertRaisesRegex(
            orchestrator.OrchestratorError,
            "workerValidationMode='compat' was removed from the live worker contract",
        ):
            orchestrator.coerce_task_run_record(payload, location="test")

    def test_load_agents_rejects_compat_worker_validation_mode(self):
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
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "analyst": {
                                "description": "Analyze",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "permissionMode": "dontAsk",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "workerValidationMode": "compat",
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
                "workerValidationMode='compat' was removed from the live worker contract",
            ):
                orchestrator.load_agents(agents_path)

    def test_worker_prompt_renders_summary_only_materialization_section(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
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
        task = orchestrator.TaskDefinition(
            id="add-feature",
            title="Add feature",
            agent="implementer",
            prompt="Implement the feature.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="materialization-section-test",
            goal="Prove materialization section rendering.",
            shared_context=orchestrator.SharedContext(
                summary="Regression test for materialization prompt section.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            dependency_materialization_mode="summary-only",
        )

        self.assertIn("Dependency materialization:", rendered.text)
        self.assertIn("Mode: `summary-only`", rendered.text)
        self.assertIn("Applied layers: none", rendered.text)
        section_names = [s.name for s in rendered.sections]
        self.assertIn("dependency_materialization", section_names)

    def test_run_loaded_plan_manifest_records_dependency_materialization_mode(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="implementer",
                description="implementation",
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
            task = orchestrator.TaskDefinition(
                id="do-work",
                title="Do work",
                agent="implementer",
                prompt="Make the change.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="materialization-manifest-test",
                goal="Prove dependencyMaterializationMode in manifest record.",
                shared_context=orchestrator.SharedContext(
                    summary="Manifest materialization field regression.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Completed.",
                    workspace_path=str(workspaces_dir / task.id),
                    dependency_materialization_mode="summary-only",
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"implementer": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        record = tasks_by_id["do-work"]
        self.assertIn("dependency_materialization_mode", record)
        self.assertEqual(
            orchestrator.DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
            record["dependency_materialization_mode"],
        )

    def test_worker_prompt_renders_apply_reviewed_materialization_section_with_applied_layers(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read", "Edit", "Write"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="build-downstream",
            title="Build downstream",
            agent="implementer",
            prompt="Continue from upstream state.",
            depends_on=["seed-upstream"],
            write_paths=["src/feature.txt"],
            dependency_materialization="apply-reviewed",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="apply-reviewed-prompt-section-test",
            goal="Prove apply-reviewed materialization prompt path.",
            shared_context=orchestrator.SharedContext(
                summary="Regression test for apply-reviewed prompt section rendering.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )
        layer = orchestrator.DependencyLayerRecord(
            task_id="seed-upstream",
            workspace_mode="copy",
            workspace_path="/tmp/seed-upstream-workspace",
            operations=[
                orchestrator.DependencyLayerOperation(
                    path="src/feature.txt",
                    action="modified",
                ),
            ],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            dependency_materialization_mode="apply-reviewed",
            dependency_layers_applied=[layer],
        )

        self.assertIn("Dependency materialization:", rendered.text)
        self.assertIn("Mode: `apply-reviewed`", rendered.text)
        self.assertIn("Applied layers:", rendered.text)
        self.assertIn("seed-upstream (1 ops from copy)", rendered.text)
        self.assertNotIn("Mode: `summary-only`", rendered.text)
        section_names = [s.name for s in rendered.sections]
        self.assertIn("dependency_materialization", section_names)

    def test_validate_command_reports_apply_reviewed_dependency_materialization(self):
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
                            "implementer": {
                                "description": "implementation",
                                "prompt": "Return JSON only.",
                                "modelProfile": "balanced",
                                "effort": "high",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "workerValidationMode": "intents-only",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read", "Edit", "Write"],
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
                        "name": "validate-apply-reviewed-materialization",
                        "goal": "Prove apply-reviewed materialization is visible in validate output.",
                        "sharedContext": {
                            "summary": "Dependency materialization validate test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "seed-upstream",
                                "title": "Seed upstream",
                                "agent": "planner",
                                "prompt": "Seed the feature.",
                            },
                            {
                                "id": "build-downstream",
                                "title": "Build downstream",
                                "agent": "implementer",
                                "prompt": "Continue from upstream state.",
                                "dependsOn": ["seed-upstream"],
                                "writePaths": ["src/feature.txt"],
                                "dependencyMaterialization": "apply-reviewed",
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

        tasks_by_id = {t["id"]: t for t in payload["tasks"]}
        self.assertEqual(
            "summary-only",
            tasks_by_id["seed-upstream"]["dependencyMaterialization"],
        )
        self.assertEqual(
            "apply-reviewed",
            tasks_by_id["build-downstream"]["dependencyMaterialization"],
        )


if __name__ == "__main__":
    unittest.main()
