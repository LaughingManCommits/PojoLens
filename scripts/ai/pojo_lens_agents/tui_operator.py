"""
tui_operator.py — Matrix/cyberpunk multi-screen operator TUI for pojolens-agents.

Screens
-------
HomeScreen            — main navigation hub with keyboard shortcuts
GoalInputScreen       — wizard step 1: enter goal
ClarificationScreen   — wizard step 1b: goal clarification (WP76: AI pending, manual context now)
EffortSelectScreen    — wizard step 2: effort / model profile
WorkspaceModeScreen   — wizard step 3: workspace isolation strategy
GovernanceScreen      — wizard step 4: governance / budget / HITL / follow-up
PlanRunScreen         — wizard step 5: run generation + execution, show output
SavedPlansScreen      — browse tracked ai/orchestrator/tasks/ + runtime saved-plans/
PlanDetailsScreen     — inspect a plan file: task graph, agents, policy, actions
PlanEditorScreen      — view plan JSON + open in $EDITOR
RunPlanScreen         — execute a saved plan, stream output
ValidatePlanScreen    — enter plan path for validation
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
import io
import json
import sys
import threading
import traceback
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Callable

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from rich.text import Text
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


# ── Theme ──────────────────────────────────────────────────────────────────────

_MTX_VARS: dict[str, str] = {
    "bg":         "#050508",
    "bg_panel":   "#07070f",
    "bg_input":   "#04040c",
    "green":      "#00ff41",
    "green_body": "#a0ffa0",
    "green_dim":  "#1a4a2a",
    "cyan":       "#00e5ff",
    "amber":      "#ffaa00",
    "red":        "#ff2244",
    "text_dim":   "#2a5a3a",
    "border_dim": "#1a3a1a",
    "purple":     "#bf5fff",
}

_BANNER_ART = (
    " ██████╗  ██╗      █████╗  ███╗   ██╗\n"
    " ██╔══██╗ ██║     ██╔══██╗ ████╗  ██║\n"
    " ██████╔╝ ██║     ███████║ ██╔██╗ ██║\n"
    " ██╔═══╝  ██║     ██╔══██║ ██║╚██╗██║\n"
    " ██║      ███████╗██║  ██║ ██║ ╚████║\n"
    " ╚═╝      ╚══════╝╚═╝  ╚═╝ ╚═╝  ╚═══╝"
)

_EFFORT_OPTIONS = [
    ("medium", "MEDIUM  —  Sonnet     recommended · balanced"),
    ("low",    "LOW     —  Haiku      faster · cheaper"),
    ("high",   "HIGH    —  Opus       thorough · expensive  ⚠"),
]

_WORKSPACE_OPTIONS = [
    ("copy",     "COPY      —  isolated sparse filesystem copy  (safest)"),
    ("worktree", "WORKTREE  —  detached git worktree at HEAD"),
    ("repo",     "REPO      —  live repo root  ⚠ HIGH RISK"),
]

_HITL_OPTIONS = [
    ("batch",      "BATCH       —  gate before every batch with pending tasks"),
    ("on-failure", "ON-FAILURE  —  gate only after a failed batch"),
    ("always",     "ALWAYS      —  gate before every task batch (strict)"),
    ("none",       "NONE        —  disable all gates"),
]

_BUDGET_BEHAVIOR_OPTIONS = [
    ("warn", "WARN  —  continue but surface warnings when budget exceeded"),
    ("stop", "STOP  —  block unscheduled batches once budget exceeded  ✓ safe"),
]

_FOLLOW_UP_OPTIONS = [
    ("ignore", "IGNORE  —  discard followUpTasks from workers  (default)"),
    ("inject", "INJECT  —  queue followUpTasks between batches"),
]

_CLARIF_QUESTIONS = [
    "What specific files, modules, or components should agents focus on?",
    "Are there constraints, patterns, or coding standards that must be preserved?",
    "What is the success criterion — how will you know the task is done correctly?",
]

_WORKSPACE_WARN = {
    "repo": "⚠  REPO mode uses the live repo root — no isolation. Proceed only for safe read-only plans.",
    "worktree": "Worktree mode requires a clean git repo. Ensure no uncommitted changes.",
}


# ── Shared CSS ────────────────────────────────────────────────────────────────

_SHARED_CSS = """
Screen {
    background: $bg;
    color: $green_body;
}
Header {
    background: $bg_panel;
    color: $green;
    border-bottom: heavy $green 30%;
}
Footer {
    background: $bg_input;
    color: $text_dim;
    border-top: solid $green 20%;
}
.panel {
    background: $bg_panel;
    border: heavy $green 25%;
    padding: 1 2;
    margin: 0 0 1 0;
}
.panel-title {
    color: $cyan;
    text-style: bold;
    margin-bottom: 1;
}
.section-rule {
    color: $green_dim;
}
.dim {
    color: $text_dim;
}
.warn {
    color: $amber;
}
.error {
    color: $red;
}
.ok {
    color: $green;
}
.accent {
    color: $cyan;
}
Button {
    margin: 0 1;
    background: $bg_panel;
    color: $green;
    border: solid $green 50%;
    min-width: 12;
}
Button:hover {
    background: $green_dim;
    color: $green;
}
Button.-primary {
    background: $green_dim;
    color: $green;
    border: heavy $green 80%;
}
Button.-primary:hover {
    background: #1a6a3a;
}
Button.-warning {
    color: $amber;
    border: solid $amber 60%;
}
Button.-warning:hover {
    background: #2a1a00;
}
Button.-error {
    color: $red;
    border: solid $red 60%;
}
Button.-error:hover {
    background: #2a0010;
}
Input {
    background: $bg_input;
    color: $green;
    border: solid $green 40%;
}
Input:focus {
    border: solid $cyan;
    color: $cyan;
}
OptionList {
    background: $bg;
    border: solid $green_dim;
    color: $green_body;
}
OptionList > .option-list--option-highlighted {
    background: $green_dim;
    color: $green;
    text-style: bold;
}
DataTable {
    background: $bg_panel;
    color: $green_body;
}
DataTable > .datatable--header {
    background: $bg_input;
    color: $cyan;
    text-style: bold;
}
DataTable > .datatable--cursor {
    background: $green_dim;
}
DataTable > .datatable--even-row {
    background: $bg_panel;
}
DataTable > .datatable--odd-row {
    background: $bg;
}
RichLog {
    background: $bg;
    padding: 0 1;
    color: $green_body;
    scrollbar-color: $green 25%;
    scrollbar-background: $bg;
}
LoadingIndicator {
    color: $green;
    background: $bg;
}
"""


# ── Helpers ────────────────────────────────────────────────────────────────────

def _status_color(status: str) -> str:
    return {
        "completed":       "#00ff41",
        "failed":          "#ff2244",
        "blocked":         "#ff2244",
        "running":         "#ffaa00",
        "retry":           "#ffaa00",
        "planned":         "#2a5a3a",
        "pending":         "#2a5a3a",
        "aborted":         "#ff2244",
        "budget_exceeded": "#ffaa00",
        "skipped":         "#2a5a3a",
        "injected":        "#00e5ff",
        "saved":           "#00e5ff",
        "stopped":         "#2a5a3a",
    }.get(status, "#a0ffa0")


def _load_plan_json(path: str) -> dict[str, Any] | None:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except Exception:
        return None


def _plan_summary_rich(plan_data: dict[str, Any]) -> list[str]:
    """Rich-markup summary lines for a plan JSON dict."""
    lines: list[str] = []
    name   = str(plan_data.get("name") or plan_data.get("planName") or "unnamed")
    goal   = str(plan_data.get("goal") or "").strip()
    tasks  = list(plan_data.get("tasks") or [])
    rp     = plan_data.get("runPolicy") or {}

    lines.append(f"[bold #00e5ff]Plan   :[/] [#a0ffa0]{name}[/]")
    if goal:
        lines.append(f"[bold #00e5ff]Goal   :[/] [dim]{goal[:120]}[/]")
    lines.append(f"[bold #00e5ff]Tasks  :[/] {len(tasks)}")

    agents = sorted({str(t.get("agent") or "") for t in tasks if t.get("agent")})
    if agents:
        lines.append(f"[bold #00e5ff]Agents :[/] {', '.join(agents)}")

    profiles = sorted({str(t.get("modelProfile") or "") for t in tasks if t.get("modelProfile")})
    if profiles:
        lines.append(f"[bold #00e5ff]Profile:[/] {', '.join(profiles)}")

    ws_modes = sorted({str(t.get("workspaceMode") or "") for t in tasks if t.get("workspaceMode")})
    if ws_modes:
        lines.append(f"[bold #00e5ff]WS Mode:[/] {', '.join(ws_modes)}")

    if rp:
        budget = rp.get("runBudgetUsd")
        hitl   = rp.get("hitlMode")
        if budget is not None:
            lines.append(f"[bold #00e5ff]Budget :[/] [#ffaa00]${budget}[/]")
        if hitl:
            lines.append(f"[bold #00e5ff]HITL   :[/] {hitl}")

    if tasks:
        lines.append("")
        lines.append("[bold #00e5ff]═══ Task Graph ═══[/]")
        for t in tasks[:20]:
            tid   = str(t.get("id") or t.get("taskId") or "?")
            title = str(t.get("title") or t.get("name") or tid)
            deps  = list(t.get("dependencies") or [])
            agent = str(t.get("agent") or "-")
            dep_s = f" [dim]← {', '.join(deps)}[/]" if deps else ""
            lines.append(
                f"  [#00ff41]▸[/] [bold #a0ffa0]{tid}[/]: {title[:50]}"
                f" [[dim]{agent}[/]]{dep_s}"
            )
        if len(tasks) > 20:
            lines.append(f"  [dim]... +{len(tasks) - 20} more tasks[/]")

    # Aggregate read / write paths and skills across all tasks
    all_read: list[str] = []
    all_write: list[str] = []
    all_skills: set[str] = set()
    all_hints: list[str] = []
    for t in tasks:
        all_read.extend(list(t.get("readPaths") or []))
        all_write.extend(list(t.get("writePaths") or []))
        all_skills.update(list(t.get("skills") or []))
        for h in list(t.get("validationHints") or []):
            vh = h if isinstance(h, str) else str(h.get("hint") or h.get("description") or h)
            if vh:
                all_hints.append(vh)
    sc_block = plan_data.get("sharedContext") or {}
    all_read.extend(list(sc_block.get("readPaths") or []))

    if all_read:
        unique_read = sorted(set(all_read))
        lines.append("")
        lines.append("[bold #00e5ff]═══ Read Paths ═══[/]")
        for p in unique_read[:12]:
            lines.append(f"  [dim #00e5ff]→[/] {p}")
        if len(unique_read) > 12:
            lines.append(f"  [dim]+{len(unique_read)-12} more[/]")

    if all_write:
        unique_write = sorted(set(all_write))
        lines.append("")
        lines.append("[bold #00e5ff]═══ Write Paths ═══[/]")
        for p in unique_write[:12]:
            lines.append(f"  [#ffaa00]→[/] {p}")
        if len(unique_write) > 12:
            lines.append(f"  [dim]+{len(unique_write)-12} more[/]")

    if all_skills:
        lines.append("")
        lines.append(f"[bold #00e5ff]Skills:[/] {', '.join(sorted(all_skills))}")
        if len(all_skills) > 4:
            lines.append(f"  [#ffaa00]⚠ {len(all_skills)} skills — check stack limit (5 max)[/]")

    if all_hints:
        lines.append("")
        lines.append("[bold #00e5ff]═══ Validation Hints ═══[/]")
        for h in all_hints[:8]:
            lines.append(f"  [#00ff41]✓[/] {h}")
        if len(all_hints) > 8:
            lines.append(f"  [dim]+{len(all_hints)-8} more hints[/]")

    # Full run policy detail
    rp2 = plan_data.get("runPolicy") or {}
    if rp2:
        lines.append("")
        lines.append("[bold #00e5ff]═══ Run Policy ═══[/]")
        for key2, label2 in [
            ("runBudgetUsd",      "Budget USD"),
            ("budgetBehavior",    "Budget Behavior"),
            ("artifactBehavior",  "Artifact Behavior"),
            ("hitlMode",          "HITL Mode"),
            ("followUpBehavior",  "Follow-Up"),
            ("maxTaskStdoutBytes","Max Stdout"),
            ("maxTaskStderrBytes","Max Stderr"),
            ("maxTaskResultBytes","Max Result"),
        ]:
            val2 = rp2.get(key2)
            if val2 is not None:
                lines.append(f"  [#00e5ff]{label2}:[/] {val2}")

    # Protection reminder
    lines.append("")
    lines.append("[dim #2a5a3a]── Protected paths (workers must not edit) ──[/]")
    for pp in ["TODO.md", "ai/state/*", "ai/log/*", "ai/indexes/*"]:
        lines.append(f"  [dim #ff2244]⚠[/] [dim]{pp}[/]")

    return lines


def _collect_previews(
    runtime_root: Path,
    tasks_dir: Path,
) -> list[PlanPreview]:
    previews: list[PlanPreview] = []
    try:
        previews.extend(discover_plan_previews(tasks_dir))
    except Exception:
        pass
    try:
        previews.extend(discover_saved_plans(runtime_root))
    except Exception:
        pass
    return previews


# ── HomeScreen ─────────────────────────────────────────────────────────────────

class HomeScreen(Screen):  # type: ignore[type-arg,misc]
    """Main navigation hub."""

    BINDINGS = [
        Binding("n", "new_plan",    "New Plan",    show=False),
        Binding("s", "saved_plans", "Saved Plans", show=False),
        Binding("r", "runs",        "Runs",        show=False),
        Binding("l", "ledger",      "Ledger",      show=False),
        Binding("v", "validate",    "Validate",    show=False),
        Binding("d", "dry_run",     "Dry Run",     show=False),
        Binding("p", "promote",     "Promote",     show=False),
        Binding("a", "agents",      "Agents",      show=False),
        Binding("k", "skills",      "Skills",      show=False),
        Binding("m", "memory",      "Memory",      show=False),
        Binding("t", "settings",    "Settings",    show=False),
        Binding("q", "quit_app",    "Quit",        show=False),
        Binding("enter", "activate_item", "Select", show=False),
    ]

    _MENU_ITEMS = [
        ("n", "CREATE NEW PLAN",  "Generate an AI task plan (primary path)"),
        ("s", "SAVED PLANS",      "Browse tracked & user-saved plans"),
        ("──", None, None),
        ("r", "RUNS",             "Run / Resume / Retry retained runs"),
        ("l", "LEDGER",           "Run history, cost summary, status"),
        ("v", "VALIDATE",         "Validate a plan file"),
        ("d", "DRY RUN",          "Cost estimate / dry-run without execution"),
        ("p", "PROMOTE",          "Review diffs and promote changes"),
        ("──", None, None),
        ("a", "AGENTS",           "Inspect agent definitions and prompt sizes"),
        ("k", "SKILLS",           "Inspect skill registry and file sizes"),
        ("m", "MEMORY TOOLS",     "Refresh / query AI memory"),
        ("t", "SETTINGS",         "View configuration defaults"),
        ("──", None, None),
        ("q", "QUIT",             "Exit operator console"),
    ]

    CSS = """
    HomeScreen {
        background: $bg;
    }
    #outer {
        align: center middle;
        height: 1fr;
        padding: 0 2;
    }
    #banner-box {
        width: 100%;
        max-width: 70;
        background: $bg_panel;
        border: heavy $green 25%;
        padding: 1 2;
        margin-bottom: 1;
        align: center middle;
        height: auto;
    }
    #banner-art {
        color: $green;
        text-style: bold;
        text-align: center;
    }
    #tagline {
        color: $cyan;
        text-align: center;
        text-style: italic;
        margin-top: 1;
    }
    #menu-box {
        width: 100%;
        max-width: 70;
        background: $bg_panel;
        border: heavy $green 20%;
        padding: 1 3;
        height: auto;
    }
    #menu-title {
        color: $cyan;
        text-style: bold;
        text-align: center;
        margin-bottom: 1;
        border-bottom: solid $green_dim;
    }
    .menu-row {
        height: 1;
        margin: 0;
    }
    .menu-divider {
        color: $text_dim;
        height: 1;
        margin: 0;
    }
    .menu-key {
        width: 6;
        color: $green;
        text-style: bold;
    }
    .menu-label {
        width: 22;
        color: $green;
        text-style: bold;
    }
    .menu-desc {
        color: $green_body;
    }
    #status-bar {
        height: 1;
        padding: 0 2;
        color: $text_dim;
        background: $bg_input;
    }
    """

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        with ScrollableContainer(id="outer"):
            with Container(id="banner-box"):
                yield Static(_BANNER_ART, id="banner-art")
                yield Static(
                    "// AI MULTI-AGENT OPERATOR CONSOLE  //  MISSION CONTROL",
                    id="tagline",
                )
            with Container(id="menu-box"):
                yield Static("[ MAIN NAVIGATION ]", id="menu-title")
                for key, label, desc in self._MENU_ITEMS:
                    if label is None:
                        yield Static(
                            "─" * 46,
                            classes="menu-divider",
                        )
                    else:
                        with Horizontal(classes="menu-row"):
                            yield Static(f"[{key.upper()}]", classes="menu-key")
                            yield Static(label, classes="menu-label")
                            yield Static(desc, classes="menu-desc")
        yield Static(
            "[dim #2a5a3a]KEYBOARD: [N] new  [S] saved  [R] runs  [L] ledger  "
            "[V] validate  [D] dry-run  [A] agents  [K] skills  [M] memory  [T] settings  [Q] quit[/]",
            id="status-bar",
        )
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  OPERATOR CONSOLE"
        self.app.sub_title = "MISSION CONTROL"

    # ── Actions ────────────────────────────────────────────────────────────────
    # action_new_plan is intentionally NOT defined here — the binding bubbles to
    # OperatorApp.action_new_plan which uses push_screen_wait to chain all wizard steps.

    def action_saved_plans(self) -> None:
        self.app.push_screen(SavedPlansScreen())  # type: ignore[attr-defined]

    def action_runs(self) -> None:
        self.app.push_screen(RunLedgerScreen(mode="runs"))  # type: ignore[attr-defined]

    def action_ledger(self) -> None:
        self.app.push_screen(RunLedgerScreen(mode="ledger"))  # type: ignore[attr-defined]

    def action_validate(self) -> None:
        self.app.push_screen(ValidatePlanScreen())  # type: ignore[attr-defined]

    def action_dry_run(self) -> None:
        self.app.push_screen(EstimateScreen())  # type: ignore[attr-defined]

    def action_promote(self) -> None:
        self.app.push_screen(RunLedgerScreen(mode="promote"))  # type: ignore[attr-defined]

    def action_agents(self) -> None:
        self.app.push_screen(AgentsScreen())  # type: ignore[attr-defined]

    def action_skills(self) -> None:
        self.app.push_screen(SkillsScreen())  # type: ignore[attr-defined]

    def action_memory(self) -> None:
        self.app.push_screen(MemoryToolsScreen())  # type: ignore[attr-defined]

    def action_settings(self) -> None:
        self.app.push_screen(SettingsScreen())  # type: ignore[attr-defined]

    def action_quit_app(self) -> None:
        self.app.exit(0)  # type: ignore[attr-defined]

    def action_activate_item(self) -> None:
        self.run_worker(self.app.action_new_plan, thread=False)  # type: ignore[attr-defined]


# ── GoalInputScreen ────────────────────────────────────────────────────────────

class GoalInputScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 1: enter goal or leave blank for saved plans."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
    ]

    CSS = """
    GoalInputScreen {
        align: center middle;
    }
    #card {
        width: 76;
        height: auto;
        border: heavy $green 30%;
        background: $bg_panel;
        padding: 2 3;
    }
    #card-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #desc {
        color: $green_body;
        margin-bottom: 1;
    }
    #hint {
        color: $text_dim;
        margin-top: 1;
        margin-bottom: 1;
    }
    #btns {
        height: auto;
        align: right middle;
        margin-top: 1;
    }
    """

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 1 / 5 ]  ENTER GOAL",
                id="card-title",
            )
            yield Static(
                "Describe what you want the AI agents to accomplish.\n"
                "Be specific: mention files, technologies, and desired outcome.",
                id="desc",
            )
            yield Input(
                placeholder="e.g.  Add dark-mode toggle to the React settings panel",
                id="goal-input",
            )
            yield Static(
                "[dim]Tip: leave blank and press CONTINUE to browse saved plans.[/]",
                id="hint",
            )
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("CANCEL", id="btn-cancel")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#goal-input", Input).focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self._submit()
        elif event.button.id == "btn-cancel":
            self.action_cancel()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        self._submit()

    def _submit(self) -> None:
        goal = self.query_one("#goal-input", Input).value.strip()
        if not goal:
            self.app.push_screen(SavedPlansScreen())  # type: ignore[attr-defined]
            self.dismiss(None)
        else:
            self.dismiss(goal)

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── ClarificationScreen ────────────────────────────────────────────────────────

class ClarificationScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 1b: goal clarification loop.

    NOTE (WP76): AI backend not yet wired.  Until then the screen collects
    manual context that gets appended to the goal string before execution.
    """

    BINDINGS = [
        Binding("escape", "skip_all", "Skip",   show=True),
        Binding("enter",  "next_q",   "Submit", show=False),
    ]

    CSS = """
    ClarificationScreen {
        align: center middle;
    }
    #card {
        width: 82;
        height: auto;
        border: heavy $green 30%;
        background: $bg_panel;
        padding: 2 3;
    }
    #cl-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #cl-wip {
        color: $amber;
        margin-bottom: 1;
    }
    #cl-goal-box {
        background: $bg_input;
        border: solid $green_dim;
        padding: 0 1;
        margin-bottom: 1;
        height: auto;
    }
    #cl-goal-label { color: $cyan; }
    #cl-goal-text  { color: $green_body; }
    #cl-separator  { color: $green_dim; margin: 1 0; }
    #cl-q-label {
        color: $cyan;
        text-style: bold;
        margin-bottom: 0;
    }
    #cl-q-text {
        color: $green_body;
        margin-bottom: 1;
    }
    #cl-progress {
        color: $text_dim;
        margin-bottom: 1;
    }
    #cl-answers {
        background: $bg_input;
        border: solid $green_dim;
        padding: 0 1;
        height: auto;
        margin-bottom: 1;
    }
    #cl-input {
        margin-top: 1;
    }
    #btns {
        height: auto;
        align: right middle;
        margin-top: 1;
    }
    """

    def __init__(self, goal: str) -> None:
        super().__init__()
        self._goal        = goal
        self._q_idx       = 0
        self._answers: list[tuple[str, str]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 1b / 5 ]  GOAL CLARIFICATION",
                id="cl-title",
            )
            yield Static(
                "⚠  AI backend not yet wired (WP76) — "
                "your answers are appended as context to the goal.",
                id="cl-wip",
            )
            with Container(id="cl-goal-box"):
                yield Static("GOAL:", id="cl-goal-label")
                yield Static(self._goal[:160], id="cl-goal-text")
            yield Rule(id="cl-separator")
            yield Static("", id="cl-q-label")
            yield Static("", id="cl-q-text")
            yield Static("", id="cl-progress")
            with ScrollableContainer(id="cl-answers"):
                yield Static("[ no answers yet ]", id="cl-answers-content")
            yield Input(
                placeholder="Your answer — or leave blank and press NEXT to skip this question",
                id="cl-input",
            )
            with Horizontal(id="btns"):
                yield Button("NEXT  →",  id="btn-next",  variant="primary")
                yield Button("SKIP ALL", id="btn-skip")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  CLARIFICATION"
        self.app.sub_title = "GOAL REFINEMENT"
        self._refresh_question()
        self.query_one("#cl-input", Input).focus()

    def _refresh_question(self) -> None:
        total = len(_CLARIF_QUESTIONS)
        idx   = self._q_idx
        if idx >= total:
            return
        self.query_one("#cl-q-label", Static).update(
            f"[bold #00e5ff]Question {idx + 1} of {total}:[/]"
        )
        self.query_one("#cl-q-text", Static).update(
            f"[#a0ffa0]{_CLARIF_QUESTIONS[idx]}[/]"
        )
        self.query_one("#cl-progress", Static).update(
            f"[dim]{'▮' * (idx + 1)}{'▯' * (total - idx - 1)}  {idx + 1}/{total}[/]"
        )
        self.query_one("#cl-input", Input).value = ""

    def _refresh_answers(self) -> None:
        if not self._answers:
            self.query_one("#cl-answers-content", Static).update("[ no answers yet ]")
            return
        lines = []
        for q, a in self._answers:
            lines.append(f"[dim #00e5ff]Q:[/] [dim]{q[:60]}[/]")
            lines.append(f"  [#00ff41]A:[/] {a[:120]}")
        self.query_one("#cl-answers-content", Static).update("\n".join(lines))

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-next":
            self.action_next_q()
        elif event.button.id == "btn-skip":
            self.action_skip_all()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        self.action_next_q()

    def action_next_q(self) -> None:
        answer = self.query_one("#cl-input", Input).value.strip()
        if answer:
            self._answers.append((_CLARIF_QUESTIONS[self._q_idx], answer))
            self._refresh_answers()
        self._q_idx += 1
        if self._q_idx >= len(_CLARIF_QUESTIONS):
            self._finish()
        else:
            self._refresh_question()

    def _finish(self) -> None:
        if self._answers:
            context = "; ".join(a for _, a in self._answers)
            enhanced = f"{self._goal}. Additional context: {context}"
        else:
            enhanced = self._goal
        self.dismiss(enhanced)

    def action_skip_all(self) -> None:
        self.dismiss(self._goal)


