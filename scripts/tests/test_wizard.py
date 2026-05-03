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

    def test_run_clarification_loop_asks_questions_and_folds_answers(self):
        old_clarify = wizard_layer.clarify_goal_with_claude
        wizard_layer.clarify_goal_with_claude = lambda *args, **kwargs: {
            "questions": ["Which module?", "Target version?"],
            "refinedGoal": "add pagination to the API endpoint",
        }
        self.addCleanup(setattr, wizard_layer, "clarify_goal_with_claude", old_clarify)

        prompter = FakePrompter(texts=["users module", ""])
        refined_goal, answers = wizard_layer._run_clarification_loop(
            "add pagination",
            [],
            prompter,
            args=argparse.Namespace(planner_agent="planner", agents="agents.json", claude_bin="claude"),
            deps={},
        )

        self.assertIn("Which module?", prompter.messages)
        self.assertIn("Target version?", prompter.messages)
        self.assertEqual(2, len(answers))
        self.assertEqual("Which module?", answers[0]["question"])
        self.assertEqual("users module", answers[0]["answer"])
        self.assertEqual("", answers[1]["answer"])
        self.assertIn("users module", refined_goal)
        self.assertNotIn("Target version?", refined_goal)

    def test_clarify_goal_with_claude_parses_questions_and_refined_goal(self):
        fake_completed = argparse.Namespace(
            stdout='{"questions": ["Q1", "Q2"], "refinedGoal": "sharper goal"}'
        )
        planner_agent = argparse.Namespace(
            permission_mode="default",
            allowed_tools=[],
            disallowed_tools=[],
            max_budget_usd=None,
            timeout_sec=30,
        )

        result = wizard_layer.clarify_goal_with_claude(
            "vague goal",
            [],
            args=argparse.Namespace(planner_agent="planner", agents="agents.json", claude_bin="claude"),
            deps={
                "root": Path("."),
                "load_agents": lambda path: {"planner": planner_agent},
                "ensure_claude_available": lambda bin: None,
                "claude_command": lambda *a, **kw: [],
                "agent_payload_for_claude": lambda *a, **kw: "{}",
                "run_subprocess": lambda *a, **kw: fake_completed,
                "extract_json_payload": lambda text: json.loads(text),
            },
        )

        self.assertEqual(["Q1", "Q2"], result["questions"])
        self.assertEqual("sharper goal", result["refinedGoal"])

    def test_clarify_goal_with_claude_returns_fallback_on_bad_response(self):
        planner_agent = argparse.Namespace(
            permission_mode="default",
            allowed_tools=[],
            disallowed_tools=[],
            max_budget_usd=None,
            timeout_sec=30,
        )

        result = wizard_layer.clarify_goal_with_claude(
            "original goal",
            [],
            args=argparse.Namespace(planner_agent="planner", agents="agents.json", claude_bin="claude"),
            deps={
                "root": Path("."),
                "load_agents": lambda path: {"planner": planner_agent},
                "ensure_claude_available": lambda bin: None,
                "claude_command": lambda *a, **kw: [],
                "agent_payload_for_claude": lambda *a, **kw: "{}",
                "run_subprocess": lambda *a, **kw: argparse.Namespace(stdout="not-json"),
                "extract_json_payload": lambda text: None,
            },
        )

        self.assertEqual([], result["questions"])
        self.assertEqual("original goal", result["refinedGoal"])

    def test_format_staged_plan_summary_includes_topology_and_cost(self):
        summary = wizard_layer._format_staged_plan_summary(
            "/path/to/plan.json",
            {
                "planName": "my-plan",
                "taskCount": 2,
                "tasks": [{"id": "t1"}],
                "topology": {"maxParallelWidth": 3},
                "costEstimate": {"totalMaxUsd": 0.0125},
            },
        )
        self.assertIn("Max parallel", summary)
        self.assertIn("3", summary)
        self.assertIn("Est. cost", summary)
        self.assertIn("0.0125", summary)

    def test_noninteractive_plan_mode_omits_plan_checkpoint_key(self):
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
                "validate_handler": lambda args: {"planName": "alpha-plan", "taskCount": 1, "tasks": []},
                "run_handler": lambda args: {
                    "runId": "r1",
                    "runDir": str(root / ".claude-orchestrator" / "runs" / "r1"),
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

        self.assertNotIn("planCheckpoint", payload)

    def test_plan_approval_checkpoint_noninteractive_returns_proceed(self):
        prompter = FakePrompter()
        result = wizard_layer._plan_approval_checkpoint("plan summary", prompter, interactive=False)
        self.assertEqual("proceed", result)
        self.assertEqual([], prompter.messages)

    def test_format_staged_plan_summary_includes_plan_name_and_tasks(self):
        summary = wizard_layer._format_staged_plan_summary(
            "/path/to/plan.json",
            {
                "planName": "my-plan",
                "taskCount": 3,
                "tasks": [
                    {"id": "task-a", "agent": "coder"},
                    {"id": "task-b"},
                    {"id": "task-c", "agent": "tester"},
                ],
            },
        )
        self.assertIn("my-plan", summary)
        self.assertIn("task-a", summary)
        self.assertIn("task-b", summary)
        self.assertIn("[coder]", summary)

    def test_plan_approval_checkpoint_stop_exits_before_run(self):
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        # plan is supplied explicitly so goal-ask and saved-plans flow are skipped;
        # choices queue only needs the checkpoint value
        prompter = FakePrompter(choices=["stop"])
        old_choose_prompter = wizard_layer.choose_prompter
        wizard_layer.choose_prompter = lambda **kwargs: prompter
        self.addCleanup(setattr, wizard_layer, "choose_prompter", old_choose_prompter)
        run_called: list[bool] = []

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
                    "run_handler": lambda args: run_called.append(True) or {},
                    "resume_handler": lambda args: {},
                    "retry_handler": lambda args: {},
                    "status_handler": lambda args: {},
                    "review_handler": lambda args: {},
                    "promote_handler": lambda args: {},
                    "validate_run_handler": lambda args: {},
                    "default_task_timeout_sec": 30,
                },
            )

        self.assertEqual([], run_called)
        last_step = payload["steps"][-1]
        self.assertEqual("stopped", last_step["status"])
        self.assertEqual("stop", payload["planCheckpoint"])

    def test_plan_approval_checkpoint_revise_reruns_validation(self):
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        # choices: ["medium" (effort), plan_path (round 0 picker), "revise" (round 0 checkpoint),
        #           plan_path (round 1 picker), "proceed" (round 1 checkpoint)]
        # texts:   ["my refined goal" (goal ask), "2" (revised goal ask)]
        prompter = FakePrompter(
            choices=["medium", plan_path, "revise", plan_path, "proceed"],
            texts=["my refined goal", "2"],
            confirms=[False],
        )
        old_choose_prompter = wizard_layer.choose_prompter
        wizard_layer.choose_prompter = lambda **kwargs: prompter
        self.addCleanup(setattr, wizard_layer, "choose_prompter", old_choose_prompter)
        validate_calls: list[bool] = []

        with mock.patch.object(wizard_layer.sys.stdin, "isatty", return_value=True), mock.patch.object(wizard_layer.sys.stderr, "isatty", return_value=True):
            payload = wizard_layer.wizard_command(
                argparse.Namespace(
                    json=False,
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
                    dry_run=False,
                    planner_agent="planner",
                    planner_effort="",
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
                    "validate_handler": lambda args: validate_calls.append(True) or {"planName": "alpha-plan", "taskCount": 1, "tasks": []},
                    "run_handler": lambda args: {
                        "runId": "run-rev",
                        "runDir": str(root / ".claude-orchestrator" / "runs" / "run-rev"),
                        "dryRun": False,
                        "statusCounts": {"completed": 1},
                        "usageTotals": {"totalCostUsd": 0.0},
                    },
                    "resume_handler": lambda args: {},
                    "retry_handler": lambda args: {},
                    "status_handler": lambda args: {"reviewSummary": {"changedTaskCount": 0}},
                    "review_handler": lambda args: {},
                    "promote_handler": lambda args: {"promotionAllowed": True, "blockedReasons": [], "filesPromoted": 0},
                    "validate_run_handler": lambda args: {},
                    "default_task_timeout_sec": 30,
                },
            )

        self.assertEqual(2, len(validate_calls))
        self.assertIn("Revised goal", prompter.messages)
        self.assertEqual("proceed", payload["planCheckpoint"])
        self.assertIn("clarification", payload)

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


