from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from rich.text import Text
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.reactive import reactive
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
        DEFAULT_RUNTIME_ROOT,
        DEFAULT_AGENTS_PATH,
    )
except ImportError:  # pragma: no cover
    DEFAULT_RUNTIME_ROOT = Path(".claude-orchestrator")
    DEFAULT_AGENTS_PATH = Path("ai/orchestrator/agents.json")

from pojo_lens_agents._tui_helpers import _status_color


# ── RunLedgerScreen ────────────────────────────────────────────────────────────

class RunLedgerScreen(Screen):  # type: ignore[type-arg,misc]
    """List retained runs from the runtime root.  mode='runs'|'ledger'|'promote'."""

    BINDINGS = [
        Binding("escape", "go_back",    "Back",    show=True),
        Binding("i",      "inspect_run","Inspect", show=True),
        Binding("r",      "resume_run", "Resume",  show=True),
        Binding("y",      "retry_run",  "Retry",   show=True),
        Binding("p",      "promote_run","Promote", show=True),
    ]

    def __init__(self, *, mode: str = "runs") -> None:
        super().__init__()
        self._mode = mode
        self._entries: list[dict[str, Any]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        label = {
            "runs":    "[ RUNS ]  Retained run history",
            "ledger":  "[ LEDGER ]  Run ledger summary",
            "promote": "[ PROMOTE ]  Select run to review / promote",
        }.get(self._mode, "[ RUNS ]")
        with Container(id="top-bar"):
            yield Static(label, id="screen-title")
            yield Static(
                "[I] Inspect  [R] Resume  [Y] Retry  [P] Promote  [Esc] Back",
                id="screen-hint",
            )
        yield DataTable(id="runs-table")
        yield Static("", id="empty-notice")
        with Horizontal(id="action-bar"):
            yield Button("INSPECT",  id="btn-inspect")
            yield Button("RESUME",   id="btn-resume")
            yield Button("RETRY",    id="btn-retry")
            yield Button("PROMOTE",  id="btn-promote")
            yield Button("BACK",     id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN LEDGER"
        table = self.query_one("#runs-table", DataTable)
        table.cursor_type = "row"
        table.add_column("Run ID",   key="run_id",   width=24)
        table.add_column("Plan",     key="plan",     width=28)
        table.add_column("Status",   key="status",   width=14)
        table.add_column("Tasks",    key="tasks",    width=10)
        table.add_column("Date",     key="date",     width=20)
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

        self._entries = entries
        self.app.call_from_thread(self._populate_table)

    def _populate_table(self) -> None:
        table = self.query_one("#runs-table", DataTable)
        table.clear()
        if not self._entries:
            self.query_one("#empty-notice", Static).update(
                "[dim #2a5a3a][ trace ] no retained runs found in runtime root[/]"
            )
            return
        self.query_one("#empty-notice", Static).update("")
        for entry in self._entries[:200]:
            run_id   = str(entry.get("runId") or entry.get("run_id") or "-")
            plan     = str(entry.get("planName") or entry.get("plan_name") or "-")
            status   = str(entry.get("status") or entry.get("lifecycleState") or "-")
            tasks    = str(entry.get("totalTasks") or entry.get("task_count") or "-")
            date_raw = str(entry.get("startedAt") or entry.get("createdAt") or "")
            date     = date_raw[:16].replace("T", " ") if date_raw else "-"
            sc       = _status_color(status)
            status_cell = Text(status, style=sc) if Text is not None else status
            table.add_row(
                run_id[:24],
                plan[:28],
                status_cell,
                tasks,
                date,
                key=run_id,
            )

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

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-inspect":
            self.action_inspect_run()
        elif event.button.id == "btn-resume":
            self.action_resume_run()
        elif event.button.id == "btn-retry":
            self.action_retry_run()
        elif event.button.id == "btn-promote":
            self.action_promote_run()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_inspect_run(self) -> None:
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


# ── RunDetailsScreen ───────────────────────────────────────────────────────────

class RunDetailsScreen(Screen):  # type: ignore[type-arg,misc]
    """Single retained run summary — tasks, cost, events."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def __init__(self, run_ref: str) -> None:
        super().__init__()
        self._run_ref = run_ref

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ RUN DETAILS ]", id="run-title")
            yield Static(self._run_ref, id="run-ref")
        yield RichLog(id="detail-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN DETAILS"
        self.run_worker(self._load_details, thread=True, name="run-details")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#detail-log", RichLog).write(text))

    def _load_details(self) -> None:
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        try:
            args    = parse_args_fn(["status", self._run_ref, "--json"])
            payload = handlers.get("status", lambda a: {})(args)
        except Exception as exc:
            self._log(f"[#ff2244]Status error: {exc}[/]")
            return

        if not payload:
            self._log("[dim]No status payload returned.[/]")
            return

        self._log("[bold #00e5ff]═══ RUN STATUS ═══[/]")
        for key in ("runId", "planName", "status", "lifecycleState", "startedAt", "finishedAt"):
            val = payload.get(key)
            if val is not None:
                label = key.replace("_", " ")
                self._log(f"  [#00e5ff]{label}:[/] {val}")

        tasks = list(payload.get("tasks") or [])
        if tasks:
            self._log("")
            self._log("[bold #00e5ff]═══ Tasks ═══[/]")
            for t in tasks:
                tid    = str(t.get("taskId") or t.get("id") or "?")
                status = str(t.get("status") or "?")
                cost   = t.get("costUsd") or t.get("cost_usd")
                sc     = _status_color(status)
                cost_s = f"  [#00e5ff]${cost:.5f}[/]" if isinstance(cost, float) else ""
                self._log(f"  [{sc}]▸ {tid}: {status}[/{sc}]{cost_s}")

        budget = payload.get("totalCostUsd") or payload.get("total_cost_usd")
        if budget is not None:
            self._log("")
            self._log(f"[bold #00e5ff]Total cost:[/] [#ffaa00]${budget:.5f}[/]")

        self._log("")
        self._log("[dim #2a5a3a][ trace ] run detail complete[/]")


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
        else:
            cmd = ["resume", self._run_ref, "--runtime-root", runtime_root,
                   "--agents", agents, "--provider-bin", claude_bin, "--json"]

        self._set_status("building args...")
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
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
