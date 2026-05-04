"""
tui_operator.py — Matrix/cyberpunk multi-screen operator TUI for pojolens-agents.

Screens
-------
HomeScreen            — main navigation hub with keyboard shortcuts
GoalInputScreen       — wizard step 1: enter goal
ClarificationScreen   — wizard step 1b: goal clarification (WP76: AI pending, manual context now)
EffortSelectScreen    — wizard step 2: effort / model profile
WorkspaceModeScreen   — wizard step 3: workspace isolation strategy
RunConfigScreen       — wizard step 4 (merged): LLM provider + governance
ProviderSelectScreen  — wizard step 4 (legacy): LLM provider selection
GovernanceScreen      — wizard step 5 (legacy): governance / budget / HITL / follow-up
PlanRunScreen         — wizard step 6: run generation + execution, show output
SavedPlansScreen      — browse tracked ai/orchestrator/tasks/ + runtime saved-plans/
PlanDetailsScreen     — inspect a plan file: task graph, agents, policy, actions
PlanEditorScreen      — view plan JSON + open in $EDITOR
RunPlanScreen         — execute a saved plan, stream output
ValidatePlanDialog    — enter plan path for validation (modal; ValidatePlanScreen is alias)
ValidateRunScreen     — run validation, display grouped results
RunLedgerScreen       — list retained runs, inspect / resume / retry / cleanup
RunDetailsScreen      — single retained-run summary
ResumeRetryScreen     — resume or retry a retained run
HitlGateScreen        — HITL approval / abort (WP75: live polling pending)
MemoryToolsScreen     — AI memory refresh / check / query (with query Input)
SettingsScreen        — display current config defaults
DiffReviewScreen      — workspace diff + promote flow
AgentsScreen          — inspect agent definitions and prompt sizes
SkillsScreen          — inspect skill registry and file sizes
EstimateScreen        — cost estimate / dry-run entry
EstimateResultScreen  — display estimate output

Entry point
-----------
run_operator_tui(args, *, handlers, parse_args_fn) -> int

Ownership
---------
All Textual widget/screen/app classes for the multi-screen operator console live
here.  The persistent REPL (ConsoleApp) remains in tui_console.py.
"""
from __future__ import annotations

import datetime
import traceback
from pathlib import Path
from typing import Any, Callable

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import App, ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, ScrollableContainer, Vertical
    from textual.reactive import reactive
    from textual.screen import Screen
    from textual.widgets import (
        Button,
        DataTable,
        Footer,
        Header,
        Input,
        Label,
        LoadingIndicator,
        OptionList,
        RichLog,
        Rule,
        Static,
    )
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

try:
    from pojo_lens_agents.wizard import (
        PlanPreview,
        _EFFORT_MODEL_MAP,
        _DEFAULT_PLANNER_EFFORT,
        discover_plan_previews,
        discover_saved_plans,
    )
    from pojo_lens_agents.orchestrator_contracts import (
        DEFAULT_RUNTIME_ROOT,
        DEFAULT_TASKS_DIR,
        DEFAULT_AGENTS_PATH,
    )
except ImportError:  # pragma: no cover
    DEFAULT_RUNTIME_ROOT = Path(".claude-orchestrator")
    DEFAULT_TASKS_DIR = Path("ai/orchestrator/tasks")
    DEFAULT_AGENTS_PATH = Path("ai/orchestrator/agents.json")

# ── Re-export all screen classes so external code can still import from here ──
# noqa: F401 — intentional re-exports

