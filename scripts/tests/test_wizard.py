from __future__ import annotations

import argparse
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from ai.pojo_lens_agents import wizard as wizard_layer


class FakePrompter(wizard_layer.WizardPrompter):
    def __init__(self, *, confirms: list[bool] | None = None, texts: list[str] | None = None, choices: list[str] | None = None) -> None:
        self.confirms = list(confirms or [])
        self.texts = list(texts or [])
        self.choices = list(choices or [])
        self.messages: list[str] = []

    def show_message(self, message: str) -> None:
        self.messages.append(message)

    def choose(self, title: str, choices: list[wizard_layer.PromptChoice], *, default_index: int = 0) -> str:
        self.messages.append(title)
        if self.choices:
            return self.choices.pop(0)
        return choices[default_index].value

    def confirm(self, question: str, *, default: bool = True) -> bool:
        self.messages.append(question)
        if self.confirms:
            return self.confirms.pop(0)
        return default

    def ask_text(self, question: str, *, default: str = "") -> str:
        self.messages.append(question)
        if self.texts:
            return self.texts.pop(0)
        return default


class WizardArgPreprocessTest(unittest.TestCase):
    def test_no_args_defaults_to_wizard(self):
        self.assertEqual(["wizard"], wizard_layer.preprocess_argv([]))

    def test_unknown_first_token_becomes_wizard_goal(self):
        self.assertEqual(
            ["wizard", "--goal", "add pagination to endpoint"],
            wizard_layer.preprocess_argv(["add", "pagination", "to", "endpoint"]),
        )

    def test_known_command_is_preserved(self):
        self.assertEqual(["run", "plan.json"], wizard_layer.preprocess_argv(["run", "plan.json"]))


