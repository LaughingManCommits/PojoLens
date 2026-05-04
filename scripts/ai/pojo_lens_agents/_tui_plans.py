from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.screen import Screen
    from textual.widgets import Button, DataTable, Footer, Header, Input, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
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
    ]

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
            yield Button("APPROVE + RUN", id="btn-run",      variant="primary")
            yield Button("VALIDATE",      id="btn-validate")
            yield Button("DRY RUN",       id="btn-dryrun")
            yield Button("SAVE COPY",     id="btn-save")
            yield Button("TOOLS [T]",     id="btn-tools")
            yield Button("FOLLOW-UP [F]", id="btn-followup")
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
                "[dim #2a5a3a]Actions: [A] Approve+Run  [V] Validate  [D] Dry Run  "
                "[S] Save  [T] Tools  [I] Intents  [O] Profiles  [P] Prompt  [F] Follow-up  [Esc] Back[/]"
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
        self.app.push_screen(PlanInspectScreen(self._plan_path, mode="followup"))  # type: ignore[attr-defined]


# ── PlanEditorScreen ──────────────────────────────────────────────────────────

class PlanEditorScreen(Screen):  # type: ignore[type-arg,misc]
    """View plan JSON and optionally open in $EDITOR / VISUAL / notepad."""

    BINDINGS = [
        Binding("escape", "go_back",      "Back",            show=True),
        Binding("e",      "open_editor",  "Open in Editor",  show=True),
        Binding("r",      "reload",       "Reload",          show=True),
    ]

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
        _platform_default = "notepad" if sys.platform == "win32" else "nano"
        candidates = [
            c for c in [
                os.environ.get("VISUAL"),
                os.environ.get("EDITOR"),
                "code",
                _platform_default,
            ] if c
        ]
        for editor in candidates:
            self._log(f"[#00e5ff][ SIGNAL ] trying editor {editor!r}...[/]")
            try:
                subprocess.run([editor, self._plan_path], check=False)
                self._log("[#00ff41][ EXIT ] editor closed — press [R] to reload changes[/]")
                return
            except FileNotFoundError:
                self._log(f"[dim]editor {editor!r} not found, trying next...[/]")
            except Exception as exc:
                self._log(f"[#ff2244]editor error ({editor!r}): {exc}[/]")
                return
        self._log(
            f"[#ff2244]no editor found — set $VISUAL or $EDITOR env var[/]"
        )
