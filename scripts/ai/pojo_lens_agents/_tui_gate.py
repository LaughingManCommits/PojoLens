from __future__ import annotations

import json
import time
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

from pojo_lens_agents._tui_helpers import _status_color

_STALE_THRESHOLD_SEC = 1800  # 30 minutes


# ── manifest parsing (pure, testable) ─────────────────────────────────────────

def _read_gate_manifest(run_dir: Path, gate_id: str) -> dict[str, Any]:
    """Read retained run manifest and extract gate data for display.

    Returns a dict with keys:
      completed_ids, failed_ids, pending_ids, task_costs, total_cost,
      stale, stale_minutes, _tasks, error
    """
    result: dict[str, Any] = {
        "completed_ids": [],
        "failed_ids": [],
        "pending_ids": [],
        "task_costs": {},
        "total_cost": 0.0,
        "stale": False,
        "stale_minutes": 0,
        "_tasks": {},
        "error": None,
    }
    manifest_path = run_dir / "manifest.json"
    sentinel_path = run_dir / "hitl-gate.lock"

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception as exc:
        result["error"] = str(exc)
        return result

    # Find most-recent hitl-gate event matching gate_id
    events = manifest.get("events") or []
    gate_det: dict[str, Any] = {}
    for ev in reversed(events):
        if not isinstance(ev, dict):
            continue
        det = ev.get("details") or {}
        if ev.get("phase") == "hitl-gate" and det.get("gateId") == gate_id:
            gate_det = det
            break

    result["completed_ids"] = list(gate_det.get("completedBatchTaskIds") or [])
    result["failed_ids"]    = list(gate_det.get("failedTaskIds") or [])
    result["pending_ids"]   = list(gate_det.get("pendingTaskIds") or [])

    # Task records: costs and agent info
    tasks: dict[str, Any] = manifest.get("tasks") or {}
    if not isinstance(tasks, dict):
        tasks = {}
    result["_tasks"] = tasks

    total_cost = 0.0
    task_costs: dict[str, float] = {}
    for tid, trec in tasks.items():
        if not isinstance(trec, dict):
            continue
        usage = trec.get("usage") or trec.get("usageSummary") or {}
        if isinstance(usage, dict):
            try:
                c = float(usage.get("totalCostUsd") or 0)
                if c:
                    task_costs[tid] = c
                    total_cost += c
            except (TypeError, ValueError):
                pass
    result["task_costs"] = task_costs
    result["total_cost"] = total_cost

    # Stale sentinel check
    if sentinel_path.exists():
        try:
            age_sec = time.time() - sentinel_path.stat().st_mtime
            result["stale_minutes"] = int(age_sec / 60)
            result["stale"] = age_sec > _STALE_THRESHOLD_SEC
        except OSError:
            pass

    return result


# ── HitlGateScreen ────────────────────────────────────────────────────────────

