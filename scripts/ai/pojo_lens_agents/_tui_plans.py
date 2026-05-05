from __future__ import annotations

import json
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, ScrollableContainer, Vertical, VerticalScroll
    from textual.screen import ModalScreen, Screen
    from textual.widgets import Button, DataTable, Footer, Header, Input, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ModalScreen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

try:
    from pojo_lens_agents.wizard import PlanPreview
    from pojo_lens_agents.orchestrator_contracts import (
        DEFAULT_RUNTIME_ROOT,
        DEFAULT_TASKS_DIR,
    )
except ImportError:  # pragma: no cover
    PlanPreview = None  # type: ignore[assignment,misc]
    DEFAULT_RUNTIME_ROOT = Path(".claude-orchestrator")
    DEFAULT_TASKS_DIR = Path("ai/orchestrator/tasks")

from pojo_lens_agents._tui_helpers import (
    _load_plan_json,
    _plan_summary_rich,
    _collect_previews,
)


# ── SavedPlansScreen ───────────────────────────────────────────────────────────

def _collect_run_stats(runtime_root: Path) -> tuple[int, float]:
    """Scan all run manifests and return (run_count, total_cost_usd)."""
    runs_dir = runtime_root / "runs"
    if not runs_dir.exists():
        return 0, 0.0
    count = 0
    total = 0.0
    for manifest_path in runs_dir.glob("*/manifest.json"):
        count += 1
        try:
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            cost = float((data.get("usageTotals") or {}).get("totalCostUsd", 0.0) or 0.0)
            if cost == 0.0:
                rg = data.get("runGovernance") or {}
                cost = sum(float(t.get("costUsd", 0.0)) for t in (rg.get("highestCostTasks") or []))
            total += cost
        except Exception:
            pass
    return count, total


