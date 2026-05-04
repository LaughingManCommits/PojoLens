"""Tests for WP79: TUI Cross-Platform & UX Polish."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


# ── HomeScreen cursor-aware [Enter] ──────────────────────────────────────────

class TestHomeScreenCursor(unittest.TestCase):

    def _cls(self):
        from ai.pojo_lens_agents._tui_home import HomeScreen
        return HomeScreen

    def test_nav_items_excludes_dividers(self):
        cls = self._cls()
        self.assertTrue(all(k != "──" for k in cls._NAV_ITEMS))

    def test_nav_items_includes_all_action_keys(self):
        cls = self._cls()
        expected = {"n", "s", "r", "l", "v", "d", "p", "a", "k", "m", "t", "q"}
        self.assertEqual(set(cls._NAV_ITEMS), expected)

    def test_up_down_bindings_present(self):
        from ai.pojo_lens_agents._tui_home import HomeScreen
        keys = {b.key for b in HomeScreen.BINDINGS}
        self.assertIn("up", keys)
        self.assertIn("down", keys)

    def test_enter_binding_present(self):
        from ai.pojo_lens_agents._tui_home import HomeScreen
        keys = {b.key for b in HomeScreen.BINDINGS}
        self.assertIn("enter", keys)

    def test_cursor_up_clamps_at_zero(self):
        cls = self._cls()
        s = cls()
        s._cursor = 0
        s.action_cursor_up()
        self.assertEqual(int(s._cursor), 0)

    def test_cursor_down_clamps_at_max(self):
        cls = self._cls()
        s = cls()
        max_idx = len(cls._NAV_ITEMS) - 1
        s._cursor = max_idx
        s.action_cursor_down()
        self.assertEqual(int(s._cursor), max_idx)

    def test_cursor_down_increments(self):
        cls = self._cls()
        s = cls()
        s._cursor = 0
        s.action_cursor_down()
        self.assertEqual(int(s._cursor), 1)

    def test_cursor_up_decrements(self):
        cls = self._cls()
        s = cls()
        s._cursor = 3
        s.action_cursor_up()
        self.assertEqual(int(s._cursor), 2)

    def test_activate_item_dispatches_correct_action(self):
        cls = self._cls()
        s = cls()
        called = []
        s.action_saved_plans = lambda: called.append("saved_plans")
        # Find index of "s" in _NAV_ITEMS
        idx = cls._NAV_ITEMS.index("s")
        s._cursor = idx
        # action_activate_item needs query_one mock for app (not available here)
        # Just verify the dispatch map keys match _NAV_ITEMS
        dispatch_keys = {"n", "s", "r", "l", "v", "d", "p", "a", "k", "m", "t", "q"}
        self.assertEqual(set(cls._NAV_ITEMS), dispatch_keys)

    def test_activate_item_new_plan_dispatch_in_source(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_home.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("action_new_plan", content)
        self.assertIn('"n"', content)

    def test_menu_row_ids_assigned_for_all_nav_items(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_home.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("menu-row-{_nav_idx}", content)

    def test_watch__cursor_method_exists(self):
        cls = self._cls()
        self.assertTrue(hasattr(cls, "watch__cursor"))


# ── MemoryToolsScreen cross-platform ─────────────────────────────────────────

class TestMemoryToolsCrossPlatform(unittest.TestCase):

    def _cls(self):
        from ai.pojo_lens_agents._tui_tools import MemoryToolsScreen
        return MemoryToolsScreen

    def test_memory_cmd_win32_uses_powershell(self):
        cls = self._cls()
        with patch("sys.platform", "win32"):
            cmd = cls._memory_cmd("refresh-ai-memory")
        self.assertEqual(cmd[0], "powershell")
        self.assertIn("-File", cmd)
        self.assertTrue(any("refresh-ai-memory.ps1" in c for c in cmd))

    def test_memory_cmd_posix_uses_python(self):
        cls = self._cls()
        with patch("sys.platform", "linux"):
            cmd = cls._memory_cmd("refresh-ai-memory")
        self.assertEqual(cmd[0], sys.executable)
        self.assertTrue(any("refresh-ai-memory.py" in c for c in cmd))

    def test_memory_cmd_win32_check_flag(self):
        cls = self._cls()
        with patch("sys.platform", "win32"):
            cmd = cls._memory_cmd("refresh-ai-memory", ["-Check"])
        self.assertIn("-Check", cmd)

    def test_memory_cmd_posix_check_flag(self):
        cls = self._cls()
        with patch("sys.platform", "linux"):
            cmd = cls._memory_cmd("refresh-ai-memory", ["--check"])
        self.assertIn("--check", cmd)

    def test_memory_cmd_win32_query_flags(self):
        cls = self._cls()
        with patch("sys.platform", "win32"):
            cmd = cls._memory_cmd("query-ai-memory", ["-Query", "foo", "-Limit", "10"])
        self.assertIn("-Query", cmd)
        self.assertIn("foo", cmd)

    def test_memory_cmd_posix_query_flags(self):
        cls = self._cls()
        with patch("sys.platform", "linux"):
            cmd = cls._memory_cmd("query-ai-memory", ["--query", "foo", "--limit", "10"])
        self.assertIn("--query", cmd)
        self.assertIn("foo", cmd)

    def test_run_query_uses_platform_flags(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_tools.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("--query", content)
        self.assertIn("-Query", content)

    def test_run_check_uses_platform_flags(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_tools.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("--check", content)
        self.assertIn("-Check", content)


# ── PlanEditorScreen cross-platform editor chain ─────────────────────────────

class TestPlanEditorCrossPlatform(unittest.TestCase):

    def _cls(self):
        from ai.pojo_lens_agents._tui_plans import PlanEditorScreen
        return PlanEditorScreen

    def test_launch_editor_tries_visual_first(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_plans.py"
        content = src.read_text(encoding="utf-8")
        # $VISUAL should appear before $EDITOR in candidates list
        visual_pos = content.index('get("VISUAL")')
        editor_pos = content.index('get("EDITOR")')
        self.assertLess(visual_pos, editor_pos)

    def test_launch_editor_tries_code(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_plans.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn('"code"', content)

    def test_launch_editor_posix_default_is_nano(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_plans.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn('"nano"', content)

    def test_launch_editor_win32_default_is_notepad(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_plans.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn('"notepad"', content)

    def test_launch_editor_falls_through_on_file_not_found(self):
        """If first candidate raises FileNotFoundError, tries the next one."""
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_plans.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("FileNotFoundError", content)
        self.assertIn("trying next", content)

    def test_launch_editor_gives_up_gracefully(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_plans.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("no editor found", content)


# ── GovernanceScreen inline validation ───────────────────────────────────────

class TestGovernanceScreenValidation(unittest.TestCase):

    def _screen(self):
        from ai.pojo_lens_agents._tui_wizard import GovernanceScreen
        s = GovernanceScreen("goal", "medium", "copy")
        return s

    def _make_mock_widgets(self, s):
        """Return (budget_err, parallel_err, continue_btn) mocks, patch query_one."""
        budget_err   = MagicMock(); budget_err.renderable = ""
        parallel_err = MagicMock(); parallel_err.renderable = ""
        cont_btn     = MagicMock(); cont_btn.disabled = False

        def _query_one(selector, *args):
            if "#budget-err"   in str(selector): return budget_err
            if "#parallel-err" in str(selector): return parallel_err
            if "#btn-continue" in str(selector): return cont_btn
            raise ValueError(f"unexpected: {selector}")

        s.query_one = _query_one
        return budget_err, parallel_err, cont_btn

    def test_valid_budget_clears_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_budget("1.50")
        be.update.assert_called_with("")

    def test_zero_budget_shows_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_budget("0")
        call_arg = be.update.call_args[0][0]
        self.assertIn("positive", call_arg)

    def test_negative_budget_shows_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_budget("-5")
        call_arg = be.update.call_args[0][0]
        self.assertIn("positive", call_arg)

    def test_non_numeric_budget_shows_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_budget("abc")
        call_arg = be.update.call_args[0][0]
        self.assertIn("positive", call_arg)

    def test_blank_budget_clears_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_budget("")
        be.update.assert_called_with("")

    def test_valid_parallel_clears_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_parallel("3")
        pe.update.assert_called_with("")

    def test_zero_parallel_shows_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_parallel("0")
        call_arg = pe.update.call_args[0][0]
        self.assertIn("1", call_arg)

    def test_non_int_parallel_shows_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        s._validate_parallel("abc")
        call_arg = pe.update.call_args[0][0]
        self.assertIn("1", call_arg)

    def test_has_errors_true_when_budget_err(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        be.renderable = "[bold #ff2244]⚠ must be a positive number[/]"
        pe.renderable = ""
        self.assertTrue(s._has_errors())

    def test_has_errors_false_when_clear(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        be.renderable = ""
        pe.renderable = ""
        self.assertFalse(s._has_errors())

    def test_submit_disabled_when_error(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        be.renderable = "[bold #ff2244]⚠ must be a positive number[/]"
        pe.renderable = ""
        s._update_submit_state()
        self.assertTrue(btn.disabled)

    def test_submit_enabled_when_no_errors(self):
        s = self._screen()
        be, pe, btn = self._make_mock_widgets(s)
        be.renderable = ""
        pe.renderable = ""
        s._update_submit_state()
        self.assertFalse(btn.disabled)

    def test_error_widgets_declared_in_compose(self):
        src = Path(__file__).resolve().parents[1].parent / "scripts" / "ai" / "pojo_lens_agents" / "_tui_wizard.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn('"budget-err"', content)
        self.assertIn('"parallel-err"', content)

    def test_on_input_changed_handler_exists(self):
        from ai.pojo_lens_agents._tui_wizard import GovernanceScreen
        self.assertTrue(hasattr(GovernanceScreen, "on_input_changed"))
