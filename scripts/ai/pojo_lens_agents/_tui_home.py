from __future__ import annotations

from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, ScrollableContainer, Vertical
    from textual.reactive import reactive
    from textual.screen import Screen
    from textual.widgets import Button, Footer, Header, Static
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
        Binding("p",     "saved_plans",    "Plans",       show=False),
        Binding("r",     "runs",           "Runs",        show=False),
        Binding("a",     "agents",         "Agents",      show=False),
        Binding("s",     "skills",         "Skills",      show=False),
        Binding("m",     "memory",         "Memory",      show=False),
        Binding("c",     "settings",       "Config",      show=False),
        Binding("q",     "quit_app",       "Quit",        show=False),
        Binding("z",     "toggle_nav",     "Nav",         show=False),
        Binding("up",    "cursor_up",      "Up",          show=False),
        Binding("down",  "cursor_down",    "Down",        show=False),
        Binding("enter", "activate_item",  "Select",      show=False),
    ]

    _MENU_ITEMS = [
        ("n", "NEW PLAN",     "Generate an AI task plan"),
        ("p", "PLANS",        "Browse & load saved plans"),
        ("──", None, None),
        ("r", "RUNS",         "Run / resume / retry / promote"),
        ("──", None, None),
        ("a", "AGENTS",       "Inspect & edit agent definitions"),
        ("s", "SKILLS",       "Inspect & edit skill registry"),
        ("m", "MEMORY TOOLS", "Refresh / query AI memory"),
        ("c", "CONFIG",       "Config, tokens & providers"),
        ("──", None, None),
        ("q", "QUIT",         "Exit operator console"),
    ]

    # Navigable (non-divider) items: list of (action_key,)
    _NAV_ITEMS: list[str] = [key for key, label, _ in _MENU_ITEMS if label is not None]

    _cursor: reactive[int] = reactive(0)  # type: ignore[assignment]
    _nav_collapsed: reactive[bool] = reactive(False)  # type: ignore[assignment]

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
            with Vertical(id="nav-panel"):
                with Horizontal(id="nav-hdr"):
                    yield Static("[ NAV ]", id="nav-hdr-title")
                    yield Button("<", id="btn-nav-toggle")
                with ScrollableContainer(id="nav-scroll"):
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
                        "[N] new  [P] plans  [R] runs  [A] agents  [S] skills  [M] memory  [C] config  [Q] quit[/]"
                    )
                    return
            sb.update(
                "[dim #2a5a3a]↑↓ navigate · Enter select · "
                "[N] new  [P] plans  [R] runs  [A] agents  [S] skills  [M] memory  [C] config  [Q] quit[/]"
            )
        except Exception:
            pass

    def action_cursor_up(self) -> None:
        self._cursor = max(0, self._cursor - 1)  # type: ignore[assignment]

    def action_cursor_down(self) -> None:
        self._cursor = min(len(self._NAV_ITEMS) - 1, self._cursor + 1)  # type: ignore[assignment]

    def watch__nav_collapsed(self, collapsed: bool) -> None:
        if collapsed:
            self.add_class("nav-collapsed")
        else:
            self.remove_class("nav-collapsed")
        try:
            btn = self.query_one("#btn-nav-toggle", Button)
            btn.label = ">" if collapsed else "<"
        except Exception:
            pass

    def action_toggle_nav(self) -> None:
        self._nav_collapsed = not self._nav_collapsed  # type: ignore[assignment]

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-nav-toggle":
            self.action_toggle_nav()

    # ── Actions ────────────────────────────────────────────────────────────────

    def action_saved_plans(self) -> None:
        from pojo_lens_agents._tui_plans import SavedPlansScreen
        self.app.push_screen(SavedPlansScreen())  # type: ignore[attr-defined]

    def action_runs(self) -> None:
        from pojo_lens_agents._tui_ledger import RunLedgerScreen
        self.app.push_screen(RunLedgerScreen(mode="runs"))  # type: ignore[attr-defined]

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

    def action_new_plan(self) -> None:
        self.app.run_worker(self.app.action_new_plan(), name="wizard")  # type: ignore[attr-defined]

    def action_quit_app(self) -> None:
        self.app.exit(0)  # type: ignore[attr-defined]

    def action_activate_item(self) -> None:
        _dispatch: dict[str, Any] = {
            "n": self.action_new_plan,
            "p": self.action_saved_plans,
            "r": self.action_runs,
            "a": self.action_agents,
            "s": self.action_skills,
            "m": self.action_memory,
            "c": self.action_settings,
            "q": self.action_quit_app,
        }
        cursor = int(self._cursor)  # type: ignore[arg-type]
        if 0 <= cursor < len(self._NAV_ITEMS):
            fn = _dispatch.get(self._NAV_ITEMS[cursor])
            if fn:
                fn()
