from __future__ import annotations

import json
from pathlib import Path
from typing import Any

try:
    from pojo_lens_agents._tui_helpers import _load_plan_json
except ImportError:  # pragma: no cover
    def _load_plan_json(path: str) -> dict[str, Any] | None:  # type: ignore[misc]
        try:
            return json.loads(Path(path).read_text(encoding="utf-8"))
        except Exception:
            return None

TEXTUAL_IMPORT_ERROR: Exception | None = None
_TEXTAREA_AVAILABLE = False

_SELECTIONLIST_AVAILABLE = False

try:
    from rich.text import Text
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, Vertical, VerticalScroll
    from textual.screen import Screen
    from textual.widgets import Button, DataTable, Footer, Header, Input, RichLog, Select, Static
    try:
        from textual.widgets import TextArea
        _TEXTAREA_AVAILABLE = True
    except ImportError:
        pass
    try:
        from textual.widgets import SelectionList
        _SELECTIONLIST_AVAILABLE = True
    except ImportError:
        pass
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

try:
    from pojo_lens_agents.orchestrator_contracts import DEFAULT_AGENTS_PATH
except ImportError:  # pragma: no cover
    DEFAULT_AGENTS_PATH = Path("ai/orchestrator/agents.json")


# ── AgentsScreen ──────────────────────────────────────────────────────────────

