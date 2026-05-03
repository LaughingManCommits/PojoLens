"""
Run-scoped Textual dashboard for pojolens-agents.

Ownership:
  - textual_is_available() / require_textual() — canonical source; imported by
    wizard.py and used by orchestrator_app via tui_layer proxy.
  - OrchestratorApp — async event-queue-driven dashboard for a single plan run:
    TaskGrid (task status / cost / elapsed), RunSummaryBar, LogPane (stderr tail),
    FooterBar (HITL approve/abort bindings).
  - This module is NOT a persistent REPL; it is scoped to one run invocation.
    The persistent operator REPL lives in console.py / tui_console.py.
"""
from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from rich.text import Text
    from textual.app import App, ComposeResult
    from textual.containers import Vertical
    from textual.widgets import DataTable, RichLog, Static
except ImportError as exc:  # pragma: no cover - exercised by non-extra installs
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment]
    ComposeResult = Any  # type: ignore[assignment]
    DataTable = object  # type: ignore[assignment]
    RichLog = object  # type: ignore[assignment]
    Static = object  # type: ignore[assignment]
    Vertical = object  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

from pojo_lens_agents.hitl import ABORT_VALUES, APPROVE_VALUES, HitlDecision, HitlGateContext, write_hitl_sentinel


def textual_is_available() -> bool:
    return TEXTUAL_IMPORT_ERROR is None


def require_textual() -> None:
    if not textual_is_available():
        raise RuntimeError(
            "The TUI dashboard requires the optional textual dependency. "
            "Install 'pojolens-agents[tui]'."
        ) from TEXTUAL_IMPORT_ERROR


def _parse_iso_datetime(value: str | None) -> datetime | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return None


def _format_elapsed(seconds: float | None) -> str:
    if seconds is None or seconds < 0:
        return "-"
    total = int(seconds)
    minutes, secs = divmod(total, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours:d}:{minutes:02d}:{secs:02d}"
    return f"{minutes:02d}:{secs:02d}"


def _status_style(status: str) -> str:
    return {
        "completed": "green",
        "failed": "red",
        "blocked": "red",
        "running": "yellow",
        "retry": "yellow",
        "planned": "dim",
        "pending": "dim",
    }.get(status, "white")


def _status_cell(status: str) -> Any:
    if Text is None:
        return status
    return Text(status, style=_status_style(status))


def _tail_lines(path: Path | None, *, max_lines: int = 40) -> list[str]:
    if path is None or not path.exists():
        return []
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []
    if len(lines) <= max_lines:
        return lines
    return lines[-max_lines:]


def _sentinel_action(path: Path) -> str | None:
    try:
        text = path.read_text(encoding="utf-8").strip().lower()
    except OSError:
        return None
    if text in APPROVE_VALUES:
        return "approve"
    if text in ABORT_VALUES:
        return "abort"
    return None


@dataclass
class TaskViewState:
    task_id: str
    model: str
    status: str = "pending"
    cost_usd: float | None = None
    started_at: str | None = None
    finished_at: str | None = None
    stderr_path: str | None = None

    def elapsed_seconds(self, *, now: datetime | None = None) -> float | None:
        started = _parse_iso_datetime(self.started_at)
        if started is None:
            return None
        finished = _parse_iso_datetime(self.finished_at)
        end = finished or now or datetime.now(timezone.utc)
        return max((end - started).total_seconds(), 0.0)