class HitlGateScreen(Screen):  # type: ignore[type-arg,misc]
    """HITL approval gate — cyberpunk control panel for batch sign-off."""

    BINDINGS = [
        Binding("escape", "go_back", "Back",    show=True),
        Binding("a",      "approve", "Approve", show=True),
        Binding("x",      "abort",   "Abort",   show=True),
    ]

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
            yield Button("APPROVE",    id="btn-approve", variant="primary")
            yield Button("ABORT",      id="btn-abort",   variant="error")
            yield Button("BACK",            id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title     = "POJOLENS  //  HITL GATE"
        self.app.sub_title = "AWAITING APPROVAL"

        self.query_one("#gate-id", Static).update(
            f"[#ffaa00]Gate ID:[/] [bold #00e5ff]{self._gate_id}[/]"
            + (f"  [dim]·  Run: {self._run_ref}[/]" if self._run_ref else "")
        )
        self.query_one("#cost-display", Static).update(
            "[dim]Cost so far:[/] [#ffaa00]—[/]  "
            "[dim]·  Stale sentinel:[/] [dim]checking...[/]"
        )

        table = self.query_one("#pending-table", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("Task ID", width=22)
        table.add_column("Status",  width=10)
        table.add_column("Agent",   width=14)

        log = self.query_one("#batch-log", RichLog)
        log.write("[dim #2a5a3a][ construct ] loading gate data...[/]")
        log.write(f"  [#00e5ff]{self._run_ref or '.claude-orchestrator/runs/<run-id>'}[/]")
        log.write("")
        log.write("[#00ff41][ OPERATOR ][/] Press [bold #00ff41][A][/] to approve  |  "
                  "[bold #ff2244][X][/] to abort")

        if self._run_ref:
            self.run_worker(self._load_gate_data, thread=True, name="hitl-load")

    def _load_gate_data(self) -> None:
        data = _read_gate_manifest(Path(self._run_ref), self._gate_id)

        completed_ids: list[str]    = data["completed_ids"]
        failed_ids: list[str]       = data["failed_ids"]
        pending_ids: list[str]      = data["pending_ids"]
        task_costs: dict[str, float] = data["task_costs"]
        total_cost: float           = data["total_cost"]
        tasks: dict[str, Any]       = data.get("_tasks") or {}
        stale: bool                 = data["stale"]
        stale_minutes: int          = data["stale_minutes"]
        error: str | None           = data["error"]

        def _update() -> None:
            log = self.query_one("#batch-log", RichLog)
            log.clear()

            if error:
                log.write(f"[#ff2244]Cannot load manifest:[/] {error}")
                log.write("")
                log.write("[#00ff41][ OPERATOR ][/] Press [bold #00ff41][A][/] to approve  |  "
                          "[bold #ff2244][X][/] to abort")
                return

            log.write(f"[bold #00e5ff]Gate:[/] {self._gate_id}")
            log.write(f"  [#00e5ff]Completed:[/] [bold #00ff41]{len(completed_ids)}[/] tasks")
            if failed_ids:
                log.write(f"  [#ff2244]Failed:[/] [bold]{len(failed_ids)}[/] tasks")
            log.write("")
            for tid in completed_ids[:20]:
                cost_part = f"  [dim]${task_costs[tid]:.5f}[/]" if tid in task_costs else ""
                log.write(f"  [#00ff41]✓[/] {tid}{cost_part}")
            if len(completed_ids) > 20:
                log.write(f"  [dim]... {len(completed_ids) - 20} more[/]")
            if failed_ids:
                log.write("")
                for tid in failed_ids[:10]:
                    log.write(f"  [#ff2244]✗[/] {tid}")
                if len(failed_ids) > 10:
                    log.write(f"  [dim]... {len(failed_ids) - 10} more[/]")
            log.write("")
            log.write("[#00ff41][ OPERATOR ][/] Press [bold #00ff41][A][/] to approve  |  "
                      "[bold #ff2244][X][/] to abort")

            # Populate pending table
            table = self.query_one("#pending-table", DataTable)
            table.clear()
            for tid in pending_ids[:50]:
                trec = tasks.get(tid) or {}
                if not isinstance(trec, dict):
                    trec = {}
                status = str(trec.get("status") or "pending")
                agent  = str(trec.get("agent") or trec.get("role") or "-")
                sc = _status_color(status)
                table.add_row(
                    tid[:22],
                    Text(status, style=sc) if Text else status,
                    agent[:14],
                )

            # Cost bar with stale indicator
            stale_part = (
                f"  [bold #ff2244]⚠ STALE — gate armed {stale_minutes}m (> 30 min)[/]"
                if stale else
                "  [#00ff41]OK[/]"
            )
            self.query_one("#cost-display", Static).update(
                f"[dim]Cost so far:[/] [bold #ffaa00]${total_cost:.5f}[/]  "
                f"[dim]·  Stale:[/]{stale_part}  "
                f"[dim]·  Pending:[/] [#00e5ff]{len(pending_ids)}[/]"
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