class WizardFlowTest(unittest.TestCase):
    def _make_plan_root(self) -> tuple[tempfile.TemporaryDirectory[str], Path, str]:
        td = tempfile.TemporaryDirectory()
        root = Path(td.name)
        tasks_dir = root / "ai" / "orchestrator" / "tasks"
        tasks_dir.mkdir(parents=True)
        plan_path = tasks_dir / "alpha.json"
        plan_path.write_text(
            '{"version":1,"name":"alpha-plan","goal":"Alpha goal","tasks":[{"id":"a"}]}',
            encoding="utf-8",
        )
        return td, root, str(plan_path.resolve())

    def test_noninteractive_dry_run_sequences_preflight_and_run(self):
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        captured: dict[str, object] = {}

        def run_handler(args):
            captured["run_args"] = args
            return {
                "runId": "run-1",
                "runDir": str(root / ".claude-orchestrator" / "runs" / "run-1"),
                "dryRun": True,
                "statusCounts": {"planned": 1},
                "usageTotals": {"totalCostUsd": 0.0},
            }

        payload = wizard_layer.wizard_command(
            argparse.Namespace(
                json=True,
                tui=False,
                watch=False,
                plan="",
                goal="",
                goal_words=[],
                resume_run_ref="",
                retry_run_ref="",
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                runtime_root=str(root / ".claude-orchestrator"),
                max_parallel=2,
                dry_run=True,
                planner_agent="planner",
                verbose=False,
            ),
            deps={
                "root": root,
                "textual_available": lambda: False,
                "slugify": lambda text: text.replace(" ", "-"),
                "write_json": lambda path, data: None,
                "error_factory": RuntimeError,
                "load_agents": lambda path: {"planner": object()},
                "ensure_claude_available": lambda bin: None,
                "claude_command": lambda *args, **kwargs: [],
                "agent_payload_for_claude": lambda *args, **kwargs: "{}",
                "run_subprocess": lambda *args, **kwargs: None,
                "extract_json_payload": lambda text: {},
                "inventory_handler": lambda args: {"runCount": 0, "runs": []},
                "validate_handler": lambda args: {
                    "planName": "alpha-plan",
                    "taskCount": 1,
                    "topology": {"maxParallelWidth": 1},
                    "tasks": [{"id": "a"}],
                },
                "run_handler": run_handler,
                "resume_handler": lambda args: {},
                "retry_handler": lambda args: {},
                "status_handler": lambda args: {},
                "review_handler": lambda args: {},
                "promote_handler": lambda args: {},
                "validate_run_handler": lambda args: {},
                "default_task_timeout_sec": 30,
            },
        )

        self.assertEqual("plan", payload["mode"])
        self.assertEqual(plan_path, payload["selectedPlanPath"])
        self.assertEqual(["preflight", "run", "done"], [step["name"] for step in payload["steps"]])
        self.assertTrue(payload["run"]["dryRun"])
        self.assertEqual(2, getattr(captured["run_args"], "max_parallel"))

    def test_noninteractive_json_does_not_force_tui_or_watch(self):
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        captured: dict[str, object] = {}

        def run_handler(args):
            captured["run_args"] = args
            return {
                "runId": "run-json",
                "runDir": str(root / ".claude-orchestrator" / "runs" / "run-json"),
                "dryRun": True,
                "statusCounts": {"planned": 1},
                "usageTotals": {"totalCostUsd": 0.0},
            }

        wizard_layer.wizard_command(
            argparse.Namespace(
                json=True,
                tui=False,
                watch=False,
                plan=plan_path,
                goal="",
                goal_words=[],
                resume_run_ref="",
                retry_run_ref="",
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                runtime_root=str(root / ".claude-orchestrator"),
                max_parallel=2,
                dry_run=True,
                planner_agent="planner",
                verbose=False,
            ),
            deps={
                "root": root,
                "textual_available": lambda: True,
                "slugify": lambda text: text.replace(" ", "-"),
                "write_json": lambda path, data: None,
                "error_factory": RuntimeError,
                "load_agents": lambda path: {"planner": object()},
                "ensure_claude_available": lambda bin: None,
                "claude_command": lambda *args, **kwargs: [],
                "agent_payload_for_claude": lambda *args, **kwargs: "{}",
                "run_subprocess": lambda *args, **kwargs: None,
                "extract_json_payload": lambda text: {},
                "inventory_handler": lambda args: {"runCount": 0, "runs": []},
                "validate_handler": lambda args: {
                    "planName": "alpha-plan",
                    "taskCount": 1,
                    "topology": {"maxParallelWidth": 1},
                    "tasks": [{"id": "a"}],
                },
                "run_handler": run_handler,
                "resume_handler": lambda args: {},
                "retry_handler": lambda args: {},
                "status_handler": lambda args: {},
                "review_handler": lambda args: {},
                "promote_handler": lambda args: {},
                "validate_run_handler": lambda args: {},
                "default_task_timeout_sec": 30,
            },
        )

        self.assertFalse(getattr(captured["run_args"], "tui"))
        self.assertFalse(getattr(captured["run_args"], "watch"))

    def test_generated_goal_plan_uses_runtime_generated_plans_directory(self):
        td, root, _ = self._make_plan_root()
        self.addCleanup(td.cleanup)
        runtime_root = root / ".claude-orchestrator"
        captured: dict[str, object] = {}
        old_resolver = wizard_layer.resolve_goal_with_claude
        wizard_layer.resolve_goal_with_claude = lambda *args, **kwargs: {
            "mode": "generate",
            "taskPlan": {
                "version": 1,
                "name": "generated-plan",
                "goal": "Add pagination",
                "tasks": [{"id": "gen-task", "agent": "planner", "prompt": "Do the thing."}],
            },
        }
        self.addCleanup(setattr, wizard_layer, "resolve_goal_with_claude", old_resolver)

        def write_json(path, data):
            captured["generated_path"] = str(path)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data), encoding="utf-8")

        payload = wizard_layer.wizard_command(
            argparse.Namespace(
                json=True,
                tui=False,
                watch=False,
                plan="",
                goal="Add pagination",
                goal_words=[],
                resume_run_ref="",
                retry_run_ref="",
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                runtime_root=str(runtime_root),
                max_parallel=2,
                dry_run=False,
                planner_agent="planner",
                verbose=False,
            ),
            deps={
                "root": root,
                "textual_available": lambda: False,
                "slugify": lambda text: text.replace(" ", "-").lower(),
                "write_json": write_json,
                "error_factory": RuntimeError,
                "load_agents": lambda path: {"planner": object()},
                "ensure_claude_available": lambda bin: None,
                "claude_command": lambda *args, **kwargs: [],
                "agent_payload_for_claude": lambda *args, **kwargs: "{}",
                "run_subprocess": lambda *args, **kwargs: None,
                "extract_json_payload": lambda text: {},
                "inventory_handler": lambda args: {"runCount": 0, "runs": []},
                "validate_handler": lambda args: {
                    "planName": "generated-plan",
                    "taskCount": 1,
                    "topology": {"maxParallelWidth": 1},
                    "tasks": [{"id": "gen-task"}],
                },
                "run_handler": lambda args: {
                    "runId": "run-generated",
                    "runDir": str(runtime_root / "runs" / "run-generated"),
                    "dryRun": True,
                    "statusCounts": {"planned": 1},
                    "usageTotals": {"totalCostUsd": 0.0},
                },
                "resume_handler": lambda args: {},
                "retry_handler": lambda args: {},
                "status_handler": lambda args: {},
                "review_handler": lambda args: {},
                "promote_handler": lambda args: {},
                "validate_run_handler": lambda args: {},
                "default_task_timeout_sec": 30,
            },
        )

        expected_prefix = str((runtime_root / "generated-plans").resolve())
        self.assertTrue(str(payload["generatedPlanPath"]).startswith(expected_prefix))
        self.assertTrue(str(captured["generated_path"]).startswith(expected_prefix))

    def test_interactive_flow_can_review_promote_and_validate(self):
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        prompter = FakePrompter(confirms=[True, True, True, True, True], texts=["3"])
        old_choose_prompter = wizard_layer.choose_prompter
        wizard_layer.choose_prompter = lambda **kwargs: prompter
        self.addCleanup(setattr, wizard_layer, "choose_prompter", old_choose_prompter)
        calls: list[str] = []

        with mock.patch.object(wizard_layer.sys.stdin, "isatty", return_value=True), mock.patch.object(wizard_layer.sys.stderr, "isatty", return_value=True):
            payload = wizard_layer.wizard_command(
                argparse.Namespace(
                    json=False,
                    tui=False,
                    watch=False,
                    plan=plan_path,
                    goal="",
                    goal_words=[],
                    resume_run_ref="",
                    retry_run_ref="",
                    agents=str(root / "ai" / "orchestrator" / "agents.json"),
                    claude_bin="claude",
                    runtime_root=str(root / ".claude-orchestrator"),
                    max_parallel=2,
                    dry_run=False,
                    planner_agent="planner",
                    verbose=False,
                ),
                deps={
                    "root": root,
                    "textual_available": lambda: False,
                    "slugify": lambda text: text.replace(" ", "-"),
                    "write_json": lambda path, data: None,
                    "error_factory": RuntimeError,
                    "load_agents": lambda path: {"planner": object()},
                    "ensure_claude_available": lambda bin: None,
                    "claude_command": lambda *args, **kwargs: [],
                    "agent_payload_for_claude": lambda *args, **kwargs: "{}",
                    "run_subprocess": lambda *args, **kwargs: None,
                    "extract_json_payload": lambda text: {},
                    "inventory_handler": lambda args: {"runCount": 0, "runs": []},
                    "validate_handler": lambda args: {"planName": "alpha-plan", "taskCount": 1, "tasks": []},
                    "run_handler": lambda args: calls.append("run") or {
                        "runId": "run-2",
                        "runDir": str(root / ".claude-orchestrator" / "runs" / "run-2"),
                        "dryRun": False,
                        "statusCounts": {"completed": 1},
                        "usageTotals": {"totalCostUsd": 0.25},
                    },
                    "resume_handler": lambda args: {},
                    "retry_handler": lambda args: {},
                    "status_handler": lambda args: {
                        "reviewSummary": {"changedTaskCount": 1},
                    },
                    "review_handler": lambda args: calls.append("review") or {
                        "summary": {"changedFileCount": 2},
                    },
                    "diff_run_handler": lambda args: calls.append("diff-stat" if args.stat else "diff-full") or {
                        "_consoleText": "diff output",
                        "summary": {"changedFileCount": 2},
                        "tasks": [],
                    },
                    "promote_handler": lambda args: calls.append("promote-dry" if args.dry_run else "promote") or (
                        {
                            "promotionAllowed": True,
                            "blockedReasons": [],
                            "filesPromoted": 0,
                        }
                        if args.dry_run
                        else {
                            "promotionAllowed": True,
                            "blockedReasons": [],
                            "filesPromoted": 2,
                        }
                    ),
                    "validate_run_handler": lambda args: calls.append("validate-run") or {
                        "statusCounts": {"completed": 1},
                    },
                    "default_task_timeout_sec": 30,
                },
            )

        self.assertEqual(["run", "review", "diff-stat", "diff-full", "promote-dry", "promote", "validate-run"], calls)
        self.assertEqual(2, payload["receipt"]["promotedFiles"])
        self.assertIn("validation", payload)
        self.assertIn("Show full diff?", prompter.messages)

    def test_resume_mode_forwards_to_resume_handler(self):
        td, root, _ = self._make_plan_root()
        self.addCleanup(td.cleanup)
        captured: dict[str, object] = {}

        def resume_handler(args):
            captured["args"] = args
            return {
                "runId": "run-3",
                "runDir": str(root / ".claude-orchestrator" / "runs" / "run-3"),
                "dryRun": True,
                "statusCounts": {"planned": 1},
                "usageTotals": {"totalCostUsd": 0.0},
            }

        payload = wizard_layer.wizard_command(
            argparse.Namespace(
                json=True,
                tui=False,
                watch=False,
                plan="",
                goal="",
                goal_words=[],
                resume_run_ref="run-dir",
                retry_run_ref="",
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                runtime_root=str(root / ".claude-orchestrator"),
                max_parallel=4,
                dry_run=True,
                planner_agent="planner",
                verbose=False,
            ),
            deps={
                "root": root,
                "textual_available": lambda: False,
                "slugify": lambda text: text.replace(" ", "-"),
                "write_json": lambda path, data: None,
                "error_factory": RuntimeError,
                "load_agents": lambda path: {"planner": object()},
                "ensure_claude_available": lambda bin: None,
                "claude_command": lambda *args, **kwargs: [],
                "agent_payload_for_claude": lambda *args, **kwargs: "{}",
                "run_subprocess": lambda *args, **kwargs: None,
                "extract_json_payload": lambda text: {},
                "inventory_handler": lambda args: {"runCount": 0, "runs": []},
                "validate_handler": lambda args: {},
                "run_handler": lambda args: {},
                "resume_handler": resume_handler,
                "retry_handler": lambda args: {},
                "status_handler": lambda args: {},
                "review_handler": lambda args: {},
                "promote_handler": lambda args: {},
                "validate_run_handler": lambda args: {},
                "default_task_timeout_sec": 30,
            },
        )

        self.assertEqual("resume", payload["mode"])
        self.assertEqual("run-dir", getattr(captured["args"], "run_ref"))
        self.assertEqual(4, getattr(captured["args"], "max_parallel"))

    def test_failed_run_returns_next_actions(self):
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)

        payload = wizard_layer.wizard_command(
            argparse.Namespace(
                json=True,
                tui=False,
                watch=False,
                plan=plan_path,
                goal="",
                goal_words=[],
                resume_run_ref="",
                retry_run_ref="",
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                runtime_root=str(root / ".claude-orchestrator"),
                max_parallel=2,
                dry_run=False,
                planner_agent="planner",
                verbose=False,
            ),
            deps={
                "root": root,
                "textual_available": lambda: False,
                "slugify": lambda text: text.replace(" ", "-"),
                "write_json": lambda path, data: None,
                "error_factory": RuntimeError,
                "load_agents": lambda path: {"planner": object()},
                "ensure_claude_available": lambda bin: None,
                "claude_command": lambda *args, **kwargs: [],
                "agent_payload_for_claude": lambda *args, **kwargs: "{}",
                "run_subprocess": lambda *args, **kwargs: None,
                "extract_json_payload": lambda text: {},
                "inventory_handler": lambda args: {"runCount": 0, "runs": []},
                "validate_handler": lambda args: {"planName": "alpha-plan", "taskCount": 1, "tasks": []},
                "run_handler": lambda args: {
                    "runId": "run-4",
                    "runDir": str(root / ".claude-orchestrator" / "runs" / "run-4"),
                    "dryRun": False,
                    "statusCounts": {"failed": 1},
                    "usageTotals": {"totalCostUsd": 0.1},
                },
                "resume_handler": lambda args: {},
                "retry_handler": lambda args: {},
                "status_handler": lambda args: {},
                "review_handler": lambda args: {},
                "promote_handler": lambda args: {},
                "validate_run_handler": lambda args: {},
                "default_task_timeout_sec": 30,
            },
        )

        self.assertIn("Retry failed tasks", " ".join(payload["nextActions"]))