# ── EffortSelectScreen ─────────────────────────────────────────────────────────

class EffortSelectScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 2: choose planning effort / model profile."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
        Binding("enter",  "submit", "Select", show=True),
    ]

    CSS = """
    EffortSelectScreen {
        align: center middle;
    }
    #card {
        width: 72;
        height: auto;
        border: heavy $green 30%;
        background: $bg_panel;
        padding: 2 3;
    }
    #card-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #desc {
        color: $green_body;
        margin-bottom: 1;
    }
    OptionList {
        height: 7;
        margin-bottom: 1;
    }
    #cost-note {
        color: $amber;
        margin-top: 1;
        margin-bottom: 1;
    }
    #btns {
        height: auto;
        align: right middle;
        margin-top: 1;
    }
    """

    def __init__(self, goal: str) -> None:
        super().__init__()
        self._goal = goal

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 2 / 5 ]  PLANNER EFFORT",
                id="card-title",
            )
            yield Static(
                "Choose the Claude model for plan generation.\n"
                "This controls cost, depth, and quality of the generated plan.",
                id="desc",
            )
            yield OptionList(
                *[label for _, label in _EFFORT_OPTIONS],
                id="effort-list",
            )
            yield Static(
                "⚠  Opus (High) is expensive. Prefer Medium for most tasks.",
                id="cost-note",
            )
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#effort-list", OptionList).highlighted = 0

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self.action_submit()
        elif event.button.id == "btn-back":
            self.action_cancel()

    def action_submit(self) -> None:
        idx = int(self.query_one("#effort-list", OptionList).highlighted or 0)
        effort, _ = _EFFORT_OPTIONS[idx]
        self.dismiss(effort)

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── WorkspaceModeScreen ────────────────────────────────────────────────────────

class WorkspaceModeScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 3: choose workspace isolation strategy."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
        Binding("enter",  "submit", "Select", show=True),
    ]

    CSS = """
    WorkspaceModeScreen {
        align: center middle;
    }
    #card {
        width: 76;
        height: auto;
        border: heavy $green 30%;
        background: $bg_panel;
        padding: 2 3;
    }
    #card-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #desc {
        color: $green_body;
        margin-bottom: 1;
    }
    OptionList {
        height: 7;
        margin-bottom: 1;
    }
    #ws-warning {
        color: $amber;
        margin-top: 1;
        margin-bottom: 1;
    }
    #btns {
        height: auto;
        align: right middle;
        margin-top: 1;
    }
    """

    def __init__(self, goal: str, effort: str) -> None:
        super().__init__()
        self._goal = goal
        self._effort = effort

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 3 / 5 ]  WORKSPACE MODE",
                id="card-title",
            )
            yield Static(
                "Choose how worker agents access the repository.\n"
                "COPY is the safest default — workers see only declared readPaths.",
                id="desc",
            )
            yield OptionList(
                *[label for _, label in _WORKSPACE_OPTIONS],
                id="ws-list",
            )
            yield Static("", id="ws-warning")
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#ws-list", OptionList).highlighted = 0

    def on_option_list_option_highlighted(self, _: OptionList.OptionHighlighted) -> None:
        idx = int(self.query_one("#ws-list", OptionList).highlighted or 0)
        mode, _ = _WORKSPACE_OPTIONS[idx]
        warn_text = _WORKSPACE_WARN.get(mode, "")
        self.query_one("#ws-warning", Static).update(
            f"[#ffaa00]{warn_text}[/]" if warn_text else ""
        )

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self.action_submit()
        elif event.button.id == "btn-back":
            self.action_cancel()

    def action_submit(self) -> None:
        idx = int(self.query_one("#ws-list", OptionList).highlighted or 0)
        mode, _ = _WORKSPACE_OPTIONS[idx]
        self.dismiss(mode)

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── GovernanceScreen ───────────────────────────────────────────────────────────

class GovernanceScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 4: configure governance — HITL, budget, parallelism."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
    ]

    CSS = """
    GovernanceScreen {
        align: center middle;
    }
    #card {
        width: 84;
        height: auto;
        max-height: 50;
        border: heavy $green 30%;
        background: $bg_panel;
        padding: 2 3;
        overflow-y: auto;
    }
    #card-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    .field-label {
        color: $cyan;
        margin-top: 1;
    }
    .field-hint {
        color: $text_dim;
        margin-bottom: 0;
    }
    #hitl-list         { height: 8; margin-bottom: 0; }
    #budget-behavior-list { height: 4; margin-bottom: 0; }
    #followup-list     { height: 4; margin-bottom: 0; }
    #budget-input {
        width: 20;
    }
    #parallel-input {
        width: 10;
    }
    Rule {
        color: $green_dim;
        margin-top: 1;
    }
    #btns {
        height: auto;
        align: right middle;
        margin-top: 2;
    }
    """

    def __init__(self, goal: str, effort: str, workspace_mode: str) -> None:
        super().__init__()
        self._goal = goal
        self._effort = effort
        self._workspace_mode = workspace_mode

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 4 / 5 ]  GOVERNANCE  (press CONTINUE to accept defaults)",
                id="card-title",
            )
            yield Rule(id="gov-rule")
            yield Static("HITL Gate Mode:", classes="field-label")
            yield Static(
                "always=every batch · batch=pending-tasks only · on-failure=after fail · none=disabled",
                classes="field-hint",
            )
            yield OptionList(
                *[label for _, label in _HITL_OPTIONS],
                id="hitl-list",
            )
            yield Static("Budget Behavior:", classes="field-label")
            yield Static(
                "warn=surface warning but continue · stop=block batches once limit hit",
                classes="field-hint",
            )
            yield OptionList(
                *[label for _, label in _BUDGET_BEHAVIOR_OPTIONS],
                id="budget-behavior-list",
            )
            yield Static("Follow-Up Task Behavior:", classes="field-label")
            yield Static(
                "ignore=discard worker followUpTasks · inject=queue them between batches",
                classes="field-hint",
            )
            yield OptionList(
                *[label for _, label in _FOLLOW_UP_OPTIONS],
                id="followup-list",
            )
            yield Rule()
            yield Static("Run Budget USD  (blank = unlimited):", classes="field-label")
            yield Input(placeholder="e.g.  0.50", id="budget-input")
            yield Static("Max Parallel Tasks:", classes="field-label")
            yield Input(value="2", id="parallel-input")
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#hitl-list",          OptionList).highlighted = 0
        self.query_one("#budget-behavior-list", OptionList).highlighted = 0
        self.query_one("#followup-list",       OptionList).highlighted = 0

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self._submit()
        elif event.button.id == "btn-back":
            self.action_cancel()

    def _submit(self) -> None:
        hitl_idx = int(self.query_one("#hitl-list", OptionList).highlighted or 0)
        hitl_mode, _ = _HITL_OPTIONS[hitl_idx]
        bb_idx = int(self.query_one("#budget-behavior-list", OptionList).highlighted or 0)
        budget_behavior, _ = _BUDGET_BEHAVIOR_OPTIONS[bb_idx]
        fu_idx = int(self.query_one("#followup-list", OptionList).highlighted or 0)
        follow_up, _ = _FOLLOW_UP_OPTIONS[fu_idx]
        budget_raw   = self.query_one("#budget-input",   Input).value.strip()
        parallel_raw = self.query_one("#parallel-input", Input).value.strip()
        try:
            max_parallel = max(1, int(parallel_raw))
        except (ValueError, TypeError):
            max_parallel = 2
        try:
            budget = float(budget_raw) if budget_raw else None
        except (ValueError, TypeError):
            budget = None
        self.dismiss({
            "hitl":            hitl_mode,
            "budget_behavior": budget_behavior,
            "follow_up":       follow_up,
            "max_parallel":    max_parallel,
            "budget":          budget,
        })

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── PlanRunScreen ──────────────────────────────────────────────────────────────

class PlanRunScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 5: generate plan + run — shows live log of wizard output."""

    BINDINGS = [
        Binding("escape", "dismiss_screen", "Back / Stop", show=True),
    ]

    CSS = """
    PlanRunScreen {
        background: $bg;
    }
    #header-strip {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #run-title {
        color: $cyan;
        text-style: bold;
    }
    #run-params {
        color: $text_dim;
        margin-top: 1;
    }
    #status-line {
        color: $green;
        margin-top: 1;
    }
    #log {
        height: 1fr;
        background: $bg;
        padding: 0 1;
    }
    #bottom-bar {
        height: 3;
        background: $bg_input;
        border-top: solid $green 25%;
        align: right middle;
        padding: 0 2;
    }
    """

    _status: reactive[str] = reactive("initializing...")

    def __init__(
        self,
        goal: str,
        effort: str,
        workspace_mode: str,
        hitl: str,
        max_parallel: int,
        budget: float | None,
        *,
        budget_behavior: str = "warn",
        follow_up: str = "ignore",
        handlers: dict[str, Any],
        parse_args_fn: Callable[..., Any],
        runtime_root: str,
        agents: str,
        claude_bin: str,
    ) -> None:
        super().__init__()
        self._goal            = goal
        self._effort          = effort
        self._workspace_mode  = workspace_mode
        self._hitl            = hitl
        self._max_parallel    = max_parallel
        self._budget          = budget
        self._budget_behavior = budget_behavior
        self._follow_up       = follow_up
        self._handlers        = handlers
        self._parse_args_fn   = parse_args_fn
        self._runtime_root    = runtime_root
        self._agents          = agents
        self._claude_bin      = claude_bin
        self._done            = False

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="header-strip"):
            yield Static(
                "[ STEP 5 / 5 ]  GENERATING  +  EXECUTING PLAN",
                id="run-title",
            )
            yield Static(
                f"goal: {self._goal[:80]}  "
                f"effort: {self._effort}  "
                f"workspace: {self._workspace_mode}  "
                f"hitl: {self._hitl}  "
                f"parallel: {self._max_parallel}",
                id="run-params",
            )
            yield Static("[bold #00ff41][ CONSTRUCT ] assembling prompt matrix...[/]", id="status-line")
        yield RichLog(id="log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="bottom-bar"):
            yield Button("BACK TO HOME", id="btn-home")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  PLAN RUN"
        self.app.sub_title = "CONSTRUCTING..."
        self.run_worker(self._run_wizard, thread=True, name="wizard-run")

    def watch__status(self, status: str) -> None:
        self.query_one("#status-line", Static).update(
            f"[bold #00ff41][ SIGNAL ] {status}[/]"
        )

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-home":
            self.action_dismiss_screen()

    def action_dismiss_screen(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(
            lambda: self.query_one("#log", RichLog).write(text)
        )

    def _set_status(self, status: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_status", status))

    def _run_wizard(self) -> None:
        import io as _io

        self._set_status("building wizard args...")
        self._log("[dim #2a5a3a]═══ WIZARD START ═══[/]")

        # Build arg list for parse_args_fn
        arg_list = ["wizard", "--json"]
        if self._goal:
            arg_list += ["--goal", self._goal]
        if self._effort:
            arg_list += ["--planner-effort", self._effort]
        if self._workspace_mode:
            arg_list += ["--workspace-mode", self._workspace_mode]
        if self._hitl:
            arg_list += ["--hitl", self._hitl]
        arg_list += ["--max-parallel", str(self._max_parallel)]
        if self._budget is not None:
            arg_list += ["--run-budget-usd", str(self._budget)]
        if self._budget_behavior and self._budget_behavior != "warn":
            arg_list += ["--budget-behavior", self._budget_behavior]
        if self._follow_up and self._follow_up != "ignore":
            arg_list += ["--follow-up-behavior", self._follow_up]
        if self._agents:
            arg_list += ["--agents", self._agents]
        if self._runtime_root:
            arg_list += ["--runtime-root", self._runtime_root]
        if self._claude_bin:
            arg_list += ["--provider-bin", self._claude_bin]

        self._set_status("parsing arguments...")
        try:
            args = self._parse_args_fn(arg_list)
        except SystemExit as exc:
            self._log(f"[#ff2244]>> arg parse failed: {exc}[/]")
            self._set_status("failed — arg parse error")
            return
        except Exception as exc:
            self._log(f"[#ff2244]>> error building args: {exc}[/]")
            self._set_status("failed — args error")
            return

        self._set_status("[ CONSTRUCT ] invoking wizard agent...")
        self._log("[#00e5ff][ SIGNAL ] wizard agent dispatched[/]")

        handler = self._handlers.get("wizard")
        if handler is None:
            self._log("[#ff2244]>> wizard handler not found in handlers dict[/]")
            self._set_status("failed — no wizard handler")
            return

        # Capture stdout from the wizard worker
        buf = _io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]>> wizard error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        # Show JSON summary
        if payload:
            self._log("")
            self._log("[dim #2a5a3a]═══ RESULT PAYLOAD ═══[/]")
            status = str(payload.get("status") or "")
            if status:
                sc = _status_color(status)
                self._log(f"[bold {sc}]status: {status}[/]")

            steps = list(payload.get("steps") or [])
            for step in steps:
                sname   = str(step.get("name", ""))
                sstatus = str(step.get("status", ""))
                sc      = _status_color(sstatus)
                self._log(
                    f"  [#00e5ff]▸[/] {sname}: [{sc}]{sstatus}[/{sc}]"
                )

            plan_path = str(payload.get("planPath") or payload.get("savedPlanPath") or "")
            if plan_path:
                self._log(f"[#00ff41]plan: {plan_path}[/]")

            saved = str(payload.get("savedPlanPath") or "")
            if saved:
                self._log(f"[#00e5ff]saved to: {saved}[/]")

        self._set_status("[ EXIT ] run complete")
        self._log("")
        self._log("[bold #00ff41]═══ WIZARD COMPLETE — press BACK TO HOME ═══[/]")
        self._done = True
        self.app.call_from_thread(
            lambda: setattr(self.app, "sub_title", "COMPLETE")  # type: ignore[attr-defined]
        )


# ── SavedPlansScreen ───────────────────────────────────────────────────────────

class SavedPlansScreen(Screen):  # type: ignore[type-arg,misc]
    """Browse tracked and user-saved plans."""

    BINDINGS = [
        Binding("escape", "go_back", "Back", show=True),
        Binding("r", "action_run",      "Run",      show=True),
        Binding("v", "action_validate", "Validate", show=True),
        Binding("d", "action_details",  "Details",  show=True),
        Binding("e", "action_edit",     "Edit",     show=True),
    ]

    CSS = """
    SavedPlansScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #screen-title {
        color: $cyan;
        text-style: bold;
    }
    #screen-hint {
        color: $text_dim;
    }
    #search-bar {
        height: 3;
        background: $bg_input;
        border-bottom: solid $green 20%;
        padding: 0 1;
    }
    #search-input {
        width: 1fr;
    }
    #plans-table {
        height: 1fr;
    }
    #empty-notice {
        color: $text_dim;
        text-align: center;
        margin: 4;
    }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    def __init__(self) -> None:
        super().__init__()
        self._previews: list[PlanPreview] = []
        self._visible_previews: list[PlanPreview] = []
        self._filter: str = ""

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ SAVED PLANS ]  Browse tracked + saved plans", id="screen-title")
            yield Static(
                "[R] Run  [V] Validate  [D] Details  [E] Edit  [Esc] Back",
                id="screen-hint",
            )
        with Horizontal(id="search-bar"):
            yield Static("FILTER: ", classes="dim")
            yield Input(placeholder="type to filter by name or goal...", id="search-input")
        yield DataTable(id="plans-table")
        yield Static("", id="empty-notice")
        with Horizontal(id="action-bar"):
            yield Button("RUN",      id="btn-run",      variant="primary")
            yield Button("DETAILS",  id="btn-details")
            yield Button("VALIDATE", id="btn-validate")
            yield Button("EDIT",     id="btn-edit")
            yield Button("BACK",     id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  SAVED PLANS"
        self.app.sub_title = "PLAN BROWSER"
        table = self.query_one("#plans-table", DataTable)
        table.cursor_type = "row"
        table.add_column("Name",    key="name",    width=30)
        table.add_column("Goal",    key="goal",    width=40)
        table.add_column("Tasks",   key="tasks",   width=7)
        table.add_column("Source",  key="source",  width=12)
        self.run_worker(self._load_plans, thread=True, name="load-plans")

    def _load_plans(self) -> None:
        try:
            runtime_root = Path(str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT)))
            tasks_dir    = Path(str(getattr(self.app, "_tasks_dir",    DEFAULT_TASKS_DIR)))
            previews     = _collect_previews(runtime_root, tasks_dir)
        except Exception:
            previews = []
        self._previews = previews
        self.app.call_from_thread(self._populate_table)

    def _populate_table(self) -> None:
        table = self.query_one("#plans-table", DataTable)
        table.clear()
        filt = self._filter.lower()
        visible = [
            p for p in self._previews
            if not filt
            or filt in (p.name or "").lower()
            or filt in (p.goal or "").lower()
        ]
        self._visible_previews = visible
        if not visible:
            msg = (
                "[dim #2a5a3a][ construct scan complete ][/]\n"
                "[dim]No saved plans found. Create a new plan to enter the system.[/]"
                if not self._previews else
                f"[dim]No plans match filter: {filt}[/]"
            )
            self.query_one("#empty-notice", Static).update(msg)
            return
        self.query_one("#empty-notice", Static).update("")
        for preview in visible:
            path_obj = Path(preview.path)
            source   = (
                "saved"   if "saved-plans" in str(path_obj) else
                "tracked" if "tasks"       in str(path_obj) else
                "file"
            )
            table.add_row(
                preview.name[:30],
                (preview.goal or "-")[:40],
                str(preview.task_count),
                source,
                key=preview.path,
            )

    def on_input_changed(self, event: Input.Changed) -> None:
        if event.input.id == "search-input":
            self._filter = event.value
            self._populate_table()

    def _selected_path(self) -> str | None:
        table = self.query_one("#plans-table", DataTable)
        if not table.row_count:
            return None
        visible = getattr(self, "_visible_previews", self._previews)
        row_key = table.cursor_row
        if row_key < 0 or row_key >= len(visible):
            return None
        return visible[row_key].path

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-run":
            self.action_run()
        elif event.button.id == "btn-details":
            self.action_details()
        elif event.button.id == "btn-validate":
            self.action_validate()
        elif event.button.id == "btn-edit":
            self.action_edit()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_run(self) -> None:
        path = self._selected_path()
        if path:
            self.app.push_screen(PlanDetailsScreen(path, mode="run"))  # type: ignore[attr-defined]

    def action_details(self) -> None:
        path = self._selected_path()
        if path:
            self.app.push_screen(PlanDetailsScreen(path, mode="view"))  # type: ignore[attr-defined]

    def action_validate(self) -> None:
        path = self._selected_path()
        if path:
            self.app.push_screen(PlanDetailsScreen(path, mode="validate"))  # type: ignore[attr-defined]

    def action_edit(self) -> None:
        path = self._selected_path()
        if path:
            self.app.push_screen(PlanEditorScreen(path))  # type: ignore[attr-defined]


