from __future__ import annotations

import datetime
import json
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.containers import Vertical
    from textual.reactive import reactive
    from textual.widget import Widget
    from textual.widgets import RichLog, Rule, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    Widget = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]

from pojo_lens_agents._tui_helpers import _status_color


class DashboardWidget(Widget):  # type: ignore[type-arg,misc]
    """Live dashboard panel — polls the most recent run manifest every 2 s."""

    _run_id:    reactive[str] = reactive("")
    _run_state: reactive[str] = reactive("idle")

    def compose(self) -> ComposeResult:
        yield Static("[ DASHBOARD ]", id="dash-title")
        yield Rule(id="dash-rule-top")
        with Vertical(id="dash-stats"):
            yield Static("", id="dash-run-id")
            yield Static("", id="dash-status")
            yield Static("", id="dash-progress")
            yield Static("", id="dash-cost")
            yield Static("", id="dash-elapsed")
        yield Rule(id="dash-rule-mid")
        yield Static("[ RECENT ACTIVITY ]", id="dash-activity-title")
        yield RichLog(id="dash-log", markup=True, auto_scroll=True,
                      max_lines=40, wrap=True, highlight=False)
        yield Static("", id="dash-idle-msg")

    def on_mount(self) -> None:
        self.set_interval(2.0, self._poll)
        self._show_idle()

    # ── polling ────────────────────────────────────────────────────────────────

    def _poll(self) -> None:
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

        try:
            data = json.loads(manifests[0].read_text(encoding="utf-8"))
            self._update_display(data, manifests[0].parent)
        except Exception:
            self._show_idle()

    # ── display ────────────────────────────────────────────────────────────────

    def _show_idle(self) -> None:
        self.query_one("#dash-run-id",   Static).update("")
        self.query_one("#dash-status",   Static).update("")
        self.query_one("#dash-progress", Static).update("")
        self.query_one("#dash-cost",     Static).update("")
        self.query_one("#dash-elapsed",  Static).update("")
        self.query_one("#dash-idle-msg", Static).update(
            "[dim #2a5a3a][ IDLE ]  No run data yet — press [N] to start a plan[/]"
        )

    def _update_display(self, data: dict[str, Any], run_path: Path) -> None:
        run_id = str(data.get("runId") or run_path.name)

        events = list(data.get("events") or [])
        phases = [e.get("phase", "") for e in events]
        if "run-finished" in phases:
            state = "completed"
        elif "run-start" in phases:
            state = "running"
        else:
            state = "unknown"

        tasks_dict = data.get("tasks") or {}
        total = len(tasks_dict) or int((data.get("topology") or {}).get("taskCount", 0))

        done = sum(
            1 for e in events
            if e.get("phase") == "task-finished"
            and e.get("status") in {"completed", "skipped", "reused"}
        )

        rg         = data.get("runGovernance") or {}
        cost_tasks = rg.get("highestCostTasks") or []
        cost: float | None = sum(t.get("costUsd", 0.0) for t in cost_tasks) or None

        start = events[0].get("ts") if events else None

        sc = _status_color(state)

        filled = int(done / total * 20) if total else 0
        bar = f"[{sc}]{'█' * filled}[/][dim]{'░' * (20 - filled)}[/]"

        elapsed_str = ""
        if start:
            try:
                t0  = datetime.datetime.fromisoformat(str(start))
                now = datetime.datetime.now(t0.tzinfo)
                secs = int((now - t0).total_seconds())
                elapsed_str = f"{secs // 3600:02d}:{(secs % 3600) // 60:02d}:{secs % 60:02d}"
            except Exception:
                pass

        self.query_one("#dash-run-id", Static).update(
            f"[#00e5ff]Run  :[/] [dim]{run_id[-52:]}[/]"
        )
        self.query_one("#dash-status", Static).update(
            f"[#00e5ff]State:[/] [{sc}]{state.upper()}[/]"
        )
        self.query_one("#dash-progress", Static).update(
            f"[#00e5ff]Tasks:[/] {bar}  {done}/{total}"
        )
        cost_str = f"${cost:.4f}" if cost is not None else "—"
        self.query_one("#dash-cost", Static).update(
            f"[#00e5ff]Cost :[/] [#ffaa00]{cost_str}[/]"
        )
        self.query_one("#dash-elapsed", Static).update(
            f"[#00e5ff]Time :[/] {elapsed_str}" if elapsed_str else ""
        )
        self.query_one("#dash-idle-msg", Static).update("")

        log = self.query_one("#dash-log", RichLog)
        log.clear()

        task_events = [
            e for e in events
            if e.get("phase") in {"task-started", "task-finished", "batch-ready", "run-finished"}
        ]
        for e in task_events[-20:]:
            phase  = str(e.get("phase", ""))
            tid    = str(e.get("taskId", ""))
            status = str(e.get("status") or "")
            ts_raw = str(e.get("ts", ""))
            ts_str = ts_raw[11:19] if len(ts_raw) >= 19 else ""
            esc    = _status_color(status) if status else "#00e5ff"

            if phase == "run-finished":
                log.write(f"[dim]{ts_str}[/]  [bold {esc}]RUN {state.upper()}[/]")
            elif phase == "batch-ready":
                batch_ids = e.get("taskIds") or []
                log.write(
                    f"[dim]{ts_str}[/]  [#00e5ff]BATCH[/] "
                    f"[dim]{', '.join(batch_ids[:3])}[/]"
                )
            elif tid:
                icon = "✓" if status == "completed" else ("✗" if status == "failed" else "▸")
                title = str((tasks_dict.get(tid) or {}).get("title") or tid)[:36]
                log.write(
                    f"[dim]{ts_str}[/]  [{esc}]{icon}[/] "
                    f"[dim]{tid[:20]}[/] {title}  [{esc}]{status}[/]"
                )

        if not task_events:
            log.write("[dim #2a5a3a]Waiting for first task to start...[/]")