class AgentsScreen(Screen):  # type: ignore[type-arg,misc]
    """Inspect available agent definitions from agents.json."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]
    _PAGE_SIZE = 5

    _BASE_TOOLS = frozenset({
        "read_file", "write_file", "str_replace_based_edit_tool",
        "bash", "write_shared_context",
    })

    def __init__(self) -> None:
        super().__init__()
        self._agents_data: list[dict[str, Any]] = []
        self._agents_path: str = ""
        self._page: int = 0

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ AGENTS ]  Available agent definitions", id="ag-title")
            yield Static("", id="ag-path")
        with Vertical(id="ag-split"):
            yield DataTable(id="ag-table")
            with Horizontal(id="ag-pages"):
                yield Button("◀ PREV", id="btn-ag-prev", disabled=True)
                yield Static("", id="ag-page-label")
                yield Button("NEXT ▶", id="btn-ag-next", disabled=True)
            yield RichLog(id="ag-detail", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("NEW",   id="btn-ag-new",  variant="primary")
            yield Button("EDIT",  id="btn-ag-edit", variant="success", disabled=True)
            yield Button("BACK",  id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  AGENTS"
        self._agents_path = str(getattr(self.app, "_agents", DEFAULT_AGENTS_PATH))
        self.query_one("#ag-path", Static).update(self._agents_path)
        table = self.query_one("#ag-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Name",        key="name",   width=24)
        table.add_column("Model",       key="model",  width=12)
        table.add_column("Skills",      key="skills", width=7)
        table.add_column("Effort",      key="effort", width=7)
        table.add_column("Workspace",   key="ws",     width=10)
        self.run_worker(self._load_agents, thread=True, name="load-agents")

    def _total_pages(self) -> int:
        total = len(self._agents_data)
        return max(1, (total + self._PAGE_SIZE - 1) // self._PAGE_SIZE)

    def _render_page(self) -> None:
        table = self.query_one("#ag-table", DataTable)
        table.clear()
        start = self._page * self._PAGE_SIZE
        page_agents = self._agents_data[start: start + self._PAGE_SIZE]
        for ag in page_agents:
            name   = str(ag.get("name") or ag.get("role") or "-")
            model  = str(ag.get("modelProfile") or ag.get("model") or "-")
            skills = str(len(ag.get("skills") or ag.get("defaultSkills") or []))
            effort = str(ag.get("effort") or "-")
            ws     = str(ag.get("workspaceMode") or "-")
            table.add_row(name[:24], model[:12], skills, effort[:7], ws[:10], key=name)
        total = self._total_pages()
        self.query_one("#ag-page-label", Static).update(
            f"  Page {self._page + 1} / {total}  ({len(self._agents_data)} agents)  "
        )
        self.query_one("#btn-ag-prev", Button).disabled = self._page == 0
        self.query_one("#btn-ag-next", Button).disabled = self._page >= total - 1
        if page_agents:
            self._show_agent_detail(page_agents[0])

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-ag-prev":
            self._page = max(0, self._page - 1)
            self._render_page()
        elif event.button.id == "btn-ag-next":
            self._page = min(self._total_pages() - 1, self._page + 1)
            self._render_page()
        elif event.button.id == "btn-ag-new":
            self.app.push_screen(  # type: ignore[attr-defined]
                AgentEditScreen(self._agents_path, {}, is_new=True),
                self._on_edit_done,
            )
        elif event.button.id == "btn-ag-edit":
            row = self.query_one("#ag-table", DataTable).cursor_row
            start = self._page * self._PAGE_SIZE
            idx   = start + row
            if 0 <= idx < len(self._agents_data):
                self.app.push_screen(  # type: ignore[attr-defined]
                    AgentEditScreen(self._agents_path, self._agents_data[idx], is_new=False),
                    self._on_edit_done,
                )
        elif event.button.id == "btn-back":
            self.action_go_back()

    def _on_edit_done(self, _result: Any) -> None:
        self._agents_data = []
        self._page = 0
        self.run_worker(self._load_agents, thread=True, name="load-agents-reload")

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
        detail = self.query_one("#ag-detail", RichLog)
        try:
            raw = json.loads(Path(self._agents_path).read_text(encoding="utf-8"))
        except Exception as exc:
            self.app.call_from_thread(lambda: detail.write(f"[#ff2244]Load error: {exc}[/]"))
            return
        self._agents_data = self._parse_agents(raw)
        self.app.call_from_thread(self._render_page)

    def on_data_table_row_highlighted(self, event: DataTable.RowHighlighted) -> None:
        start = self._page * self._PAGE_SIZE
        idx   = start + event.cursor_row
        if 0 <= idx < len(self._agents_data):
            self._show_agent_detail(self._agents_data[idx])
            try:
                self.query_one("#btn-ag-edit", Button).disabled = False
            except Exception:
                pass

    def _show_agent_detail(self, ag: dict[str, Any]) -> None:
        detail = self.query_one("#ag-detail", RichLog)
        detail.clear()
        name    = str(ag.get("name") or ag.get("role") or "-")
        model   = str(ag.get("modelProfile") or ag.get("model") or "-")
        pfile   = str(ag.get("promptFile") or ag.get("systemPromptPath") or "-")
        val_m   = str(ag.get("validationMode") or ag.get("workerValidationMode") or "-")
        effort  = str(ag.get("effort") or "-")
        ws      = str(ag.get("workspaceMode") or "-")
        out_p   = str(ag.get("outputProfile") or "-")
        desc    = str(ag.get("description") or "")
        skills  = list(ag.get("skills") or ag.get("defaultSkills") or [])
        tools   = list(ag.get("allowedTools") or ag.get("extraTools") or [])
        detail.write(f"[bold #00e5ff]{name}[/]")
        if desc:
            detail.write(f"  [dim]{desc}[/]")
        detail.write(f"  [#00e5ff]Model     :[/] {model}   [#00e5ff]Effort:[/] {effort}   [#00e5ff]Workspace:[/] {ws}")
        detail.write(f"  [#00e5ff]Output    :[/] {out_p}   [#00e5ff]Validation:[/] {val_m}")
        detail.write(f"  [#00e5ff]Prompt    :[/] {pfile}")
        if pfile != "-":
            ag_dir = Path(self._agents_path).parent
            resolved = Path(pfile) if Path(pfile).is_absolute() else ag_dir / pfile
            try:
                size = resolved.stat().st_size
                if size > 8192:
                    detail.write(f"  [bold #ff2244]⚠ PROMPT TOO LARGE: {size} bytes[/]")
                elif size > 6144:
                    detail.write(f"  [#ffaa00]⚠ Prompt large: {size} bytes[/]")
                else:
                    detail.write(f"  [#00ff41]✓ {size} bytes[/]")
            except OSError:
                detail.write("  [dim](prompt file not found)[/]")
        if skills:
            detail.write(f"  [#00e5ff]Skills    :[/] {', '.join(skills)}")
            if len(skills) > 4:
                detail.write(f"  [#ffaa00]⚠ {len(skills)} skills — check stack limit (5 max)[/]")
        if tools:
            detail.write(f"  [#00e5ff]Tools     :[/] {', '.join(str(t) for t in tools)}")


# ── SkillsScreen ──────────────────────────────────────────────────────────────

class SkillsScreen(Screen):  # type: ignore[type-arg,misc]
    """Inspect the skill registry from skills/registry.json."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]
    _PAGE_SIZE   = 5
    _WARN_BYTES  = 6 * 1024
    _ERROR_BYTES = 8 * 1024

    def __init__(self) -> None:
        super().__init__()
        self._skills_data: list[dict[str, Any]] = []
        self._skills_path: str = ""
        self._skills_dir:  Path = Path(".")
        self._page: int = 0

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ SKILLS ]  Skill registry", id="sk-title")
            yield Static("", id="sk-path")
        with Vertical(id="sk-split"):
            yield DataTable(id="sk-table")
            with Horizontal(id="sk-pages"):
                yield Button("◀ PREV", id="btn-sk-prev", disabled=True)
                yield Static("", id="sk-page-label")
                yield Button("NEXT ▶", id="btn-sk-next", disabled=True)
            yield RichLog(id="sk-detail", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("NEW",  id="btn-sk-new",  variant="primary")
            yield Button("EDIT", id="btn-sk-edit", variant="success", disabled=True)
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  SKILLS"
        try:
            from pojo_lens_agents.orchestrator_contracts import DEFAULT_SKILL_REGISTRY_PATH
            self._skills_path = str(DEFAULT_SKILL_REGISTRY_PATH)
        except ImportError:
            self._skills_path = "ai/orchestrator/skills/registry.json"
        self._skills_dir = Path(self._skills_path).parent
        self.query_one("#sk-path", Static).update(self._skills_path)
        table = self.query_one("#sk-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Skill",       key="name",  width=24)
        table.add_column("Size",        key="size",  width=8)
        table.add_column("State",       key="state", width=6)
        table.add_column("Description", key="desc",  width=40)
        self.run_worker(lambda: self._load_skills(self._skills_path), thread=True, name="load-skills")

    def _total_pages(self) -> int:
        return max(1, (len(self._skills_data) + self._PAGE_SIZE - 1) // self._PAGE_SIZE)

    def _render_page(self) -> None:
        table      = self.query_one("#sk-table", DataTable)
        skills_dir = self._skills_dir
        table.clear()
        start  = self._page * self._PAGE_SIZE
        page_skills = self._skills_data[start: start + self._PAGE_SIZE]
        for sk in page_skills:
            name  = str(sk.get("name") or sk.get("id") or "-")
            pfile = str(sk.get("promptFile") or sk.get("file") or sk.get("path") or "")
            desc  = str(sk.get("description") or "")
            size_str    = "-"
            state_str   = "OK"
            state_color = "#00ff41"
            if pfile:
                resolved = Path(pfile) if Path(pfile).is_absolute() else skills_dir / pfile
                try:
                    sz = resolved.stat().st_size
                    size_str = f"{sz//1024}KB" if sz >= 1024 else f"{sz}B"
                    if sz > self._ERROR_BYTES:
                        state_str, state_color = "ERR", "#ff2244"
                    elif sz > self._WARN_BYTES:
                        state_str, state_color = "WARN", "#ffaa00"
                except OSError:
                    size_str, state_str, state_color = "miss", "MISS", "#ff2244"
            state_cell = Text(state_str, style=state_color) if Text is not None else state_str
            table.add_row(name[:24], size_str, state_cell, desc[:40], key=name)
        total = self._total_pages()
        self.query_one("#sk-page-label", Static).update(
            f"  Page {self._page + 1} / {total}  ({len(self._skills_data)} skills)  "
        )
        self.query_one("#btn-sk-prev", Button).disabled = self._page == 0
        self.query_one("#btn-sk-next", Button).disabled = self._page >= total - 1
        if page_skills:
            self._show_skill_detail(page_skills[0])

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-sk-prev":
            self._page = max(0, self._page - 1)
            self._render_page()
        elif event.button.id == "btn-sk-next":
            self._page = min(self._total_pages() - 1, self._page + 1)
            self._render_page()
        elif event.button.id == "btn-sk-new":
            self.app.push_screen(  # type: ignore[attr-defined]
                SkillEditScreen(self._skills_path, {}, is_new=True),
                self._on_edit_done,
            )
        elif event.button.id == "btn-sk-edit":
            row   = self.query_one("#sk-table", DataTable).cursor_row
            start = self._page * self._PAGE_SIZE
            idx   = start + row
            if 0 <= idx < len(self._skills_data):
                self.app.push_screen(  # type: ignore[attr-defined]
                    SkillEditScreen(self._skills_path, self._skills_data[idx], is_new=False),
                    self._on_edit_done,
                )
        elif event.button.id == "btn-back":
            self.action_go_back()

    def _on_edit_done(self, _result: Any) -> None:
        self._skills_data = []
        self._page = 0
        self.run_worker(lambda: self._load_skills(self._skills_path), thread=True, name="load-skills-reload")

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
        self._skills_data = self._parse_skills(raw)
        self.app.call_from_thread(self._render_page)

    def on_data_table_row_highlighted(self, event: DataTable.RowHighlighted) -> None:
        start = self._page * self._PAGE_SIZE
        idx   = start + event.cursor_row
        if 0 <= idx < len(self._skills_data):
            self._show_skill_detail(self._skills_data[idx])
            try:
                self.query_one("#btn-sk-edit", Button).disabled = False
            except Exception:
                pass

    def _show_skill_detail(self, sk: dict[str, Any]) -> None:
        detail = self.query_one("#sk-detail", RichLog)
        detail.clear()
        name  = str(sk.get("name") or sk.get("id") or "-")
        desc  = str(sk.get("description") or "")
        pfile = str(sk.get("promptFile") or sk.get("file") or sk.get("path") or "-")
        tags  = list(sk.get("tags") or [])
        detail.write(f"[bold #00e5ff]{name}[/]")
        if desc:
            detail.write(f"  [dim]{desc}[/]")
        detail.write(f"  [#00e5ff]Prompt File:[/] {pfile}")
        if tags:
            detail.write(f"  [#00e5ff]Tags:[/] {', '.join(tags)}")
        if pfile != "-":
            resolved = Path(pfile) if Path(pfile).is_absolute() else self._skills_dir / pfile
            try:
                sz = resolved.stat().st_size
                if sz > self._ERROR_BYTES:
                    detail.write(f"  [bold #ff2244]⚠ TOO LARGE: {sz} bytes[/]")
                elif sz > self._WARN_BYTES:
                    detail.write(f"  [#ffaa00]⚠ Large: {sz} bytes[/]")
                else:
                    detail.write(f"  [#00ff41]✓ {sz} bytes[/]")
                lines = resolved.read_text(encoding="utf-8", errors="replace").splitlines()
                detail.write("")
                detail.write("[dim #2a5a3a]── Content preview ──[/]")
                for ln in lines[:20]:
                    detail.write(f"[dim]{ln}[/]")
                if len(lines) > 20:
                    detail.write(f"[dim]... +{len(lines)-20} more lines[/]")
            except OSError:
                detail.write("  [dim](file not found)[/]")


# ── AgentEditScreen ───────────────────────────────────────────────────────────

_MODEL_OPTS  = [("(unset)",""),("simple","simple"),("balanced","balanced"),("power","power"),("standard","standard")]
_EFFORT_OPTS = [("(unset)",""),("low","low"),("medium","medium"),("high","high")]
_WS_OPTS     = [("(unset)",""),("repo","repo"),("copy","copy"),("scratch","scratch")]
_CTX_OPTS    = [("(unset)",""),("minimal","minimal"),("full","full")]
_PERM_OPTS   = [("(unset)",""),("dontAsk","dontAsk"),("ask","ask")]
_OUT_OPTS    = [("(unset)",""),("lean","lean"),("standard","standard"),("full","full")]

_KNOWN_TOOLS = [
    "Read", "Write", "Edit", "MultiEdit", "Glob", "Grep", "Bash",
    "WebFetch", "WebSearch", "Agent", "TodoRead", "TodoWrite",
    "NotebookRead", "NotebookEdit",
]


def _load_skill_names() -> list[str]:
    try:
        from pojo_lens_agents.orchestrator_contracts import DEFAULT_SKILL_REGISTRY_PATH
        raw = json.loads(Path(str(DEFAULT_SKILL_REGISTRY_PATH)).read_text(encoding="utf-8"))
        skills = raw.get("skills") or {}
        if isinstance(skills, dict):
            return list(skills.keys())
    except Exception:
        pass
    return []


class AgentEditScreen(Screen):  # type: ignore[type-arg,misc]
    """Create or edit an agent definition in agents.json."""

    BINDINGS = [Binding("escape", "go_back", "Cancel", show=True)]

    def __init__(self, agents_path: str, agent: dict[str, Any], *, is_new: bool) -> None:
        super().__init__()
        self._agents_path     = agents_path
        self._agent           = dict(agent)
        self._is_new          = is_new
        self._available_skills = _load_skill_names()
        self._cur_skills      = list(agent.get("skills") or [])
        self._cur_tools       = list(agent.get("allowedTools") or [])

    def compose(self) -> ComposeResult:
        title = "[ NEW AGENT ]" if self._is_new else "[ EDIT AGENT ]"
        desc  = str(self._agent.get("description") or "")
        yield Header()
        with Container(id="top-bar"):
            yield Static(title, id="ag-edit-title")
            yield Static(self._agents_path, id="ag-edit-path")
        with VerticalScroll(id="set-form"):
            yield Static("name", classes="set-label")
            yield Input(id="f-ag-name", placeholder="my-agent", value=str(self._agent.get("name") or ""))
            yield Static("description", classes="set-label")
            if _TEXTAREA_AVAILABLE:
                yield TextArea(desc, id="f-ag-desc")  # type: ignore[arg-type]
            else:
                yield Input(id="f-ag-desc", placeholder="What this agent does", value=desc)
            yield Static("promptFile  (relative to agents.json dir)", classes="set-label")
            yield Input(id="f-ag-prompt", placeholder="agents/my-agent/prompt.md",
                        value=str(self._agent.get("promptFile") or ""))
            yield Static("modelProfile", classes="set-label")
            yield Select(_MODEL_OPTS, id="f-ag-model", allow_blank=False)
            yield Static("effort", classes="set-label")
            yield Select(_EFFORT_OPTS, id="f-ag-effort", allow_blank=False)
            yield Static("workspaceMode", classes="set-label")
            yield Select(_WS_OPTS, id="f-ag-ws", allow_blank=False)
            yield Static("contextMode", classes="set-label")
            yield Select(_CTX_OPTS, id="f-ag-ctx", allow_blank=False)
            yield Static("permissionMode", classes="set-label")
            yield Select(_PERM_OPTS, id="f-ag-perm", allow_blank=False)
            yield Static("outputProfile", classes="set-label")
            yield Select(_OUT_OPTS, id="f-ag-out", allow_blank=False)
            yield Static("skills  (space = toggle)", classes="set-label")
            if _SELECTIONLIST_AVAILABLE:
                yield SelectionList(  # type: ignore[misc]
                    *[(s, s, s in self._cur_skills) for s in (self._available_skills or self._cur_skills)],
                    id="f-ag-skills",
                )
            else:
                yield Input(id="f-ag-skills", placeholder="caveman, docs",
                            value=", ".join(self._cur_skills))
            yield Static("allowedTools  (space = toggle)", classes="set-label")
            if _SELECTIONLIST_AVAILABLE:
                # include any tools already set that aren't in the known list
                all_tools = list(dict.fromkeys(_KNOWN_TOOLS + self._cur_tools))
                yield SelectionList(  # type: ignore[misc]
                    *[(t, t, t in self._cur_tools) for t in all_tools],
                    id="f-ag-tools",
                )
            else:
                yield Input(id="f-ag-tools", placeholder="Read, Grep, Glob",
                            value=", ".join(self._cur_tools))
            yield Static("maxPromptEstimatedTokens", classes="set-label")
            yield Input(id="f-ag-maxtok", placeholder="1600",
                        value=str(self._agent.get("maxPromptEstimatedTokens") or ""))
            yield Static("timeoutSec", classes="set-label")
            yield Input(id="f-ag-timeout", placeholder="1800",
                        value=str(self._agent.get("timeoutSec") or ""))
        with Horizontal(id="action-bar"):
            yield Button("SAVE", id="btn-save", variant="primary")
            yield Button("CANCEL", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  AGENT EDITOR"
        self._set_select("f-ag-model",  str(self._agent.get("modelProfile")   or ""))
        self._set_select("f-ag-effort", str(self._agent.get("effort")         or ""))
        self._set_select("f-ag-ws",     str(self._agent.get("workspaceMode")  or ""))
        self._set_select("f-ag-ctx",    str(self._agent.get("contextMode")    or ""))
        self._set_select("f-ag-perm",   str(self._agent.get("permissionMode") or ""))
        self._set_select("f-ag-out",    str(self._agent.get("outputProfile")  or ""))

    def _set_select(self, wid: str, value: str) -> None:
        try:
            self.query_one(f"#{wid}", Select).value = value or Select.BLANK
        except Exception:
            pass

    def _get_input(self, wid: str) -> str:
        try:
            return self.query_one(f"#{wid}", Input).value.strip()
        except Exception:
            return ""

    def _get_select(self, wid: str) -> str:
        try:
            v = self.query_one(f"#{wid}", Select).value
            return "" if v is Select.BLANK else str(v)
        except Exception:
            return ""

    def _get_desc(self) -> str:
        if _TEXTAREA_AVAILABLE:
            try:
                return self.query_one("#f-ag-desc", TextArea).text.strip()  # type: ignore[attr-defined]
            except Exception:
                pass
        return self._get_input("f-ag-desc")

    def _get_multiselect(self, wid: str) -> list[str]:
        if _SELECTIONLIST_AVAILABLE:
            try:
                return list(self.query_one(f"#{wid}", SelectionList).selected)  # type: ignore[attr-defined]
            except Exception:
                pass
        # fallback: csv Input
        raw = self._get_input(wid)
        return [v.strip() for v in raw.split(",") if v.strip()]

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-save":
            self._save()
        elif event.button.id == "btn-back":
            self.dismiss(None)

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _save(self) -> None:
        name = self._get_input("f-ag-name")
        if not name:
            self.app.notify("Agent name is required.", title="Validation", severity="error")  # type: ignore[attr-defined]
            return

        entry: dict[str, Any] = {}
        desc        = self._get_desc()
        prompt_file = self._get_input("f-ag-prompt")
        model       = self._get_select("f-ag-model")
        effort      = self._get_select("f-ag-effort")
        ws_mode     = self._get_select("f-ag-ws")
        ctx_mode    = self._get_select("f-ag-ctx")
        perm_mode   = self._get_select("f-ag-perm")
        out_profile = self._get_select("f-ag-out")
        skills      = self._get_multiselect("f-ag-skills")
        tools       = self._get_multiselect("f-ag-tools")
        maxtok_s    = self._get_input("f-ag-maxtok")
        timeout_s   = self._get_input("f-ag-timeout")

        if desc:         entry["description"]   = desc
        if prompt_file:  entry["promptFile"]     = prompt_file
        if model:        entry["modelProfile"]   = model
        if effort:       entry["effort"]         = effort
        if ws_mode:      entry["workspaceMode"]  = ws_mode
        if ctx_mode:     entry["contextMode"]    = ctx_mode
        if perm_mode:    entry["permissionMode"] = perm_mode
        if out_profile:  entry["outputProfile"]  = out_profile
        if skills:       entry["skills"]         = skills
        if tools:        entry["allowedTools"]   = tools
        if maxtok_s:
            try:
                entry["maxPromptEstimatedTokens"] = int(maxtok_s)
            except ValueError:
                self.app.notify("maxPromptEstimatedTokens must be an integer.", title="Validation", severity="error")  # type: ignore[attr-defined]
                return
        if timeout_s:
            try:
                entry["timeoutSec"] = int(timeout_s)
            except ValueError:
                self.app.notify("timeoutSec must be an integer.", title="Validation", severity="error")  # type: ignore[attr-defined]
                return

        agents_path = Path(self._agents_path)
        try:
            raw: dict = json.loads(agents_path.read_text(encoding="utf-8")) if agents_path.exists() else {}
        except Exception as exc:
            self.app.notify(f"Read error: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]
            return

        if "agents" not in raw or not isinstance(raw["agents"], dict):
            raw["agents"] = {}
        if "version" not in raw:
            raw["version"] = 1

        old_name = str(self._agent.get("name") or "")
        if not self._is_new and old_name and old_name != name and old_name in raw["agents"]:
            del raw["agents"][old_name]
        raw["agents"][name] = entry

        try:
            agents_path.write_text(json.dumps(raw, indent=2), encoding="utf-8")
            self.app.notify(f"Agent '{name}' saved.", title="Saved")  # type: ignore[attr-defined]
            self.dismiss(True)
        except Exception as exc:
            self.app.notify(f"Write error: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]


# ── SkillEditScreen ────────────────────────────────────────────────────────────

class SkillEditScreen(Screen):  # type: ignore[type-arg,misc]
    """Create or edit a skill entry in the skill registry."""

    BINDINGS = [Binding("escape", "go_back", "Cancel", show=True)]

    def __init__(self, registry_path: str, skill: dict[str, Any], *, is_new: bool) -> None:
        super().__init__()
        self._registry_path = registry_path
        self._registry_dir  = Path(registry_path).parent
        self._skill         = dict(skill)
        self._is_new        = is_new

    def compose(self) -> ComposeResult:
        title = "[ NEW SKILL ]" if self._is_new else "[ EDIT SKILL ]"
        name  = str(self._skill.get("name") or "")
        pfile = str(self._skill.get("promptFile") or self._skill.get("file") or "")
        desc  = str(self._skill.get("description") or "")
        tags  = ", ".join(self._skill.get("tags") or [])

        prompt_content = ""
        if pfile:
            resolved = Path(pfile) if Path(pfile).is_absolute() else self._registry_dir / pfile
            try:
                prompt_content = resolved.read_text(encoding="utf-8")
            except OSError:
                pass

        yield Header()
        with Container(id="top-bar"):
            yield Static(title, id="sk-edit-title")
            yield Static(self._registry_path, id="sk-edit-path")
        with VerticalScroll(id="set-form"):
            yield Static("name (key in registry.json)", classes="set-label")
            yield Input(id="f-sk-name",  placeholder="my-skill", value=name)
            yield Static("description", classes="set-label")
            if _TEXTAREA_AVAILABLE:
                yield TextArea(desc, id="f-sk-desc")  # type: ignore[arg-type]
            else:
                yield Input(id="f-sk-desc",  placeholder="What this skill does", value=desc)
            yield Static("promptFile  (relative to registry.json dir, e.g. my-skill/SKILL.md)", classes="set-label")
            yield Input(id="f-sk-pfile", placeholder="my-skill/SKILL.md", value=pfile)
            yield Static("tags  (comma-separated, optional)", classes="set-label")
            yield Input(id="f-sk-tags",  placeholder="java, testing", value=tags)
            yield Static("prompt content  (saved to promptFile path on SAVE)", classes="set-label")
            if _TEXTAREA_AVAILABLE:
                yield TextArea(prompt_content, id="f-sk-content", language="markdown")  # type: ignore[arg-type]
            else:
                yield Input(id="f-sk-content", value=prompt_content,
                            placeholder="(TextArea unavailable — edit file directly)")
        with Horizontal(id="action-bar"):
            yield Button("SAVE", id="btn-save", variant="primary")
            yield Button("CANCEL", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  SKILL EDITOR"

    def _get_input(self, wid: str) -> str:
        try:
            return self.query_one(f"#{wid}", Input).value.strip()
        except Exception:
            return ""

    def _get_sk_desc(self) -> str:
        if _TEXTAREA_AVAILABLE:
            try:
                return self.query_one("#f-sk-desc", TextArea).text.strip()  # type: ignore[attr-defined]
            except Exception:
                pass
        return self._get_input("f-sk-desc")

    def _get_content(self) -> str:
        if _TEXTAREA_AVAILABLE:
            try:
                return self.query_one("#f-sk-content", TextArea).text  # type: ignore[attr-defined]
            except Exception:
                pass
        return self._get_input("f-sk-content")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-save":
            self._save()
        elif event.button.id == "btn-back":
            self.dismiss(None)

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _save(self) -> None:
        name    = self._get_input("f-sk-name")
        desc    = self._get_sk_desc()
        pfile   = self._get_input("f-sk-pfile")
        tags_s  = self._get_input("f-sk-tags")
        content = self._get_content()

        if not name:
            self.app.notify("Skill name is required.", title="Validation", severity="error")  # type: ignore[attr-defined]
            return
        if not pfile:
            self.app.notify("promptFile is required.", title="Validation", severity="error")  # type: ignore[attr-defined]
            return

        # Load, update, write registry.json
        reg_path = Path(self._registry_path)
        try:
            raw: dict = json.loads(reg_path.read_text(encoding="utf-8")) if reg_path.exists() else {}
        except Exception as exc:
            self.app.notify(f"Read error: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]
            return

        if "skills" not in raw or not isinstance(raw["skills"], dict):
            raw["skills"] = {}
        if "version" not in raw:
            raw["version"] = 1

        old_name = str(self._skill.get("name") or "")
        if not self._is_new and old_name and old_name != name and old_name in raw["skills"]:
            del raw["skills"][old_name]

        entry: dict[str, Any] = {"promptFile": pfile}
        if desc:   entry["description"] = desc
        if tags_s: entry["tags"] = [t.strip() for t in tags_s.split(",") if t.strip()]
        raw["skills"][name] = entry

        # Write prompt file
        resolved = Path(pfile) if Path(pfile).is_absolute() else self._registry_dir / pfile
        try:
            resolved.parent.mkdir(parents=True, exist_ok=True)
            resolved.write_text(content, encoding="utf-8")
        except Exception as exc:
            self.app.notify(f"Prompt file write error: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]
            return

        try:
            reg_path.write_text(json.dumps(raw, indent=2), encoding="utf-8")
            self.app.notify(f"Skill '{name}' saved.", title="Saved")  # type: ignore[attr-defined]
            self.dismiss(True)
        except Exception as exc:
            self.app.notify(f"Registry write error: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]


# ── PlanInspectScreen ─────────────────────────────────────────────────────────

class PlanInspectScreen(Screen):  # type: ignore[type-arg,misc]
    """Unified plan inspector: tools / intents / profiles / prompt / follow-up."""

    BINDINGS = [
        Binding("escape", "go_back",        "Back",      show=True),
        Binding("t",      "mode_tools",     "Tools",     show=True),
        Binding("i",      "mode_intents",   "Intents",   show=True),
        Binding("o",      "mode_profiles",  "Profiles",  show=True),
        Binding("p",      "mode_prompt",    "Prompt",    show=True),
        Binding("f",      "mode_followup",  "Follow-up", show=True),
    ]

    _MODES = ("tools", "intents", "profiles", "prompt", "followup")
    _TITLES = {
        "tools":    "[ EXTRA TOOLS ]  Per-agent and per-task tool definitions",
        "intents":  "[ VALIDATION INTENTS ]  Per-task validation strategy",
        "profiles": "[ OUTPUT PROFILES ]  Per-task output verbosity",
        "prompt":   "[ PROMPT ACCOUNTING ]  Per-task prompt budget breakdown",
        "followup": "[ FOLLOW-UP TASKS ]  Conditional task injection",
    }
    _BASE_TOOLS = frozenset({
        "read_file", "write_file", "str_replace_based_edit_tool",
        "bash", "write_shared_context",
    })
    _SECTION_KEYS = [
        ("systemPrompt",      "System Prompt"),
        ("rolePrompt",        "Role Prompt"),
        ("skillStack",        "Skill Stack"),
        ("dependencyContext", "Dependency Context"),
        ("sharedContext",     "Shared Context"),
        ("taskPrompt",        "Task Prompt"),
    ]
    _WARN_TOKENS  = 80_000
    _BLOCK_TOKENS = 120_000

    def __init__(self, plan_path: str, mode: str = "tools", run_dir: str = "") -> None:
        super().__init__()
        self._plan_path   = plan_path
        self._mode        = mode
        self._run_dir     = run_dir
        self._rows_cache: list[Any] = []
        self._load_gen: int = 0

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("", id="insp-title")
            yield Static(self._plan_path, id="insp-path")
        with Horizontal(id="mode-tabs"):
            yield Button("TOOLS",      id="tab-tools",    variant="default")
            yield Button("INTENTS",    id="tab-intents",  variant="default")
            yield Button("PROFILES",   id="tab-profiles", variant="default")
            yield Button("PROMPT",     id="tab-prompt",   variant="default")
            yield Button("FOLLOW-UP",  id="tab-followup", variant="default")
        with Horizontal(id="insp-split"):
            yield DataTable(id="insp-table")
            yield RichLog(id="insp-log", markup=True, auto_scroll=True,
                          wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        table = self.query_one("#insp-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Source", key="source", width=16)
        table.add_column("Tool",   key="tool",   width=22)
        table.add_column("Kind",   key="kind",   width=10)
        table.add_column("State",  key="state",  width=6)
        table.display = False
        self._switch_mode(self._mode)

    # ── mode switching ─────────────────────────────────────────────────────────

    def _switch_mode(self, mode: str) -> None:
        self._mode = mode
        self._rows_cache = []
        self._load_gen += 1
        gen = self._load_gen

        try:
            self.app.title = f"POJOLENS  //  {mode.upper()}"
            self.query_one("#insp-title", Static).update(
                self._TITLES.get(mode, mode.upper())
            )
        except Exception:
            pass

        for m in self._MODES:
            try:
                btn = self.query_one(f"#tab-{m}", Button)
                if m == mode:
                    btn.add_class("tab-active")
                else:
                    btn.remove_class("tab-active")
            except Exception:
                pass

        try:
            table = self.query_one("#insp-table", DataTable)
            table.display = (mode == "tools")
            if mode == "tools":
                table.clear()
        except Exception:
            pass

        try:
            self.query_one("#insp-log", RichLog).clear()
        except Exception:
            pass

        plan_path = self._plan_path
        run_dir   = self._run_dir
        self.run_worker(
            lambda: self._load(plan_path, mode, run_dir, gen),
            thread=True,
            name="insp-load",
            exclusive=True,
        )

    def _load(self, path: str, mode: str, run_dir: str, gen: int) -> None:
        if self._load_gen != gen:
            return
        if mode == "tools":
            self._load_tools(path, gen)
        elif mode == "intents":
            self._load_intents(path, gen)
        elif mode == "profiles":
            self._load_profiles(path, gen)
        elif mode == "prompt":
            self._load_prompt(path, gen)
        elif mode == "followup":
            self._load_followup(path, run_dir, gen)

    def _log(self, text: str, gen: int) -> None:
        if self._load_gen != gen:
            return
        self.app.call_from_thread(
            lambda: self.query_one("#insp-log", RichLog).write(text)
        )

    # ── tools ──────────────────────────────────────────────────────────────────

    def _load_tools(self, path: str, gen: int) -> None:
        plan = _load_plan_json(path)
        if plan is None:
            self._log(f"[#ff2244]Cannot load: {path}[/]", gen)
            return

        rows: list[tuple[str, str, str, str, dict[str, Any]]] = []
        for ag in (plan.get("agents") or []):
            ag_name = str(ag.get("name") or ag.get("role") or "agent")
            for t in (ag.get("extraTools") or []):
                if isinstance(t, dict):
                    rows.append((f"agent:{ag_name[:12]}", str(t.get("name") or "-"),
                                 str(t.get("kind") or t.get("type") or "-"), "OK", t))
        for task in (plan.get("tasks") or []):
            task_id = str(task.get("id") or "task")[:12]
            for t in (task.get("extraTools") or []):
                if isinstance(t, dict):
                    rows.append((f"task:{task_id}", str(t.get("name") or "-"),
                                 str(t.get("kind") or t.get("type") or "-"), "OK", t))

        if not rows:
            self._log("[dim]No extraTools defined in this plan.[/]", gen)
            return
        if self._load_gen != gen:
            return

        def _fill(rows: list = rows, gen: int = gen) -> None:
            if self._load_gen != gen:
                return
            try:
                table = self.query_one("#insp-table", DataTable)
                table.clear()
                for src, name, kind, _state, tool in rows:
                    col_s = "#00ff41"
                    disp  = "OK"
                    if name in self._BASE_TOOLS:
                        disp, col_s = "COLL", "#ff2244"
                    elif ".." in str(tool.get("path") or ""):
                        disp, col_s = "TRAV", "#ff2244"
                    sc = Text(disp, style=col_s) if Text is not None else disp
                    table.add_row(src, name[:22], kind[:10], sc, key=f"{src}:{name}")
                self._rows_cache = rows
                if rows:
                    self._show_tool_detail(rows[0][4], rows[0][0], rows[0][1])
            except Exception:
                pass

        self.app.call_from_thread(_fill)

    def on_data_table_row_highlighted(self, event: DataTable.RowHighlighted) -> None:
        if self._mode != "tools":
            return
        idx = event.cursor_row
        if 0 <= idx < len(self._rows_cache):
            src, name, _k, _s, tool = self._rows_cache[idx]
            self._show_tool_detail(tool, src, name)

    def _show_tool_detail(self, tool: dict[str, Any], src: str, name: str) -> None:
        try:
            log = self.query_one("#insp-log", RichLog)
            log.clear()
            log.write(f"[bold #00e5ff]Tool: {name}[/]  [dim]from {src}[/]")
            for k, v in sorted(tool.items()):
                log.write(f"  [#00e5ff]{k}:[/] {v}")
            if name in self._BASE_TOOLS:
                log.write("")
                log.write("[bold #ff2244]⚠ COLLISION: shadows a base tool.[/]")
            if ".." in str(tool.get("path") or ""):
                log.write("[bold #ff2244]⚠ PATH TRAVERSAL: '..'. Review carefully.[/]")
        except Exception:
            pass

    # ── intents ────────────────────────────────────────────────────────────────

    def _load_intents(self, path: str, gen: int) -> None:
        plan = _load_plan_json(path)
        if plan is None:
            self._log(f"[#ff2244]Cannot load: {path}[/]", gen)
            return
        tasks = plan.get("tasks") or []
        if not tasks:
            self._log("[dim]No tasks found.[/]", gen)
            return
        for task in tasks:
            if self._load_gen != gen:
                return
            tid     = str(task.get("id") or "-")
            intents = task.get("validationIntents") or []
            legacy  = task.get("validationCommands") or []
            self._log(f"[bold #00e5ff]Task: {tid}[/]", gen)
            if intents:
                for intent in intents:
                    kind = str(intent.get("kind") or intent.get("type") or "-")
                    cmd  = str(intent.get("command") or intent.get("script") or "")
                    col  = "#00e5ff" if kind == "tool" else "#a0ffa0"
                    self._log(f"  [{col}]• {kind}[/{col}]  {cmd[:80]}", gen)
            else:
                self._log("  [dim](no validationIntents)[/]", gen)
            if legacy:
                self._log(
                    f"  [#ff2244]⚠ legacy validationCommands ({len(legacy)})"
                    " — migrate to validationIntents[/]", gen
                )
            self._log("", gen)

    # ── profiles ───────────────────────────────────────────────────────────────

    def _load_profiles(self, path: str, gen: int) -> None:
        plan = _load_plan_json(path)
        if plan is None:
            self._log(f"[#ff2244]Cannot load: {path}[/]", gen)
            return
        tasks = plan.get("tasks") or []
        if not tasks:
            self._log("[dim]No tasks found.[/]", gen)
            return
        self._log("[bold #00e5ff]Output profile per task:[/]", gen)
        self._log("  [dim]lean = minimal JSON  ·  default = full rich output[/]", gen)
        self._log("", gen)
        for task in tasks:
            if self._load_gen != gen:
                return
            tid     = str(task.get("id") or "-")
            profile = str(task.get("outputProfile") or "default")
            color   = "#00ff41" if profile == "lean" else "#00e5ff"
            label   = " [dim](recommended)[/]" if profile == "lean" else ""
            self._log(
                f"  [{color}]• {tid}[/{color}]  profile=[bold]{profile}[/bold]{label}", gen
            )

    # ── prompt accounting ──────────────────────────────────────────────────────

    def _load_prompt(self, path: str, gen: int) -> None:
        plan = _load_plan_json(path)
        if plan is None:
            self._log(f"[#ff2244]Cannot load: {path}[/]", gen)
            return
        tasks = plan.get("tasks") or []
        if not tasks:
            self._log("[dim]No tasks found.[/]", gen)
            return
        self._log(
            f"[dim]Warn ≥ {self._WARN_TOKENS:,}  ·  Block ≥ {self._BLOCK_TOKENS:,} tokens[/]",
            gen,
        )
        self._log("", gen)
        for task in tasks:
            if self._load_gen != gen:
                return
            tid = str(task.get("id") or "-")
            pa  = task.get("promptAccounting") or {}
            if not pa:
                self._log(f"[#00e5ff]Task: {tid}[/]  [dim](no promptAccounting)[/]", gen)
                continue
            total = sum(int(pa.get(k, 0) or 0) for k, _ in self._SECTION_KEYS)
            col_t = (
                "#ff2244" if total >= self._BLOCK_TOKENS else
                "#ffaa00" if total >= self._WARN_TOKENS  else
                "#00ff41"
            )
            self._log(
                f"[bold #00e5ff]Task: {tid}[/]  "
                f"total=[bold {col_t}]{total:,}[/bold {col_t}] tokens", gen
            )
            for key, label in self._SECTION_KEYS:
                tokens = int(pa.get(key, 0) or 0)
                if tokens:
                    bar = "█" * min(int(tokens / 5000), 20)
                    self._log(f"  [#00e5ff]{label:<22}[/] {tokens:>8,}  [dim]{bar}[/]", gen)
            if total >= self._BLOCK_TOKENS:
                self._log(f"  [bold #ff2244]⚠ OVERSIZED — exceeds {self._BLOCK_TOKENS:,}![/]", gen)
            elif total >= self._WARN_TOKENS:
                self._log(f"  [#ffaa00]⚠ Large ({total:,} tokens)[/]", gen)
            self._log("", gen)

    # ── follow-up tasks ────────────────────────────────────────────────────────

    def _load_followup(self, path: str, run_dir: str, gen: int) -> None:
        plan = _load_plan_json(path)
        if plan is None:
            self._log(f"[#ff2244]Cannot load: {path}[/]", gen)
            return
        tasks = plan.get("tasks") or []
        fu_tasks: list[dict[str, Any]] = []
        for task in tasks:
            for fut in (task.get("followUpTasks") or []):
                if isinstance(fut, dict):
                    fu_tasks.append({"emitter": str(task.get("id") or "-"), **fut})

        if not fu_tasks:
            self._log("[dim]No followUpTasks defined in plan.[/]", gen)
        else:
            self._log("[bold #00e5ff]Plan-defined followUpTasks:[/]", gen)
            for fut in fu_tasks:
                if self._load_gen != gen:
                    return
                emitter = fut.get("emitter", "-")
                tid     = str(fut.get("id") or fut.get("taskId") or "-")
                cond_f  = str(fut.get("conditionField") or "")
                cond_v  = str(fut.get("conditionValue") or "")
                cond    = f"  if {cond_f}={cond_v!r}" if cond_f else ""
                self._log(
                    f"  [#00e5ff]• {tid}[/]  emitted by [dim]{emitter}[/]{cond}", gen
                )
            self._log("", gen)

        if run_dir:
            manifest_path = Path(run_dir) / "manifest.json"
            try:
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                events   = manifest.get("events") or []
                injected = [
                    e for e in events
                    if e.get("phase") in ("task-injection", "task-injection-skipped")
                ]
                if injected:
                    self._log("[bold #00e5ff]Run injection events:[/]", gen)
                    for ev in injected:
                        if self._load_gen != gen:
                            return
                        phase  = str(ev.get("phase") or "")
                        tid    = str(ev.get("taskId") or "-")
                        reason = str(ev.get("reason") or "")
                        col    = "#00ff41" if "skipped" not in phase else "#ffaa00"
                        self._log(
                            f"  [{col}]• {phase}[/{col}]  task={tid}  {reason[:60]}", gen
                        )
                else:
                    self._log("[dim](no task-injection events in manifest)[/]", gen)
            except Exception:
                self._log("[dim](manifest not available)[/]", gen)

    # ── button / key handlers ──────────────────────────────────────────────────

    def on_button_pressed(self, event: Button.Pressed) -> None:
        _tab_map = {
            "tab-tools":    "tools",
            "tab-intents":  "intents",
            "tab-profiles": "profiles",
            "tab-prompt":   "prompt",
            "tab-followup": "followup",
        }
        bid = event.button.id
        if bid in _tab_map:
            self._switch_mode(_tab_map[bid])
        elif bid == "btn-back":
            self.action_go_back()

    def action_go_back(self)        -> None: self.dismiss(None)
    def action_mode_tools(self)     -> None: self._switch_mode("tools")
    def action_mode_intents(self)   -> None: self._switch_mode("intents")
    def action_mode_profiles(self)  -> None: self._switch_mode("profiles")
    def action_mode_prompt(self)    -> None: self._switch_mode("prompt")
    def action_mode_followup(self)  -> None: self._switch_mode("followup")


# ── Backward-compatible aliases ───────────────────────────────────────────────

class ExtraToolsScreen(PlanInspectScreen):
    def __init__(self, plan_path: str) -> None:
        super().__init__(plan_path, mode="tools")

class ValidationIntentsScreen(PlanInspectScreen):
    def __init__(self, plan_path: str) -> None:
        super().__init__(plan_path, mode="intents")

class OutputProfilesScreen(PlanInspectScreen):
    def __init__(self, plan_path: str) -> None:
        super().__init__(plan_path, mode="profiles")

class PromptAccountingScreen(PlanInspectScreen):
    def __init__(self, plan_path: str) -> None:
        super().__init__(plan_path, mode="prompt")

class FollowUpTaskScreen(PlanInspectScreen):
    def __init__(self, plan_path: str, run_dir: str = "") -> None:
        super().__init__(plan_path, mode="followup", run_dir=run_dir)