class SavedPlansScreen(Screen):  # type: ignore[type-arg,misc]
    """Browse tracked and user-saved plans with pagination and stats."""

    BINDINGS = [
        Binding("escape", "go_back",        "Back",     show=True),
        Binding("r",      "action_run",     "Run",      show=True),
        Binding("v",      "action_validate","Validate", show=True),
        Binding("d",      "action_details", "Details",  show=True),
        Binding("e",      "action_edit",    "Edit",     show=True),
        Binding("left",   "prev_page",      "Prev",     show=False),
        Binding("right",  "next_page",      "Next",     show=False),
    ]

    PAGE_SIZE = 15

    def __init__(self) -> None:
        super().__init__()
        self._previews: list[Any] = []
        self._visible_previews: list[Any] = []
        self._filter: str = ""
        self._page: int = 0
        self._run_count: int = 0
        self._total_cost: float = 0.0

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
        yield Static("", id="plans-stats")
        yield DataTable(id="plans-table")
        yield Static("", id="empty-notice")
        with Horizontal(id="pagination-bar"):
            yield Button("◀",    id="btn-prev",  variant="default", disabled=True)
            yield Static("",    id="page-label")
            yield Button("▶",    id="btn-next",  variant="default", disabled=True)
        with Horizontal(id="action-bar"):
            yield Button("RUN",      id="btn-run",      variant="primary")
            yield Button("DETAILS",  id="btn-details",  variant="success")
            yield Button("VALIDATE", id="btn-validate", variant="success")
            yield Button("EDIT",     id="btn-edit",     variant="success")
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
        try:
            runtime_root = Path(str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT)))
            self._run_count, self._total_cost = _collect_run_stats(runtime_root)
        except Exception:
            pass
        self.app.call_from_thread(self._populate_table)

    def _total_pages(self) -> int:
        n = len(self._visible_previews)
        return max(1, (n + self.PAGE_SIZE - 1) // self.PAGE_SIZE)

    def _update_stats(self) -> None:
        total_tasks = sum(getattr(p, "task_count", 0) for p in self._previews)
        n_visible   = len(self._visible_previews)
        n_total     = len(self._previews)
        cost_str    = f"${self._total_cost:.4f}" if self._total_cost else "—"
        filt_part   = f"  [dim](filtered: {n_visible})[/]" if self._filter else ""
        try:
            self.query_one("#plans-stats", Static).update(
                f"[#00e5ff]Plans:[/] [#a0ffa0]{n_total}[/]{filt_part}"
                f"   [#00e5ff]Tasks:[/] [#a0ffa0]{total_tasks}[/]"
                f"   [#00e5ff]Runs:[/] [#a0ffa0]{self._run_count}[/]"
                f"   [#00e5ff]Total cost:[/] [#ffaa00]{cost_str}[/]"
            )
        except Exception:
            pass

    def _update_pagination_bar(self) -> None:
        pages = self._total_pages()
        page  = self._page
        n     = len(self._visible_previews)
        start = page * self.PAGE_SIZE + 1
        end   = min((page + 1) * self.PAGE_SIZE, n)
        try:
            self.query_one("#page-label", Static).update(
                f"  Page [bold]{page + 1}[/bold] / {pages}  "
                f"({start}–{end} of {n})  "
            )
            self.query_one("#btn-prev", Button).disabled = page == 0
            self.query_one("#btn-next", Button).disabled = page >= pages - 1
        except Exception:
            pass

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
        # Reset to page 0 when filter changes
        self._page = 0

        self._update_stats()

        if not visible:
            msg = (
                "[dim #2a5a3a][ construct scan complete ][/]\n"
                "[dim]No saved plans found. Create a new plan to enter the system.[/]"
                if not self._previews else
                f"[dim]No plans match filter: {filt}[/]"
            )
            self.query_one("#empty-notice", Static).update(msg)
            self._update_pagination_bar()
            return
        self.query_one("#empty-notice", Static).update("")

        page_slice = visible[self._page * self.PAGE_SIZE : (self._page + 1) * self.PAGE_SIZE]
        for preview in page_slice:
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
        self._update_pagination_bar()

    def _go_to_page(self, page: int) -> None:
        pages = self._total_pages()
        self._page = max(0, min(page, pages - 1))
        table = self.query_one("#plans-table", DataTable)
        table.clear()
        page_slice = self._visible_previews[
            self._page * self.PAGE_SIZE : (self._page + 1) * self.PAGE_SIZE
        ]
        for preview in page_slice:
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
        self._update_pagination_bar()

    def action_prev_page(self) -> None:
        if self._page > 0:
            self._go_to_page(self._page - 1)

    def action_next_page(self) -> None:
        if self._page < self._total_pages() - 1:
            self._go_to_page(self._page + 1)

    def on_input_changed(self, event: Input.Changed) -> None:
        if event.input.id == "search-input":
            self._filter = event.value
            self._populate_table()

    def _selected_path(self) -> str | None:
        table = self.query_one("#plans-table", DataTable)
        if not table.row_count:
            return None
        page_slice = self._visible_previews[
            self._page * self.PAGE_SIZE : (self._page + 1) * self.PAGE_SIZE
        ]
        row_key = table.cursor_row
        if row_key < 0 or row_key >= len(page_slice):
            return None
        return page_slice[row_key].path

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-prev":
            self.action_prev_page()
        elif event.button.id == "btn-next":
            self.action_next_page()
        elif event.button.id == "btn-run":
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
            from pojo_lens_agents._tui_validate import RunPlanScreen
            self.app.push_screen(RunPlanScreen(path))  # type: ignore[attr-defined]

    def action_details(self) -> None:
        path = self._selected_path()
        if path:
            self.app.push_screen(PlanDetailsScreen(path))  # type: ignore[attr-defined]

    def action_validate(self) -> None:
        path = self._selected_path()
        if path:
            from pojo_lens_agents._tui_validate import ValidateRunScreen
            self.app.push_screen(ValidateRunScreen(path))  # type: ignore[attr-defined]

    def action_edit(self) -> None:
        path = self._selected_path()
        if path:
            self.app.push_screen(PlanEditorScreen(path))  # type: ignore[attr-defined]


# ── PlanDetailsScreen ──────────────────────────────────────────────────────────

class PlanDetailsScreen(Screen):  # type: ignore[type-arg,misc]
    """Inspect a plan and choose an action: run, validate, dry-run, save, back."""

    BINDINGS = [
        Binding("escape", "go_back",      "Back",          show=True),
        Binding("a",      "approve_run",  "Approve + Run", show=True),
        Binding("v",      "validate_plan","Validate",      show=True),
        Binding("d",      "dry_run_plan", "Dry Run",       show=True),
        Binding("s",      "save_plan",    "Save",          show=True),
        Binding("t",      "extra_tools",  "Tools",         show=True),
        Binding("i",      "val_intents",  "Intents",       show=True),
        Binding("o",      "out_profiles", "Profiles",      show=True),
        Binding("p",      "prompt_acct",  "Prompt",        show=True),
        Binding("f",      "follow_up",    "Follow-up",     show=True),
        Binding("h",      "view_runs",    "History",       show=True),
    ]

    def __init__(self, plan_path: str) -> None:
        super().__init__()
        self._plan_path = plan_path

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ PLAN REVIEW ]", id="screen-title")
            yield Static(self._plan_path, id="plan-path")
        yield RichLog(id="details-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("APPROVE + RUN", id="btn-run",      variant="primary")
            yield Button("VALIDATE",      id="btn-validate", variant="success")
            yield Button("DRY RUN",       id="btn-dryrun",   variant="warning")
            yield Button("SAVE COPY",     id="btn-save",     variant="warning")
            yield Button("TOOLS",         id="btn-tools",    variant="success")
            yield Button("FOLLOW-UP",     id="btn-followup", variant="success")
            yield Button("HISTORY",       id="btn-runs",     variant="success")
            yield Button("BACK",          id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  PLAN DETAILS"
        self.run_worker(self._load_and_display, thread=True, name="plan-detail")

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
                "[dim #2a5a3a]Actions: [A] Approve+Run  [V] Validate  [D] Dry Run  "
                "[S] Save  [T] Tools  [I] Intents  [O] Profiles  [P] Prompt  [F] Follow-up  [H] History  [Esc] Back[/]"
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
        elif event.button.id == "btn-tools":
            self.action_extra_tools()
        elif event.button.id == "btn-followup":
            self.action_follow_up()
        elif event.button.id == "btn-runs":
            self.action_view_runs()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_approve_run(self) -> None:
        from pojo_lens_agents._tui_validate import RunPlanScreen
        self.app.push_screen(  # type: ignore[attr-defined]
            RunPlanScreen(self._plan_path)
        )

    def action_validate_plan(self) -> None:
        from pojo_lens_agents._tui_validate import ValidateRunScreen
        self.app.push_screen(  # type: ignore[attr-defined]
            ValidateRunScreen(self._plan_path)
        )

    def action_dry_run_plan(self) -> None:
        from pojo_lens_agents._tui_validate import RunPlanScreen
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

    def action_extra_tools(self) -> None:
        from pojo_lens_agents._tui_inspect import PlanInspectScreen
        self.app.push_screen(PlanInspectScreen(self._plan_path, mode="tools"))  # type: ignore[attr-defined]

    def action_val_intents(self) -> None:
        from pojo_lens_agents._tui_inspect import PlanInspectScreen
        self.app.push_screen(PlanInspectScreen(self._plan_path, mode="intents"))  # type: ignore[attr-defined]

    def action_out_profiles(self) -> None:
        from pojo_lens_agents._tui_inspect import PlanInspectScreen
        self.app.push_screen(PlanInspectScreen(self._plan_path, mode="profiles"))  # type: ignore[attr-defined]

    def action_prompt_acct(self) -> None:
        from pojo_lens_agents._tui_inspect import PlanInspectScreen
        self.app.push_screen(PlanInspectScreen(self._plan_path, mode="prompt"))  # type: ignore[attr-defined]

    def action_follow_up(self) -> None:
        from pojo_lens_agents._tui_inspect import PlanInspectScreen
        run_dir = self._find_most_recent_run()
        self.app.push_screen(PlanInspectScreen(self._plan_path, mode="followup", run_dir=run_dir))  # type: ignore[attr-defined]

    def action_view_runs(self) -> None:
        from pojo_lens_agents._tui_ledger import RunLedgerScreen
        self.app.push_screen(RunLedgerScreen(plan_filter=self._plan_path))  # type: ignore[attr-defined]

    def _find_most_recent_run(self) -> str:
        """Find the most recent run dir that used this plan file."""
        import json as _json
        try:
            runtime_root = Path(str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT)))
            runs_dir = runtime_root / "runs"
            if not runs_dir.exists():
                return ""
            plan_name = Path(self._plan_path).name
            candidates: list[tuple[float, str]] = []
            for mp in runs_dir.glob("*/manifest.json"):
                try:
                    data = _json.loads(mp.read_text(encoding="utf-8"))
                    if Path(str(data.get("planPath") or "")).name == plan_name:
                        candidates.append((mp.stat().st_mtime, str(mp.parent)))
                except Exception:
                    pass
            return sorted(candidates, reverse=True)[0][1] if candidates else ""
        except Exception:
            return ""