# ── PlanDetailsScreen ──────────────────────────────────────────────────────────

class PlanDetailsScreen(Screen):  # type: ignore[type-arg,misc]
    """Inspect a plan and choose an action: run, validate, dry-run, save, back."""

    BINDINGS = [
        Binding("escape", "go_back", "Back", show=True),
        Binding("a", "approve_run",    "Approve + Run", show=True),
        Binding("v", "validate_plan",  "Validate",      show=True),
        Binding("d", "dry_run_plan",   "Dry Run",       show=True),
        Binding("s", "save_plan",      "Save",          show=True),
    ]

    CSS = """
    PlanDetailsScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #screen-title {
        color: $cyan;
        text-style: bold;
    }
    #plan-path {
        color: $text_dim;
    }
    #details-log {
        height: 1fr;
    }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    def __init__(self, plan_path: str, *, mode: str = "view") -> None:
        super().__init__()
        self._plan_path = plan_path
        self._mode = mode

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ PLAN REVIEW ]", id="screen-title")
            yield Static(self._plan_path, id="plan-path")
        yield RichLog(id="details-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("APPROVE + RUN", id="btn-run", variant="primary")
            yield Button("VALIDATE",      id="btn-validate")
            yield Button("DRY RUN",       id="btn-dryrun")
            yield Button("SAVE COPY",     id="btn-save")
            yield Button("BACK",          id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  PLAN DETAILS"
        self.run_worker(self._load_and_display, thread=True, name="plan-detail")
        if self._mode == "run":
            # Focus run button immediately
            pass

    def _load_and_display(self) -> None:
        log = self.query_one("#details-log", RichLog)
        plan_data = _load_plan_json(self._plan_path)
        if plan_data is None:
            self.app.call_from_thread(
                lambda: log.write(f"[#ff2244]ERROR: could not load plan: {self._plan_path}[/]")
            )
            return

        lines = _plan_summary_rich(plan_data)

        def _write() -> None:
            for ln in lines:
                log.write(ln)
            log.write("")
            log.write(
                "[dim #2a5a3a]Actions: [A] Approve+Run  [V] Validate  "
                "[D] Dry Run  [S] Save Copy  [Esc] Back[/]"
            )
        self.app.call_from_thread(_write)

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-run":
            self.action_approve_run()
        elif event.button.id == "btn-validate":
            self.action_validate_plan()
        elif event.button.id == "btn-dryrun":
            self.action_dry_run_plan()
        elif event.button.id == "btn-save":
            self.action_save_plan()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_approve_run(self) -> None:
        self.app.push_screen(  # type: ignore[attr-defined]
            RunPlanScreen(self._plan_path)
        )

    def action_validate_plan(self) -> None:
        self.app.push_screen(  # type: ignore[attr-defined]
            ValidateRunScreen(self._plan_path)
        )

    def action_dry_run_plan(self) -> None:
        self.app.push_screen(  # type: ignore[attr-defined]
            RunPlanScreen(self._plan_path, dry_run=True)
        )

    def action_save_plan(self) -> None:
        runtime_root = Path(str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT)))
        dest_dir = runtime_root / "saved-plans"
        try:
            from pojo_lens_agents.wizard import save_plan_to
            dest = save_plan_to(Path(self._plan_path), dest_dir)
            self.app.notify(f"Saved to:\n{dest}", title="Plan Saved")  # type: ignore[attr-defined]
        except Exception as exc:
            self.app.notify(f"Save failed: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]


# ── PlanEditorScreen ──────────────────────────────────────────────────────────

class PlanEditorScreen(Screen):  # type: ignore[type-arg,misc]
    """View plan JSON and optionally open in $EDITOR / VISUAL / notepad."""

    BINDINGS = [
        Binding("escape", "go_back",      "Back",            show=True),
        Binding("e",      "open_editor",  "Open in Editor",  show=True),
        Binding("r",      "reload",       "Reload",          show=True),
    ]

    CSS = """
    PlanEditorScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #ed-title  { color: $cyan; text-style: bold; }
    #ed-path   { color: $text_dim; }
    #ed-status { color: $green; margin-top: 1; }
    #ed-log    { height: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    def __init__(self, plan_path: str) -> None:
        super().__init__()
        self._plan_path = plan_path

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ PLAN EDITOR ]  View / open plan file", id="ed-title")
            yield Static(self._plan_path, id="ed-path")
            yield Static(
                "[ EDITOR ] press [E] to open in $EDITOR  ·  [R] to reload",
                id="ed-status",
            )
        yield RichLog(id="ed-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("OPEN IN EDITOR", id="btn-editor", variant="primary")
            yield Button("RELOAD",          id="btn-reload")
            yield Button("BACK",            id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  PLAN EDITOR"
        self.run_worker(self._load, thread=True, name="plan-ed-load")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-editor":
            self.action_open_editor()
        elif event.button.id == "btn-reload":
            self.action_reload()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_reload(self) -> None:
        self.query_one("#ed-log", RichLog).clear()
        self.run_worker(self._load, thread=True, name="plan-ed-reload")

    def action_open_editor(self) -> None:
        self.run_worker(self._launch_editor, thread=True, name="plan-ed-editor")

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#ed-log", RichLog).write(text))

    def _load(self) -> None:
        plan_data = _load_plan_json(self._plan_path)
        if plan_data is None:
            self._log(f"[#ff2244]ERROR: cannot load: {self._plan_path}[/]")
            return
        lines = _plan_summary_rich(plan_data)
        def _write() -> None:
            log = self.query_one("#ed-log", RichLog)
            for ln in lines:
                log.write(ln)
            log.write("")
            log.write("[dim #2a5a3a]Press [E] to open in system editor, [R] to reload after edits.[/]")
        self.app.call_from_thread(_write)

    def _launch_editor(self) -> None:
        import os
        import subprocess
        editor = (
            os.environ.get("EDITOR")
            or os.environ.get("VISUAL")
            or ("notepad" if sys.platform == "win32" else "vi")
        )
        self._log(f"[#00e5ff][ SIGNAL ] launching {editor!r} for {self._plan_path}[/]")
        try:
            subprocess.run([editor, self._plan_path], check=False)
            self._log("[#00ff41][ EXIT ] editor closed — press [R] to reload changes[/]")
        except FileNotFoundError:
            self._log(f"[#ff2244]editor not found: {editor!r}  — set $EDITOR env var[/]")
        except Exception as exc:
            self._log(f"[#ff2244]editor error: {exc}[/]")


# ── RunPlanScreen ──────────────────────────────────────────────────────────────

class RunPlanScreen(Screen):  # type: ignore[type-arg,misc]
    """Execute a saved plan — runs in background, streams output log."""

    BINDINGS = [
        Binding("escape", "go_back", "Back", show=True),
    ]

    CSS = """
    RunPlanScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #run-title { color: $cyan; text-style: bold; }
    #run-plan  { color: $text_dim; }
    #run-status { color: $green; margin-top: 1; }
    #run-log {
        height: 1fr;
    }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    _run_status: reactive[str] = reactive("[ CONSTRUCT ] initializing run...")

    def __init__(self, plan_path: str, *, dry_run: bool = False) -> None:
        super().__init__()
        self._plan_path = plan_path
        self._dry_run = dry_run

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            prefix = "DRY RUN" if self._dry_run else "RUN"
            yield Static(f"[ {prefix} ]  Executing plan", id="run-title")
            yield Static(self._plan_path, id="run-plan")
            yield Static("[ SIGNAL ] initializing...", id="run-status")
        yield RichLog(id="run-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK TO PLANS", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN"
        self.app.sub_title = "DRY RUN" if self._dry_run else "LIVE RUN"
        self.run_worker(self._execute_run, thread=True, name="plan-exec")

    def watch__run_status(self, status: str) -> None:
        self.query_one("#run-status", Static).update(f"[#00ff41][ SIGNAL ] {status}[/]")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#run-log", RichLog).write(text))

    def _set_status(self, s: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_run_status", s))

    def _execute_run(self) -> None:
        import io as _io
        handlers = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn available on app.[/]")
            return

        runtime_root = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))
        agents       = str(getattr(self.app, "_agents", DEFAULT_AGENTS_PATH))
        claude_bin   = str(getattr(self.app, "_claude_bin", "claude"))

        cmd = ["run", self._plan_path]
        if self._dry_run:
            cmd.append("--dry-run")
        cmd += ["--runtime-root", runtime_root, "--agents", agents,
                "--provider-bin", claude_bin, "--json"]

        self._set_status("building args...")
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            self._log(f"[#ff2244]Arg parse error: {exc}[/]")
            return

        self._set_status("[ OPERATOR ] launching run...")
        self._log(f"[#00e5ff][ SIGNAL ] run started: {self._plan_path}[/]")
        handler = handlers.get("run")
        if handler is None:
            self._log("[#ff2244]No 'run' handler available.[/]")
            return

        buf = _io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]Run error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log("[dim #2a5a3a]═══ RUN RESULT ═══[/]")
            status = str(payload.get("status") or "")
            if status:
                sc = _status_color(status)
                self._log(f"[bold {sc}]status: {status}[/]")
            # Show status counts
            counts = dict(payload.get("statusCounts") or {})
            for k, v in sorted(counts.items()):
                sc = _status_color(k)
                self._log(f"  [{sc}]{k}: {v}[/{sc}]")
            run_dir = str(payload.get("runDir") or "")
            if run_dir:
                self._log(f"[dim]run dir: {run_dir}[/]")

        self._set_status("[ EXIT ] run complete")
        self._log("[bold #00ff41]═══ EXECUTION COMPLETE ═══[/]")


# ── ValidatePlanScreen / ValidateRunScreen ─────────────────────────────────────

class ValidatePlanScreen(Screen):  # type: ignore[type-arg,misc]
    """Enter a plan path and validate it."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    ValidatePlanScreen { align: center middle; }
    #card {
        width: 76;
        height: auto;
        border: heavy $green 30%;
        background: $bg_panel;
        padding: 2 3;
    }
    #card-title { color: $cyan; text-style: bold; margin-bottom: 1; }
    #btns { height: auto; align: right middle; margin-top: 1; }
    """

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static("[ VALIDATE ]  Enter plan file path", id="card-title")
            yield Input(
                placeholder="ai/orchestrator/tasks/my-plan.json",
                id="plan-path-input",
            )
            with Horizontal(id="btns"):
                yield Button("VALIDATE →", id="btn-validate", variant="primary")
                yield Button("CANCEL",     id="btn-cancel")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#plan-path-input", Input).focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-validate":
            path = self.query_one("#plan-path-input", Input).value.strip()
            if path:
                self.app.push_screen(ValidateRunScreen(path))  # type: ignore[attr-defined]
        elif event.button.id == "btn-cancel":
            self.action_go_back()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        path = self.query_one("#plan-path-input", Input).value.strip()
        if path:
            self.app.push_screen(ValidateRunScreen(path))  # type: ignore[attr-defined]

    def action_go_back(self) -> None:
        self.dismiss(None)


class ValidateRunScreen(Screen):  # type: ignore[type-arg,misc]
    """Run validation on a plan file, display grouped results."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    ValidateRunScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #val-title { color: $cyan; text-style: bold; }
    #val-path  { color: $text_dim; }
    #val-log   { height: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: right middle;
        padding: 0 2;
    }
    """

    def __init__(self, plan_path: str) -> None:
        super().__init__()
        self._plan_path = plan_path

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ VALIDATE ]  Scanning plan...", id="val-title")
            yield Static(self._plan_path, id="val-path")
        yield RichLog(id="val-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  VALIDATE"
        self.run_worker(self._run_validation, thread=True, name="validate")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#val-log", RichLog).write(text))

    def _run_validation(self) -> None:
        import io as _io

        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        agents     = str(getattr(self.app, "_agents",     DEFAULT_AGENTS_PATH))
        claude_bin = str(getattr(self.app, "_claude_bin", "claude"))

        self._log("[dim #2a5a3a][ SIGNAL ] validating workspace boundaries...[/]")

        cmd = ["validate", self._plan_path, "--agents", agents, "--provider-bin", claude_bin]
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            self._log(f"[#ff2244]Arg parse error: {exc}[/]")
            return

        handler = handlers.get("validate")
        if handler is None:
            self._log("[#ff2244]No 'validate' handler available.[/]")
            return

        buf = _io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]Validation error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log("[bold #00e5ff]═══ VALIDATION RESULT ═══[/]")
            valid  = payload.get("valid")
            errors = list(payload.get("errors") or [])
            warns  = list(payload.get("warnings") or [])

            if valid is True:
                self._log("[bold #00ff41]✓  PLAN IS VALID[/]")
            elif valid is False:
                self._log("[bold #ff2244]✗  PLAN HAS ERRORS[/]")
            else:
                self._log("[dim]validation status unknown[/]")

            if errors:
                self._log("")
                self._log("[bold #ff2244]── Critical Errors ──[/]")
                for e in errors:
                    self._log(f"  [#ff2244]✗ {e}[/]")
            if warns:
                self._log("")
                self._log("[bold #ffaa00]── Warnings ──[/]")
                for w in warns:
                    self._log(f"  [#ffaa00]⚠ {w}[/]")

            # Show cost estimate if present
            cost = payload.get("costEstimate") or payload.get("estimate")
            if cost:
                self._log("")
                self._log("[bold #00e5ff]── Cost Estimate ──[/]")
                if isinstance(cost, dict):
                    for k, v in cost.items():
                        self._log(f"  {k}: {v}")
                else:
                    self._log(f"  {cost}")

        self._log("")
        self._log("[dim #2a5a3a][ EXIT ] validation complete[/]")

        # Update title based on result
        valid = payload.get("valid") if payload else None
        if valid is True:
            self.app.call_from_thread(lambda: setattr(self.query_one("#val-title", Static), "update",
                                                  lambda _: None))
            self.app.call_from_thread(
                lambda: self.query_one("#val-title", Static).update(
                    "[bold #00ff41][ VALIDATE ]  ✓ VALID[/]"
                )
            )
        elif valid is False:
            self.app.call_from_thread(
                lambda: self.query_one("#val-title", Static).update(
                    "[bold #ff2244][ VALIDATE ]  ✗ ERRORS FOUND[/]"
                )
            )


# ── RunLedgerScreen ────────────────────────────────────────────────────────────

class RunLedgerScreen(Screen):  # type: ignore[type-arg,misc]
    """List retained runs from the runtime root.  mode='runs'|'ledger'|'promote'."""

    BINDINGS = [
        Binding("escape", "go_back",    "Back",    show=True),
        Binding("i",      "inspect_run","Inspect", show=True),
        Binding("r",      "resume_run", "Resume",  show=True),
        Binding("y",      "retry_run",  "Retry",   show=True),
        Binding("p",      "promote_run","Promote", show=True),
    ]

    CSS = """
    RunLedgerScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #screen-title { color: $cyan; text-style: bold; }
    #screen-hint  { color: $text_dim; }
    #runs-table   { height: 1fr; }
    #empty-notice {
        color: $text_dim;
        text-align: center;
        margin: 4;
    }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    def __init__(self, *, mode: str = "runs") -> None:
        super().__init__()
        self._mode = mode
        self._entries: list[dict[str, Any]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        label = {
            "runs":    "[ RUNS ]  Retained run history",
            "ledger":  "[ LEDGER ]  Run ledger summary",
            "promote": "[ PROMOTE ]  Select run to review / promote",
        }.get(self._mode, "[ RUNS ]")
        with Container(id="top-bar"):
            yield Static(label, id="screen-title")
            yield Static(
                "[I] Inspect  [R] Resume  [Y] Retry  [P] Promote  [Esc] Back",
                id="screen-hint",
            )
        yield DataTable(id="runs-table")
        yield Static("", id="empty-notice")
        with Horizontal(id="action-bar"):
            yield Button("INSPECT",  id="btn-inspect")
            yield Button("RESUME",   id="btn-resume")
            yield Button("RETRY",    id="btn-retry")
            yield Button("PROMOTE",  id="btn-promote")
            yield Button("BACK",     id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN LEDGER"
        table = self.query_one("#runs-table", DataTable)
        table.cursor_type = "row"
        table.add_column("Run ID",   key="run_id",   width=24)
        table.add_column("Plan",     key="plan",     width=28)
        table.add_column("Status",   key="status",   width=14)
        table.add_column("Tasks",    key="tasks",    width=10)
        table.add_column("Date",     key="date",     width=20)
        self.run_worker(self._load_runs, thread=True, name="load-runs")

    def _load_runs(self) -> None:
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        runtime_root  = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))

        entries: list[dict[str, Any]] = []

        if parse_args_fn is not None and "inventory" in handlers:
            try:
                args    = parse_args_fn(["inventory", "--runtime-root", runtime_root, "--json"])
                payload = handlers["inventory"](args)
                runs    = list(payload.get("runs") or payload.get("entries") or [])
                entries = sorted(runs, key=lambda r: str(r.get("startedAt") or ""), reverse=True)
            except Exception:
                pass

        self._entries = entries
        self.app.call_from_thread(self._populate_table)

    def _populate_table(self) -> None:
        table = self.query_one("#runs-table", DataTable)
        table.clear()
        if not self._entries:
            self.query_one("#empty-notice", Static).update(
                "[dim #2a5a3a][ trace ] no retained runs found in runtime root[/]"
            )
            return
        self.query_one("#empty-notice", Static).update("")
        for entry in self._entries[:200]:
            run_id   = str(entry.get("runId") or entry.get("run_id") or "-")
            plan     = str(entry.get("planName") or entry.get("plan_name") or "-")
            status   = str(entry.get("status") or entry.get("lifecycleState") or "-")
            tasks    = str(entry.get("totalTasks") or entry.get("task_count") or "-")
            date_raw = str(entry.get("startedAt") or entry.get("createdAt") or "")
            date     = date_raw[:16].replace("T", " ") if date_raw else "-"
            sc       = _status_color(status)
            status_cell = Text(status, style=sc) if Text is not None else status
            table.add_row(
                run_id[:24],
                plan[:28],
                status_cell,
                tasks,
                date,
                key=run_id,
            )

    def _selected_run_id(self) -> str | None:
        table = self.query_one("#runs-table", DataTable)
        if not table.row_count or self._entries is None:
            return None
        row  = table.cursor_row
        if row < 0 or row >= len(self._entries):
            return None
        return str(self._entries[row].get("runId") or self._entries[row].get("run_id") or "")

    def _selected_run_dir(self) -> str | None:
        table = self.query_one("#runs-table", DataTable)
        if not table.row_count or not self._entries:
            return None
        row = table.cursor_row
        if row < 0 or row >= len(self._entries):
            return None
        e = self._entries[row]
        return str(e.get("runDir") or e.get("run_dir") or e.get("manifestPath") or "")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-inspect":
            self.action_inspect_run()
        elif event.button.id == "btn-resume":
            self.action_resume_run()
        elif event.button.id == "btn-retry":
            self.action_retry_run()
        elif event.button.id == "btn-promote":
            self.action_promote_run()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_inspect_run(self) -> None:
        run_dir = self._selected_run_dir()
        if run_dir:
            self.app.push_screen(RunDetailsScreen(run_dir))  # type: ignore[attr-defined]
        else:
            self.app.notify("Select a run first.", title="No Run Selected")  # type: ignore[attr-defined]

    def action_resume_run(self) -> None:
        run_dir = self._selected_run_dir()
        if run_dir:
            self.app.push_screen(ResumeRetryScreen(run_dir, mode="resume"))  # type: ignore[attr-defined]
        else:
            self.app.notify("Select a run first.", title="No Run Selected")  # type: ignore[attr-defined]

    def action_retry_run(self) -> None:
        run_dir = self._selected_run_dir()
        if run_dir:
            self.app.push_screen(ResumeRetryScreen(run_dir, mode="retry"))  # type: ignore[attr-defined]
        else:
            self.app.notify("Select a run first.", title="No Run Selected")  # type: ignore[attr-defined]

    def action_promote_run(self) -> None:
        run_dir = self._selected_run_dir()
        if run_dir:
            self.app.push_screen(DiffReviewScreen(run_dir))  # type: ignore[attr-defined]
        else:
            self.app.notify("Select a run first.", title="No Run Selected")  # type: ignore[attr-defined]


