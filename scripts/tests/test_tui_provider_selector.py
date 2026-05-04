"""Tests for TUI provider selector: ProviderSelectScreen, AgentsScreen provider field,
SettingsScreen providers section, default_provider_id wiring."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


# ── ProviderSelectScreen init ─────────────────────────────────────────────────

class TestProviderSelectScreenInit(unittest.TestCase):

    def _cls(self):
        from ai.pojo_lens_agents._tui_wizard import ProviderSelectScreen
        return ProviderSelectScreen

    def test_init_stores_params(self):
        cls = self._cls()
        s = cls("build a feature", "medium", "copy")
        self.assertEqual(s._goal, "build a feature")
        self.assertEqual(s._effort, "medium")
        self.assertEqual(s._workspace_mode, "copy")

    def test_provider_ids_initially_empty(self):
        cls = self._cls()
        s = cls("g", "low", "repo")
        self.assertEqual(s._provider_ids, [])

    def test_action_submit_returns_none_for_default(self):
        cls = self._cls()
        s = cls("g", "medium", "copy")
        s._provider_ids = ["(default)", "openai-compat"]
        # Simulate index 0 == default → dismiss None
        dismissed: list = []
        s.dismiss = lambda v: dismissed.append(v)
        # Simulate OptionList highlighted at 0
        mock_ol = MagicMock()
        mock_ol.highlighted = 0
        with patch.object(s, "query_one", return_value=mock_ol):
            s.action_submit()
        self.assertIsNone(dismissed[0])

    def test_action_submit_returns_provider_id(self):
        cls = self._cls()
        s = cls("g", "medium", "copy")
        s._provider_ids = ["(default)", "openai-compat"]
        dismissed: list = []
        s.dismiss = lambda v: dismissed.append(v)
        mock_ol = MagicMock()
        mock_ol.highlighted = 1
        with patch.object(s, "query_one", return_value=mock_ol):
            s.action_submit()
        self.assertEqual(dismissed[0], "openai-compat")

    def test_action_cancel_dismisses_none(self):
        cls = self._cls()
        s = cls("g", "low", "scratch")
        dismissed: list = []
        s.dismiss = lambda v: dismissed.append(v)
        s.action_cancel()
        self.assertIsNone(dismissed[0])

    def test_exported_from_tui_wizard(self):
        from ai.pojo_lens_agents._tui_wizard import ProviderSelectScreen
        self.assertTrue(callable(ProviderSelectScreen))

    def test_exported_from_tui_operator(self):
        from ai.pojo_lens_agents.tui_operator import ProviderSelectScreen
        self.assertTrue(callable(ProviderSelectScreen))


# ── Step number bump (1/5 → 1/6) ─────────────────────────────────────────────

class TestWizardStepNumbers(unittest.TestCase):

    def _src(self, module: str) -> str:
        import importlib
        spec = importlib.util.find_spec(module)
        if spec is None or spec.origin is None:
            return ""
        return Path(spec.origin).read_text(encoding="utf-8")

    def test_goal_input_step_label(self):
        src = self._src("ai.pojo_lens_agents._tui_wizard")
        self.assertIn("STEP 1 / 6", src)

    def test_clarification_step_label(self):
        src = self._src("ai.pojo_lens_agents._tui_wizard")
        self.assertIn("STEP 1b / 6", src)

    def test_effort_select_step_label(self):
        src = self._src("ai.pojo_lens_agents._tui_wizard")
        self.assertIn("STEP 2 / 6", src)

    def test_workspace_mode_step_label(self):
        src = self._src("ai.pojo_lens_agents._tui_wizard")
        self.assertIn("STEP 3 / 6", src)

    def test_provider_select_step_label(self):
        src = self._src("ai.pojo_lens_agents._tui_wizard")
        self.assertIn("STEP 4 / 6", src)

    def test_governance_step_label(self):
        src = self._src("ai.pojo_lens_agents._tui_wizard")
        self.assertIn("STEP 5 / 6", src)

    def test_plan_run_step_label(self):
        src = self._src("ai.pojo_lens_agents._tui_wizard_run")
        self.assertIn("STEP 6 / 6", src)


# ── AgentsScreen provider field ───────────────────────────────────────────────

class TestAgentsScreenProviderField(unittest.TestCase):

    def test_show_agent_detail_includes_provider_line(self):
        from ai.pojo_lens_agents._tui_inspect import AgentsScreen
        s = AgentsScreen()
        written: list[str] = []
        mock_detail = MagicMock()
        mock_detail.write = lambda t: written.append(t)
        mock_detail.clear = lambda: written.clear()
        with patch.object(s, "query_one", return_value=mock_detail):
            s._show_agent_detail({
                "name": "builder",
                "role": "coder",
                "provider": "openai-compat",
            })
        joined = "\n".join(written)
        self.assertIn("openai-compat", joined)
        self.assertIn("Provider", joined)

    def test_show_agent_detail_provider_absent_shows_dash(self):
        from ai.pojo_lens_agents._tui_inspect import AgentsScreen
        s = AgentsScreen()
        written: list[str] = []
        mock_detail = MagicMock()
        mock_detail.write = lambda t: written.append(t)
        mock_detail.clear = lambda: written.clear()
        with patch.object(s, "query_one", return_value=mock_detail):
            s._show_agent_detail({"name": "worker", "role": "tester"})
        joined = "\n".join(written)
        self.assertIn("Provider", joined)
        self.assertIn("-", joined)


# ── SettingsScreen providers section ─────────────────────────────────────────

class TestSettingsScreenProviders(unittest.TestCase):

    def test_settings_screen_has_providers_section(self):
        import importlib.util
        spec = importlib.util.find_spec("ai.pojo_lens_agents._tui_tools")
        if spec is None or spec.origin is None:
            self.skipTest("_tui_tools not found")
        src = Path(spec.origin).read_text(encoding="utf-8")
        self.assertIn("Providers", src)
        self.assertIn("get_registry", src)
        self.assertIn("rate_limit_meta", src)
        self.assertIn("model_pricing", src)

    def test_settings_screen_providers_section_has_import_guard(self):
        import importlib.util
        spec = importlib.util.find_spec("ai.pojo_lens_agents._tui_tools")
        if spec is None or spec.origin is None:
            self.skipTest("_tui_tools not found")
        src = Path(spec.origin).read_text(encoding="utf-8")
        # Guard present so missing provider_registry doesn't crash settings load
        self.assertIn("provider registry unavailable", src)


# ── default_provider_id ContextVar wiring ─────────────────────────────────────

class TestDefaultProviderIdContextVar(unittest.TestCase):

    def test_context_var_exists(self):
        from ai.pojo_lens_agents.orchestrator_app import _DEFAULT_PROVIDER_ID_CTX
        self.assertIsNotNone(_DEFAULT_PROVIDER_ID_CTX)

    def test_context_var_default_is_none(self):
        from ai.pojo_lens_agents.orchestrator_app import _DEFAULT_PROVIDER_ID_CTX
        import contextvars
        ctx = contextvars.copy_context()
        result = ctx.run(lambda: _DEFAULT_PROVIDER_ID_CTX.get())
        self.assertIsNone(result)

    def test_run_loaded_plan_accepts_default_provider_id(self):
        import inspect
        from ai.pojo_lens_agents.orchestrator_app import run_loaded_plan
        sig = inspect.signature(run_loaded_plan)
        self.assertIn("default_provider_id", sig.parameters)

    def test_cli_parser_wizard_has_default_provider_flag(self):
        from ai.pojo_lens_agents.cli_parser import parse_args
        args = parse_args(["wizard", "--default-provider", "openai-compat"])
        self.assertEqual(args.default_provider, "openai-compat")

    def test_cli_parser_wizard_default_provider_defaults_empty(self):
        from ai.pojo_lens_agents.cli_parser import parse_args
        args = parse_args(["wizard"])
        self.assertEqual(args.default_provider, "")

    def test_task_execution_uses_default_provider_id_from_deps(self):
        """default_provider_id in deps is used as fallback when task+agent have no provider."""
        import importlib.util
        spec = importlib.util.find_spec("ai.pojo_lens_agents.task_execution")
        if spec is None or spec.origin is None:
            self.skipTest("task_execution not found")
        src = Path(spec.origin).read_text(encoding="utf-8")
        self.assertIn('deps.get("default_provider_id")', src)

    def test_plan_run_screen_stores_provider(self):
        from ai.pojo_lens_agents._tui_wizard_run import PlanRunScreen
        import inspect
        sig = inspect.signature(PlanRunScreen.__init__)
        self.assertIn("provider", sig.parameters)

    def test_plan_run_screen_provider_defaults_none(self):
        from ai.pojo_lens_agents._tui_wizard_run import PlanRunScreen
        s = PlanRunScreen(
            goal="g", effort="medium", workspace_mode="copy",
            hitl="batch", max_parallel=2, budget=None,
            handlers={}, parse_args_fn=lambda x: x,
            runtime_root=".", agents=".", claude_bin="claude",
        )
        self.assertIsNone(s._provider)
