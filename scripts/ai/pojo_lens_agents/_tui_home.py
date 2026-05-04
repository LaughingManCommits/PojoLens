from __future__ import annotations

from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, ScrollableContainer
    from textual.reactive import reactive
    from textual.screen import Screen
    from textual.widgets import Footer, Header, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    reactive = lambda v: v  # type: ignore[assignment]

from pojo_lens_agents._tui_theme import _BANNER_ART
from pojo_lens_agents._tui_dashboard import DashboardWidget  # noqa: F401


# ── HomeScreen ─────────────────────────────────────────────────────────────────

class HomeScreen(Screen):  # type: ignore[type-arg,misc]
    """Main navigation hub with live dashboard."""

    BINDINGS = [
        Binding("n",     "new_plan",       "New Plan",    show=False),
        Binding("s",     "saved_plans",    "Saved Plans", show=False),
        Binding("r",     "runs",           "Runs",        show=False),
        Binding("l",     "ledger",         "Ledger",      show=False),
        Binding("v",     "validate",       "Validate",    show=False),
        Binding("d",     "dry_run",        "Dry Run",     show=False),
        Binding("p",     "promote",        "Promote",     show=False),
        Binding("a",     "agents",         "Agents",      show=False),
        Binding("k",     "skills",         "Skills",      show=False),
        Binding("m",     "memory",         "Memory",      show=False),
        Binding("t",     "settings",       "Settings",    show=False),
        Binding("q",     "quit_app",       "Quit",        show=False),
        Binding("up",    "cursor_up",      "Up",          show=False),
        Binding("down",  "cursor_down",    "Down",        show=False),
        Binding("enter", "activate_item",  "Select",      show=False),
    ]

    _MENU_ITEMS = [
        ("n", "CREATE NEW PLAN",  "Generate an AI task plan"),
        ("s", "SAVED PLANS",      "Browse & load saved plans"),
        ("──", None, None),
        ("r", "RUNS",             "Run / resume / retry runs"),
        ("l", "LEDGER",           "Run history & cost summary"),
        ("v", "VALIDATE",         "Validate a plan file"),
        ("d", "DRY RUN",          "Cost estimate, no execution"),
        ("p", "PROMOTE",          "Review diffs & promote"),
        ("──", None, None),
        ("a", "AGENTS",           "Inspect agent definitions"),
        ("k", "SKILLS",           "Inspect skill registry"),
        ("m", "MEMORY TOOLS",     "Refresh / query AI memory"),
        ("t", "SETTINGS",         "View config & providers"),
        ("──", None, None),
        ("q", "QUIT",             "Exit operator console"),
    ]

    # Navigable (non-divider) items: list of (action_key,)
    _NAV_ITEMS: list[str] = [key for key, label, _ in _MENU_ITEMS if label is not None]

    _cursor: reactive[int] = reactive(0)  # type: ignore[assignment]

    DEFAULT_CSS = """
    HomeScreen .menu-row--selected .menu-label {
        color: #00ff41;
        text-style: bold;
    }
    HomeScreen .menu-row--selected .menu-key {
        color: #ffaa00;
        text-style: bold;
    }
    """

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
                    _nav_idx = 0
                    for key, label, desc in self._MENU_ITEMS:
                        if label is None:
                            yield Static("─" * 40, classes="menu-divider")
                        else:
                            with Horizontal(
                                classes="menu-row",
                                id=f"menu-row-{_nav_idx}",
                            ):
                                yield Static(f"[{key.upper()}]", classes="menu-key")
                                yield Static(label, classes="menu-label")
                                yield Static(desc, classes="menu-desc")
                            _nav_idx += 1
            with Container(id="dashboard-panel"):
                yield DashboardWidget(id="dashboard")
        yield Static("", id="status-bar")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  OPERATOR CONSOLE"  # type: ignore[attr-defined]
        self.app.sub_title = "MISSION CONTROL"  # type: ignore[attr-defined]
        try:
            self.query_one("#menu-row-0").add_class("menu-row--selected")
        except Exception:
            pass
        self._refresh_status_bar(0)

    def watch__cursor(self, old: int, new: int) -> None:
        try:
            self.query_one(f"#menu-row-{old}").remove_class("menu-row--selected")
        except Exception:
            pass
        try:
            self.query_one(f"#menu-row-{new}").add_class("menu-row--selected")
        except Exception:
            pass
        self._refresh_status_bar(new)

    def _refresh_status_bar(self, cursor: int) -> None:
        try:
            sb = self.query_one("#status-bar", Static)
            nav_items = self._NAV_ITEMS
            if 0 <= cursor < len(nav_items):
                key = nav_items[cursor]
                item = next(
                    (i for i in self._MENU_ITEMS if i[0] == key and i[1] is not None),
                    None,
                )
                if item:
                    _, label, desc = item
                    sb.update(
                        f"[bold #00e5ff]▶ {label}[/]  [dim]{desc}[/]\n"
                        "[dim #2a5a3a]↑↓ navigate · Enter/letter select · "
                        "[N] new  [S] saved  [R] runs  [V] validate  "
                        "[A] agents  [M] memory  [T] settings  [Q] quit[/]"
                    )
                    return
            sb.update(
                "[dim #2a5a3a]↑↓ navigate · Enter select · "
                "[N] new  [S] saved  [R] runs  [V] validate  "
                "[A] agents  [M] memory  [T] settings  [Q] quit[/]"
            )
        except Exception:
            pass

    def action_cursor_up(self) -> None:
        self._cursor = max(0, self._cursor - 1)  # type: ignore[assignment]

    def action_cursor_down(self) -> None:
        self._cursor = min(len(self._NAV_ITEMS) - 1, self._cursor + 1)  # type: ignore[assignment]

    # ── Actions ────────────────────────────────────────────────────────────────

    def action_saved_plans(self) -> None:
        from pojo_lens_agents._tui_plans import SavedPlansScreen
        self.app.push_screen(SavedPlansScreen())  # type: ignore[attr-defined]

    def action_runs(self) -> None:
        from pojo_lens_agents._tui_ledger import RunLedgerScreen
        self.app.push_screen(RunLedgerScreen(mode="runs"))  # type: ignore[attr-defined]

    def action_ledger(self) -> None:
        from pojo_lens_agents._tui_ledger import RunLedgerScreen
        self.app.push_screen(RunLedgerScreen(mode="ledger"))  # type: ignore[attr-defined]

    async def action_validate(self) -> None:
        from pojo_lens_agents._tui_validate import ValidatePlanScreen, ValidateRunScreen
        path = await self.app.push_screen_wait(ValidatePlanScreen())  # type: ignore[attr-defined]
        if path:
            self.app.push_screen(ValidateRunScreen(path))  # type: ignore[attr-defined]

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
        _dispatch: dict[str, Any] = {
            "n": lambda: self.app.run_worker(self.app.action_new_plan()),  # type: ignore[attr-defined]
            "s": self.action_saved_plans,
            "r": self.action_runs,
            "l": self.action_ledger,
            "v": lambda: self.app.run_worker(self.action_validate()),  # type: ignore[attr-defined]
            "d": self.action_dry_run,
            "p": self.action_promote,
            "a": self.action_agents,
            "k": self.action_skills,
            "m": self.action_memory,
            "t": self.action_settings,
            "q": self.action_quit_app,
        }
        cursor = int(self._cursor)  # type: ignore[arg-type]
        if 0 <= cursor < len(self._NAV_ITEMS):
            fn = _dispatch.get(self._NAV_ITEMS[cursor])
            if fn:
                fn()