# ── RunDetailsScreen ───────────────────────────────────────────────────────────

class RunDetailsScreen(Screen):  # type: ignore[type-arg,misc]
    """Single retained run summary — tasks, cost, events."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    RunDetailsScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #run-title { color: $cyan; text-style: bold; }
    #run-ref   { color: $text_dim; }
    #detail-log { height: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: right middle;
        padding: 0 2;
    }
    """

    def __init__(self, run_ref: str) -> None:
        super().__init__()
        self._run_ref = run_ref

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ RUN DETAILS ]", id="run-title")
            yield Static(self._run_ref, id="run-ref")
        yield RichLog(id="detail-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN DETAILS"
        self.run_worker(self._load_details, thread=True, name="run-details")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#detail-log", RichLog).write(text))

    def _load_details(self) -> None:
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        try:
            args    = parse_args_fn(["status", self._run_ref, "--json"])
            payload = handlers.get("status", lambda a: {})(args)
        except Exception as exc:
            self._log(f"[#ff2244]Status error: {exc}[/]")
            return

        if not payload:
            self._log("[dim]No status payload returned.[/]")
            return

        self._log("[bold #00e5ff]═══ RUN STATUS ═══[/]")
        for key in ("runId", "planName", "status", "lifecycleState", "startedAt", "finishedAt"):
            val = payload.get(key)
            if val is not None:
                label = key.replace("_", " ")
                self._log(f"  [#00e5ff]{label}:[/] {val}")

        tasks = list(payload.get("tasks") or [])
        if tasks:
            self._log("")
            self._log("[bold #00e5ff]═══ Tasks ═══[/]")
            for t in tasks:
                tid    = str(t.get("taskId") or t.get("id") or "?")
                status = str(t.get("status") or "?")
                cost   = t.get("costUsd") or t.get("cost_usd")
                sc     = _status_color(status)
                cost_s = f"  [#00e5ff]${cost:.5f}[/]" if isinstance(cost, float) else ""
                self._log(f"  [{sc}]▸ {tid}: {status}[/{sc}]{cost_s}")

        budget = payload.get("totalCostUsd") or payload.get("total_cost_usd")
        if budget is not None:
            self._log("")
            self._log(f"[bold #00e5ff]Total cost:[/] [#ffaa00]${budget:.5f}[/]")

        self._log("")
        self._log("[dim #2a5a3a][ trace ] run detail complete[/]")


# ── ResumeRetryScreen ──────────────────────────────────────────────────────────

class ResumeRetryScreen(Screen):  # type: ignore[type-arg,misc]
    """Resume, retry, or promote a retained run."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    ResumeRetryScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #rr-title  { color: $cyan; text-style: bold; }
    #rr-ref    { color: $text_dim; }
    #rr-status { color: $green; margin-top: 1; }
    #rr-log    { height: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    _op_status: reactive[str] = reactive("ready")

    def __init__(self, run_ref: str, *, mode: str = "resume") -> None:
        super().__init__()
        self._run_ref = run_ref
        self._mode    = mode

    def compose(self) -> ComposeResult:
        yield Header()
        verb = self._mode.upper()
        with Container(id="top-bar"):
            yield Static(f"[ {verb} ]  {self._run_ref}", id="rr-title")
            yield Static(self._run_ref, id="rr-ref")
            yield Static(f"[ OPERATOR ] {self._mode} staged — confirm to proceed", id="rr-status")
        yield RichLog(id="rr-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button(f"CONFIRM {verb}", id="btn-confirm", variant="primary")
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = f"POJOLENS  //  {self._mode.upper()}"
        log = self.query_one("#rr-log", RichLog)
        log.write(f"[#00e5ff][ SIGNAL ] {self._mode} ready: {self._run_ref}[/]")
        log.write("[dim]Press CONFIRM to proceed, or BACK to cancel.[/]")

    def watch__op_status(self, s: str) -> None:
        self.query_one("#rr-status", Static).update(f"[#00ff41][ SIGNAL ] {s}[/]")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-confirm":
            self.run_worker(self._execute_op, thread=True, name=f"{self._mode}-op")
            self.query_one("#btn-confirm", Button).disabled = True
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#rr-log", RichLog).write(text))

    def _set_status(self, s: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_op_status", s))

    def _execute_op(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        runtime_root = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))
        agents       = str(getattr(self.app, "_agents",       DEFAULT_AGENTS_PATH))
        claude_bin   = str(getattr(self.app, "_claude_bin",   "claude"))

        if self._mode == "promote":
            cmd = ["promote", self._run_ref, "--agents", agents,
                   "--provider-bin", claude_bin, "--json"]
        elif self._mode == "retry":
            cmd = ["retry", self._run_ref, "--runtime-root", runtime_root,
                   "--agents", agents, "--provider-bin", claude_bin, "--json"]
        else:
            cmd = ["resume", self._run_ref, "--runtime-root", runtime_root,
                   "--agents", agents, "--provider-bin", claude_bin, "--json"]

        self._set_status("building args...")
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            self._log(f"[#ff2244]Arg error: {exc}[/]")
            return

        handler = handlers.get(self._mode)
        if handler is None:
            self._log(f"[#ff2244]Handler '{self._mode}' not available.[/]")
            return

        self._set_status(f"[ OPERATOR ] {self._mode} in progress...")
        self._log(f"[#00e5ff][ TRACE ] {self._mode} dispatched[/]")

        buf = _io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]{self._mode} error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log(f"[bold #00e5ff]═══ {self._mode.upper()} RESULT ═══[/]")
            status = str(payload.get("status") or "")
            if status:
                sc = _status_color(status)
                self._log(f"[bold {sc}]status: {status}[/]")
            for k, v in sorted((payload or {}).items()):
                if k not in {"status", "_consoleText"} and v is not None:
                    self._log(f"  {k}: {str(v)[:80]}")

        self._set_status(f"[ EXIT ] {self._mode} complete")
        self._log(f"[bold #00ff41]═══ {self._mode.upper()} COMPLETE ═══[/]")


# ── HitlGateScreen ────────────────────────────────────────────────────────────

class HitlGateScreen(Screen):  # type: ignore[type-arg,misc]
    """HITL approval gate — cyberpunk control panel for batch sign-off.

    NOTE (WP75): Live run polling not yet wired.  Gate ID and batch data will
    be driven from the retained run manifest when WP75 is implemented.
    """

    BINDINGS = [
        Binding("escape", "go_back", "Back",    show=True),
        Binding("a",      "approve", "Approve", show=True),
        Binding("x",      "abort",   "Abort",   show=True),
    ]

    CSS = """
    HitlGateScreen {
        background: $bg;
    }
    #gate-header {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $amber 40%;
        padding: 1 2;
    }
    #gate-title {
        color: $amber;
        text-style: bold;
    }
    #gate-id {
        color: $green_body;
        margin-top: 1;
    }
    #gate-wip {
        color: $text_dim;
        margin-top: 0;
    }
    #main-split {
        height: 1fr;
        layout: horizontal;
    }
    #left-panel {
        width: 1fr;
        background: $bg_panel;
        border-right: heavy $green 20%;
        padding: 1 2;
    }
    #right-panel {
        width: 1fr;
        background: $bg_panel;
        padding: 1 2;
    }
    #left-panel .panel-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #right-panel .panel-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #batch-log {
        height: 1fr;
    }
    #pending-table {
        height: 1fr;
    }
    #cost-bar {
        height: 3;
        background: $bg_input;
        border-top: solid $green_dim;
        border-bottom: solid $green_dim;
        padding: 0 2;
        align: left middle;
    }
    #cost-display {
        color: $green_body;
    }
    #gate-actions {
        height: 3;
        background: $bg_input;
        border-top: heavy $amber 40%;
        align: left middle;
        padding: 0 1;
    }
    """

    def __init__(self, run_ref: str = "", gate_id: str = "") -> None:
        super().__init__()
        self._run_ref = run_ref
        self._gate_id = gate_id or "GATE-PENDING"

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="gate-header"):
            yield Static(
                "[ HITL GATE ]  ⚠  HUMAN APPROVAL REQUIRED  ⚠",
                id="gate-title",
            )
            yield Static("", id="gate-id")
            yield Static(
                "WP75: live run polling pending — data above is placeholder until wired",
                id="gate-wip",
            )
        with Horizontal(id="main-split"):
            with Container(id="left-panel"):
                yield Static("COMPLETED BATCH SUMMARY", classes="panel-title")
                yield RichLog(
                    id="batch-log", markup=True, auto_scroll=False,
                    wrap=True, highlight=False,
                )
            with Container(id="right-panel"):
                yield Static("PENDING TASKS", classes="panel-title")
                yield DataTable(id="pending-table")
        with Container(id="cost-bar"):
            yield Static("", id="cost-display")
        with Horizontal(id="gate-actions"):
            yield Button("[A]  APPROVE",    id="btn-approve", variant="primary")
            yield Button("[X]  ABORT",      id="btn-abort",   variant="error")
            yield Button("BACK",            id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title   = "POJOLENS  //  HITL GATE"
        self.app.sub_title = "AWAITING APPROVAL"

        self.query_one("#gate-id", Static).update(
            f"[#ffaa00]Gate ID:[/] [bold #00e5ff]{self._gate_id}[/]"
            + (f"  [dim]·  Run: {self._run_ref}[/]" if self._run_ref else "")
        )
        self.query_one("#cost-display", Static).update(
            "[dim]Cost so far:[/] [#ffaa00]—[/]  "
            "[dim]·  Stale sentinel check:[/] [#00ff41]OK[/]  "
            "[dim]·  Live polling: WP75 pending[/]"
        )

        table = self.query_one("#pending-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Task ID",  width=22)
        table.add_column("Status",   width=10)
        table.add_column("Agent",    width=14)

        log = self.query_one("#batch-log", RichLog)
        log.write("[dim #2a5a3a][ construct ] connecting to retained run manifest...[/]")
        log.write("")
        log.write("[dim]Batch completion data will stream from:[/]")
        log.write(f"  [#00e5ff]{self._run_ref or '.claude-orchestrator/runs/<run-id>/manifest.json'}[/]")
        log.write("")
        log.write("[dim #ffaa00]WP75 will wire:[/]")
        log.write("  [dim]• polling retained run for pending HITL sentinels[/]")
        log.write("  [dim]• completed batch task counts and cost[/]")
        log.write("  [dim]• stale sentinel gateId validation[/]")
        log.write("  [dim]• approve/abort calling orchestrator handlers[/]")
        log.write("")
        log.write("[#00ff41][ OPERATOR ][/] Press [bold #00ff41][A][/] to approve  |  "
                  "[bold #ff2244][X][/] to abort")

        if self._run_ref:
            self.run_worker(self._load_gate_data, thread=True, name="hitl-load")

    def _load_gate_data(self) -> None:
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            return
        try:
            args    = parse_args_fn(["status", self._run_ref, "--json"])
            payload = handlers.get("status", lambda a: {})(args)
        except Exception:
            return
        tasks = list(payload.get("tasks") or [])
        pending = [t for t in tasks if str(t.get("status") or "") in ("pending", "blocked")]
        cost    = payload.get("totalCostUsd") or payload.get("total_cost_usd")
        def _update() -> None:
            table = self.query_one("#pending-table", DataTable)
            table.clear()
            for t in pending[:50]:
                tid    = str(t.get("taskId") or t.get("id") or "?")
                status = str(t.get("status") or "?")
                agent  = str(t.get("agent") or "-")
                sc     = _status_color(status)
                table.add_row(tid[:22], Text(status, style=sc) if Text else status, agent[:14])
            if cost is not None:
                self.query_one("#cost-display", Static).update(
                    f"[dim]Cost so far:[/] [bold #ffaa00]${cost:.5f}[/]"
                )
        self.app.call_from_thread(_update)

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-approve":
            self.action_approve()
        elif event.button.id == "btn-abort":
            self.action_abort()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_approve(self) -> None:
        self.dismiss({"decision": "approve", "gate_id": self._gate_id})

    def action_abort(self) -> None:
        self.dismiss({"decision": "abort", "gate_id": self._gate_id})

    def action_go_back(self) -> None:
        self.dismiss(None)


# ── MemoryToolsScreen ──────────────────────────────────────────────────────────

class MemoryToolsScreen(Screen):  # type: ignore[type-arg,misc]
    """AI memory maintenance: refresh, check, query."""

    BINDINGS = [
        Binding("escape", "go_back", "Back", show=True),
        Binding("r", "refresh",    "Refresh", show=True),
        Binding("c", "check",      "Check",   show=True),
        Binding("q", "query_mem",  "Query",   show=True),
    ]

    CSS = """
    MemoryToolsScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #mem-title { color: $cyan; text-style: bold; }
    #mem-hint  { color: $text_dim; }
    #mem-desc  { color: $green_body; margin-top: 1; }
    #query-bar {
        height: 3;
        background: $bg_input;
        border-bottom: solid $green 20%;
        padding: 0 1;
        align: left middle;
    }
    #query-label { color: $cyan; width: 10; }
    #query-input { width: 1fr; }
    #mem-log   { height: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ MEMORY TOOLS ]  AI Memory Maintenance", id="mem-title")
            yield Static("[R] Refresh  [C] Check  [Q] Query  [Esc] Back", id="mem-hint")
            yield Static(
                "Hot context: ai/core/agent-invariants.md · ai/core/repo-purpose.md\n"
                "             ai/state/current-state.md · ai/state/handoff.md\n"
                "After tracked memory changes: run refresh → check.",
                id="mem-desc",
            )
        with Horizontal(id="query-bar"):
            yield Static("QUERY: ", id="query-label")
            yield Input(
                placeholder="search keywords for query-ai-memory.ps1 ...",
                id="query-input",
            )
        yield RichLog(id="mem-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("REFRESH", id="btn-refresh", variant="primary")
            yield Button("CHECK",   id="btn-check")
            yield Button("QUERY",   id="btn-query")
            yield Button("BACK",    id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  MEMORY TOOLS"
        log = self.query_one("#mem-log", RichLog)
        log.write("[dim #2a5a3a]Memory tools ready.[/]")
        log.write("Hot context files:")
        log.write("  [#00e5ff]•[/] ai/core/agent-invariants.md")
        log.write("  [#00e5ff]•[/] ai/core/repo-purpose.md")
        log.write("  [#00e5ff]•[/] ai/state/current-state.md")
        log.write("  [#00e5ff]•[/] ai/state/handoff.md")
        log.write("")
        log.write("[dim]Use [R] to refresh derived memory, [C] to check consistency.[/]")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-refresh":
            self.action_refresh()
        elif event.button.id == "btn-check":
            self.action_check()
        elif event.button.id == "btn-query":
            self.action_query_mem()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#mem-log", RichLog).write(text))

    def action_refresh(self) -> None:
        self.run_worker(self._run_refresh, thread=True, name="mem-refresh")

    def action_check(self) -> None:
        self.run_worker(self._run_check, thread=True, name="mem-check")

    def action_query_mem(self) -> None:
        query = self.query_one("#query-input", Input).value.strip()
        if not query:
            self.query_one("#query-input", Input).focus()
            self._log("[#ffaa00]Enter a search query above then press [Q] or the QUERY button.[/]")
            return
        self.run_worker(
            lambda: self._run_query(query), thread=True, name="mem-query"
        )

    def _run_query(self, query: str) -> None:
        self._log(f"[#00e5ff][ SIGNAL ] querying memory: {query!r}...[/]")
        try:
            import subprocess
            result = subprocess.run(
                ["powershell", "-File", "scripts/ai/query-ai-memory.ps1",
                 "-Query", query, "-Limit", "10"],
                capture_output=True,
                text=True,
                timeout=60,
            )
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ query complete[/]")
            else:
                self._log(f"[#ff2244]✗ query exited {result.returncode}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]query error: {exc}[/]")

    def _run_refresh(self) -> None:
        self._log("[#00e5ff][ SIGNAL ] launching memory refresh...[/]")
        try:
            import subprocess
            result = subprocess.run(
                ["powershell", "-File", "scripts/ai/refresh-ai-memory.ps1"],
                capture_output=True,
                text=True,
                timeout=120,
            )
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ refresh complete[/]")
            else:
                self._log(f"[#ff2244]✗ refresh exited {result.returncode}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]refresh error: {exc}[/]")

    def _run_check(self) -> None:
        self._log("[#00e5ff][ SIGNAL ] running memory check...[/]")
        try:
            import subprocess
            result = subprocess.run(
                ["powershell", "-File", "scripts/ai/refresh-ai-memory.ps1", "-Check"],
                capture_output=True,
                text=True,
                timeout=120,
            )
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ check passed[/]")
            else:
                self._log(f"[#ff2244]✗ check failed (exit {result.returncode})[/]")
        except Exception as exc:
            self._log(f"[#ff2244]check error: {exc}[/]")


# ── SettingsScreen ─────────────────────────────────────────────────────────────

class SettingsScreen(Screen):  # type: ignore[type-arg,misc]
    """Display current configuration defaults."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    SettingsScreen {
        background: $bg;
    }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #set-title { color: $cyan; text-style: bold; }
    #set-hint  { color: $text_dim; }
    #set-log   { height: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: right middle;
        padding: 0 2;
    }
    """

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ SETTINGS ]  Current configuration defaults", id="set-title")
            yield Static(
                "Edit ai/orchestrator/README.md or pojolens config for persistent changes.",
                id="set-hint",
            )
        yield RichLog(id="set-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("REFRESH", id="btn-refresh")
            yield Button("BACK",    id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  SETTINGS"
        self.run_worker(self._load_config, thread=True, name="load-config")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-refresh":
            self.query_one("#set-log", RichLog).clear()
            self.run_worker(self._load_config, thread=True, name="load-config-2")
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#set-log", RichLog).write(text))

    def _load_config(self) -> None:
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)

        self._log("[bold #00e5ff]═══ Operator Defaults ═══[/]")

        for key, attr in [
            ("Runtime Root",  "_runtime_root"),
            ("Agents File",   "_agents"),
            ("Claude Binary", "_claude_bin"),
            ("Tasks Dir",     "_tasks_dir"),
        ]:
            val = str(getattr(self.app, attr, "-"))
            self._log(f"  [#00e5ff]{key}:[/] {val}")

        self._log("")
        self._log("[bold #00e5ff]═══ Effort → Model Mapping ═══[/]")
        try:
            for effort, (model, effort_val) in _EFFORT_MODEL_MAP.items():
                self._log(f"  [#00e5ff]{effort:<8}[/] → {model}")
        except Exception:
            self._log("  [dim](mapping unavailable)[/]")

        self._log("")
        self._log("[bold #00e5ff]═══ Config File ═══[/]")
        if parse_args_fn is not None and "config" in handlers:
            try:
                import io as _io
                args = parse_args_fn(["config", "show", "--json"])
                buf  = _io.StringIO()
                old_stdout = sys.stdout
                sys.stdout = buf  # type: ignore[assignment]
                try:
                    payload = handlers["config"](args)
                except Exception:
                    payload = {}
                finally:
                    sys.stdout = old_stdout
                    captured = buf.getvalue().strip()
                if captured:
                    for line in captured.splitlines():
                        self._log(f"  {line}")
                elif payload:
                    for k, v in sorted(payload.items()):
                        if not k.startswith("_"):
                            self._log(f"  [#00e5ff]{k}:[/] {v}")
            except Exception as exc:
                self._log(f"  [dim](config load error: {exc})[/]")
        else:
            self._log("  [dim](config handler not available)[/]")

        self._log("")
        self._log("[dim #2a5a3a][ trace ] settings loaded[/]")


# ── DiffReviewScreen ──────────────────────────────────────────────────────────

class DiffReviewScreen(Screen):  # type: ignore[type-arg,misc]
    """Review workspace diffs and promote changes for a retained run."""

    BINDINGS = [
        Binding("escape", "go_back",        "Back",          show=True),
        Binding("p",      "promote_run",    "Promote",       show=True),
        Binding("e",      "export_patch",   "Export Patch",  show=True),
        Binding("c",      "coord_validate", "Coord. Val.",   show=True),
    ]

    CSS = """
    DiffReviewScreen { background: $bg; }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #diff-title  { color: $cyan; text-style: bold; }
    #diff-ref    { color: $text_dim; }
    #diff-status { color: $green; margin-top: 1; }
    #split-view  { height: 1fr; layout: horizontal; }
    #file-list   { width: 38; border-right: heavy $green 20%; }
    #diff-log    { width: 1fr; }
    #action-bar  {
        height: 3;
        background: $bg_input;
        border-top: solid $green 25%;
        align: left middle;
        padding: 0 1;
    }
    """

    _diff_status: reactive[str] = reactive("loading diff...")

    def __init__(self, run_ref: str) -> None:
        super().__init__()
        self._run_ref = run_ref
        self._file_entries: list[dict[str, Any]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ DIFF REVIEW ]  Workspace changes", id="diff-title")
            yield Static(self._run_ref, id="diff-ref")
            yield Static("[ SIGNAL ] loading...", id="diff-status")
        with Horizontal(id="split-view"):
            yield DataTable(id="file-list")
            yield RichLog(id="diff-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("PROMOTE",        id="btn-promote",  variant="primary")
            yield Button("EXPORT PATCH",   id="btn-export")
            yield Button("COORD. VALID.",  id="btn-coord")
            yield Button("BACK",           id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  DIFF REVIEW"
        table = self.query_one("#file-list", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("File", key="file", width=28)
        table.add_column("Task", key="task", width=8)
        self.run_worker(self._load_diff, thread=True, name="load-diff")

    def watch__diff_status(self, s: str) -> None:
        self.query_one("#diff-status", Static).update(f"[#00ff41][ SIGNAL ] {s}[/]")

    def _set_status(self, s: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_diff_status", s))

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#diff-log", RichLog).write(text))

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-promote":
            self.action_promote_run()
        elif event.button.id == "btn-export":
            self.action_export_patch()
        elif event.button.id == "btn-coord":
            self.action_coord_validate()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _load_diff(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        # Review summary
        self._set_status("loading review summary...")
        if "review" in handlers:
            try:
                args    = parse_args_fn(["review", self._run_ref, "--json"])
                buf     = _io.StringIO()
                old_out = sys.stdout
                sys.stdout = buf  # type: ignore[assignment]
                try:
                    review_payload: dict[str, Any] = handlers["review"](args)
                except Exception as exc:
                    review_payload = {}
                    self._log(f"[#ff2244]review error: {exc}[/]")
                finally:
                    sys.stdout = old_out
                    captured = buf.getvalue().strip()
                if captured:
                    self._log("[bold #00e5ff]═══ REVIEW SUMMARY ═══[/]")
                    for line in captured.splitlines():
                        self._log(line)
                elif review_payload:
                    self._log("[bold #00e5ff]═══ REVIEW SUMMARY ═══[/]")
                    for k, v in sorted(review_payload.items()):
                        if k != "_consoleText" and v is not None:
                            self._log(f"  [#00e5ff]{k}:[/] {str(v)[:80]}")
            except Exception as exc:
                self._log(f"[#ffaa00]review load: {exc}[/]")

        # Diff output
        self._set_status("loading diff output...")
        if "diff-run" in handlers:
            try:
                args    = parse_args_fn(["diff-run", self._run_ref, "--json"])
                buf     = _io.StringIO()
                old_out = sys.stdout
                sys.stdout = buf  # type: ignore[assignment]
                try:
                    diff_payload: dict[str, Any] = handlers["diff-run"](args)
                except Exception as exc:
                    diff_payload = {}
                    self._log(f"[#ff2244]diff-run error: {exc}[/]")
                finally:
                    sys.stdout = old_out
                    captured = buf.getvalue().strip()

                if captured:
                    self._log("")
                    self._log("[bold #00e5ff]═══ WORKSPACE DIFF ═══[/]")
                    for line in captured.splitlines():
                        if line.startswith("+") and not line.startswith("+++"):
                            self._log(f"[#00ff41]{line}[/]")
                        elif line.startswith("-") and not line.startswith("---"):
                            self._log(f"[#ff2244]{line}[/]")
                        elif line.startswith("@@"):
                            self._log(f"[#00e5ff]{line}[/]")
                        else:
                            self._log(line)

                files = list(diff_payload.get("changedFiles") or diff_payload.get("files") or [])
                if files:
                    self._file_entries = files

                    def _fill(fs: list[dict[str, Any]]) -> None:
                        table = self.query_one("#file-list", DataTable)
                        for f in fs[:100]:
                            fname = str(f.get("path") or f.get("file") or "-")
                            task  = str(f.get("taskId") or "-")
                            table.add_row(fname[-28:], task[:8], key=fname)

                    self.app.call_from_thread(lambda: _fill(files))

            except Exception as exc:
                self._log(f"[#ff2244]diff-run: {exc}[/]")

        self._set_status("[P] Promote  [E] Export Patch  [C] Coord. Validate  [Esc] Back")

    def action_promote_run(self) -> None:
        self.app.push_screen(ResumeRetryScreen(self._run_ref, mode="promote"))  # type: ignore[attr-defined]

    def action_export_patch(self) -> None:
        self.run_worker(self._do_export_patch, thread=True, name="export-patch")

    def action_coord_validate(self) -> None:
        self.run_worker(self._do_coord_validate, thread=True, name="coord-validate")

    def _do_export_patch(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None or "export-patch" not in handlers:
            self._log("[#ff2244]export-patch handler not available.[/]")
            return
        try:
            args    = parse_args_fn(["export-patch", self._run_ref])
            buf     = _io.StringIO()
            old_out = sys.stdout
            sys.stdout = buf  # type: ignore[assignment]
            try:
                payload: dict[str, Any] = handlers["export-patch"](args)
            except Exception as exc:
                payload = {}
                self._log(f"[#ff2244]export-patch error: {exc}[/]")
            finally:
                sys.stdout = old_out
                captured = buf.getvalue().strip()
            if captured:
                for line in captured.splitlines():
                    self._log(line)
            patch_path = str(payload.get("patchPath") or payload.get("path") or "")
            if patch_path:
                self._log(f"[bold #00ff41]patch exported: {patch_path}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]export-patch: {exc}[/]")

    def _do_coord_validate(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None or "validate-run" not in handlers:
            self._log("[#ff2244]validate-run handler not available.[/]")
            return
        self._set_status("[ SIGNAL ] running coordinator validation...")
        try:
            args    = parse_args_fn(["validate-run", self._run_ref, "--json"])
            buf     = _io.StringIO()
            old_out = sys.stdout
            sys.stdout = buf  # type: ignore[assignment]
            try:
                payload = handlers["validate-run"](args)
            except Exception as exc:
                payload = {}
                self._log(f"[#ff2244]validate-run error: {exc}[/]")
            finally:
                sys.stdout = old_out
                captured = buf.getvalue().strip()
            if captured:
                self._log("[bold #00e5ff]═══ COORDINATOR VALIDATION ═══[/]")
                for line in captured.splitlines():
                    self._log(line)
            valid = payload.get("valid") or payload.get("passed")
            if valid is True:
                self._set_status("[ EXIT ] coord. validation PASSED")
                self._log("[bold #00ff41]✓ COORDINATOR VALIDATION PASSED[/]")
            elif valid is False:
                self._set_status("[ EXIT ] coord. validation FAILED")
                self._log("[bold #ff2244]✗ COORDINATOR VALIDATION FAILED[/]")
            else:
                self._set_status("[ EXIT ] coord. validation complete")
        except Exception as exc:
            self._log(f"[#ff2244]coord validate: {exc}[/]")


# ── AgentsScreen ──────────────────────────────────────────────────────────────

class AgentsScreen(Screen):  # type: ignore[type-arg,misc]
    """Inspect available agent definitions from agents.json."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    AgentsScreen { background: $bg; }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #ag-title { color: $cyan; text-style: bold; }
    #ag-path  { color: $text_dim; }
    #split    { height: 1fr; layout: horizontal; }
    #ag-table { width: 42; border-right: heavy $green 20%; }
    #ag-detail { width: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: right middle;
        padding: 0 2;
    }
    """

    _BASE_TOOLS = frozenset({
        "read_file", "write_file", "str_replace_based_edit_tool",
        "bash", "write_shared_context",
    })

    def __init__(self) -> None:
        super().__init__()
        self._agents_data: list[dict[str, Any]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ AGENTS ]  Available agent definitions", id="ag-title")
            yield Static("", id="ag-path")
        with Horizontal(id="split"):
            yield DataTable(id="ag-table")
            yield RichLog(id="ag-detail", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  AGENTS"
        agents_path = str(getattr(self.app, "_agents", DEFAULT_AGENTS_PATH))
        self.query_one("#ag-path", Static).update(agents_path)
        table = self.query_one("#ag-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Name",    key="name",    width=22)
        table.add_column("Role",    key="role",    width=12)
        table.add_column("Skills",  key="skills",  width=7)
        self.run_worker(self._load_agents, thread=True, name="load-agents")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _parse_agents(self, data: Any) -> list[dict[str, Any]]:
        if isinstance(data, list):
            return [a for a in data if isinstance(a, dict)]
        if isinstance(data, dict):
            if "agents" in data:
                inner = data["agents"]
                if isinstance(inner, list):
                    return [a for a in inner if isinstance(a, dict)]
                if isinstance(inner, dict):
                    return [{"name": k, **v} for k, v in inner.items() if isinstance(v, dict)]
            return [{"name": k, **v} for k, v in data.items() if isinstance(v, dict)]
        return []

    def _load_agents(self) -> None:
        agents_path = str(getattr(self.app, "_agents", DEFAULT_AGENTS_PATH))
        detail = self.query_one("#ag-detail", RichLog)
        try:
            raw = json.loads(Path(agents_path).read_text(encoding="utf-8"))
        except Exception as exc:
            self.app.call_from_thread(lambda: detail.write(f"[#ff2244]Load error: {exc}[/]"))
            return

        agents = self._parse_agents(raw)
        self._agents_data = agents

        def _fill() -> None:
            table = self.query_one("#ag-table", DataTable)
            for ag in agents:
                name   = str(ag.get("name") or ag.get("role") or "-")
                role   = str(ag.get("role") or "-")
                skills = str(len(ag.get("skills") or ag.get("defaultSkills") or []))
                table.add_row(name[:22], role[:12], skills, key=name)
            if agents:
                self._show_agent_detail(agents[0])

        self.app.call_from_thread(_fill)

    def on_data_table_row_highlighted(self, event: DataTable.RowHighlighted) -> None:
        idx = event.cursor_row
        if 0 <= idx < len(self._agents_data):
            self._show_agent_detail(self._agents_data[idx])

    def _show_agent_detail(self, ag: dict[str, Any]) -> None:
        def _write() -> None:
            detail = self.query_one("#ag-detail", RichLog)
            detail.clear()
            name  = str(ag.get("name") or ag.get("role") or "-")
            role  = str(ag.get("role") or "-")
            model = str(ag.get("modelProfile") or ag.get("model") or "-")
            pfile = str(ag.get("promptFile") or ag.get("systemPromptPath") or "-")
            val_m = str(ag.get("validationMode") or ag.get("workerValidationMode") or "-")
            skills = list(ag.get("skills") or ag.get("defaultSkills") or [])
            tools  = list(ag.get("extraTools") or [])
            out_p  = str(ag.get("outputProfile") or "-")

            detail.write(f"[bold #00e5ff]Agent: {name}[/]")
            detail.write(f"  [#00e5ff]Role            :[/] {role}")
            detail.write(f"  [#00e5ff]Model Profile   :[/] {model}")
            detail.write(f"  [#00e5ff]Output Profile  :[/] {out_p}")
            detail.write(f"  [#00e5ff]Validation Mode :[/] {val_m}")
            detail.write(f"  [#00e5ff]Prompt File     :[/] {pfile}")

            if pfile != "-":
                try:
                    size = Path(pfile).stat().st_size
                    if size > 8192:
                        detail.write(f"  [bold #ff2244]⚠ PROMPT TOO LARGE: {size} bytes (limit 8 KB)[/]")
                    elif size > 6144:
                        detail.write(f"  [#ffaa00]⚠ Prompt large: {size} bytes (warn 6 KB)[/]")
                    else:
                        detail.write(f"  [#00ff41]✓ Prompt size: {size} bytes[/]")
                except OSError:
                    detail.write("  [dim](prompt file not found)[/]")

            if skills:
                detail.write("")
                detail.write(f"  [#00e5ff]Skills ({len(skills)}):[/]")
                for s in skills:
                    detail.write(f"    [#00ff41]•[/] {s}")
                if len(skills) > 4:
                    detail.write(f"    [#ffaa00]⚠ {len(skills)} skills — check stack limit (5 max)[/]")

            if tools:
                detail.write("")
                detail.write(f"  [#00e5ff]Extra Tools ({len(tools)}):[/]")
                for t in tools:
                    tname = str(t.get("name") or t if isinstance(t, str) else "-")
                    tkind = str(t.get("kind") or t.get("type") or "-") if isinstance(t, dict) else "-"
                    col   = "#ff2244" if tname in self._BASE_TOOLS else "#00e5ff"
                    warn  = " [#ff2244]⚠ collides with base tool[/]" if tname in self._BASE_TOOLS else ""
                    detail.write(f"    [{col}]•[/{col}] {tname} ({tkind}){warn}")

        self.app.call_from_thread(_write)


# ── SkillsScreen ──────────────────────────────────────────────────────────────

class SkillsScreen(Screen):  # type: ignore[type-arg,misc]
    """Inspect the skill registry from skills/registry.json."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    SkillsScreen { background: $bg; }
    #top-bar {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #sk-title { color: $cyan; text-style: bold; }
    #sk-path  { color: $text_dim; }
    #split    { height: 1fr; layout: horizontal; }
    #sk-table  { width: 36; border-right: heavy $green 20%; }
    #sk-detail { width: 1fr; }
    #action-bar {
        height: 5;
        background: $bg_input;
        border-top: solid $green 25%;
        align: right middle;
        padding: 0 2;
    }
    """

    _WARN_BYTES  = 6 * 1024
    _ERROR_BYTES = 8 * 1024

    def __init__(self) -> None:
        super().__init__()
        self._skills_data: list[dict[str, Any]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ SKILLS ]  Skill registry", id="sk-title")
            yield Static("", id="sk-path")
        with Horizontal(id="split"):
            yield DataTable(id="sk-table")
            yield RichLog(id="sk-detail", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  SKILLS"
        try:
            from pojo_lens_agents.orchestrator_contracts import DEFAULT_SKILL_REGISTRY_PATH
            skills_path = str(DEFAULT_SKILL_REGISTRY_PATH)
        except ImportError:
            skills_path = "ai/orchestrator/skills/registry.json"
        self.query_one("#sk-path", Static).update(skills_path)
        table = self.query_one("#sk-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Skill", key="name",  width=22)
        table.add_column("Size",  key="size",  width=8)
        table.add_column("State", key="state", width=6)
        self.run_worker(lambda: self._load_skills(skills_path), thread=True, name="load-skills")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _parse_skills(self, data: Any) -> list[dict[str, Any]]:
        if isinstance(data, list):
            return [s for s in data if isinstance(s, dict)]
        if isinstance(data, dict):
            if "skills" in data:
                inner = data["skills"]
                if isinstance(inner, list):
                    return [s for s in inner if isinstance(s, dict)]
                if isinstance(inner, dict):
                    return [{"name": k, **v} for k, v in inner.items() if isinstance(v, dict)]
            return [{"name": k, **v} if isinstance(v, dict) else {"name": k, "description": str(v)}
                    for k, v in data.items()]
        return []

    def _load_skills(self, skills_path: str) -> None:
        detail = self.query_one("#sk-detail", RichLog)
        try:
            raw = json.loads(Path(skills_path).read_text(encoding="utf-8"))
        except Exception as exc:
            self.app.call_from_thread(lambda: detail.write(f"[#ff2244]Load error: {exc}[/]"))
            return

        skills = self._parse_skills(raw)
        self._skills_data = skills

        def _fill() -> None:
            table = self.query_one("#sk-table", DataTable)
            for sk in skills:
                name  = str(sk.get("name") or sk.get("id") or "-")
                pfile = str(sk.get("promptFile") or sk.get("file") or sk.get("path") or "")
                size_str   = "-"
                state_str  = "OK"
                state_color = "#00ff41"
                if pfile:
                    try:
                        sz = Path(pfile).stat().st_size
                        size_str = f"{sz//1024}KB" if sz >= 1024 else f"{sz}B"
                        if sz > self._ERROR_BYTES:
                            state_str, state_color = "ERR", "#ff2244"
                        elif sz > self._WARN_BYTES:
                            state_str, state_color = "WARN", "#ffaa00"
                    except OSError:
                        size_str, state_str, state_color = "?", "?", "#ffaa00"
                state_cell = Text(state_str, style=state_color) if Text is not None else state_str
                table.add_row(name[:22], size_str, state_cell, key=name)
            if skills:
                self._show_skill_detail(skills[0])

        self.app.call_from_thread(_fill)

    def on_data_table_row_highlighted(self, event: DataTable.RowHighlighted) -> None:
        idx = event.cursor_row
        if 0 <= idx < len(self._skills_data):
            self._show_skill_detail(self._skills_data[idx])

    def _show_skill_detail(self, sk: dict[str, Any]) -> None:
        def _write() -> None:
            detail = self.query_one("#sk-detail", RichLog)
            detail.clear()
            name  = str(sk.get("name") or sk.get("id") or "-")
            desc  = str(sk.get("description") or "")
            pfile = str(sk.get("promptFile") or sk.get("file") or sk.get("path") or "-")
            tags  = list(sk.get("tags") or [])

            detail.write(f"[bold #00e5ff]Skill: {name}[/]")
            if desc:
                detail.write(f"  [dim]{desc[:200]}[/]")
            detail.write(f"  [#00e5ff]Prompt File:[/] {pfile}")
            if tags:
                detail.write(f"  [#00e5ff]Tags:[/] {', '.join(tags)}")

            if pfile != "-":
                try:
                    sz = Path(pfile).stat().st_size
                    if sz > self._ERROR_BYTES:
                        detail.write(f"  [bold #ff2244]⚠ TOO LARGE: {sz} bytes (limit {self._ERROR_BYTES//1024} KB)[/]")
                    elif sz > self._WARN_BYTES:
                        detail.write(f"  [#ffaa00]⚠ Large: {sz} bytes (warn {self._WARN_BYTES//1024} KB)[/]")
                    else:
                        detail.write(f"  [#00ff41]✓ Size: {sz} bytes[/]")
                    content_lines = Path(pfile).read_text(encoding="utf-8", errors="replace").splitlines()
                    detail.write("")
                    detail.write("[dim #2a5a3a]── Content preview ──[/]")
                    for ln in content_lines[:20]:
                        detail.write(f"[dim]{ln}[/]")
                    if len(content_lines) > 20:
                        detail.write(f"[dim]... +{len(content_lines)-20} more lines[/]")
                except OSError:
                    detail.write(f"  [dim](file not found)[/]")

        self.app.call_from_thread(_write)


# ── EstimateScreen / EstimateResultScreen ──────────────────────────────────────

class EstimateScreen(Screen):  # type: ignore[type-arg,misc]
    """Entry point for --estimate / --dry-run: enter plan path + choose mode."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    EstimateScreen { align: center middle; }
    #card {
        width: 80; height: auto;
        border: heavy $green 30%;
        background: $bg_panel;
        padding: 2 3;
    }
    #card-title { color: $cyan; text-style: bold; margin-bottom: 1; }
    #mode-list  { height: 5; margin-bottom: 1; }
    .field-label { color: $cyan; margin-top: 1; }
    #btns { height: auto; align: right middle; margin-top: 1; }
    """

    _MODES = [
        ("estimate", "ESTIMATE  — token/cost ranges without a retained run  (fast)"),
        ("dry-run",  "DRY RUN   — tighter prompt-assembly estimate, no retained run"),
    ]

    def __init__(self, plan_path: str = "") -> None:
        super().__init__()
        self._plan_path = plan_path

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static("[ ESTIMATE / DRY RUN ]  Cost and token projection", id="card-title")
            yield Static("Plan file path:", classes="field-label")
            yield Input(value=self._plan_path, placeholder="ai/orchestrator/tasks/my-plan.json", id="plan-input")
            yield Static("Mode:", classes="field-label")
            yield OptionList(*[label for _, label in self._MODES], id="mode-list")
            with Horizontal(id="btns"):
                yield Button("RUN →", id="btn-run", variant="primary")
                yield Button("CANCEL", id="btn-cancel")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  ESTIMATE"
        self.query_one("#mode-list", OptionList).highlighted = 0
        if not self._plan_path:
            self.query_one("#plan-input", Input).focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-run":
            self._submit()
        elif event.button.id == "btn-cancel":
            self.action_go_back()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        self._submit()

    def _submit(self) -> None:
        path = self.query_one("#plan-input", Input).value.strip()
        idx  = int(self.query_one("#mode-list", OptionList).highlighted or 0)
        mode, _ = self._MODES[idx]
        if path:
            self.dismiss(None)
            self.app.push_screen(EstimateResultScreen(path, mode=mode))  # type: ignore[attr-defined]
        else:
            self.app.notify("Enter a plan file path first.", title="Required")  # type: ignore[attr-defined]

    def action_go_back(self) -> None:
        self.dismiss(None)


class EstimateResultScreen(Screen):  # type: ignore[type-arg,misc]
    """Display output from --estimate or --dry-run."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    CSS = """
    EstimateResultScreen { background: $bg; }
    #top-bar {
        height: auto; background: $bg_panel;
        border-bottom: heavy $green 25%; padding: 1 2;
    }
    #est-title { color: $cyan; text-style: bold; }
    #est-plan  { color: $text_dim; }
    #est-log   { height: 1fr; }
    #action-bar {
        height: 5; background: $bg_input;
        border-top: solid $green 25%; align: right middle; padding: 0 2;
    }
    """

    def __init__(self, plan_path: str, *, mode: str = "estimate") -> None:
        super().__init__()
        self._plan_path = plan_path
        self._mode      = mode

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static(f"[ {self._mode.upper()} ]", id="est-title")
            yield Static(self._plan_path, id="est-plan")
        yield RichLog(id="est-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = f"POJOLENS  //  {self._mode.upper()}"
        self.run_worker(self._run_estimate, thread=True, name="estimate")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#est-log", RichLog).write(text))

    def _run_estimate(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return
        runtime_root = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))
        agents       = str(getattr(self.app, "_agents",       DEFAULT_AGENTS_PATH))
        claude_bin   = str(getattr(self.app, "_claude_bin",   "claude"))

        cmd = ["run", self._plan_path,
               "--runtime-root", runtime_root, "--agents", agents,
               "--provider-bin", claude_bin, "--json"]
        if self._mode == "estimate":
            cmd.append("--estimate")
        elif self._mode == "dry-run":
            cmd.append("--dry-run")

        self._log(f"[#00e5ff][ SIGNAL ] {self._mode} starting...[/]")
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            self._log(f"[#ff2244]Arg error: {exc}[/]")
            return

        buf     = _io.StringIO()
        old_out = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload: dict[str, Any] = handlers.get("run", lambda a: {})(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]Error: {exc}[/]")
        finally:
            sys.stdout = old_out
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log(f"[bold #00e5ff]═══ {self._mode.upper()} RESULT ═══[/]")

            estimate = payload.get("estimate") or payload.get("costEstimate") or {}
            if isinstance(estimate, dict) and estimate:
                self._log("[bold #00e5ff]── Cost / Token Estimate ──[/]")
                for k, v in sorted(estimate.items()):
                    self._log(f"  [#00e5ff]{k}:[/] {v}")

            tasks = list(payload.get("tasks") or payload.get("taskEstimates") or [])
            if tasks:
                self._log("")
                self._log("[bold #00e5ff]── Per-Task Estimates ──[/]")
                for t in tasks:
                    tid  = str(t.get("taskId") or t.get("id") or "?")
                    mdl  = str(t.get("model") or t.get("modelProfile") or "-")
                    cost = t.get("estimatedCostUsd") or t.get("costUsd") or t.get("cost")
                    toks = t.get("estimatedTokens") or t.get("tokens")
                    c_s  = f"[#ffaa00]${cost:.5f}[/]" if isinstance(cost, (int, float)) else "-"
                    t_s  = f"{toks:,}" if isinstance(toks, int) else "-"
                    self._log(f"  [#00e5ff]▸ {tid}[/] [{mdl}]  cost {c_s}  tokens {t_s}")

            total = payload.get("totalEstimatedCostUsd") or payload.get("totalCostUsd")
            if total is not None:
                self._log("")
                self._log(f"[bold #ffaa00]Total estimated: ${total:.5f}[/]")

        self._log("")
        self._log(f"[dim #2a5a3a][ EXIT ] {self._mode} complete[/]")


# ── OperatorApp ────────────────────────────────────────────────────────────────

class OperatorApp(App):  # type: ignore[type-arg,misc]
    """Main multi-screen operator console."""

    CSS = _SHARED_CSS

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

    async def action_new_plan(self) -> None:
        goal = await self.push_screen_wait(GoalInputScreen())
        if not goal:
            return
        # ClarificationScreen: collects manual context; AI loop wired in WP76
        clarified = await self.push_screen_wait(ClarificationScreen(goal))
        if clarified is None:
            return
        goal = clarified
        effort = await self.push_screen_wait(EffortSelectScreen(goal))
        if effort is None:
            return
        ws_mode = await self.push_screen_wait(WorkspaceModeScreen(goal, effort))
        if ws_mode is None:
            return
        gov = await self.push_screen_wait(GovernanceScreen(goal, effort, ws_mode))
        if gov is None:
            return
        hitl            = gov.get("hitl",            "batch")
        max_parallel    = int(gov.get("max_parallel", 2))
        budget          = gov.get("budget")
        budget_behavior = gov.get("budget_behavior",  "warn")
        follow_up       = gov.get("follow_up",        "ignore")
        await self.push_screen_wait(PlanRunScreen(
            goal=goal,
            effort=effort,
            workspace_mode=ws_mode,
            hitl=hitl,
            max_parallel=max_parallel,
            budget=budget,
            budget_behavior=budget_behavior,
            follow_up=follow_up,
            handlers=self._handlers,
            parse_args_fn=self._parse_args_fn,
            runtime_root=self._runtime_root,
            agents=self._agents,
            claude_bin=self._claude_bin,
        ))


# ── Entry point ────────────────────────────────────────────────────────────────

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
