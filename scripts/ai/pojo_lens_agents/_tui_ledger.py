from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from rich.text import Text
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, Vertical
    from textual.reactive import reactive
    from textual.screen import Screen
    from textual.widgets import Button, DataTable, Footer, Header, RichLog, Rule, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

try:
    from pojo_lens_agents.orchestrator_contracts import (
        DEFAULT_RUNTIME_ROOT,
        DEFAULT_AGENTS_PATH,
    )
except ImportError:  # pragma: no cover
    DEFAULT_RUNTIME_ROOT = Path(".claude-orchestrator")
    DEFAULT_AGENTS_PATH = Path("ai/orchestrator/agents.json")

from pojo_lens_agents._tui_helpers import _fmt_tok, _calc_run_duration, _status_color


def _manifest_state(data: dict[str, Any]) -> str:
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


# ── RunLedgerScreen ────────────────────────────────────────────────────────────

class RunLedgerScreen(Screen):  # type: ignore[type-arg,misc]
    """List retained runs from the runtime root.  mode='runs'|'ledger'|'promote'."""

    BINDINGS = [
        Binding("escape", "go_back",    "Back",    show=True),
        Binding("i",      "inspect_run","Inspect", show=True),
        Binding("r",      "resume_run", "Resume",  show=True),
        Binding("y",      "retry_run",  "Retry",   show=True),
        Binding("p",      "promote_run","Promote", show=True),
        Binding("g",      "gate_run",   "Gate",    show=True),
    ]

    def __init__(self, *, mode: str = "runs", plan_filter: str | None = None) -> None:
        super().__init__()
        self._mode        = mode
        self._plan_filter = plan_filter  # path or name of plan to scope runs to
        self._entries: list[dict[str, Any]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        if self._plan_filter:
            plan_stem = Path(self._plan_filter).stem
            label = f"[ RUNS ]  {plan_stem}"
        else:
            label = {
                "runs":    "[ RUNS ]  Retained run history",
                "ledger":  "[ LEDGER ]  Run ledger summary",
                "promote": "[ PROMOTE ]  Select run to review / promote",
            }.get(self._mode, "[ RUNS ]")
        with Container(id="top-bar"):
            yield Static(label, id="screen-title")
            yield Static(
                "[I] Inspect  [R] Resume  [Y] Retry  [P] Promote  [G] Gate  [Esc] Back",
                id="screen-hint",
            )
        yield DataTable(id="runs-table")
        yield Static("", id="empty-notice")
        with Horizontal(id="action-bar"):
            yield Button("OPEN",    id="btn-open",    variant="success", disabled=True)
            yield Button("RESUME",  id="btn-resume",  variant="primary", disabled=True)
            yield Button("RETRY",   id="btn-retry",                      disabled=True)
            yield Button("PROMOTE", id="btn-promote", variant="warning", disabled=True)
            yield Button("GATE",    id="btn-gate",                       disabled=True)
            yield Button("PLAN",    id="btn-plan",    variant="success", disabled=True)
            yield Button("BACK",    id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN LEDGER"
        table = self.query_one("#runs-table", DataTable)
        table.cursor_type = "row"
        table.add_column("Run ID",   key="run_id",   width=24)
        table.add_column("Plan",     key="plan",     width=24)
        table.add_column("Status",   key="status",   width=12)
        table.add_column("Tasks",    key="tasks",    width=6)
        table.add_column("Cost",     key="cost",     width=9)
        table.add_column("Tokens",   key="tokens",   width=14)
        table.add_column("Duration", key="duration", width=10)
        table.add_column("Date",     key="date",     width=17)
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

        # Fallback: scan manifests directly to get enriched cost/token/duration data
        # Also used when inventory handler unavailable or returns empty
        runs_dir = Path(runtime_root) / "runs"
        if runs_dir.exists():
            manifest_map: dict[str, dict[str, Any]] = {}
            for mp in runs_dir.glob("*/manifest.json"):
                try:
                    data = json.loads(mp.read_text(encoding="utf-8"))
                    run_id = str(data.get("runId") or mp.parent.name)
                    ut = data.get("usageTotals") or {}
                    manifest_map[run_id] = {
                        "_cost":        _manifest_cost(data),
                        "_inputTokens": int(ut.get("inputTokens",  0) or 0),
                        "_outputTokens":int(ut.get("outputTokens", 0) or 0),
                        "_duration":    _calc_run_duration(data),
                        "_runDir":      str(mp.parent),
                        "planPath":     str(data.get("planPath") or ""),
                        "planName":     Path(str(data.get("planPath") or "")).name,
                    }
                except Exception:
                    pass

            if not entries:
                # Build entries from manifests directly
                for mp in sorted(
                    runs_dir.glob("*/manifest.json"),
                    key=lambda p: p.stat().st_mtime, reverse=True,
                ):
                    try:
                        data = json.loads(mp.read_text(encoding="utf-8"))
                        run_id = str(data.get("runId") or mp.parent.name)
                        events = data.get("events") or []
                        ut = data.get("usageTotals") or {}
                        entries.append({
                            "runId":      run_id,
                            "planName":   Path(str(data.get("planPath") or "")).name,
                            "planPath":   str(data.get("planPath") or ""),
                            "status":     _manifest_state(data),
                            "totalTasks": len(data.get("tasks") or {}),
                            "startedAt":  events[0].get("ts") if events else "",
                            "runDir":     str(mp.parent),
                            "_cost":         _manifest_cost(data),
                            "_inputTokens":  int(ut.get("inputTokens",  0) or 0),
                            "_outputTokens": int(ut.get("outputTokens", 0) or 0),
                            "_duration":     _calc_run_duration(data),
                        })
                    except Exception:
                        pass
            else:
                # Enrich handler entries with manifest data
                for e in entries:
                    rid = str(e.get("runId") or e.get("run_id") or "")
                    m = manifest_map.get(rid) or {}
                    for k, v in m.items():
                        if k not in e:
                            e[k] = v
                    if "runDir" not in e and "_runDir" in m:
                        e["runDir"] = m["_runDir"]

        self._entries = entries
        self.app.call_from_thread(self._populate_table)

    def _populate_table(self) -> None:
        table = self.query_one("#runs-table", DataTable)
        table.clear()
        entries = self._entries
        if self._plan_filter:
            plan_name = Path(self._plan_filter).name.lower()   # e.g. "my-plan.json"
            plan_stem = Path(self._plan_filter).stem.lower()   # e.g. "my-plan"
            def _matches(e: dict) -> bool:
                e_name = str(e.get("planName") or e.get("plan_name") or "").lower()
                e_path = str(e.get("planPath") or e.get("plan_path") or "").lower()
                return (
                    e_name == plan_name
                    or e_name == plan_stem
                    or (e_path and (e_path == self._plan_filter.lower()
                                    or e_path.endswith("/" + plan_name)
                                    or e_path.endswith("\\" + plan_name)))
                )
            entries = [e for e in entries if _matches(e)]
            self._entries = entries
        if not self._entries:
            msg = (
                f"[dim #2a5a3a][ trace ] no runs found for plan: {Path(self._plan_filter).stem}[/]"
                if self._plan_filter else
                "[dim #2a5a3a][ trace ] no retained runs found in runtime root[/]"
            )
            self.query_one("#empty-notice", Static).update(msg)
            self._update_ledger_hint()
            return
        self.query_one("#empty-notice", Static).update("")
        for entry in self._entries[:200]:
            run_id   = str(entry.get("runId") or entry.get("run_id") or "-")
            plan     = str(entry.get("planName") or entry.get("plan_name") or "-")
            status   = str(entry.get("status") or entry.get("lifecycleState") or "-")
            tasks    = str(entry.get("totalTasks") or entry.get("task_count") or "-")
            date_raw = str(entry.get("startedAt") or entry.get("createdAt") or "")
            date     = date_raw[:16].replace("T", " ") if date_raw else "-"
            cost     = float(entry.get("_cost") or 0.0)
            cost_s   = f"${cost:.4f}" if cost else "—"
            inp      = int(entry.get("_inputTokens",  0) or 0)
            out      = int(entry.get("_outputTokens", 0) or 0)
            tok_s    = f"↓{_fmt_tok(inp)} ↑{_fmt_tok(out)}" if (inp or out) else "—"
            dur_s    = str(entry.get("_duration") or "—") or "—"
            sc       = _status_color(status)
            status_cell = Text(status, style=sc) if Text is not None else status
            table.add_row(
                run_id[:24],
                plan[:24],
                status_cell,
                tasks,
                cost_s,
                tok_s,
                dur_s,
                date,
                key=run_id,
            )
        # Reflect first row's status in buttons immediately
        if self._entries:
            self._set_action_buttons(self._entries[0])
        self._update_ledger_hint()

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

    def on_data_table_row_highlighted(self, event: DataTable.RowHighlighted) -> None:
        row = event.cursor_row
        if row < 0 or row >= len(self._entries):
            self._set_action_buttons(None)
        else:
            self._set_action_buttons(self._entries[row])

    def _set_action_buttons(self, entry: dict | None) -> None:
        if entry is None:
            for btn_id in ("btn-open", "btn-resume", "btn-retry", "btn-promote", "btn-gate"):
                try:
                    self.query_one(f"#{btn_id}", Button).disabled = True
                except Exception:
                    pass
            return
        status = str(entry.get("status") or entry.get("lifecycleState") or "").lower()
        running   = status == "running"
        completed = status == "completed"
        failed    = status in {"failed", "error"}
        paused    = status in {"paused", "interrupted", "partial"}
        blocked   = status in {"blocked", "hitl-pending", "pending-approval"}
        unknown   = status in {"unknown", ""}
        resumable = paused or (unknown and not running)
        plan_path   = str(entry.get("planPath") or entry.get("plan_path") or "")
        plan_exists = bool(plan_path) and Path(plan_path).exists()
        try:
            self.query_one("#btn-open",    Button).disabled = False
            self.query_one("#btn-resume",  Button).disabled = not resumable
            self.query_one("#btn-retry",   Button).disabled = not (failed or completed)
            self.query_one("#btn-promote", Button).disabled = not completed
            self.query_one("#btn-gate",    Button).disabled = not blocked
            self.query_one("#btn-plan",    Button).disabled = not plan_exists
        except Exception:
            pass

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-open":
            self.action_open_run()
        elif event.button.id == "btn-resume":
            self.action_resume_run()
        elif event.button.id == "btn-retry":
            self.action_retry_run()
        elif event.button.id == "btn-promote":
            self.action_promote_run()
        elif event.button.id == "btn-gate":
            self.action_gate_run()
        elif event.button.id == "btn-plan":
            self.action_open_plan()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_open_plan(self) -> None:
        table = self.query_one("#runs-table", DataTable)
        row   = table.cursor_row
        if row < 0 or row >= len(self._entries):
            self.app.notify("Select a run first.", title="No Run")  # type: ignore[attr-defined]
            return
        plan_path = str(self._entries[row].get("planPath") or self._entries[row].get("plan_path") or "")
        if plan_path and Path(plan_path).exists():
            from pojo_lens_agents._tui_plans import PlanDetailsScreen
            self.app.push_screen(PlanDetailsScreen(plan_path))  # type: ignore[attr-defined]
        else:
            self.app.notify("Plan file not found.", title="No Plan", severity="warning")  # type: ignore[attr-defined]

    def _update_ledger_hint(self) -> None:
        if not self._entries:
            try:
                self.query_one("#screen-hint", Static).update(
                    "[I] Inspect  [R] Resume  [Y] Retry  [P] Promote  [G] Gate  [Esc] Back"
                )
            except Exception:
                pass
            return
        total_cost  = sum(float(e.get("_cost") or 0.0) for e in self._entries)
        total_inp   = sum(int(e.get("_inputTokens",  0) or 0) for e in self._entries)
        total_out   = sum(int(e.get("_outputTokens", 0) or 0) for e in self._entries)
        n_completed = sum(1 for e in self._entries
                         if "completed" in str(e.get("status") or "").lower())
        n_failed    = sum(1 for e in self._entries
                         if str(e.get("status") or "").lower() in {"failed", "error"})
        try:
            self.query_one("#screen-hint", Static).update(
                f"[bold #00e5ff]{len(self._entries)}[/] runs  ·  "
                f"[#00ff41]✓ {n_completed}[/]  [#ff2244]✗ {n_failed}[/]  ·  "
                f"cost [#ffaa00]${total_cost:.4f}[/]  ·  "
                f"↓{_fmt_tok(total_inp)} ↑{_fmt_tok(total_out)}  ·  "
                f"[dim][I] Inspect  [R] Resume  [Y] Retry  [P] Promote  [G] Gate[/]"
            )
        except Exception:
            pass

    def action_inspect_run(self) -> None:
        self.action_open_run()

    def action_open_run(self) -> None:
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
            from pojo_lens_agents._tui_diff import DiffReviewScreen
            self.app.push_screen(DiffReviewScreen(run_dir))  # type: ignore[attr-defined]
        else:
            self.app.notify("Select a run first.", title="No Run Selected")  # type: ignore[attr-defined]

    def action_gate_run(self) -> None:
        run_dir = self._selected_run_dir()
        if run_dir:
            from pojo_lens_agents._tui_gate import HitlGateScreen
            self.app.push_screen(HitlGateScreen(run_ref=run_dir))  # type: ignore[attr-defined]
        else:
            self.app.notify("Select a run first.", title="No Run Selected")  # type: ignore[attr-defined]


# ── RunDetailsScreen ───────────────────────────────────────────────────────────

class RunDetailsScreen(Screen):  # type: ignore[type-arg,misc]
    """Rich run detail view — tasks, events, inline code diff, and actions."""

    BINDINGS = [
        Binding("escape", "go_back",       "Back",     show=True),
        Binding("r",      "resume_run",    "Resume",   show=True),
        Binding("y",      "retry_run",     "Retry",    show=True),
        Binding("p",      "promote_run",   "Promote",  show=True),
        Binding("a",      "show_all_diff", "All Diff", show=True),
    ]

    def __init__(self, run_ref: str) -> None:
        super().__init__()
        self._run_ref   = run_ref
        self._plan_path = ""
        self._file_diffs: dict[str, list[str]] = {}
        self._tasks_data: dict[str, Any] = {}

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ RUN DETAILS ]", id="run-title")
            yield Static(self._run_ref, id="run-ref")
            yield Static("", id="run-status-line")
            yield Static("", id="run-plan-line")
        with Horizontal(id="detail-split"):
            with Vertical(id="detail-left"):
                yield Static("[ TASKS ]", id="tasks-title")
                yield DataTable(id="tasks-table")
                yield Rule()
                yield Static("[ RESULTS ]", id="events-title")
                yield RichLog(id="events-log", markup=True, auto_scroll=False,
                              wrap=True, highlight=False, max_lines=60)
            with Vertical(id="detail-right"):
                yield Static("[ DIFF ]", id="diff-header")
                yield DataTable(id="diff-file-list")
                yield RichLog(id="diff-log", markup=True, auto_scroll=False,
                              wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("RESUME",  id="btn-resume",  variant="primary", disabled=True)
            yield Button("RETRY",   id="btn-retry",                       disabled=True)
            yield Button("PROMOTE", id="btn-promote", variant="warning",  disabled=True)
            yield Button("PLAN",    id="btn-plan",    variant="success",  disabled=True)
            yield Button("BACK",    id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN DETAILS"
        t = self.query_one("#tasks-table", DataTable)
        t.cursor_type = "row"
        t.zebra_stripes = True
        t.add_column("Task",   key="tid",    width=18)
        t.add_column("Agent",  key="agent",  width=12)
        t.add_column("Title",  key="title",  width=20)
        t.add_column("Status", key="status", width=11)
        t.add_column("Cost",   key="cost",   width=9)
        t.add_column("Tokens", key="tokens", width=13)
        d = self.query_one("#diff-file-list", DataTable)
        d.cursor_type = "row"
        d.add_column("File", key="file", width=28)
        d.add_column("+ins", key="ins",  width=5)
        d.add_column("-del", key="dels", width=5)
        self.run_worker(self._load_all, thread=True, name="run-details")

    def on_data_table_row_selected(self, event: DataTable.RowSelected) -> None:
        if event.data_table.id == "diff-file-list":
            fname = str(event.row_key.value) if event.row_key else None
            if fname and fname in self._file_diffs:
                self._render_file_diff(fname)
        elif event.data_table.id == "tasks-table":
            tid = str(event.row_key.value) if event.row_key else None
            if tid and tid in self._tasks_data:
                self._show_task_result(tid, self._tasks_data[tid])

    def _show_task_result(self, tid: str, t: dict) -> None:
        log = self.query_one("#events-log", RichLog)
        log.clear()
        try:
            self.query_one("#events-title", Static).update(
                f"[bold #00e5ff][ RESULT: {tid[:24]} ][/]"
            )
        except Exception:
            pass

        status = str(t.get("status") or "—")
        sc = _status_color(status)
        log.write(f"[{sc}]{status.upper()}[/]  [dim]{tid}[/]")

        summary = str(t.get("summary") or "").strip()
        if summary:
            log.write("")
            log.write("[bold #00e5ff]Summary[/]")
            for line in summary.splitlines():
                log.write(f"  {line}")

        files = list(t.get("actual_files_touched") or t.get("files_touched") or [])
        if files:
            log.write("")
            log.write("[bold #00e5ff]Files changed[/]")
            for f in files:
                log.write(f"  [#a0ffa0]{f}[/]")

        notes = list(t.get("notes") or [])
        if notes:
            log.write("")
            log.write("[bold #00e5ff]Notes[/]")
            for n in notes:
                log.write(f"  [#ffaa00]·[/] {n}")

        follow_ups = list(t.get("follow_ups") or t.get("followUps") or [])
        if follow_ups:
            log.write("")
            log.write("[bold #00e5ff]Follow-ups[/]")
            for fu in follow_ups:
                log.write(f"  [#00e5ff]→[/] {fu}")

        val_cmds = list(t.get("validation_commands") or t.get("validationCommands") or [])
        if val_cmds:
            log.write("")
            log.write("[bold #00e5ff]Validation commands[/]")
            for cmd in val_cmds:
                log.write(f"  [dim]$[/] [#a0ffa0]{cmd}[/]")

        rc = t.get("return_code")
        if rc is not None:
            log.write("")
            log.write(f"[dim]exit code: {rc}[/]")

    def _log_ev(self, text: str) -> None:
        self.app.call_from_thread(
            lambda: self.query_one("#events-log", RichLog).write(text)
        )

    def _log_diff(self, text: str) -> None:
        self.app.call_from_thread(
            lambda: self.query_one("#diff-log", RichLog).write(text)
        )

    def _render_file_diff(self, fname: str) -> None:
        from pojo_lens_agents._tui_diff import _render_diff_lines
        lines = self._file_diffs.get(fname, [])
        log = self.query_one("#diff-log", RichLog)
        log.clear()
        if not lines:
            log.write(f"[dim]No diff data for {fname}[/]")
            return
        for line in _render_diff_lines(lines):
            log.write(line)

    def action_show_all_diff(self) -> None:
        from pojo_lens_agents._tui_diff import _render_diff_lines
        log = self.query_one("#diff-log", RichLog)
        log.clear()
        if not self._file_diffs:
            log.write("[dim]No diff data loaded.[/]")
            return
        for fname, raw_lines in self._file_diffs.items():
            for line in _render_diff_lines(raw_lines):
                log.write(line)
            log.write("")

    def _load_all(self) -> None:
        self._load_from_manifest()
        self._load_diff()

    def _load_from_manifest(self) -> None:
        import datetime as _dt
        run_path = Path(self._run_ref)
        mp = (run_path if run_path.is_dir() else run_path.parent) / "manifest.json"
        if not mp.exists():
            self._load_from_handler()
            return
        try:
            data = json.loads(mp.read_text(encoding="utf-8"))
        except Exception as exc:
            self._log_ev(f"[#ff2244]manifest read: {exc}[/]")
            return

        run_id        = str(data.get("runId") or run_path.name)
        plan_path     = str(data.get("planPath") or "")
        workspace_dir = str(data.get("workspacesDir") or data.get("workspaceDir") or data.get("workspace_dir") or "")
        ws_mode       = str(data.get("workspaceMode") or data.get("workspace_mode") or data.get("runConfig", {}).get("workspaceMode") or "")
        self._plan_path = plan_path
        events     = list(data.get("events") or [])
        tasks_dict = data.get("tasks") or {}   # manifest results: tid → result
        ut         = data.get("usageTotals") or {}
        inp        = int(ut.get("inputTokens",  0) or 0)
        out        = int(ut.get("outputTokens", 0) or 0)
        cost       = float(ut.get("totalCostUsd", 0.0) or 0.0)

        # ── Load plan file — primary source for task/agent definitions ──────────
        plan_exists   = bool(plan_path) and Path(plan_path).exists()
        plan_name     = Path(plan_path).stem if plan_path else ""
        plan_tasks: list[dict] = []
        if plan_exists:
            try:
                pdata      = json.loads(Path(plan_path).read_text(encoding="utf-8"))
                plan_name  = str(pdata.get("name") or pdata.get("planName") or plan_name)
                plan_tasks = list(pdata.get("tasks") or [])
            except Exception:
                pass

        # ── Run state ────────────────────────────────────────────────────────────
        phases = [e.get("phase", "") for e in events]
        if "run-finished" in phases:
            task_statuses = {str(t.get("status") or "") for t in tasks_dict.values() if t}
            if "failed" in task_statuses:
                state = "failed"
            elif "blocked" in task_statuses:
                state = "blocked"
            else:
                state = "completed"
        elif "run-start" in phases:
            state = "running"
        else:
            state = "unknown"

        elapsed_str = ""
        if events:
            try:
                t0 = _dt.datetime.fromisoformat(str(events[0].get("ts", "")))
                fin = next((e for e in reversed(events) if e.get("phase") == "run-finished"), None)
                t1 = _dt.datetime.fromisoformat(str(fin["ts"])) if fin else _dt.datetime.now(t0.tzinfo)
                secs = int((t1 - t0).total_seconds())
                elapsed_str = f"{secs//3600:02d}:{(secs%3600)//60:02d}:{secs%60:02d}"
            except Exception:
                pass

        sc     = _status_color(state)
        cost_s = f"${cost:.4f}" if cost else "—"
        tok_s  = f"↓{_fmt_tok(inp)} ↑{_fmt_tok(out)}" if (inp or out) else "—"

        running   = state == "running"
        completed = state == "completed"
        failed    = state == "failed"
        paused    = state in {"paused", "suspended"}
        blocked   = state in {"blocked", "hitl-pending", "pending-approval"}
        unknown   = state in {"unknown", ""}
        resumable = paused or (unknown and not running)

        # ── Build task rows — plan tasks are primary; manifest results overlay ──
        # Agents come from plan task definitions (authoritative)
        self._tasks_data = {}
        task_rows: list[tuple] = []

        if plan_tasks:
            agents = sorted({str(pt.get("agent") or "") for pt in plan_tasks if pt.get("agent")})
            for pt in plan_tasks:
                tid    = str(pt.get("id") or "")
                agent  = str(pt.get("agent") or "—")[:12]
                title  = str(pt.get("title") or tid)[:20]
                t_data = (tasks_dict.get(tid) or {})
                self._tasks_data[tid[:20]] = t_data
                status = str(t_data.get("status") or "pending")
                t_ut   = t_data.get("usageTotals") or {}
                t_cost = float(t_data.get("costUsd") or t_ut.get("totalCostUsd") or 0.0)
                t_inp  = int(t_ut.get("inputTokens",  0) or 0)
                t_out  = int(t_ut.get("outputTokens", 0) or 0)
                t_sc   = _status_color(status)
                c_s    = f"${t_cost:.4f}" if t_cost else "—"
                k_s    = f"↓{_fmt_tok(t_inp)}↑{_fmt_tok(t_out)}" if (t_inp or t_out) else "—"
                task_rows.append((tid[:18], agent, title, status, t_sc, c_s, k_s))
        else:
            # No plan file — fall back to manifest task results
            agents = sorted({str(t.get("agent") or "") for t in tasks_dict.values()
                             if t and t.get("agent")})
            for tid, t_data in tasks_dict.items():
                t_data = t_data or {}
                self._tasks_data[tid[:20]] = t_data
                agent  = str(t_data.get("agent") or "—")[:12]
                title  = str(t_data.get("title") or tid)[:20]
                status = str(t_data.get("status") or "—")
                t_ut   = t_data.get("usageTotals") or {}
                t_cost = float(t_data.get("costUsd") or t_ut.get("totalCostUsd") or 0.0)
                t_inp  = int(t_ut.get("inputTokens",  0) or 0)
                t_out  = int(t_ut.get("outputTokens", 0) or 0)
                t_sc   = _status_color(status)
                c_s    = f"${t_cost:.4f}" if t_cost else "—"
                k_s    = f"↓{_fmt_tok(t_inp)}↑{_fmt_tok(t_out)}" if (t_inp or t_out) else "—"
                task_rows.append((tid[:18], agent, title, status, t_sc, c_s, k_s))

        agents_str_rich = "  ·  ".join(f"[#a0ffa0]{a}[/]" for a in agents) if agents else "[dim]—[/]"

        def _fill_header() -> None:
            try:
                display_name = plan_name or Path(plan_path).stem if plan_path else run_id
                self.query_one("#run-title", Static).update(
                    f"[ {display_name.upper()} ]"
                )
                self.query_one("#run-ref", Static).update(f"[dim]{run_id}[/]")
                ws_line = ""
                if ws_mode:
                    ws_line += f"  [dim]Mode:[/] [#00e5ff]{ws_mode}[/]"
                if workspace_dir:
                    ws_line += f"  [dim]Workspace:[/] [dim #a0ffa0]{workspace_dir[-60:]}[/]"
                self.query_one("#run-status-line", Static).update(
                    f"[{sc}]{state.upper()}[/]"
                    f"  [dim]Cost:[/] [#ffaa00]{cost_s}[/]"
                    f"  [dim]Tok:[/] [#00e5ff]{tok_s}[/]"
                    + (f"  [dim]Time:[/] {elapsed_str}" if elapsed_str else "")
                    + ws_line
                )
                self.query_one("#run-plan-line", Static).update(
                    f"[dim]Agents:[/] {agents_str_rich}"
                )
                self.query_one("#btn-resume",  Button).disabled = not resumable
                self.query_one("#btn-retry",   Button).disabled = not (failed or completed)
                self.query_one("#btn-promote", Button).disabled = not completed
                self.query_one("#btn-plan",    Button).disabled = not plan_exists
            except Exception:
                pass

        self.app.call_from_thread(_fill_header)

        def _fill_tasks() -> None:
            tbl = self.query_one("#tasks-table", DataTable)
            tbl.clear()
            for (tid, agent, title, status, t_sc, c_s, k_s) in task_rows:
                try:
                    from rich.text import Text as _T
                    status_cell: Any = _T(status, style=t_sc)
                except Exception:
                    status_cell = status
                tbl.add_row(tid, agent, title, status_cell, c_s, k_s, key=tid)

        self.app.call_from_thread(_fill_tasks)

        # Results overview — per-task summary + click hint
        self._log_ev("[dim #2a5a3a]Click a task row to see full result details.[/]")
        self._log_ev("")
        for tid, t_data in tasks_dict.items():
            t_data  = t_data or {}
            status  = str(t_data.get("status") or "—")
            sc      = _status_color(status)
            title   = str(t_data.get("title") or tid)[:40]
            summary = str(t_data.get("summary") or "").strip()
            files   = list(t_data.get("actual_files_touched") or t_data.get("files_touched") or [])
            icon    = "✓" if status == "completed" else ("✗" if status in {"failed", "error"} else "▸")
            self._log_ev(f"[{sc}]{icon} {tid[:20]}[/]  [dim]{title}[/]")
            if summary:
                for line in summary.splitlines()[:3]:
                    self._log_ev(f"   [dim]{line}[/]")
            if files:
                self._log_ev(f"   [#a0ffa0]{len(files)} file(s):[/] [dim]{', '.join(f.split('/')[-1] for f in files[:4])}{'…' if len(files) > 4 else ''}[/]")
            self._log_ev("")
        if not tasks_dict:
            self._log_ev("[dim #2a5a3a]No task data in manifest.[/]")

    def _load_from_handler(self) -> None:
        handlers = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log_ev("[#ff2244]No parse_args_fn.[/]")
            return
        try:
            args = parse_args_fn(["status", self._run_ref, "--json"])
            payload = handlers.get("status", lambda a: {})(args) or {}
        except Exception as exc:
            self._log_ev(f"[#ff2244]Status error: {exc}[/]")
            return
        self._log_ev("[bold #00e5ff]═══ RUN STATUS ═══[/]")
        for k in ("runId", "planName", "status", "lifecycleState", "startedAt", "finishedAt"):
            v = payload.get(k)
            if v is not None:
                self._log_ev(f"  [#00e5ff]{k}:[/] {v}")

    def _load_diff(self) -> None:
        import io as _io, sys as _sys
        from pojo_lens_agents._tui_diff import (
            _parse_unified_diff, _render_diff_lines, _file_change_stats,
            _D_ADDED_FG, _D_REMOVED_FG,
        )
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if "diff-run" not in handlers or parse_args_fn is None:
            self.app.call_from_thread(
                lambda: self.query_one("#diff-log", RichLog).write(
                    "[dim #2a5a3a]diff-run handler not registered[/]"
                )
            )
            return
        try:
            args    = parse_args_fn(["diff-run", self._run_ref, "--json"])
            buf     = _io.StringIO()
            old_out = _sys.stdout
            _sys.stdout = buf  # type: ignore[assignment]
            try:
                diff_payload = handlers["diff-run"](args) or {}
            except Exception as exc:
                diff_payload = {}
                self._log_diff(f"[#ff2244]diff-run: {exc}[/]")
            finally:
                _sys.stdout = old_out
                captured = buf.getvalue().strip()

            raw_diff = captured or str(
                diff_payload.get("diff") or diff_payload.get("unifiedDiff") or ""
            )
            file_diffs: dict[str, list[str]] = {}
            if raw_diff:
                file_diffs = _parse_unified_diff(raw_diff)
            for fe in (diff_payload.get("changedFiles") or diff_payload.get("files") or []):
                fname = str(fe.get("path") or fe.get("file") or "")
                fdiff = str(fe.get("diff") or fe.get("unifiedDiff") or "")
                if fname and fdiff and fname not in file_diffs:
                    file_diffs[fname] = fdiff.splitlines()

            self._file_diffs = file_diffs
            rows: list[tuple[str, int, int]] = [
                (fname, *_file_change_stats(flines))  # type: ignore[misc]
                for fname, flines in file_diffs.items()
            ]

            def _fill_diff(r: list = rows) -> None:
                tbl = self.query_one("#diff-file-list", DataTable)
                tbl.clear()
                for fname, ins, dels in r:
                    tbl.add_row(
                        fname[-28:],
                        f"[{_D_ADDED_FG}]+{ins}[/]",
                        f"[{_D_REMOVED_FG}]-{dels}[/]",
                        key=fname,
                    )
                try:
                    self.query_one("#diff-header", Static).update(
                        f"[bold #00e5ff][ DIFF ][/]  [dim]{len(r)} file(s) — click to view · [A] all[/]"
                        if r else "[dim #2a5a3a][ DIFF ]  no workspace changes[/]"
                    )
                except Exception:
                    pass
                if r:
                    self._render_file_diff(r[0][0])

            self.app.call_from_thread(_fill_diff)

            if not file_diffs and raw_diff:
                self._log_diff("[bold #00e5ff]═══ WORKSPACE DIFF ═══[/]")
                for line in _render_diff_lines(raw_diff.splitlines()):
                    self._log_diff(line)

            if not file_diffs and not raw_diff:
                from pojo_lens_agents._tui_diff import _explain_empty_diff
                self._log_diff(_explain_empty_diff(self._run_ref, diff_payload))

        except Exception as exc:
            self._log_diff(f"[#ff2244]diff load: {exc}[/]")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-resume":
            self.action_resume_run()
        elif event.button.id == "btn-retry":
            self.action_retry_run()
        elif event.button.id == "btn-promote":
            self.action_promote_run()
        elif event.button.id == "btn-plan":
            self.action_open_plan()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_resume_run(self) -> None:
        self.app.push_screen(ResumeRetryScreen(self._run_ref, mode="resume"))  # type: ignore[attr-defined]

    def action_retry_run(self) -> None:
        self.app.push_screen(ResumeRetryScreen(self._run_ref, mode="retry"))  # type: ignore[attr-defined]

    def action_promote_run(self) -> None:
        from pojo_lens_agents._tui_diff import DiffReviewScreen
        self.app.push_screen(DiffReviewScreen(self._run_ref))  # type: ignore[attr-defined]

    def action_open_plan(self) -> None:
        if self._plan_path and Path(self._plan_path).exists():
            from pojo_lens_agents._tui_plans import PlanDetailsScreen
            self.app.push_screen(PlanDetailsScreen(self._plan_path))  # type: ignore[attr-defined]
        else:
            self.app.notify("Plan file not found.", title="No Plan", severity="warning")  # type: ignore[attr-defined]

    def _log(self, text: str) -> None:
        self._log_ev(text)


# ── ResumeRetryScreen ──────────────────────────────────────────────────────────

class ResumeRetryScreen(Screen):  # type: ignore[type-arg,misc]
    """Resume, retry, or promote a retained run."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

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
        else:  # resume — no --runtime-root flag on this subcommand
            cmd = ["resume", self._run_ref,
                   "--agents", agents, "--provider-bin", claude_bin, "--json"]

        self._set_status("building args...")
        try:
            args = parse_args_fn(cmd)
        except (Exception, SystemExit) as exc:
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