if textual_is_available():
    class TaskGrid(DataTable):
        def on_mount(self) -> None:
            self.cursor_type = "row"
            self.zebra_stripes = True
            self.add_column("Task", key="task")
            self.add_column("Status", key="status")
            self.add_column("Model", key="model")
            self.add_column("Cost", key="cost")
            self.add_column("Elapsed", key="elapsed")


    class RunSummaryBar(Static):
        def update_summary(
            self,
            *,
            completed: int,
            total: int,
            running: int,
            failed: int,
            total_cost_usd: float,
            elapsed_seconds: float,
        ) -> None:
            self.update(
                "  ".join(
                    [
                        f"done {completed}/{total}",
                        f"running {running}",
                        f"failed {failed}",
                        f"cost ${total_cost_usd:.5f}",
                        f"elapsed {_format_elapsed(elapsed_seconds)}",
                    ]
                )
            )


    class FooterBar(Static):
        def set_state(self, *, hitl_gate_id: str | None = None, sentinel_path: str | None = None) -> None:
            if hitl_gate_id:
                suffix = f"  gate {hitl_gate_id}"
                if sentinel_path:
                    suffix += f"  sentinel {sentinel_path}"
                self.update(f"[a] approve  [x] abort{suffix}")
                return
            self.update("Live dashboard")


    class LogPane(RichLog):
        pass


    class OrchestratorApp(App[None]):
        BINDINGS = [
            ("a", "approve_gate", "Approve"),
            ("x", "abort_gate", "Abort"),
        ]

        CSS = """
        Screen {
            layout: vertical;
        }

        #summary {
            height: 1;
            padding: 0 1;
        }

        #grid {
            height: 12;
            min-height: 8;
        }

        #log {
            height: 1fr;
        }

        #footer {
            height: 1;
            padding: 0 1;
        }
        """

        def __init__(
            self,
            *,
            event_queue: asyncio.Queue[dict[str, Any]],
            task_models: dict[str, str],
            plan_name: str,
        ) -> None:
            super().__init__()
            self.event_queue = event_queue
            self.plan_name = plan_name
            self.task_states = {
                task_id: TaskViewState(task_id=task_id, model=model or "-")
                for task_id, model in task_models.items()
            }
            self.task_order = list(task_models)
            self.run_started_at = datetime.now(timezone.utc)
            self.running_task_ids: set[str] = set()
            self.active_task_id: str | None = None
            self.active_stderr_path: Path | None = None
            self._last_log_lines: list[str] = []
            self._pending_hitl_future: asyncio.Future[str] | None = None
            self._pending_hitl_gate_id: str | None = None
            self._pending_hitl_sentinel_path: str | None = None

        def compose(self) -> ComposeResult:
            yield RunSummaryBar("", id="summary")
            with Vertical():
                yield TaskGrid(id="grid")
                yield LogPane(id="log", wrap=True, markup=False, highlight=False)
            yield FooterBar("", id="footer")

        def on_mount(self) -> None:
            self.title = f"PojoLens Agents: {self.plan_name}"
            grid = self.query_one(TaskGrid)
            for task_id in self.task_order:
                state = self.task_states[task_id]
                grid.add_row(
                    task_id,
                    _status_cell(state.status),
                    state.model,
                    "-",
                    "-",
                    key=task_id,
                )
            self._refresh_summary()
            self.query_one(FooterBar).set_state()
            self.set_interval(0.1, self._drain_events)
            self.set_interval(0.5, self._refresh_log_tail)
            self.set_interval(1.0, self._refresh_elapsed_cells)

        async def wait_for_hitl_decision(
            self,
            context: HitlGateContext,
            *,
            auto_approve: bool = False,
            write_text: Any = None,
        ) -> HitlDecision:
            sentinel_path = write_hitl_sentinel(context, write_text=write_text)
            sentinel = str(sentinel_path)
            if auto_approve:
                return HitlDecision(
                    approved=True,
                    action="approve",
                    reason="HITL gate auto-approved by operator flag.",
                    source="auto",
                    sentinel_path=sentinel,
                )
            loop = asyncio.get_running_loop()
            self._pending_hitl_future = loop.create_future()
            self._pending_hitl_gate_id = context.gate_id
            self._pending_hitl_sentinel_path = sentinel
            self.query_one(FooterBar).set_state(
                hitl_gate_id=context.gate_id,
                sentinel_path=sentinel,
            )
            try:
                while True:
                    if self._pending_hitl_future.done():
                        action = self._pending_hitl_future.result()
                        approved = action == "approve"
                        return HitlDecision(
                            approved=approved,
                            action=action,
                            reason=(
                                "Operator approved continuation via TUI."
                                if approved
                                else "Operator aborted continuation via TUI."
                            ),
                            source="tui",
                            sentinel_path=sentinel,
                        )
                    action = _sentinel_action(sentinel_path)
                    if action is not None:
                        approved = action == "approve"
                        return HitlDecision(
                            approved=approved,
                            action=action,
                            reason=(
                                "Operator approved via sentinel file."
                                if approved
                                else "Operator aborted via sentinel file."
                            ),
                            source="sentinel",
                            sentinel_path=sentinel,
                        )
                    await asyncio.sleep(0.25)
            finally:
                self._pending_hitl_future = None
                self._pending_hitl_gate_id = None
                self._pending_hitl_sentinel_path = None
                self.query_one(FooterBar).set_state()

        def action_approve_gate(self) -> None:
            if self._pending_hitl_future is not None and not self._pending_hitl_future.done():
                self._pending_hitl_future.set_result("approve")

        def action_abort_gate(self) -> None:
            if self._pending_hitl_future is not None and not self._pending_hitl_future.done():
                self._pending_hitl_future.set_result("abort")

        def _drain_events(self) -> None:
            while True:
                try:
                    event = self.event_queue.get_nowait()
                except asyncio.QueueEmpty:
                    break
                self._handle_event(event)

        def _handle_event(self, event: dict[str, Any]) -> None:
            phase = str(event.get("phase", "") or "")
            if phase == "task-started":
                self._handle_task_started(event)
            elif phase in {"task-finished", "task-reused"}:
                self._handle_task_finished(event)
            elif phase == "task-retry":
                self._handle_task_retry(event)
            elif phase == "run-finished":
                self._refresh_summary()
                self.exit()
                return
            self._refresh_summary()

        def _handle_task_started(self, event: dict[str, Any]) -> None:
            task_id = str(event.get("taskId", "") or "")
            if task_id not in self.task_states:
                return
            details = event.get("details", {}) or {}
            state = self.task_states[task_id]
            state.status = "running"
            state.started_at = str(details.get("startedAt", "") or datetime.now(timezone.utc).isoformat())
            state.stderr_path = str(details.get("stderrPath", "") or "") or None
            model = str(details.get("model", "") or "").strip()
            if model:
                state.model = model
            self.running_task_ids.add(task_id)
            self.active_task_id = task_id
            self.active_stderr_path = Path(state.stderr_path) if state.stderr_path else None
            self._update_task_row(task_id)

        def _handle_task_finished(self, event: dict[str, Any]) -> None:
            task_id = str(event.get("taskId", "") or "")
            if task_id not in self.task_states:
                return
            details = event.get("details", {}) or {}
            state = self.task_states[task_id]
            state.status = str(event.get("status", "") or state.status or "completed")
            state.finished_at = str(details.get("finishedAt", "") or datetime.now(timezone.utc).isoformat())
            if not state.started_at:
                state.started_at = str(details.get("startedAt", "") or state.finished_at)
            state.stderr_path = str(details.get("stderrPath", "") or state.stderr_path or "") or None
            usage = details.get("usage", {})
            if isinstance(usage, dict):
                total_cost = usage.get("totalCostUsd")
                if total_cost is not None:
                    try:
                        state.cost_usd = float(total_cost)
                    except (TypeError, ValueError):
                        pass
            self.running_task_ids.discard(task_id)
            if self.active_task_id == task_id:
                self.active_task_id = next(iter(self.running_task_ids), None)
                if self.active_task_id is not None:
                    active_state = self.task_states[self.active_task_id]
                    self.active_stderr_path = Path(active_state.stderr_path) if active_state.stderr_path else None
                else:
                    self.active_stderr_path = None
            self._update_task_row(task_id)

        def _handle_task_retry(self, event: dict[str, Any]) -> None:
            task_id = str(event.get("taskId", "") or "")
            if task_id in self.task_states:
                self.task_states[task_id].status = "retry"
                self._update_task_row(task_id)
            details = event.get("details", {}) or {}
            error = str(details.get("error", "") or event.get("message", "") or "").strip()
            if error:
                log = self.query_one(LogPane)
                log.write(f"[retry] {task_id}: {error}")

        def _refresh_summary(self) -> None:
            now = datetime.now(timezone.utc)
            completed = 0
            failed = 0
            total_cost = 0.0
            for state in self.task_states.values():
                if state.status == "completed":
                    completed += 1
                elif state.status in {"failed", "blocked"}:
                    failed += 1
                if state.cost_usd is not None:
                    total_cost += state.cost_usd
            self.query_one(RunSummaryBar).update_summary(
                completed=completed,
                total=len(self.task_states),
                running=len(self.running_task_ids),
                failed=failed,
                total_cost_usd=total_cost,
                elapsed_seconds=max((now - self.run_started_at).total_seconds(), 0.0),
            )

        def _refresh_elapsed_cells(self) -> None:
            for task_id in self.task_order:
                self._update_task_row(task_id)
            self._refresh_summary()

        def _update_task_row(self, task_id: str) -> None:
            state = self.task_states[task_id]
            grid = self.query_one(TaskGrid)
            elapsed = state.elapsed_seconds()
            cost_text = "-" if state.cost_usd is None else f"${state.cost_usd:.5f}"
            grid.update_cell(task_id, "status", _status_cell(state.status))
            grid.update_cell(task_id, "model", state.model)
            grid.update_cell(task_id, "cost", cost_text)
            grid.update_cell(task_id, "elapsed", _format_elapsed(elapsed))

        def _refresh_log_tail(self) -> None:
            lines = _tail_lines(self.active_stderr_path)
            if lines == self._last_log_lines:
                return
            self._last_log_lines = list(lines)
            log = self.query_one(LogPane)
            log.clear()
            if not lines:
                if self.active_task_id:
                    log.write(f"No stderr yet for {self.active_task_id}.")
                else:
                    log.write("No active task stderr.")
                return
            for line in lines:
                log.write(line)
