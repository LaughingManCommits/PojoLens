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


if __name__ == "__main__":
    unittest.main()
