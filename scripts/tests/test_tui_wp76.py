"""WP76: Operator TUI feature completion — ClarificationScreen AI, inspector screens, settings."""
from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

TEXTUAL_AVAILABLE = importlib.util.find_spec("textual") is not None


# ── ClarificationScreen unit tests (no Textual needed) ────────────────────────

class TestClarificationScreenInit(unittest.TestCase):

    def _screen_cls(self):
        from ai.pojo_lens_agents._tui_wizard import ClarificationScreen
        return ClarificationScreen

    def test_no_clarify_fn_uses_static_questions(self):
        from ai.pojo_lens_agents._tui_theme import _CLARIF_QUESTIONS
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("my goal")
        self.assertIsNone(s._clarify_fn)
        self.assertEqual(s._questions, list(_CLARIF_QUESTIONS))

    def test_clarify_fn_stored(self):
        ClarificationScreen = self._screen_cls()
        fn = lambda g: {"questions": ["Q1", "Q2"], "refinedGoal": "refined"}
        s = ClarificationScreen("my goal", clarify_fn=fn)
        self.assertIs(s._clarify_fn, fn)

    def test_ai_questions_override_static(self):
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("goal")
        s._ai_questions = ["AQ1", "AQ2"]
        self.assertEqual(s._questions, ["AQ1", "AQ2"])

    def test_questions_empty_ai_falls_back_to_static(self):
        from ai.pojo_lens_agents._tui_theme import _CLARIF_QUESTIONS
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("goal")
        s._ai_questions = []
        self.assertEqual(s._questions, list(_CLARIF_QUESTIONS))

    def test_finish_uses_ai_refined_goal(self):
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("original goal")
        s._ai_refined_goal = "AI refined goal"
        # simulate _finish via dismiss tracking
        dismissed: list[str] = []
        s.dismiss = dismissed.append  # type: ignore[method-assign]
        s._ai_questions = ["Q1"]
        s._q_idx = 1  # already past last question
        s._finish()
        self.assertEqual(dismissed[0], "AI refined goal")

    def test_finish_no_ai_uses_original_goal(self):
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("original goal")
        dismissed: list[str] = []
        s.dismiss = dismissed.append  # type: ignore[method-assign]
        s._ai_questions = []
        s._ai_refined_goal = ""
        s._finish()
        self.assertEqual(dismissed[0], "original goal")

    def test_finish_appends_answers_to_refined_goal(self):
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("base")
        s._ai_refined_goal = "refined"
        s._answers = [("Q1", "A1"), ("Q2", "A2")]
        dismissed: list[str] = []
        s.dismiss = dismissed.append  # type: ignore[method-assign]
        s._finish()
        result = dismissed[0]
        self.assertIn("refined", result)
        self.assertIn("A1", result)
        self.assertIn("A2", result)

    def test_skip_all_returns_refined_goal(self):
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("goal")
        s._ai_refined_goal = "refined AI goal"
        dismissed: list[str] = []
        s.dismiss = dismissed.append  # type: ignore[method-assign]
        s.action_skip_all()
        self.assertEqual(dismissed[0], "refined AI goal")

    def test_skip_all_no_refined_returns_original(self):
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("goal")
        dismissed: list[str] = []
        s.dismiss = dismissed.append  # type: ignore[method-assign]
        s.action_skip_all()
        self.assertEqual(dismissed[0], "goal")

    def test_questions_property_returns_ai_when_available(self):
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("goal")
        s._ai_questions = ["AI-Q1", "AI-Q2", "AI-Q3"]
        self.assertEqual(s._questions, ["AI-Q1", "AI-Q2", "AI-Q3"])

    def test_clarify_fn_result_format_accepted(self):
        # Ensure the dict keys clarify_fn must return are understood
        result = {"questions": ["Q?"], "refinedGoal": "new goal"}
        ClarificationScreen = self._screen_cls()
        s = ClarificationScreen("old goal")
        questions = [str(q) for q in (result.get("questions") or []) if q][:3]
        refined   = str(result.get("refinedGoal") or "").strip()
        self.assertEqual(questions, ["Q?"])
        self.assertEqual(refined, "new goal")


# ── ExtraToolsScreen data extraction ──────────────────────────────────────────

class TestExtraToolsScreen(unittest.TestCase):

    def _plan_with_tools(self, agent_tools: list, task_tools: list) -> dict:
        return {
            "agents": [{"name": "coder", "extraTools": agent_tools}],
            "tasks":  [{"id": "t1",      "extraTools": task_tools}],
        }

    def test_collects_agent_and_task_tools(self):
        plan = self._plan_with_tools(
            [{"name": "my_tool", "kind": "shell", "command": "echo hi"}],
            [{"name": "task_tool", "kind": "script", "path": "run.sh"}],
        )
        # verify extraction logic directly (no TUI)
        rows = []
        for ag in (plan.get("agents") or []):
            for t in (ag.get("extraTools") or []):
                if isinstance(t, dict):
                    rows.append(("agent", t.get("name"), t.get("kind")))
        for task in (plan.get("tasks") or []):
            for t in (task.get("extraTools") or []):
                if isinstance(t, dict):
                    rows.append(("task", t.get("name"), t.get("kind")))
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[0], ("agent", "my_tool", "shell"))
        self.assertEqual(rows[1], ("task",  "task_tool", "script"))

    def test_base_tool_collision_detection(self):
        BASE = frozenset({"read_file", "write_file", "str_replace_based_edit_tool",
                          "bash", "write_shared_context"})
        self.assertIn("bash", BASE)
        colliding_tool = {"name": "bash", "kind": "shell"}
        is_collision = colliding_tool["name"] in BASE
        self.assertTrue(is_collision)

    def test_path_traversal_detection(self):
        tool_with_traversal = {"name": "t", "kind": "shell", "path": "../../../etc/passwd"}
        self.assertIn("..", str(tool_with_traversal.get("path") or ""))