# ── PlanEditorScreen ──────────────────────────────────────────────────────────

_TEXTAREA_AVAILABLE = False
try:
    from textual.widgets import TextArea as _TextArea
    _TEXTAREA_AVAILABLE = True
except ImportError:
    _TextArea = None  # type: ignore[assignment,misc]

_SELECTIONLIST_AVAILABLE = False
try:
    from textual.widgets import SelectionList as _SelectionList
    _SELECTIONLIST_AVAILABLE = True
except ImportError:
    _SelectionList = None  # type: ignore[assignment,misc]

_SELECT_AVAILABLE = False
_Select = None
try:
    from textual.widgets import Select as _Select  # type: ignore[assignment]
    _SELECT_AVAILABLE = True
except ImportError:
    pass

# Option tuples: (label, value) — matches AgentEditScreen / SkillEditScreen
_WS_OPTS     = [("(unset)", ""), ("copy", "copy"), ("worktree", "worktree"), ("repo", "repo")]
_HITL_OPTS   = [("(unset)", ""), ("always", "always"), ("batch", "batch"),
                ("on-failure", "on-failure"), ("none", "none")]
_BUDG_OPTS   = [("(unset)", ""), ("warn", "warn"), ("stop", "stop")]
_FOLL_OPTS   = [("(unset)", ""), ("ignore", "ignore"), ("inject", "inject")]
_EFFORT_OPTS = [("(unset)", ""), ("low", "low"), ("medium", "medium"), ("high", "high")]


