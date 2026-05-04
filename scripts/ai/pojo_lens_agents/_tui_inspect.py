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
    from pojo_lens_agents.orchestrator_contracts import DEFAULT_AGENTS_PATH
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
        table.add_column("Name",   key="name",   width=22)
        table.add_column("Role",   key="role",   width=12)
        table.add_column("Skills", key="skills", width=7)
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
        detail.write("")
        detail.write(f"  [#00e5ff]Provider        :[/] {ag.get('provider') or '-'}")
        if tools:
            detail.write("")
            detail.write(f"  [#00e5ff]Extra Tools ({len(tools)}):[/]")
            for t in tools:
                tname = str(t.get("name") or t if isinstance(t, str) else "-")
                tkind = str(t.get("kind") or t.get("type") or "-") if isinstance(t, dict) else "-"
                col   = "#ff2244" if tname in self._BASE_TOOLS else "#00e5ff"
                warn  = " [#ff2244]⚠ collides with base tool[/]" if tname in self._BASE_TOOLS else ""
                detail.write(f"    [{col}]•[/{col}] {tname} ({tkind}){warn}")


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
                    detail.write(f"  [bold #ff2244]⚠ TOO LARGE: {sz} bytes[/]")
                elif sz > self._WARN_BYTES:
                    detail.write(f"  [#ffaa00]⚠ Large: {sz} bytes[/]")
                else:
                    detail.write(f"  [#00ff41]✓ Size: {sz} bytes[/]")
                lines = Path(pfile).read_text(encoding="utf-8", errors="replace").splitlines()
                detail.write("")
                detail.write("[dim #2a5a3a]── Content preview ──[/]")
                for ln in lines[:20]:
                    detail.write(f"[dim]{ln}[/]")
                if len(lines) > 20:
                    detail.write(f"[dim]... +{len(lines)-20} more lines[/]")
            except OSError:
                detail.write("  [dim](file not found)[/]")


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
            yield Button("TOOLS [T]",      id="tab-tools",    variant="default")
            yield Button("INTENTS [I]",    id="tab-intents",  variant="default")
            yield Button("PROFILES [O]",   id="tab-profiles", variant="default")
            yield Button("PROMPT [P]",     id="tab-prompt",   variant="default")
            yield Button("FOLLOW-UP [F]",  id="tab-followup", variant="default")
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
