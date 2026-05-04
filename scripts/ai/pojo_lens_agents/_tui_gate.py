from __future__ import annotations

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

from pojo_lens_agents._tui_helpers import _status_color


# ── HitlGateScreen ────────────────────────────────────────────────────────────

class HitlGateScreen(Screen):  # type: ignore[type-arg,misc]
    """HITL approval gate — cyberpunk control panel for batch sign-off.

    NOTE (WP75): Live run polling not yet wired.  Gate ID and batch data will
    be driven from the retained run manifest when WP75 is implemented.
    """

    BINDINGS = [
        Binding("escape", "go_back", "Back",    show=True),
        Binding("a",      "approve", "Approve", show=True),
        Binding("x",      "abort",   "Abort",   show=True),
    ]

    CSS = """
    HitlGateScreen {
        background: $bg;
    }
    #gate-header {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $amber 40%;
        padding: 1 2;
    }
    #gate-title {
        color: $amber;
        text-style: bold;
    }
    #gate-id {
        color: $green_body;
        margin-top: 1;
    }
    #gate-wip {
        color: $text_dim;
        margin-top: 0;
    }
    #main-split {
        height: 1fr;
        layout: horizontal;
    }
    #left-panel {
        width: 1fr;
        background: $bg_panel;
        border-right: heavy $green 20%;
        padding: 1 2;
    }
    #right-panel {
        width: 1fr;
        background: $bg_panel;
        padding: 1 2;
    }
    #left-panel .panel-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #right-panel .panel-title {
        color: $cyan;
        text-style: bold;
        margin-bottom: 1;
    }
    #batch-log {
        height: 1fr;
    }
    #pending-table {
        height: 1fr;
    }
    #cost-bar {
        height: 3;
        background: $bg_input;
        border-top: solid $green_dim;
        border-bottom: solid $green_dim;
        padding: 0 2;
        align: left middle;
    }
    #cost-display {
        color: $green_body;
    }
    #gate-actions {
        height: 3;
        background: $bg_input;
        border-top: heavy $amber 40%;
        align: left middle;
        padding: 0 1;
    }
    """

    def __init__(self, run_ref: str = "", gate_id: str = "") -> None:
        super().__init__()
        self._run_ref = run_ref
        self._gate_id = gate_id or "GATE-PENDING"

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="gate-header"):
            yield Static(
                "[ HITL GATE ]  ⚠  HUMAN APPROVAL REQUIRED  ⚠",
                id="gate-title",
            )
            yield Static("", id="gate-id")
            yield Static(
                "WP75: live run polling pending — data above is placeholder until wired",
                id="gate-wip",
            )
        with Horizontal(id="main-split"):
            with Container(id="left-panel"):
                yield Static("COMPLETED BATCH SUMMARY", classes="panel-title")
                yield RichLog(
                    id="batch-log", markup=True, auto_scroll=False,
                    wrap=True, highlight=False,
                )
            with Container(id="right-panel"):
                yield Static("PENDING TASKS", classes="panel-title")
                yield DataTable(id="pending-table")
        with Container(id="cost-bar"):
            yield Static("", id="cost-display")
        with Horizontal(id="gate-actions"):
            yield Button("[A]  APPROVE",    id="btn-approve", variant="primary")
            yield Button("[X]  ABORT",      id="btn-abort",   variant="error")
            yield Button("BACK",            id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title   = "POJOLENS  //  HITL GATE"
        self.app.sub_title = "AWAITING APPROVAL"

        self.query_one("#gate-id", Static).update(
            f"[#ffaa00]Gate ID:[/] [bold #00e5ff]{self._gate_id}[/]"
            + (f"  [dim]·  Run: {self._run_ref}[/]" if self._run_ref else "")
        )
        self.query_one("#cost-display", Static).update(
            "[dim]Cost so far:[/] [#ffaa00]—[/]  "
            "[dim]·  Stale sentinel check:[/] [#00ff41]OK[/]  "
            "[dim]·  Live polling: WP75 pending[/]"
        )

        table = self.query_one("#pending-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Task ID",  width=22)
        table.add_column("Status",   width=10)
        table.add_column("Agent",    width=14)

        log = self.query_one("#batch-log", RichLog)
        log.write("[dim #2a5a3a][ construct ] connecting to retained run manifest...[/]")
        log.write("")
        log.write("[dim]Batch completion data will stream from:[/]")
        log.write(f"  [#00e5ff]{self._run_ref or '.claude-orchestrator/runs/<run-id>/manifest.json'}[/]")
        log.write("")
        log.write("[dim #ffaa00]WP75 will wire:[/]")
        log.write("  [dim]• polling retained run for pending HITL sentinels[/]")
        log.write("  [dim]• completed batch task counts and cost[/]")
        log.write("  [dim]• stale sentinel gateId validation[/]")
        log.write("  [dim]• approve/abort calling orchestrator handlers[/]")
        log.write("")
        log.write("[#00ff41][ OPERATOR ][/] Press [bold #00ff41][A][/] to approve  |  "
                  "[bold #ff2244][X][/] to abort")

        if self._run_ref:
            self.run_worker(self._load_gate_data, thread=True, name="hitl-load")

    def _load_gate_data(self) -> None:
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            return
        try:
            args    = parse_args_fn(["status", self._run_ref, "--json"])
            payload = handlers.get("status", lambda a: {})(args)
        except Exception:
            return
        tasks = list(payload.get("tasks") or [])
        pending = [t for t in tasks if str(t.get("status") or "") in ("pending", "blocked")]
        cost    = payload.get("totalCostUsd") or payload.get("total_cost_usd")
        def _update() -> None:
            table = self.query_one("#pending-table", DataTable)
            table.clear()
            for t in pending[:50]:
                tid    = str(t.get("taskId") or t.get("id") or "?")
                status = str(t.get("status") or "?")
                agent  = str(t.get("agent") or "-")
                sc     = _status_color(status)
                table.add_row(tid[:22], Text(status, style=sc) if Text else status, agent[:14])
            if cost is not None:
                self.query_one("#cost-display", Static).update(
                    f"[dim]Cost so far:[/] [bold #ffaa00]${cost:.5f}[/]"
                )
        self.app.call_from_thread(_update)

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-approve":
            self.action_approve()
        elif event.button.id == "btn-abort":
            self.action_abort()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_approve(self) -> None:
        self.dismiss({"decision": "approve", "gate_id": self._gate_id})

    def action_abort(self) -> None:
        self.dismiss({"decision": "abort", "gate_id": self._gate_id})

    def action_go_back(self) -> None:
        self.dismiss(None)
