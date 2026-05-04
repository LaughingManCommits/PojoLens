from __future__ import annotations

import json
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from rich.text import Text
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.screen import Screen
    from textual.widgets import Button, DataTable, Footer, Header, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

try:
    from pojo_lens_agents.orchestrator_contracts import (
        DEFAULT_AGENTS_PATH,
    )
except ImportError:  # pragma: no cover
    DEFAULT_AGENTS_PATH = Path("ai/orchestrator/agents.json")


# ── AgentsScreen ──────────────────────────────────────────────────────────────

class AgentsScreen(Screen):  # type: ignore[type-arg,misc]
    """Inspect available agent definitions from agents.json."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

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
