from __future__ import annotations

from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, ScrollableContainer
    from textual.screen import Screen
    from textual.widgets import Footer, Header, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]

from pojo_lens_agents._tui_theme import _BANNER_ART
from pojo_lens_agents._tui_dashboard import DashboardWidget  # noqa: F401


# ── HomeScreen ─────────────────────────────────────────────────────────────────

class HomeScreen(Screen):  # type: ignore[type-arg,misc]
    """Main navigation hub with live dashboard."""

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

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        with Horizontal(id="home-main"):
            with ScrollableContainer(id="nav-panel"):
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
                            yield Static("─" * 40, classes="menu-divider")
                        else:
                            with Horizontal(classes="menu-row"):
                                yield Static(f"[{key.upper()}]", classes="menu-key")
                                yield Static(label, classes="menu-label")
                                yield Static(desc, classes="menu-desc")
            with Container(id="dashboard-panel"):
                yield DashboardWidget(id="dashboard")
        yield Static(
            "[dim #2a5a3a]KEYBOARD: [N] new  [S] saved  [R] runs  [L] ledger  "
            "[V] validate  [D] dry-run  [A] agents  [K] skills  [M] memory  "
            "[T] settings  [Q] quit[/]",
            id="status-bar",
        )
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  OPERATOR CONSOLE"  # type: ignore[attr-defined]
        self.app.sub_title = "MISSION CONTROL"  # type: ignore[attr-defined]

    # ── Actions ────────────────────────────────────────────────────────────────
    # action_new_plan bubbles to OperatorApp.action_new_plan

    def action_saved_plans(self) -> None:
        from pojo_lens_agents._tui_plans import SavedPlansScreen
        self.app.push_screen(SavedPlansScreen())  # type: ignore[attr-defined]

    def action_runs(self) -> None:
        from pojo_lens_agents._tui_ledger import RunLedgerScreen
        self.app.push_screen(RunLedgerScreen(mode="runs"))  # type: ignore[attr-defined]

    def action_ledger(self) -> None:
        from pojo_lens_agents._tui_ledger import RunLedgerScreen
        self.app.push_screen(RunLedgerScreen(mode="ledger"))  # type: ignore[attr-defined]

    def action_validate(self) -> None:
        from pojo_lens_agents._tui_validate import ValidatePlanScreen
        self.app.push_screen(ValidatePlanScreen())  # type: ignore[attr-defined]

    def action_dry_run(self) -> None:
        from pojo_lens_agents._tui_estimate import EstimateScreen
        self.app.push_screen(EstimateScreen())  # type: ignore[attr-defined]

    def action_promote(self) -> None:
        from pojo_lens_agents._tui_ledger import RunLedgerScreen
        self.app.push_screen(RunLedgerScreen(mode="promote"))  # type: ignore[attr-defined]

    def action_agents(self) -> None:
        from pojo_lens_agents._tui_inspect import AgentsScreen
        self.app.push_screen(AgentsScreen())  # type: ignore[attr-defined]

    def action_skills(self) -> None:
        from pojo_lens_agents._tui_inspect import SkillsScreen
        self.app.push_screen(SkillsScreen())  # type: ignore[attr-defined]

    def action_memory(self) -> None:
        from pojo_lens_agents._tui_tools import MemoryToolsScreen
        self.app.push_screen(MemoryToolsScreen())  # type: ignore[attr-defined]

    def action_settings(self) -> None:
        from pojo_lens_agents._tui_tools import SettingsScreen
        self.app.push_screen(SettingsScreen())  # type: ignore[attr-defined]

    def action_quit_app(self) -> None:
        self.app.exit(0)  # type: ignore[attr-defined]

    def action_activate_item(self) -> None:
        self.app.run_worker(self.app.action_new_plan())  # type: ignore[attr-defined]