def _make_ta(widget_id: str, text: str = "") -> Any:
    if _TEXTAREA_AVAILABLE and _TextArea is not None:
        return _TextArea(text, id=widget_id)
    return Input(id=widget_id, value=text[:400], placeholder="(TextArea unavailable)")


def _read_ta(owner: Any, widget_id: str) -> str:
    if _TEXTAREA_AVAILABLE and _TextArea is not None:
        try:
            return owner.query_one(f"#{widget_id}", _TextArea).text.strip()
        except Exception:
            pass
    try:
        return owner.query_one(f"#{widget_id}", Input).value.strip()
    except Exception:
        return ""


def _load_ta(owner: Any, widget_id: str, text: str) -> None:
    if _TEXTAREA_AVAILABLE and _TextArea is not None:
        try:
            owner.query_one(f"#{widget_id}", _TextArea).load_text(text)
            return
        except Exception:
            pass
    try:
        owner.query_one(f"#{widget_id}", Input).value = text[:400]
    except Exception:
        pass


def _make_sel(widget_id: str, options: list[tuple[str, str]], *, allow_blank: bool = False) -> Any:
    if _SELECT_AVAILABLE and _Select is not None:
        return _Select(options, id=widget_id, allow_blank=allow_blank)
    return Input(id=widget_id, placeholder="(Select unavailable)")


def _set_sel(owner: Any, wid: str, value: str) -> None:
    if _SELECT_AVAILABLE and _Select is not None:
        try:
            w = owner.query_one(f"#{wid}", _Select)
            w.value = value if value else _Select.BLANK
            return
        except Exception:
            pass
    try:
        owner.query_one(f"#{wid}", Input).value = value
    except Exception:
        pass


def _get_sel(owner: Any, wid: str) -> str:
    if _SELECT_AVAILABLE and _Select is not None:
        try:
            v = owner.query_one(f"#{wid}", _Select).value
            return "" if v is _Select.BLANK else str(v)
        except Exception:
            pass
    try:
        return owner.query_one(f"#{wid}", Input).value.strip()
    except Exception:
        return ""