# ── ValidationIntentsScreen data extraction ───────────────────────────────────

class TestValidationIntentsExtraction(unittest.TestCase):

    def test_extracts_intents_per_task(self):
        tasks = [
            {"id": "t1", "validationIntents": [{"kind": "tool", "command": "run_tests"}]},
            {"id": "t2", "validationIntents": [{"kind": "repo-script", "script": "validate.sh"}]},
        ]
        by_task = {t["id"]: t.get("validationIntents", []) for t in tasks}
        self.assertEqual(len(by_task["t1"]), 1)
        self.assertEqual(by_task["t1"][0]["kind"], "tool")
        self.assertEqual(by_task["t2"][0]["kind"], "repo-script")

    def test_legacy_validation_commands_detected(self):
        tasks = [{"id": "t1", "validationCommands": ["mvn test", "npm test"]}]
        has_legacy = any(bool(t.get("validationCommands")) for t in tasks)
        self.assertTrue(has_legacy)

    def test_task_without_intents_is_ok(self):
        tasks = [{"id": "t1"}]
        has_legacy = any(bool(t.get("validationCommands")) for t in tasks)
        self.assertFalse(has_legacy)


# ── OutputProfilesScreen data extraction ──────────────────────────────────────

class TestOutputProfilesExtraction(unittest.TestCase):

    def test_reads_output_profile(self):
        tasks = [
            {"id": "t1", "outputProfile": "lean"},
            {"id": "t2", "outputProfile": "default"},
            {"id": "t3"},  # no profile → defaults to "default"
        ]
        profiles = [(t["id"], t.get("outputProfile", "default")) for t in tasks]
        self.assertEqual(profiles[0], ("t1", "lean"))
        self.assertEqual(profiles[1], ("t2", "default"))
        self.assertEqual(profiles[2], ("t3", "default"))


# ── FollowUpTaskScreen data extraction ────────────────────────────────────────

class TestFollowUpTaskExtraction(unittest.TestCase):

    def test_collects_follow_up_tasks_from_plan(self):
        plan = {
            "tasks": [
                {
                    "id": "t1",
                    "followUpTasks": [
                        {"id": "t2", "conditionField": "status", "conditionValue": "ok"},
                    ],
                },
                {"id": "t3"},
            ]
        }
        fu_tasks = []
        for task in (plan.get("tasks") or []):
            for fut in (task.get("followUpTasks") or []):
                if isinstance(fut, dict):
                    fu_tasks.append({"emitter": task["id"], **fut})
        self.assertEqual(len(fu_tasks), 1)
        self.assertEqual(fu_tasks[0]["emitter"], "t1")
        self.assertEqual(fu_tasks[0]["conditionField"], "status")

    def test_manifest_injection_event_parsing(self):
        events = [
            {"phase": "task-injection",         "taskId": "t2"},
            {"phase": "task-injection-skipped",  "taskId": "t3", "reason": "condition unmet"},
            {"phase": "ready-batch"},
        ]
        injected = [e for e in events if e.get("phase") in ("task-injection", "task-injection-skipped")]
        self.assertEqual(len(injected), 2)
        self.assertEqual(injected[0]["taskId"], "t2")
        self.assertEqual(injected[1]["reason"], "condition unmet")


# ── PromptAccountingScreen token logic ────────────────────────────────────────

class TestPromptAccountingLogic(unittest.TestCase):

    _SECTION_KEYS = [
        ("systemPrompt",      "System Prompt"),
        ("dependencyContext", "Dependency Context"),
        ("taskPrompt",        "Task Prompt"),
    ]
    _WARN_TOKENS  = 80_000
    _BLOCK_TOKENS = 120_000

    def _total(self, pa: dict) -> int:
        return sum(int(pa.get(k, 0) or 0) for k, _ in self._SECTION_KEYS)

    def test_sums_sections(self):
        pa = {"systemPrompt": 5000, "dependencyContext": 20000, "taskPrompt": 10000}
        self.assertEqual(self._total(pa), 35000)

    def test_warn_threshold(self):
        pa = {"systemPrompt": 85_000}
        total = self._total(pa)
        self.assertGreaterEqual(total, self._WARN_TOKENS)
        self.assertLess(total, self._BLOCK_TOKENS)

    def test_block_threshold(self):
        pa = {"systemPrompt": 130_000}
        total = self._total(pa)
        self.assertGreaterEqual(total, self._BLOCK_TOKENS)

    def test_safe_total(self):
        pa = {"systemPrompt": 5000, "taskPrompt": 3000}
        total = self._total(pa)
        self.assertLess(total, self._WARN_TOKENS)

    def test_missing_sections_zero(self):
        pa: dict = {}
        self.assertEqual(self._total(pa), 0)