from pojo_lens_agents._tui_theme import (  # noqa: F401
    _MTX_VARS,
    _BANNER_ART,
    _EFFORT_OPTIONS,
    _WORKSPACE_OPTIONS,
    _HITL_OPTIONS,
    _BUDGET_BEHAVIOR_OPTIONS,
    _FOLLOW_UP_OPTIONS,
    _CLARIF_QUESTIONS,
    _WORKSPACE_WARN,
    _SHARED_CSS,
)
from pojo_lens_agents._tui_helpers import (  # noqa: F401
    _status_color,
    _load_plan_json,
    _plan_summary_rich,
    _collect_previews,
)
from pojo_lens_agents._tui_dashboard import DashboardWidget  # noqa: F401
from pojo_lens_agents._tui_home import HomeScreen  # noqa: F401
from pojo_lens_agents._tui_wizard import (  # noqa: F401
    GoalInputScreen,
    ClarificationScreen,
    EffortSelectScreen,
    WorkspaceModeScreen,
    ProviderSelectScreen,
    GovernanceScreen,
    RunConfigScreen,
)
from pojo_lens_agents._tui_wizard_run import PlanRunScreen  # noqa: F401
from pojo_lens_agents._tui_plans import (  # noqa: F401
    SavedPlansScreen,
    PlanDetailsScreen,
    PlanEditorScreen,
)
from pojo_lens_agents._tui_validate import (  # noqa: F401
    RunPlanScreen,
    ValidatePlanDialog,
    ValidatePlanScreen,
    ValidateRunScreen,
)
from pojo_lens_agents._tui_ledger import (  # noqa: F401
    RunLedgerScreen,
    RunDetailsScreen,
    ResumeRetryScreen,
)
from pojo_lens_agents._tui_gate import HitlGateScreen  # noqa: F401
from pojo_lens_agents._tui_tools import MemoryToolsScreen, SettingsScreen  # noqa: F401
from pojo_lens_agents._tui_diff import DiffReviewScreen  # noqa: F401
from pojo_lens_agents._tui_inspect import (  # noqa: F401
    AgentsScreen,
    SkillsScreen,
    PlanInspectScreen,
    ExtraToolsScreen,
    ValidationIntentsScreen,
    OutputProfilesScreen,
    FollowUpTaskScreen,
    PromptAccountingScreen,
)
from pojo_lens_agents._tui_estimate import EstimateScreen, EstimateResultScreen  # noqa: F401


# ── ErrorLog ───────────────────────────────────────────────────────────────────

class _ErrorLog:
    """Minimal file-based error log — avoids Python logging module to prevent Textual conflicts."""

    def __init__(self, path: Path) -> None:
        self._path = path

    def _write(self, level: str, msg: str) -> None:
        ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        try:
            with open(self._path, "a", encoding="utf-8") as f:
                f.write(f"{ts}  {level:<8}  {msg}\n")
        except OSError:
            pass

    def info(self, msg: str, *args: Any) -> None:
        self._write("INFO", msg % args if args else msg)

    def error(self, msg: str, *args: Any) -> None:
        self._write("ERROR", msg % args if args else msg)

    def debug(self, msg: str, *args: Any) -> None:
        self._write("DEBUG", msg % args if args else msg)


# ── OperatorApp ────────────────────────────────────────────────────────────────