def _load_skill_names() -> list[str]:
    try:
        from pojo_lens_agents.orchestrator_contracts import DEFAULT_SKILL_REGISTRY_PATH
        data = json.loads(DEFAULT_SKILL_REGISTRY_PATH.read_text(encoding="utf-8"))
        return sorted(str(k) for k in data)
    except Exception:
        return []


# ── TaskEditModal ──────────────────────────────────────────────────────────────

class TaskEditModal(ModalScreen):  # type: ignore[type-arg,misc]
    """Modal form for adding or editing a single task."""

    BINDINGS = [Binding("escape", "cancel", "Cancel", show=True)]

    def __init__(
        self,
        task: dict[str, Any] | None = None,
        *,
        skill_names: list[str] | None = None,
        task_ids: list[str] | None = None,
    ) -> None:
        super().__init__()
        self._task        = task or {}
        self._skill_names = skill_names or []
        self._task_ids    = task_ids    or []

    def compose(self) -> ComposeResult:
        t           = self._task
        cur_skills  = set(t.get("skills")    or [])
        cur_deps    = set(t.get("dependsOn") or [])

        with Container(id="modal-card"):
            yield Static(
                "[ EDIT TASK ]" if self._task else "[ ADD TASK ]",
                id="modal-title",
            )
            with VerticalScroll(id="set-form"):
                yield Static("id *", classes="set-label")
                yield Input(id="f-id", value=str(t.get("id") or ""), placeholder="task-1")
                yield Static("title *", classes="set-label")
                yield Input(id="f-title", value=str(t.get("title") or ""), placeholder="Short task title")
                yield Static("agent *", classes="set-label")
                yield Input(id="f-agent", value=str(t.get("agent") or ""), placeholder="coder")
                yield Static("prompt *", classes="set-label")
                yield _make_ta("f-prompt", str(t.get("prompt") or ""))

                yield Static("skills  (space = toggle)", classes="set-label")
                if _SELECTIONLIST_AVAILABLE and _SelectionList is not None:
                    _skill_opts = self._skill_names or sorted(cur_skills)
                    yield _SelectionList(
                        *[(s, s, s in cur_skills) for s in _skill_opts],
                        id="f-skills",
                    )
                else:
                    yield Input(id="f-skills",
                                value=", ".join(t.get("skills") or []),
                                placeholder="java, python  (comma-separated)")

                yield Static("dependsOn  (space = toggle)", classes="set-label")
                if _SELECTIONLIST_AVAILABLE and _SelectionList is not None:
                    _dep_opts = self._task_ids or sorted(cur_deps)
                    yield _SelectionList(
                        *[(tid, tid, tid in cur_deps) for tid in _dep_opts],
                        id="f-deps",
                    )
                else:
                    yield Input(id="f-deps",
                                value=", ".join(t.get("dependsOn") or []),
                                placeholder="task-1  (comma-separated)")

                yield Static("readPaths  (one per line)", classes="set-label")
                yield _make_ta("f-readpaths", "\n".join(t.get("readPaths") or []))
                yield Static("writePaths  (one per line)", classes="set-label")
                yield _make_ta("f-writepaths", "\n".join(t.get("writePaths") or []))

                yield Static("workspaceMode  (blank = agent default)", classes="set-label")
                yield _make_sel("f-wsmode", _WS_OPTS, allow_blank=False)
                yield Static("effort  (blank = agent default)", classes="set-label")
                yield _make_sel("f-effort", _EFFORT_OPTS, allow_blank=False)

            with Horizontal(id="modal-btns"):
                yield Button("SAVE", id="btn-save", variant="primary")
                yield Button("CANCEL", id="btn-cancel")

    def on_mount(self) -> None:
        _set_sel(self, "f-wsmode", str(self._task.get("workspaceMode") or ""))
        _set_sel(self, "f-effort", str(self._task.get("effort") or ""))

    def _gi(self, fid: str) -> str:
        try:
            return self.query_one(f"#{fid}", Input).value.strip()
        except Exception:
            return ""

    def _read_sl(self, fid: str, fallback_input: str) -> list[str]:
        if _SELECTIONLIST_AVAILABLE and _SelectionList is not None:
            try:
                return list(self.query_one(f"#{fid}", _SelectionList).selected)
            except Exception:
                pass
        return [s.strip() for s in fallback_input.split(",") if s.strip()]

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-save":
            self._do_save()
        else:
            self.action_cancel()

    def _do_save(self) -> None:
        task_id = self._gi("f-id")
        title   = self._gi("f-title")
        agent   = self._gi("f-agent")
        prompt  = _read_ta(self, "f-prompt")
        if not (task_id and title and agent and prompt):
            self.app.notify("id, title, agent and prompt are required", severity="error")  # type: ignore[attr-defined]
            return
        task: dict[str, Any] = {**self._task, "id": task_id, "title": title,
                                 "agent": agent, "prompt": prompt}
        skills = self._read_sl("f-skills", self._gi("f-skills"))
        deps   = self._read_sl("f-deps",   self._gi("f-deps"))
        read_p = [p.strip() for p in _read_ta(self, "f-readpaths").splitlines()  if p.strip()]
        writ_p = [p.strip() for p in _read_ta(self, "f-writepaths").splitlines() if p.strip()]
        wsmode = _get_sel(self, "f-wsmode")
        effort = _get_sel(self, "f-effort")
        for key, val in [("skills", skills), ("dependsOn", deps),
                          ("readPaths", read_p), ("writePaths", writ_p),
                          ("workspaceMode", wsmode), ("effort", effort)]:
            if val:
                task[key] = val
            else:
                task.pop(key, None)
        self.dismiss(task)

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── PlanEditorScreen ───────────────────────────────────────────────────────────

