from __future__ import annotations

import datetime
import json
import shutil
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.containers import Horizontal, Vertical
    from textual.reactive import reactive
    from textual.widget import Widget
    from textual.widgets import Button, DataTable, Rule, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    Widget = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]

from pojo_lens_agents._tui_helpers import _fmt_tok, _status_color


def _manifest_run_state(data: dict[str, Any]) -> str:
    phases = [e.get("phase", "") for e in (data.get("events") or [])]
    if "run-finished" in phases:
        return "completed"
    if "run-start" in phases:
        return "running"
    return "unknown"


def _manifest_cost(data: dict[str, Any]) -> float:
    cost = float((data.get("usageTotals") or {}).get("totalCostUsd", 0.0) or 0.0)
    if cost == 0.0:
        rg = data.get("runGovernance") or {}
        cost = sum(float(t.get("costUsd", 0.0)) for t in (rg.get("highestCostTasks") or []))
    return cost


_TASK_ICONS = {
    "completed": "✓", "failed": "✗", "running": "⟳",
    "skipped": "⊘",  "reused": "⊕",  "pending": "○",
}

_RUN_ICONS = {"completed": "✓", "running": "⟳", "unknown": "✗"}


class DashboardWidget(Widget):  # type: ignore[type-arg,misc]
    """Live dashboard — paginated runs list (upper) + per-run detail panel (lower).

    page_size controls how many run rows appear per page (default 5).
    Click a row or use keyboard to select a run; detail panel updates below.
    """

    _run_state: reactive[str]         = reactive("idle")
    _run_dir:   reactive[Path | None] = reactive(None)  # type: ignore[type-arg]

    def __init__(self, *args: Any, page_size: int = 5, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        self._page_size          = page_size
        self._manifests: list[Path] = []
        self._selected_idx       = 0    # index in _manifests shown in detail
        self._page_index         = 0    # current page (0 = newest runs)
        self._rendered_page      = -1   # last built page (avoids full rebuild on update)
        self._rendered_count     = -1   # last built manifest count
        self._last_run_path: Path | None = None
        self._cached_plan_path   = ""
        self._cached_plan_tasks: dict[str, dict[str, Any]] = {}
        self._ordered_tasks: list[str] = []        # task IDs for current run (plan order)
        self._task_page_index    = 0               # current task page
        self._task_rendered_page = -1              # last built task page
        self._last_data: dict[str, Any] = {}       # last manifest data (for task page nav)

    def compose(self) -> ComposeResult:
        # Scrollable content area fills remaining height; actions pinned at bottom.
        with Vertical(id="dash-scroll"):
            with Horizontal(id="dash-header"):
                yield Static("[ DASHBOARD ]", id="dash-title")
                yield Static("", id="dash-active-badge")
            yield Static("", id="dash-stats-box")
            yield Rule(id="dash-rule-top")
            yield Static("[ RUNS ]", id="dash-runs-label")
            yield DataTable(id="dash-runs-table", cursor_type="row", zebra_stripes=True)
            with Horizontal(id="dash-page-bar"):
                yield Button("◀", id="btn-dash-pg-prev", disabled=True)
                yield Static("—", id="dash-page-label")
                yield Button("▶", id="btn-dash-pg-next", disabled=True)
            yield Rule(id="dash-rule-nav")
            with Vertical(id="dash-stats"):
                yield Static("", id="dash-run-id")
                yield Static("", id="dash-status")
                yield Static("", id="dash-progress")
                yield Static("", id="dash-cost")
                yield Static("", id="dash-tokens")
                yield Static("", id="dash-elapsed")
            yield Rule(id="dash-rule-mid")
            yield Static("[ TASKS ]", id="dash-tasks-title")
            yield DataTable(id="dash-task-table", cursor_type="none", zebra_stripes=True)
            with Horizontal(id="dash-task-page-bar"):
                yield Button("◀", id="btn-dash-tpg-prev", disabled=True)
                yield Static("—", id="dash-task-page-label")
                yield Button("▶", id="btn-dash-tpg-next", disabled=True)
            yield Static("", id="dash-idle-msg")
        # Actions bar always visible at bottom, outside the scroll area.
        with Horizontal(id="dash-actions"):
            yield Button("OPEN",   id="btn-dash-open",   variant="success", disabled=True)
            yield Button("STOP",   id="btn-dash-stop",   variant="error",   disabled=True)
            yield Button("PAUSE",  id="btn-dash-pause",  variant="warning", disabled=True)
            yield Button("DELETE", id="btn-dash-delete", variant="error",   disabled=True)

    def on_mount(self) -> None:
        rtable = self.query_one("#dash-runs-table", DataTable)
        rtable.add_column("RUN",    key="run",    width=22)
        rtable.add_column("STATUS", key="status", width=11)
        rtable.add_column("TASKS",  key="tasks",  width=7)
        rtable.add_column("COST",   key="cost",   width=9)
        table = self.query_one("#dash-task-table", DataTable)
        table.add_column("TASK",   key="task",   width=22)
        table.add_column("AGENT",  key="agent",  width=12)
        table.add_column("STATUS", key="status", width=12)
        table.add_column("COST",   key="cost",   width=9)
        self.set_interval(1.0, self._poll)
        self._show_idle()

    # ── polling ────────────────────────────────────────────────────────────────

    def _poll(self) -> None:
        active_runs  = getattr(self.app, "_active_runs", {})
        active_count = sum(1 for r in active_runs.values() if r.get("status") == "running")
        try:
            self.query_one("#dash-active-badge", Static).update(
                f"[bold #00ff41]● {active_count} ACTIVE[/]" if active_count else ""
            )
        except Exception:
            pass

        runtime_root = Path(str(getattr(self.app, "_runtime_root", ".claude-orchestrator")))
        runs_dir = runtime_root / "runs"
        if not runs_dir.exists():
            self._show_idle()
            return

        manifests = sorted(
            runs_dir.glob("*/manifest.json"),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
        if not manifests:
            self._show_idle()
            return

        prev_count = len(self._manifests)
        self._manifests = manifests
        if len(manifests) != prev_count or active_count > 0:
            self._update_stats_box(manifests)
            if len(manifests) > prev_count:
                self._selected_idx = 0
                self._page_index   = 0

        self._render_runs_page()

        sel = max(0, min(self._selected_idx, len(manifests) - 1))
        self._selected_idx = sel
        try:
            data = json.loads(manifests[sel].read_text(encoding="utf-8"))
            self._update_display(data, manifests[sel].parent)
        except Exception:
            self._show_idle()

    # ── runs list (upper panel) ────────────────────────────────────────────────

    def _render_runs_page(self) -> None:
        try:
            table = self.query_one("#dash-runs-table", DataTable)
        except Exception:
            return
        n   = len(self._manifests)
        ps  = self._page_size
        pg  = self._page_index
        start       = pg * ps
        end         = min(start + ps, n)
        total_pages = max(1, (n + ps - 1) // ps)

        need_rebuild = (pg != self._rendered_page or n != self._rendered_count)
        if need_rebuild:
            table.clear()
            self._rendered_page  = pg
            self._rendered_count = n

        for i in range(start, end):
            mp = self._manifests[i]
            try:
                d = json.loads(mp.read_text(encoding="utf-8"))
            except Exception:
                d = {}
            state   = _manifest_run_state(d)
            events  = list(d.get("events") or [])
            td      = d.get("tasks") or {}
            total   = len(td) or int((d.get("topology") or {}).get("taskCount", 0))
            done    = sum(
                1 for e in events
                if e.get("phase") == "task-finished"
                and e.get("status") in {"completed", "skipped", "reused"}
            )
            cost    = _manifest_cost(d)
            run_id  = str(d.get("runId") or mp.parent.name)
            icon    = _RUN_ICONS.get(state, "✗")
            stat_s  = f"{icon} {state[:9]}"
            tasks_s = f"{done}/{total}" if total else "—"
            cost_s  = f"${cost:.4f}" if cost else "—"
            key     = str(i)
            if need_rebuild:
                table.add_row(run_id[-22:], stat_s, tasks_s, cost_s, key=key)
            else:
                try:
                    table.update_cell(key, "status", stat_s,  update_width=False)
                    table.update_cell(key, "tasks",  tasks_s, update_width=False)
                    table.update_cell(key, "cost",   cost_s,  update_width=False)
                except Exception:
                    pass

        if need_rebuild:
            sel_in_page = self._selected_idx - start
            if 0 <= sel_in_page < (end - start):
                try:
                    table.move_cursor(row=sel_in_page)
                except Exception:
                    pass

        try:
            a, b = start + 1, end
            self.query_one("#dash-page-label", Static).update(
                f"Page [bold]{pg + 1}[/bold]/{total_pages}  ({a}–{b} of {n})"
            )
            self.query_one("#btn-dash-pg-prev", Button).disabled = pg <= 0
            self.query_one("#btn-dash-pg-next", Button).disabled = pg >= total_pages - 1
        except Exception:
            pass

    # ── aggregate stats box ────────────────────────────────────────────────────

    def _update_stats_box(self, manifests: list[Path]) -> None:
        total = len(manifests)
        completed = failed = running = 0
        total_cost = 0.0
        total_inp = total_out = 0
        for mp in manifests:
            try:
                d = json.loads(mp.read_text(encoding="utf-8"))
                state = _manifest_run_state(d)
                if state == "completed":
                    completed += 1
                elif state == "running":
                    running += 1
                else:
                    failed += 1
                total_cost += _manifest_cost(d)
                ut = d.get("usageTotals") or {}
                total_inp += int(ut.get("inputTokens", 0) or 0)
                total_out += int(ut.get("outputTokens", 0) or 0)
            except Exception:
                pass
        cost_str = f"${total_cost:.4f}" if total_cost else "—"
        tok_str = (
            f"  [dim]Tok:[/] [#00e5ff]↓{_fmt_tok(total_inp)}[/] [#a0ffa0]↑{_fmt_tok(total_out)}[/]"
            if (total_inp or total_out) else ""
        )
        try:
            self.query_one("#dash-stats-box", Static).update(
                f"[dim]Runs:[/] [#a0ffa0]{total}[/]"
                f"  [#00ff41]✓{completed}[/]"
                f"  [#ff2244]✗{failed}[/]"
                f"  [#ffaa00]{running}▸[/]"
                f"   [dim]Total:[/] [#ffaa00]{cost_str}[/]"
                f"{tok_str}"
            )
        except Exception:
            pass

    # ── display ────────────────────────────────────────────────────────────────

    def _show_idle(self) -> None:
        for wid in ("#dash-run-id", "#dash-status", "#dash-progress",
                    "#dash-cost", "#dash-tokens", "#dash-elapsed"):
            try:
                self.query_one(wid, Static).update("")
            except Exception:
                pass
        try:
            self.query_one("#dash-idle-msg", Static).update(
                "[dim #2a5a3a][ IDLE ]  No run data yet — press [N] to start a plan[/]"
            )
            self.query_one("#dash-page-label", Static).update("—")
            self.query_one("#btn-dash-pg-prev", Button).disabled = True
            self.query_one("#btn-dash-pg-next", Button).disabled = True
        except Exception:
            pass
        try:
            self.query_one("#dash-task-table", DataTable).clear()
        except Exception:
            pass
        try:
            self.query_one("#dash-runs-table", DataTable).clear()
        except Exception:
            pass
        try:
            self.query_one("#dash-task-page-label", Static).update("—")
            self.query_one("#btn-dash-tpg-prev", Button).disabled = True
            self.query_one("#btn-dash-tpg-next", Button).disabled = True
        except Exception:
            pass
        self._run_dir          = None   # type: ignore[assignment]
        self._last_run_path    = None
        self._cached_plan_path = ""
        self._cached_plan_tasks = {}
        self._ordered_tasks    = []
        self._selected_idx     = 0
        self._page_index       = 0
        self._rendered_page    = -1
        self._rendered_count   = -1
        self._task_page_index    = 0
        self._task_rendered_page = -1
        self._last_data          = {}
        self._set_action_buttons("idle", None)

    def _update_display(self, data: dict[str, Any], run_path: Path) -> None:
        run_id = str(data.get("runId") or run_path.name)
        state  = _manifest_run_state(data)

        events     = list(data.get("events") or [])
        tasks_dict = data.get("tasks") or {}
        total = len(tasks_dict) or int((data.get("topology") or {}).get("taskCount", 0))

        done = sum(
            1 for e in events
            if e.get("phase") == "task-finished"
            and e.get("status") in {"completed", "skipped", "reused"}
        )

        cost = _manifest_cost(data)
        ut   = data.get("usageTotals") or {}
        inp  = int(ut.get("inputTokens", 0) or 0)
        out  = int(ut.get("outputTokens", 0) or 0)
        start = events[0].get("ts") if events else None
        sc    = _status_color(state)

        filled = int(done / total * 20) if total else 0
        bar = f"[{sc}]{'█' * filled}[/][dim]{'░' * (20 - filled)}[/]"

        elapsed_str = ""
        if start:
            try:
                t0 = datetime.datetime.fromisoformat(str(start))
                finished_event = next(
                    (e for e in reversed(events) if e.get("phase") == "run-finished"), None
                )
                if finished_event and finished_event.get("ts"):
                    t1 = datetime.datetime.fromisoformat(str(finished_event["ts"]))
                else:
                    t1 = datetime.datetime.now(t0.tzinfo)
                secs = int((t1 - t0).total_seconds())
                elapsed_str = f"{secs // 3600:02d}:{(secs % 3600) // 60:02d}:{secs % 60:02d}"
            except Exception:
                pass

        try:
            self.query_one("#dash-run-id", Static).update(
                f"[#00e5ff]Run  :[/] [dim]{run_id[-52:]}[/]"
            )
            self.query_one("#dash-status", Static).update(
                f"[#00e5ff]State:[/] [{sc}]{state.upper()}[/]"
            )
            self.query_one("#dash-progress", Static).update(
                f"[#00e5ff]Tasks:[/] {bar}  {done}/{total}"
            )
            cost_str = f"${cost:.4f}" if cost else "—"
            self.query_one("#dash-cost", Static).update(
                f"[#00e5ff]Cost :[/] [#ffaa00]{cost_str}[/]"
            )
            if inp or out:
                self.query_one("#dash-tokens", Static).update(
                    f"[#00e5ff]Tokens:[/] [#00e5ff]↓{_fmt_tok(inp)}[/] in"
                    f"  [#a0ffa0]↑{_fmt_tok(out)}[/] out"
                )
            else:
                self.query_one("#dash-tokens", Static).update("")
            self.query_one("#dash-elapsed", Static).update(
                f"[#00e5ff]Time :[/] {elapsed_str}" if elapsed_str else ""
            )
            self.query_one("#dash-idle-msg", Static).update("")
        except Exception:
            pass

        self._run_dir   = run_path   # type: ignore[assignment]
        self._run_state = state      # type: ignore[assignment]
        self._last_data = data
        self._set_action_buttons(state, run_path)
        self._update_task_table(data, run_path)

    def _update_task_table(self, data: dict[str, Any], run_path: Path) -> None:
        tasks_dict = data.get("tasks") or {}

        run_changed = run_path != self._last_run_path
        if run_changed:
            try:
                self.query_one("#dash-task-table", DataTable).clear()
            except Exception:
                pass
            self._last_run_path      = run_path
            self._cached_plan_path   = ""
            self._cached_plan_tasks  = {}
            self._task_page_index    = 0
            self._task_rendered_page = -1

        plan_path = str(data.get("planPath") or data.get("plan_path") or "")
        if plan_path and plan_path != self._cached_plan_path:
            self._cached_plan_path = plan_path
            try:
                pdata = json.loads(Path(plan_path).read_text(encoding="utf-8"))
                self._cached_plan_tasks = {
                    str(pt.get("id") or ""): pt
                    for pt in (pdata.get("tasks") or [])
                }
            except Exception:
                self._cached_plan_tasks = {}

        ordered: list[str] = list(self._cached_plan_tasks.keys()) if self._cached_plan_tasks else []
        for tid in tasks_dict:
            if tid and tid not in self._cached_plan_tasks:
                ordered.append(tid)

        if ordered != self._ordered_tasks or run_changed:
            self._ordered_tasks      = ordered
            self._task_rendered_page = -1

        self._render_task_page(tasks_dict)

    def _render_task_page(self, tasks_dict: dict[str, Any]) -> None:
        try:
            table = self.query_one("#dash-task-table", DataTable)
        except Exception:
            return
        ordered = self._ordered_tasks
        n   = len(ordered)
        ps  = self._page_size
        pg  = self._task_page_index
        start       = pg * ps
        end         = min(start + ps, n)
        total_pages = max(1, (n + ps - 1) // ps) if n > 0 else 1

        need_rebuild = (pg != self._task_rendered_page)
        if need_rebuild:
            table.clear()
            self._task_rendered_page = pg

        for tid in ordered[start:end]:
            t_data  = tasks_dict.get(tid) or {}
            pt      = self._cached_plan_tasks.get(tid) or {}
            status  = str(t_data.get("status") or "pending")
            t_cost  = t_data.get("costUsd") or t_data.get("cost_usd")
            cost_s  = f"${float(t_cost):.4f}" if t_cost is not None else "—"
            icon    = _TASK_ICONS.get(status, "○")
            stat_s  = f"{icon} {status[:9]}"
            agent   = str(pt.get("agent") or t_data.get("agent") or "—")[:12]
            title   = str(pt.get("title") or tid)[:22]
            if need_rebuild:
                table.add_row(title, agent, stat_s, cost_s, key=tid)
            else:
                try:
                    table.update_cell(tid, "status", stat_s, update_width=False)
                    table.update_cell(tid, "cost",   cost_s, update_width=False)
                except Exception:
                    pass

        try:
            a = start + 1 if n > 0 else 0
            lbl = f"Page [bold]{pg + 1}[/bold]/{total_pages}  ({a}–{end} of {n})" if n > 0 else "—"
            self.query_one("#dash-task-page-label", Static).update(lbl)
            self.query_one("#btn-dash-tpg-prev", Button).disabled = pg <= 0
            self.query_one("#btn-dash-tpg-next", Button).disabled = pg >= total_pages - 1 or n == 0
        except Exception:
            pass

    def _navigate_task_page(self, delta: int) -> None:
        n = len(self._ordered_tasks)
        if n == 0:
            return
        total_pages = max(1, (n + self._page_size - 1) // self._page_size)
        new_page = max(0, min(self._task_page_index + delta, total_pages - 1))
        if new_page != self._task_page_index:
            self._task_page_index    = new_page
            self._task_rendered_page = -1
            tasks_dict = (self._last_data or {}).get("tasks") or {}
            self._render_task_page(tasks_dict)

    # ── page navigation ────────────────────────────────────────────────────────

    def _navigate_page(self, delta: int) -> None:
        n = len(self._manifests)
        if n == 0:
            return
        total_pages = max(1, (n + self._page_size - 1) // self._page_size)
        new_page = max(0, min(self._page_index + delta, total_pages - 1))
        if new_page != self._page_index:
            self._page_index    = new_page
            self._rendered_page = -1  # force full rebuild
            self._render_runs_page()

    def on_data_table_row_selected(self, event: "DataTable.RowSelected") -> None:
        if getattr(event.data_table, "id", None) != "dash-runs-table":
            return
        try:
            idx = int(str(event.row_key.value))
            if 0 <= idx < len(self._manifests):
                self._selected_idx = idx
                data = json.loads(self._manifests[idx].read_text(encoding="utf-8"))
                self._update_display(data, self._manifests[idx].parent)
        except Exception:
            pass

    # ── action buttons ─────────────────────────────────────────────────────────

    def _set_action_buttons(self, state: str, run_path: Path | None) -> None:
        running = state == "running"
        paused  = run_path is not None and (run_path / "pause.flag").exists()
        try:
            self.query_one("#btn-dash-open",   Button).disabled = run_path is None
            self.query_one("#btn-dash-stop",   Button).disabled = not running
            pause_btn = self.query_one("#btn-dash-pause", Button)
            pause_btn.disabled = not running
            pause_btn.label = "RESUME" if paused else "PAUSE"
            self.query_one("#btn-dash-delete", Button).disabled = run_path is None or running
        except Exception:
            pass

    def on_button_pressed(self, event: "Button.Pressed") -> None:
        btn_id = event.button.id
        if btn_id == "btn-dash-pg-prev":
            self._navigate_page(-1)   # newer page (lower index)
        elif btn_id == "btn-dash-pg-next":
            self._navigate_page(+1)   # older page (higher index)
        elif btn_id == "btn-dash-tpg-prev":
            self._navigate_task_page(-1)
        elif btn_id == "btn-dash-tpg-next":
            self._navigate_task_page(+1)
        elif btn_id == "btn-dash-open":
            self._do_open()
        elif btn_id == "btn-dash-stop":
            self._do_stop()
        elif btn_id == "btn-dash-pause":
            self._do_pause_toggle()
        elif btn_id == "btn-dash-delete":
            self._do_delete()

    def _do_open(self) -> None:
        run_path = self._run_dir
        if run_path is None:
            return
        from pojo_lens_agents._tui_ledger import RunDetailsScreen
        self.app.push_screen(RunDetailsScreen(str(run_path)))  # type: ignore[attr-defined]

    def _do_stop(self) -> None:
        run_path = self._run_dir
        if run_path is None:
            return
        try:
            (run_path / "stop.flag").write_text("stop", encoding="utf-8")
            self.query_one("#btn-dash-stop", Button).disabled = True
        except Exception:
            pass

    def _do_pause_toggle(self) -> None:
        run_path = self._run_dir
        if run_path is None:
            return
        flag = run_path / "pause.flag"
        try:
            if flag.exists():
                flag.unlink()
            else:
                flag.write_text("pause", encoding="utf-8")
            self._set_action_buttons(str(self._run_state), run_path)
        except Exception:
            pass

    def _do_delete(self) -> None:
        run_path = self._run_dir
        if run_path is None or self._run_state == "running":
            return
        try:
            shutil.rmtree(run_path, ignore_errors=True)
            self._run_dir    = None  # type: ignore[assignment]
            self._manifests  = [m for m in self._manifests if m.parent != run_path]
            self._rendered_page  = -1
            self._rendered_count = -1
            if self._manifests:
                new_idx = min(self._selected_idx, len(self._manifests) - 1)
                self._selected_idx = new_idx
                try:
                    data = json.loads(self._manifests[new_idx].read_text(encoding="utf-8"))
                    self._update_display(data, self._manifests[new_idx].parent)
                    self._update_stats_box(self._manifests)
                    self._render_runs_page()
                    return
                except Exception:
                    pass
            self._show_idle()
        except Exception:
            pass