class OperatorApp(App):  # type: ignore[type-arg,misc]
    """Main multi-screen operator console."""

    CSS_PATH = [Path(__file__).parent / "tui_operator.tcss"]

    TITLE    = "POJOLENS  //  OPERATOR CONSOLE"
    SUB_TITLE = "MISSION CONTROL"

    def get_css_variables(self) -> dict[str, str]:
        return {**super().get_css_variables(), **_MTX_VARS}

    def __init__(
        self,
        args: Any,
        *,
        handlers: dict[str, Any],
        parse_args_fn: Callable[..., Any],
        auto_start_wizard: bool = False,
        logger: _ErrorLog | None = None,
    ) -> None:
        super().__init__()
        self._args        = args
        self._handlers    = handlers
        self._parse_args_fn = parse_args_fn
        self._auto_start_wizard = auto_start_wizard
        self._op_logger   = logger or _ErrorLog(Path("error.log"))

        # Expose runtime paths as app attributes so screens can read them
        self._runtime_root = str(getattr(args, "runtime_root", DEFAULT_RUNTIME_ROOT) or DEFAULT_RUNTIME_ROOT)
        self._agents       = str(getattr(args, "agents",       DEFAULT_AGENTS_PATH)  or DEFAULT_AGENTS_PATH)
        self._claude_bin   = str(getattr(args, "claude_bin",   "claude")             or "claude")
        self._tasks_dir    = str(DEFAULT_TASKS_DIR)

    def on_mount(self) -> None:
        self._op_logger.info("OperatorApp mounted — runtime_root=%s", self._runtime_root)
        self.push_screen(HomeScreen())
        if self._auto_start_wizard:
            self.run_worker(self.action_new_plan, thread=False)

    def on_worker_state_changed(self, event: Any) -> None:
        try:
            from textual.worker import WorkerState
            if event.worker.state == WorkerState.ERROR:
                err = event.worker.error
                tb = "".join(traceback.format_exception(type(err), err, err.__traceback__))
                self._op_logger.error(
                    "Worker '%s' failed:\n%s",
                    event.worker.name or repr(event.worker),
                    tb,
                )
        except Exception:
            pass

    # ── Wizard flow: n → goal → effort → workspace → governance → run ──────────

    def _make_clarify_fn(self) -> Any:
        """Build a thread-safe closure that calls clarify_goal_with_claude."""
        import argparse as _ap
        runtime_root = self._runtime_root
        agents_path  = self._agents
        claude_bin   = self._claude_bin

        def _clarify(goal: str) -> dict[str, Any]:
            from pojo_lens_agents.wizard import (
                clarify_goal_with_claude,
                _EFFORT_MODEL_MAP,
                _DEFAULT_PLANNER_EFFORT,
            )
            from pojo_lens_agents.orchestrator_app import (
                ROOT,
                load_agents,
                claude_command,
                agent_payload_for_claude,
                run_process,
                extract_json_payload,
                ensure_provider_available,
            )
            try:
                from pojo_lens_agents.sdk_provider import detect_provider_mode
                _provider_mode = detect_provider_mode()
            except Exception:
                _provider_mode = "subprocess"
            _model, _effort_val = _EFFORT_MODEL_MAP.get(
                _DEFAULT_PLANNER_EFFORT, ("claude-haiku-4-5-20251001", "low")
            )
            _args = _ap.Namespace(
                runtime_root=runtime_root,
                agents=agents_path,
                claude_bin=claude_bin,
                planner_agent="planner",
            )
            _deps: dict[str, Any] = {
                "root": ROOT,
                "load_agents": load_agents,
                "ensure_claude_available": lambda b: ensure_provider_available(b, _provider_mode),
                "claude_command": claude_command,
                "agent_payload_for_claude": agent_payload_for_claude,
                "run_subprocess": run_process,
                "extract_json_payload": extract_json_payload,
            }
            return clarify_goal_with_claude(
                goal, [], args=_args, deps=_deps,
                planner_model=_model, planner_effort_val=_effort_val,
            )

        return _clarify

    def go_home(self) -> None:
        """Pop all screens back to HomeScreen."""
        while len(self.screen_stack) > 1:
            self.pop_screen()

    async def action_new_plan(self) -> None:
        from pojo_lens_agents._tui_wizard import (
            GoalInputScreen,
            ClarificationScreen,
            EffortSelectScreen,
            WorkspaceModeScreen,
            RunConfigScreen,
        )
        from pojo_lens_agents._tui_wizard_run import PlanRunScreen

        goal = await self.push_screen_wait(GoalInputScreen())
        if not goal:
            return
        clarify_fn = self._make_clarify_fn()
        clarified = await self.push_screen_wait(ClarificationScreen(goal, clarify_fn=clarify_fn))
        if clarified is None:
            return
        goal = clarified
        effort = await self.push_screen_wait(EffortSelectScreen(goal))
        if effort is None:
            return
        ws_result = await self.push_screen_wait(WorkspaceModeScreen(goal, effort))
        if ws_result is None:
            return
        ws_mode   = ws_result["mode"]
        ws_source = ws_result.get("source") or ""
        config = await self.push_screen_wait(RunConfigScreen(goal, effort, ws_mode))
        if config is None:
            return
        provider        = config.get("provider")
        hitl            = config.get("hitl",            "batch")
        max_parallel    = int(config.get("max_parallel", 2))
        budget          = config.get("budget")
        budget_behavior = config.get("budget_behavior",  "warn")
        follow_up       = config.get("follow_up",        "ignore")
        await self.push_screen_wait(PlanRunScreen(wizard_params={
            "goal":             goal,
            "effort":           effort,
            "workspace_mode":   ws_mode,
            "workspace_source": ws_source,
            "hitl":             hitl,
            "max_parallel":     max_parallel,
            "budget":           budget,
            "provider":         provider,
            "budget_behavior":  budget_behavior,
            "follow_up":        follow_up,
        }))


# ── Entry point ────────────────────────────────────────────────────────────────

def run_operator_tui(
    args: Any,
    *,
    handlers: dict[str, Any],
    parse_args_fn: Callable[..., Any],
    auto_start_wizard: bool = False,
) -> int:
    """Launch the multi-screen operator TUI. Returns exit code."""
    if TEXTUAL_IMPORT_ERROR is not None:
        raise RuntimeError(
            "Operator TUI requires textual. Install 'pojolens-agents[tui]'."
        ) from TEXTUAL_IMPORT_ERROR

    logger = _ErrorLog(Path("error.log"))

    app = OperatorApp(args, handlers=handlers, parse_args_fn=parse_args_fn,
                      auto_start_wizard=auto_start_wizard, logger=logger)
    try:
        result = app.run()
        return result if isinstance(result, int) else 0
    except Exception:
        logger.error("Uncaught exception in OperatorApp.run()\n%s", traceback.format_exc())
        raise