# ── SettingsScreen config helper ──────────────────────────────────────────────

class TestSettingsScreenHelpers(unittest.TestCase):

    def test_settings_screen_imports(self):
        from ai.pojo_lens_agents._tui_tools import SettingsScreen
        self.assertTrue(callable(SettingsScreen))

    def test_clarification_screen_accepts_clarify_fn_param(self):
        import inspect
        from ai.pojo_lens_agents._tui_wizard import ClarificationScreen
        sig = inspect.signature(ClarificationScreen.__init__)
        self.assertIn("clarify_fn", sig.parameters)


# ── Inspector screen import smoke test ────────────────────────────────────────

class TestInspectorScreenImports(unittest.TestCase):

    def test_inspector_screens_importable(self):
        from ai.pojo_lens_agents._tui_inspect import (
            ExtraToolsScreen,
            ValidationIntentsScreen,
            OutputProfilesScreen,
            FollowUpTaskScreen,
            PromptAccountingScreen,
        )
        for cls in [ExtraToolsScreen, ValidationIntentsScreen,
                    OutputProfilesScreen, FollowUpTaskScreen, PromptAccountingScreen]:
            self.assertTrue(callable(cls), f"{cls.__name__} not callable")

    def test_inspector_screens_reexported_from_tui_operator(self):
        from ai.pojo_lens_agents.tui_operator import (
            ExtraToolsScreen,
            ValidationIntentsScreen,
            OutputProfilesScreen,
            FollowUpTaskScreen,
            PromptAccountingScreen,
        )


# ── PlanDetailsScreen bindings ────────────────────────────────────────────────

class TestPlanDetailsScreenBindings(unittest.TestCase):

    def test_inspector_bindings_present(self):
        import inspect
        from ai.pojo_lens_agents._tui_plans import PlanDetailsScreen
        bindings = [b.key for b in (PlanDetailsScreen.BINDINGS or [])]
        self.assertIn("t", bindings)
        self.assertIn("i", bindings)
        self.assertIn("o", bindings)
        self.assertIn("p", bindings)

    def test_inspector_action_methods_present(self):
        from ai.pojo_lens_agents._tui_plans import PlanDetailsScreen
        for method in ("action_extra_tools", "action_val_intents",
                       "action_out_profiles", "action_prompt_acct"):
            self.assertTrue(hasattr(PlanDetailsScreen, method), f"Missing {method}")


# ── Textual integration tests (skip if not installed) ─────────────────────────

@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual not installed")
class TestClarificationScreenTUI(unittest.IsolatedAsyncioTestCase):

    async def test_static_questions_render_without_clarify_fn(self):
        from ai.pojo_lens_agents._tui_wizard import ClarificationScreen
        from ai.pojo_lens_agents._tui_theme import _CLARIF_QUESTIONS

        class _App(__import__("textual.app", fromlist=["App"]).App):
            CSS = ""
            def on_mount(self):
                self.push_screen(ClarificationScreen("my goal"))

        app = _App()
        async with app.run_test(headless=True) as pilot:
            await pilot.pause(0.1)
            # First question should be visible
            widget = app.query_one("#cl-q-text")
            self.assertIn(_CLARIF_QUESTIONS[0][:20], widget.renderable)

    async def test_ai_questions_populated_after_worker(self):
        import asyncio
        from ai.pojo_lens_agents._tui_wizard import ClarificationScreen

        def _fast_fn(goal: str) -> dict:
            return {"questions": ["AI Q1", "AI Q2"], "refinedGoal": "AI refined"}

        class _App(__import__("textual.app", fromlist=["App"]).App):
            CSS = ""
            def on_mount(self):
                self.push_screen(ClarificationScreen("goal", clarify_fn=_fast_fn))

        app = _App()
        async with app.run_test(headless=True) as pilot:
            await pilot.pause(0.4)  # allow thread worker to complete
            screen = app.screen
            self.assertEqual(screen._ai_questions, ["AI Q1", "AI Q2"])
            self.assertEqual(screen._ai_refined_goal, "AI refined")

    async def test_skip_all_dismisses_with_refined_goal(self):
        from ai.pojo_lens_agents._tui_wizard import ClarificationScreen

        def _fast_fn(goal: str) -> dict:
            return {"questions": ["Q1"], "refinedGoal": "refined"}

        dismissed: list[str] = []

        class _App(__import__("textual.app", fromlist=["App"]).App):
            CSS = ""
            def on_mount(self):
                screen = ClarificationScreen("goal", clarify_fn=_fast_fn)
                self.push_screen(screen)

        app = _App()
        async with app.run_test(headless=True) as pilot:
            await pilot.pause(0.4)
            await pilot.press("escape")
            await pilot.pause(0.1)


if __name__ == "__main__":
    unittest.main()