class WizardGoalPromptTest(unittest.TestCase):
    """When interactive=True and no --goal flag, wizard asks for the goal via ask_text."""

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

    def _make_deps(self, root: Path):
        return {
            "root": root,
            "textual_available": lambda: False,
            "slugify": lambda text: text.replace(" ", "-"),
            "write_json": lambda path, data: None,
            "error_factory": RuntimeError,
            "load_agents": lambda path: {},
            "ensure_claude_available": lambda b: None,
            "claude_command": lambda *a, **k: [],
            "agent_payload_for_claude": lambda a: {},
            "run_subprocess": lambda *a, **k: None,
            "extract_json_payload": lambda t: {},
            "inventory_handler": lambda a: {"runs": []},
            "validate_handler": lambda a: {
                "planName": "alpha-plan",
                "taskCount": 1,
                "topology": {"maxParallelWidth": 1},
                "tasks": [{"id": "a"}],
            },
            "run_handler": lambda a: {
                "runId": "run-g",
                "runDir": str(root / ".claude-orchestrator" / "runs" / "run-g"),
                "dryRun": True,
                "statusCounts": {"planned": 1},
                "usageTotals": {"totalCostUsd": 0.0},
            },
            "resume_handler": lambda a: {},
            "retry_handler": lambda a: {},
            "status_handler": lambda a: {},
            "review_handler": lambda a: {},
            "diff_run_handler": lambda a: {},
            "promote_handler": lambda a: {},
            "validate_run_handler": lambda a: {},
            "default_task_timeout_sec": 30,
        }

    def _interactive_patches(self):
        """Context manager: make stdin+stderr look like a tty so interactive=True."""
        import contextlib
        @contextlib.contextmanager
        def _ctx():
            with mock.patch("sys.stdin") as mock_stdin, \
                 mock.patch("sys.stderr") as mock_stderr:
                mock_stdin.isatty = lambda: True
                mock_stderr.isatty = lambda: True
                yield
        return _ctx()

    def test_ask_text_called_when_interactive_and_no_goal(self):
        """When interactive and no goal, wizard calls ask_text before proceeding."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)

        prompter = FakePrompter(texts=["add pagination to items endpoint"])

        with self._interactive_patches():
            with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
                wizard_layer.wizard_command(
                    argparse.Namespace(
                        json=False,
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
                        max_parallel=1,
                        dry_run=True,
                        planner_agent="planner",
                        verbose=False,
                    ),
                    deps=self._make_deps(root),
                )

        self.assertIn("What do you want to accomplish?", prompter.messages)

    def test_typed_goal_becomes_active_goal(self):
        """Goal typed at the prompt becomes refinedGoal in clarification payload."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)

        prompter = FakePrompter(texts=["add rate limiting"])

        with self._interactive_patches():
            with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
                payload = wizard_layer.wizard_command(
                    argparse.Namespace(
                        json=False,
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
                        max_parallel=1,
                        dry_run=True,
                        planner_agent="planner",
                        verbose=False,
                    ),
                    deps=self._make_deps(root),
                )

        self.assertEqual("add rate limiting", payload["clarification"]["refinedGoal"])

    def test_empty_typed_goal_falls_through_to_plan_chooser(self):
        """If user presses Enter with no input, wizard shows the tracked plan list."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)

        prompter = FakePrompter(texts=[""])  # user pressed Enter

        with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
            payload = wizard_layer.wizard_command(
                argparse.Namespace(
                    json=False,
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
                    max_parallel=1,
                    dry_run=True,
                    planner_agent="planner",
                    verbose=False,
                ),
                deps=self._make_deps(root),
            )

        # plan chooser was shown — selectedPlanPath is set to the tracked plan
        self.assertIn("selectedPlanPath", payload)

    def test_no_goal_prompt_when_not_interactive(self):
        """Non-interactive mode (--json) never calls ask_text for goal."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)

        prompter = FakePrompter()

        with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
            wizard_layer.wizard_command(
                argparse.Namespace(
                    json=True,  # non-interactive
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
                    max_parallel=1,
                    dry_run=True,
                    planner_agent="planner",
                    verbose=False,
                ),
                deps=self._make_deps(root),
            )

        self.assertNotIn("What do you want to accomplish?", prompter.messages)