class PlanEditorScreen(Screen):  # type: ignore[type-arg,misc]
    """Form-based plan editor — structured fields, no raw JSON."""

    BINDINGS = [
        Binding("escape", "go_back", "Back",   show=True),
        Binding("ctrl+s", "save",    "Save",   show=True),
        Binding("ctrl+r", "reload",  "Reload", show=True),
    ]

    def __init__(self, plan_path: str) -> None:
        super().__init__()
        self._plan_path   = plan_path
        self._raw:        dict[str, Any]       = {}
        self._tasks:      list[dict[str, Any]] = []
        self._skill_names: list[str]           = []

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ PLAN EDITOR ]", id="ed-title")
            yield Static(self._plan_path,   id="ed-path")
            yield Static("",                id="ed-status")
        with Horizontal(id="ed-split"):
            with VerticalScroll(id="set-form"):
                # ── PLAN ──────────────────────────────────────────────────────
                yield Static("[ PLAN ]", classes="section-hdr")
                yield Static("name *", classes="set-label")
                yield Input(id="f-name", placeholder="my-plan")
                yield Static("goal *", classes="set-label")
                yield _make_ta("f-goal")
                yield Static("codebase path  (blank = current repo)", classes="set-label")
                yield Input(id="f-codebase", placeholder="/absolute/path/to/repo")
                yield Static("workspace strategy", classes="set-label")
                yield _make_sel("f-workspace", _WS_OPTS, allow_blank=False)
                # ── GOVERNANCE ────────────────────────────────────────────────
                yield Static("[ GOVERNANCE ]", classes="section-hdr")
                yield Static("run budget USD  (blank = unlimited)", classes="set-label")
                yield Input(id="f-budget", placeholder="0.50")
                yield Static("HITL mode", classes="set-label")
                yield _make_sel("f-hitl", _HITL_OPTS, allow_blank=False)
                yield Static("budget behavior", classes="set-label")
                yield _make_sel("f-budget-beh", _BUDG_OPTS, allow_blank=False)
                yield Static("follow-up behavior", classes="set-label")
                yield _make_sel("f-followup", _FOLL_OPTS, allow_blank=False)
                # ── SHARED CONTEXT ─────────────────────────────────────────────
                yield Static("[ SHARED CONTEXT ]", classes="section-hdr")
                yield Static("summary", classes="set-label")
                yield _make_ta("f-summary")
                yield Static("constraints  (one per line)", classes="set-label")
                yield _make_ta("f-constraints")
                yield Static("read paths  (one per line)", classes="set-label")
                yield _make_ta("f-readpaths")
                # ── TASKS ──────────────────────────────────────────────────────
                yield Static("[ TASKS ]", classes="section-hdr")
                yield DataTable(id="task-table")
                with Horizontal(id="task-actions"):
                    yield Button("+ ADD",     id="btn-task-add",    variant="success")
                    yield Button("✎ EDIT",   id="btn-task-edit",   variant="success")
                    yield Button("✕ REMOVE", id="btn-task-remove", variant="error")
            with Container(id="json-pane"):
                yield Static("[ ORIGINAL JSON ]", id="json-pane-title")
                yield RichLog(id="json-log", markup=False, highlight=True,
                              auto_scroll=False, wrap=False)
        with Horizontal(id="action-bar"):
            yield Button("SAVE  [Ctrl+S]",   id="btn-save",   variant="primary")
            yield Button("RELOAD  [Ctrl+R]", id="btn-reload", variant="warning")
            yield Button("BACK",             id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  PLAN EDITOR"
        table = self.query_one("#task-table", DataTable)
        table.cursor_type = "row"
        table.add_column("id",    key="id",    width=16)
        table.add_column("title", key="title", width=32)
        table.add_column("agent", key="agent", width=14)
        table.add_column("deps",  key="deps",  width=18)
        self._skill_names = _load_skill_names()
        self._load()
        self._refresh_json_preview()

    # ── helpers ────────────────────────────────────────────────────────────────

    def _refresh_json_preview(self) -> None:
        try:
            log = self.query_one("#json-log", RichLog)
            log.clear()
            raw_text = Path(self._plan_path).read_text(encoding="utf-8")
            try:
                from rich.syntax import Syntax
                log.write(Syntax(raw_text, "json", theme="monokai", word_wrap=False))
            except Exception:
                for line in raw_text.splitlines():
                    log.write(line)
        except Exception:
            pass

    def _set_status(self, msg: str, *, error: bool = False) -> None:
        color = "#ff2244" if error else "#00e5ff"
        try:
            self.query_one("#ed-status", Static).update(f"[{color}]{msg}[/]")
        except Exception:
            pass

    def _gi(self, fid: str) -> str:
        try:
            return self.query_one(f"#{fid}", Input).value.strip()
        except Exception:
            return ""

    def _si(self, fid: str, val: str) -> None:
        try:
            self.query_one(f"#{fid}", Input).value = val
        except Exception:
            pass

    # ── load / populate ────────────────────────────────────────────────────────

    def _load(self) -> None:
        try:
            raw = json.loads(Path(self._plan_path).read_text(encoding="utf-8"))
        except Exception as exc:
            self._set_status(f"Load error: {exc}", error=True)
            return
        self._raw   = raw
        self._tasks = list(raw.get("tasks") or [])

        self._si("f-name",     str(raw.get("name") or ""))
        _load_ta(self, "f-goal", str(raw.get("goal") or ""))
        self._si("f-codebase", str(raw.get("codebasePath") or ""))
        _set_sel(self, "f-workspace", str(raw.get("workspaceStrategy") or ""))

        rp = raw.get("runPolicy") or {}
        self._si("f-budget", str(rp.get("runBudgetUsd") or ""))
        _set_sel(self, "f-hitl",       str(rp.get("hitlMode")         or ""))
        _set_sel(self, "f-budget-beh", str(rp.get("budgetBehavior")   or ""))
        _set_sel(self, "f-followup",   str(rp.get("followUpBehavior") or ""))

        sc = raw.get("sharedContext") or {}
        _load_ta(self, "f-summary",     str(sc.get("summary") or ""))
        _load_ta(self, "f-constraints", "\n".join(sc.get("constraints") or []))
        _load_ta(self, "f-readpaths",   "\n".join(sc.get("readPaths")   or []))

        self._refresh_tasks()
        self._set_status("Ctrl+S  save  ·  Ctrl+R  reload  ·  * required")

    def _refresh_tasks(self) -> None:
        table = self.query_one("#task-table", DataTable)
        table.clear()
        for t in self._tasks:
            deps = ", ".join(t.get("dependsOn") or []) or "—"
            table.add_row(
                str(t.get("id")    or "")[:16],
                str(t.get("title") or "")[:32],
                str(t.get("agent") or "")[:14],
                deps[:18],
            )

    # ── save ───────────────────────────────────────────────────────────────────

    def action_save(self) -> None:
        name = self._gi("f-name")
        goal = _read_ta(self, "f-goal")
        if not name or not goal:
            self.app.notify("name and goal are required", severity="error")  # type: ignore[attr-defined]
            return

        rp: dict[str, Any] = dict(self._raw.get("runPolicy") or {})
        budget_s = self._gi("f-budget")
        if budget_s:
            try:
                rp["runBudgetUsd"] = float(budget_s)
            except ValueError:
                self.app.notify(f"budget must be a number: {budget_s!r}", severity="error")  # type: ignore[attr-defined]
                return
        else:
            rp.pop("runBudgetUsd", None)
        for key, fid in [
            ("hitlMode",         "f-hitl"),
            ("budgetBehavior",   "f-budget-beh"),
            ("followUpBehavior", "f-followup"),
        ]:
            v = _get_sel(self, fid)
            if v:   rp[key] = v
            else:   rp.pop(key, None)

        sc: dict[str, Any] = dict(self._raw.get("sharedContext") or {})
        sc["summary"]     = _read_ta(self, "f-summary")
        sc["constraints"] = [c.strip() for c in _read_ta(self, "f-constraints").splitlines() if c.strip()]
        sc["readPaths"]   = [p.strip() for p in _read_ta(self, "f-readpaths").splitlines()   if p.strip()]
        sc.setdefault("validation", [])

        plan: dict[str, Any] = {**self._raw, "name": name, "goal": goal,
                                  "sharedContext": sc, "tasks": self._tasks}
        plan.setdefault("version", 1)
        codebase = self._gi("f-codebase")
        ws       = _get_sel(self, "f-workspace")
        if codebase: plan["codebasePath"]      = codebase
        else:        plan.pop("codebasePath",      None)
        if ws:       plan["workspaceStrategy"] = ws
        else:        plan.pop("workspaceStrategy", None)
        if rp:       plan["runPolicy"]         = rp
        else:        plan.pop("runPolicy",         None)

        try:
            Path(self._plan_path).write_text(
                json.dumps(plan, indent=2, ensure_ascii=False), encoding="utf-8"
            )
        except OSError as exc:
            self._set_status(f"Write error: {exc}", error=True)
            return
        self._raw = plan
        self._set_status("[ SAVED ]  changes written to disk")

    def action_reload(self) -> None:
        self._load()

    # ── task management ────────────────────────────────────────────────────────

    def _task_ids_except(self, exclude_idx: int = -1) -> list[str]:
        return [
            str(t.get("id") or "")
            for i, t in enumerate(self._tasks)
            if i != exclude_idx and t.get("id")
        ]

    def _open_task_add(self) -> None:
        def _on_result(task: dict[str, Any] | None) -> None:
            if task:
                self._tasks.append(task)
                self._refresh_tasks()
        self.app.push_screen(  # type: ignore[attr-defined]
            TaskEditModal(None, skill_names=self._skill_names,
                          task_ids=self._task_ids_except()),
            _on_result,
        )

    def _open_task_edit(self) -> None:
        idx = self.query_one("#task-table", DataTable).cursor_row
        if not (0 <= idx < len(self._tasks)):
            return
        original = self._tasks[idx]
        def _on_result(task: dict[str, Any] | None) -> None:
            if task:
                self._tasks[idx] = task
                self._refresh_tasks()
        self.app.push_screen(  # type: ignore[attr-defined]
            TaskEditModal(original, skill_names=self._skill_names,
                          task_ids=self._task_ids_except(exclude_idx=idx)),
            _on_result,
        )

    def _remove_task(self) -> None:
        idx = self.query_one("#task-table", DataTable).cursor_row
        if 0 <= idx < len(self._tasks):
            self._tasks.pop(idx)
            self._refresh_tasks()

    # ── events ─────────────────────────────────────────────────────────────────

    def on_button_pressed(self, event: Button.Pressed) -> None:
        bid = event.button.id
        if   bid == "btn-save":        self.action_save()
        elif bid == "btn-reload":      self.action_reload()
        elif bid == "btn-back":        self.action_go_back()
        elif bid == "btn-task-add":    self._open_task_add()
        elif bid == "btn-task-edit":   self._open_task_edit()
        elif bid == "btn-task-remove": self._remove_task()

    def action_go_back(self) -> None:
        self.dismiss(None)
