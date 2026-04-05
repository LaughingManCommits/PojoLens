import contextlib
import importlib.util
import io
import json
import pathlib
import subprocess
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


def make_task_run_record(
    orchestrator,
    task,
    *,
    status,
    summary,
    agent_name=None,
    workspace_mode="copy",
    workspace_path="",
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
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
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
        usage=None,
        return_code=0 if status == "completed" else 1,
        prompt_path="",
        command_path="",
        stdout_path=None,
        stderr_path=None,
        result_path=None,
    )


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


class ValidateCommandTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

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
                            "files": [],
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
                                "workerValidationMode": "compat",
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
            {"inspect": "intents-only", "implement": "compat"},
            payload["taskWorkerValidationModes"],
        )
        self.assertEqual(
            {"inspect": "agent", "implement": "task"},
            payload["taskWorkerValidationModeSources"],
        )
        self.assertEqual(
            [
                {
                    "id": "inspect",
                    "agent": "analyst",
                    "workerValidationMode": "intents-only",
                    "workerValidationModeSource": "agent",
                },
                {
                    "id": "implement",
                    "agent": "implementer",
                    "workerValidationMode": "compat",
                    "workerValidationModeSource": "task",
                },
            ],
            payload["tasks"],
        )

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

    def test_worker_prompt_intents_only_forbids_raw_validation_commands(self):
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
            name="intent-only-worker-prompt",
            goal="Keep validation suggestions intent-only.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                files=[],
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

        self.assertIn("Emit structured `validationIntents` only", rendered.text)
        self.assertIn("Keep `validationCommands` as `[]`", rendered.text)

    def test_coerce_worker_result_compacts_verbose_fields(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "completed",
            "summary": "Summary " * 80,
            "filesTouched": ["src/main/App.java", "src/main/App.java"],
            "validationCommands": [
                "python -m pytest -q",
                "mvn -q test",
                "scripts/unused-third-command.ps1",
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
        self.assertEqual(2, len(result["validationCommands"]))
        self.assertEqual(3, len(result["followUps"]))
        self.assertEqual(5, len(result["notes"]))

    def test_coerce_worker_result_preserves_unknown_null_fields(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "blocked",
            "summary": "A tool failure left some fields unknown.",
            "filesTouched": None,
            "validationCommands": None,
            "followUps": ["Retry after the tool comes back."],
            "notes": None,
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual([], result["filesTouched"])
        self.assertEqual([], result["validationCommands"])
        self.assertEqual(["Retry after the tool comes back."], result["followUps"])
        self.assertEqual([], result["notes"])
        self.assertEqual(
            ["filesTouched", "validationCommands", "notes"],
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
            "validationCommands": [],
            "followUps": [],
            "notes": [],
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual(2, len(result["validationIntents"]))
        self.assertEqual("repo-script", result["validationIntents"][0]["kind"])
        self.assertEqual("mvn", result["validationIntents"][1]["entrypoint"])

    def test_coerce_worker_result_rejects_invalid_status(self):
        orchestrator = self.orchestrator

        with self.assertRaisesRegex(orchestrator.OrchestratorError, "field 'status' must be one of"):
            orchestrator.coerce_worker_result(
                {
                    "status": "planned",
                    "summary": "bad status",
                    "filesTouched": [],
                    "validationCommands": [],
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
                        files=[],
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
                        files=[],
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
                    files=[],
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
                worker_validation_mode="compat",
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
                    files=[],
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
                worker_validation_mode="compat",
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
                worker_validation_mode="compat",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="tracked-worker-validation-mode",
                goal="Use tracked validation modes.",
                shared_context=orchestrator.SharedContext(
                    summary="Tracked mode test.",
                    constraints=[],
                    files=[],
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
        self.assertEqual("mixed", payload["workerValidationMode"])
        self.assertIsNone(payload["workerValidationModeOverride"])
        self.assertEqual(
            {"inspect-a": "intents-only", "review-b": "compat"},
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
                    files=[],
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
                    worker_validation_mode="compat",
                )
            finally:
                orchestrator.execute_task = old_execute_task

        self.assertEqual(["compat"], seen_modes)
        self.assertEqual("compat", payload["workerValidationMode"])
        self.assertEqual("compat", payload["workerValidationModeOverride"])
        self.assertEqual({"inspect-a": "compat"}, payload["taskWorkerValidationModes"])
        self.assertEqual({"inspect-a": "override"}, payload["taskWorkerValidationModeSources"])
        self.assertEqual("override", payload["tasks"][0]["worker_validation_mode_source"])

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
                            "files": [],
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
                worker_validation_mode="compat",
                initial_records=None,
                retry_of_run_id=None,
                requested_task_ids=None,
                retried_task_ids=None,
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


if __name__ == "__main__":
    unittest.main()