class WizardSavedPlansFlowTest(unittest.TestCase):
    """Tests for _saved_plans_flow function."""

    def _make_runtime_root(self) -> tuple[tempfile.TemporaryDirectory[str], Path]:
        td = tempfile.TemporaryDirectory()
        return td, Path(td.name)

    def _make_saved_plan(self, saved_dir: Path, name: str, goal: str) -> Path:
        saved_dir.mkdir(parents=True, exist_ok=True)
        plan_path = saved_dir / f"{name}.json"
        plan_path.write_text(
            json.dumps({"version": 1, "name": name, "goal": goal, "tasks": [{"id": "t1"}]}),
            encoding="utf-8",
        )
        return plan_path

    def test_empty_list_shows_message_and_returns_stop(self):
        """No tracked or saved plans → shows message and returns _SAVED_FLOW_STOP."""
        td, runtime_root = self._make_runtime_root()
        self.addCleanup(td.cleanup)
        prompter = FakePrompter()
        result = wizard_layer._saved_plans_flow([], runtime_root, prompter)
        self.assertEqual(wizard_layer._SAVED_FLOW_STOP, result)
        self.assertTrue(any("No saved plans" in m for m in prompter.messages))

    def test_cancel_from_plan_list_returns_stop(self):
        """Selecting Cancel value from plan list returns _SAVED_FLOW_STOP."""
        td, runtime_root = self._make_runtime_root()
        self.addCleanup(td.cleanup)
        saved_dir = runtime_root / "saved-plans"
        self._make_saved_plan(saved_dir, "my-plan", "Do something")
        prompter = FakePrompter(choices=[wizard_layer._SAVED_FLOW_STOP])
        result = wizard_layer._saved_plans_flow([], runtime_root, prompter)
        self.assertEqual(wizard_layer._SAVED_FLOW_STOP, result)

    def test_start_action_returns_plan_path(self):
        """Selecting Start for a plan returns that plan's resolved path."""
        td, runtime_root = self._make_runtime_root()
        self.addCleanup(td.cleanup)
        saved_dir = runtime_root / "saved-plans"
        plan_path = self._make_saved_plan(saved_dir, "my-plan", "Do something")
        plan_path_str = str(plan_path.resolve())
        prompter = FakePrompter(choices=[plan_path_str, wizard_layer._SAVED_ACTION_START])
        result = wizard_layer._saved_plans_flow([], runtime_root, prompter)
        self.assertEqual(plan_path_str, result)

    def test_edit_action_shows_path_message_and_returns_stop(self):
        """Edit action emits a show_message with the plan path and exits."""
        td, runtime_root = self._make_runtime_root()
        self.addCleanup(td.cleanup)
        saved_dir = runtime_root / "saved-plans"
        plan_path = self._make_saved_plan(saved_dir, "my-plan", "Do something")
        plan_path_str = str(plan_path.resolve())
        prompter = FakePrompter(choices=[plan_path_str, wizard_layer._SAVED_ACTION_EDIT])
        result = wizard_layer._saved_plans_flow([], runtime_root, prompter)
        self.assertEqual(wizard_layer._SAVED_FLOW_STOP, result)
        self.assertTrue(any(plan_path_str in m for m in prompter.messages))

    def test_delete_removes_file_and_loops_to_next(self):
        """Delete removes the file; list refreshes and user can start another plan."""
        td, runtime_root = self._make_runtime_root()
        self.addCleanup(td.cleanup)
        saved_dir = runtime_root / "saved-plans"
        plan_a = self._make_saved_plan(saved_dir, "plan-a", "Goal A")
        plan_b = self._make_saved_plan(saved_dir, "plan-b", "Goal B")
        plan_a_str = str(plan_a.resolve())
        plan_b_str = str(plan_b.resolve())
        prompter = FakePrompter(choices=[
            plan_a_str, wizard_layer._SAVED_ACTION_DELETE,
            plan_b_str, wizard_layer._SAVED_ACTION_START,
        ])
        result = wizard_layer._saved_plans_flow([], runtime_root, prompter)
        self.assertEqual(plan_b_str, result)
        self.assertFalse(plan_a.exists())

    def test_back_loops_to_plan_list(self):
        """Back action re-shows the plan list without exiting."""
        td, runtime_root = self._make_runtime_root()
        self.addCleanup(td.cleanup)
        saved_dir = runtime_root / "saved-plans"
        plan_path = self._make_saved_plan(saved_dir, "my-plan", "Do something")
        plan_path_str = str(plan_path.resolve())
        # Round 1: select plan → back; Round 2: select plan → start
        prompter = FakePrompter(choices=[
            plan_path_str, wizard_layer._SAVED_ACTION_BACK,
            plan_path_str, wizard_layer._SAVED_ACTION_START,
        ])
        result = wizard_layer._saved_plans_flow([], runtime_root, prompter)
        self.assertEqual(plan_path_str, result)

    def test_tracked_previews_appear_in_browser(self):
        """Tracked plan previews are shown alongside saved plans."""
        td, runtime_root = self._make_runtime_root()
        self.addCleanup(td.cleanup)
        tracked = wizard_layer.PlanPreview(
            path="/tracked/alpha.json",
            name="alpha",
            goal="tracked goal",
            task_count=2,
        )
        prompter = FakePrompter(choices=[wizard_layer._SAVED_FLOW_STOP])
        wizard_layer._saved_plans_flow([tracked], runtime_root, prompter)
        self.assertTrue(any("Saved plans" in m for m in prompter.messages))


class WizardPlannerEffortTest(unittest.TestCase):
    """Tests for effort level selection in wizard plan mode."""

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

    def _base_deps(self, root: Path) -> dict:
        return {
            "root": root,
            "textual_available": lambda: False,
            "slugify": lambda t: t.replace(" ", "-"),
            "write_json": lambda p, d: None,
            "error_factory": RuntimeError,
            "load_agents": lambda p: {},
            "ensure_claude_available": lambda b: None,
            "claude_command": lambda *a, **k: [],
            "agent_payload_for_claude": lambda a: {},
            "run_subprocess": lambda *a, **k: None,
            "extract_json_payload": lambda t: {},
            "inventory_handler": lambda a: {"runs": []},
            "validate_handler": lambda a: {
                "planName": "alpha-plan", "taskCount": 1,
                "topology": {"maxParallelWidth": 1}, "tasks": [{"id": "a"}],
            },
            "run_handler": lambda a: {
                "runId": "run-e",
                "runDir": str(root / ".claude-orchestrator" / "runs" / "run-e"),
                "dryRun": True, "statusCounts": {"planned": 1},
                "usageTotals": {"totalCostUsd": 0.0},
            },
            "resume_handler": lambda a: {},
            "retry_handler": lambda a: {},
            "status_handler": lambda a: {},
            "review_handler": lambda a: {},
            "promote_handler": lambda a: {},
            "validate_run_handler": lambda a: {},
            "default_task_timeout_sec": 30,
        }

    def test_effort_high_maps_to_opus(self):
        model, effort_val = wizard_layer._EFFORT_MODEL_MAP["high"]
        self.assertIn("opus", model.lower())
        self.assertEqual("high", effort_val)

    def test_effort_medium_maps_to_sonnet(self):
        model, effort_val = wizard_layer._EFFORT_MODEL_MAP["medium"]
        self.assertIn("sonnet", model.lower())
        self.assertEqual("medium", effort_val)

    def test_effort_low_maps_to_haiku(self):
        model, effort_val = wizard_layer._EFFORT_MODEL_MAP["low"]
        self.assertIn("haiku", model.lower())
        self.assertEqual("low", effort_val)

    def test_effort_prompt_shown_when_goal_typed_interactively(self):
        """Interactive goal entry triggers the planner effort chooser."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        # texts: goal; choices: effort, then plan fallback picker
        prompter = FakePrompter(texts=["add caching"], choices=["low", plan_path])

        with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
            with mock.patch("sys.stdin") as si, mock.patch("sys.stderr") as se:
                si.isatty = lambda: True
                se.isatty = lambda: True
                wizard_layer.wizard_command(
                    argparse.Namespace(
                        json=False, tui=False, watch=False, plan="",
                        goal="", goal_words=[], resume_run_ref="", retry_run_ref="",
                        agents=str(root / "ai" / "orchestrator" / "agents.json"),
                        claude_bin="claude",
                        runtime_root=str(root / ".claude-orchestrator"),
                        max_parallel=1, dry_run=True, planner_agent="planner",
                        planner_effort="", verbose=False,
                    ),
                    deps=self._base_deps(root),
                )

        self.assertTrue(any("Planner effort" in m for m in prompter.messages))

    def test_cli_effort_arg_skips_prompt(self):
        """When --planner-effort is supplied, the effort chooser is not presented."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        # No effort choice needed in queue — only the fallback plan picker
        prompter = FakePrompter(texts=["add caching"], choices=[plan_path])

        with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
            with mock.patch("sys.stdin") as si, mock.patch("sys.stderr") as se:
                si.isatty = lambda: True
                se.isatty = lambda: True
                wizard_layer.wizard_command(
                    argparse.Namespace(
                        json=False, tui=False, watch=False, plan="",
                        goal="", goal_words=[], resume_run_ref="", retry_run_ref="",
                        agents=str(root / "ai" / "orchestrator" / "agents.json"),
                        claude_bin="claude",
                        runtime_root=str(root / ".claude-orchestrator"),
                        max_parallel=1, dry_run=True, planner_agent="planner",
                        planner_effort="high", verbose=False,
                    ),
                    deps=self._base_deps(root),
                )

        self.assertFalse(any("Planner effort" in m for m in prompter.messages))

    def test_planner_effort_recorded_in_payload(self):
        """wizard_command stores the effective effort in payload['plannerEffort']."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        payload = wizard_layer.wizard_command(
            argparse.Namespace(
                json=True, tui=False, watch=False, plan=plan_path,
                goal="", goal_words=[], resume_run_ref="", retry_run_ref="",
                agents=str(root / "ai" / "orchestrator" / "agents.json"),
                claude_bin="claude",
                runtime_root=str(root / ".claude-orchestrator"),
                max_parallel=1, dry_run=True, planner_agent="planner",
                planner_effort="low", verbose=False,
            ),
            deps=self._base_deps(root),
        )
        self.assertEqual("low", payload.get("plannerEffort"))


class WizardCheckpointNewOptionsTest(unittest.TestCase):
    """Tests for save_only, save_and_start, and edit checkpoint options."""

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

    def _base_deps(self, root: Path) -> tuple[dict, list]:
        run_called: list[bool] = []
        deps = {
            "root": root,
            "textual_available": lambda: False,
            "slugify": lambda t: t,
            "write_json": lambda p, d: None,
            "error_factory": RuntimeError,
            "load_agents": lambda p: {},
            "ensure_claude_available": lambda b: None,
            "claude_command": lambda *a, **k: [],
            "agent_payload_for_claude": lambda a: {},
            "run_subprocess": lambda *a, **k: None,
            "extract_json_payload": lambda t: {},
            "inventory_handler": lambda a: {"runs": []},
            "validate_handler": lambda a: {"planName": "alpha-plan", "taskCount": 1, "tasks": []},
            "run_handler": lambda a: run_called.append(True) or {
                "runId": "run-c",
                "runDir": str(root / ".claude-orchestrator" / "runs" / "run-c"),
                "dryRun": False, "statusCounts": {"completed": 1},
                "usageTotals": {"totalCostUsd": 0.0},
            },
            "resume_handler": lambda a: {},
            "retry_handler": lambda a: {},
            "status_handler": lambda a: {"reviewSummary": {"changedTaskCount": 0}},
            "review_handler": lambda a: {},
            "promote_handler": lambda a: {"promotionAllowed": True, "blockedReasons": [], "filesPromoted": 0},
            "validate_run_handler": lambda a: {},
            "default_task_timeout_sec": 30,
        }
        return deps, run_called

    def _interactive_args(self, root: Path, plan_path: str) -> argparse.Namespace:
        return argparse.Namespace(
            json=False, tui=False, watch=False, plan=plan_path,
            goal="", goal_words=[], resume_run_ref="", retry_run_ref="",
            agents=str(root / "ai" / "orchestrator" / "agents.json"),
            claude_bin="claude",
            runtime_root=str(root / ".claude-orchestrator"),
            max_parallel=1, dry_run=False, planner_agent="planner",
            planner_effort="", verbose=False,
        )

    def test_save_only_stops_wizard_and_creates_file(self):
        """save_only saves the plan file and exits without running."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        deps, run_called = self._base_deps(root)
        prompter = FakePrompter(choices=["save_only"])

        with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
            with mock.patch("sys.stdin") as si, mock.patch("sys.stderr") as se:
                si.isatty = lambda: True
                se.isatty = lambda: True
                payload = wizard_layer.wizard_command(
                    self._interactive_args(root, plan_path), deps=deps
                )

        self.assertEqual([], run_called)
        self.assertIn("savedPlanPath", payload)
        self.assertTrue(Path(payload["savedPlanPath"]).exists())
        self.assertEqual("saved", payload["steps"][-1]["status"])

    def test_save_and_start_saves_plan_and_runs(self):
        """save_and_start saves the plan and continues to run."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        deps, run_called = self._base_deps(root)
        prompter = FakePrompter(choices=["save_and_start"], confirms=[False, False])

        with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
            with mock.patch("sys.stdin") as si, mock.patch("sys.stderr") as se:
                si.isatty = lambda: True
                se.isatty = lambda: True
                payload = wizard_layer.wizard_command(
                    self._interactive_args(root, plan_path), deps=deps
                )

        self.assertEqual([True], run_called)
        self.assertIn("savedPlanPath", payload)
        self.assertTrue(Path(payload["savedPlanPath"]).exists())

    def test_edit_checkpoint_stops_wizard_without_running(self):
        """edit checkpoint emits plan path message and exits without running."""
        td, root, plan_path = self._make_plan_root()
        self.addCleanup(td.cleanup)
        deps, run_called = self._base_deps(root)
        prompter = FakePrompter(choices=["edit"])

        with mock.patch.object(wizard_layer, "choose_prompter", return_value=prompter):
            with mock.patch("sys.stdin") as si, mock.patch("sys.stderr") as se:
                si.isatty = lambda: True
                se.isatty = lambda: True
                payload = wizard_layer.wizard_command(
                    self._interactive_args(root, plan_path), deps=deps
                )

        self.assertEqual([], run_called)
        self.assertEqual("edit", payload["steps"][-1]["status"])
        self.assertTrue(any(plan_path in m for m in prompter.messages))
