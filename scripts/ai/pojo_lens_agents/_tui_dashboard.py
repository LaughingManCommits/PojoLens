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
    from textual.widgets import Button, RichLog, Rule, Static
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


class DashboardWidget(Widget):  # type: ignore[type-arg,misc]
    """Live dashboard — paginate through all runs; shows aggregate stats."""

    _run_state: reactive[str]           = reactive("idle")
    _run_dir:   reactive[Path | None]   = reactive(None)  # type: ignore[type-arg]
    _run_index: reactive[int]           = reactive(0)

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        self._manifests: list[Path] = []

    def compose(self) -> ComposeResult:
        yield Static("[ DASHBOARD ]", id="dash-title")
        yield Static("", id="dash-stats-box")
        yield Rule(id="dash-rule-top")
        with Horizontal(id="dash-nav"):
            yield Button("◀", id="btn-run-prev", variant="default", disabled=True)
            yield Static("", id="dash-run-nav")
            yield Button("▶", id="btn-run-next", variant="default", disabled=True)
        yield Rule(id="dash-rule-nav")
        with Vertical(id="dash-stats"):
            yield Static("", id="dash-run-id")
            yield Static("", id="dash-status")
            yield Static("", id="dash-progress")
            yield Static("", id="dash-cost")
            yield Static("", id="dash-tokens")
            yield Static("", id="dash-elapsed")
        yield Rule(id="dash-rule-mid")
        yield Static("[ RECENT ACTIVITY ]", id="dash-activity-title")
        yield RichLog(id="dash-log", markup=True, auto_scroll=True,
                      max_lines=40, wrap=True, highlight=False)
        yield Static("", id="dash-idle-msg")
        yield Rule(id="dash-rule-bot")
        with Horizontal(id="dash-actions"):
            yield Button("STOP",   id="btn-dash-stop",   variant="error",   disabled=True)
            yield Button("PAUSE",  id="btn-dash-pause",  variant="warning", disabled=True)
            yield Button("DELETE", id="btn-dash-delete", variant="default", disabled=True)

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

        # Rebuild aggregate stats when list size changes
        if len(manifests) != len(self._manifests):
            self._manifests = manifests
            self._update_stats_box(manifests)

        self._manifests = manifests
        idx = max(0, min(int(self._run_index), len(manifests) - 1))
        self._run_index = idx  # type: ignore[assignment]

        try:
            data = json.loads(manifests[idx].read_text(encoding="utf-8"))
            self._update_display(data, manifests[idx].parent)
        except Exception:
            self._show_idle()

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

    # ── run nav bar ────────────────────────────────────────────────────────────

    def _update_nav_bar(self, idx: int, manifests: list[Path]) -> None:
        n = len(manifests)
        if n == 0:
            return
        label = f"Run [bold]{idx + 1}[/bold] / {n}"
        if idx == 0:
            label += "  [#00ff41][latest][/]"
        try:
            self.query_one("#dash-run-nav", Static).update(label)
            self.query_one("#btn-run-prev", Button).disabled = idx >= n - 1
            self.query_one("#btn-run-next", Button).disabled = idx <= 0
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
            self.query_one("#dash-run-nav", Static).update("—")
            self.query_one("#btn-run-prev", Button).disabled = True
            self.query_one("#btn-run-next", Button).disabled = True
        except Exception:
            pass
        self._run_dir = None  # type: ignore[assignment]
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
        self._set_action_buttons(state, run_path)
        self._update_nav_bar(int(self._run_index), self._manifests)

        try:
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
                    icon  = "✓" if status == "completed" else ("✗" if status == "failed" else "▸")
                    title = str((tasks_dict.get(tid) or {}).get("title") or tid)[:36]
                    log.write(
                        f"[dim]{ts_str}[/]  [{esc}]{icon}[/] "
                        f"[dim]{tid[:20]}[/] {title}  [{esc}]{status}[/]"
                    )
            if not task_events:
                log.write("[dim #2a5a3a]Waiting for first task to start...[/]")
        except Exception:
            pass

    # ── run navigation ─────────────────────────────────────────────────────────

    def _navigate_run(self, delta: int) -> None:
        n = len(self._manifests)
        if n == 0:
            return
        new_idx = max(0, min(int(self._run_index) + delta, n - 1))
        self._run_index = new_idx  # type: ignore[assignment]
        try:
            data = json.loads(self._manifests[new_idx].read_text(encoding="utf-8"))
            self._update_display(data, self._manifests[new_idx].parent)
        except Exception:
            pass

    # ── action buttons ─────────────────────────────────────────────────────────

    def _set_action_buttons(self, state: str, run_path: Path | None) -> None:
        running = state == "running"
        paused  = run_path is not None and (run_path / "pause.flag").exists()
        try:
            self.query_one("#btn-dash-stop",   Button).disabled = not running
            pause_btn = self.query_one("#btn-dash-pause", Button)
            pause_btn.disabled = not running
            pause_btn.label = "RESUME" if paused else "PAUSE"
            self.query_one("#btn-dash-delete", Button).disabled = run_path is None or running
        except Exception:
            pass

    def on_button_pressed(self, event: "Button.Pressed") -> None:
        btn_id = event.button.id
        if btn_id == "btn-run-prev":
            self._navigate_run(+1)      # older
        elif btn_id == "btn-run-next":
            self._navigate_run(-1)      # newer
        elif btn_id == "btn-dash-stop":
            self._do_stop()
        elif btn_id == "btn-dash-pause":
            self._do_pause_toggle()
        elif btn_id == "btn-dash-delete":
            self._do_delete()

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
            self._run_dir = None  # type: ignore[assignment]
            # remove from cached list and go to next
            self._manifests = [m for m in self._manifests if m.parent != run_path]
            if self._manifests:
                new_idx = min(int(self._run_index), len(self._manifests) - 1)
                self._run_index = new_idx  # type: ignore[assignment]
                try:
                    data = json.loads(self._manifests[new_idx].read_text(encoding="utf-8"))
                    self._update_display(data, self._manifests[new_idx].parent)
                    self._update_stats_box(self._manifests)
                    return
                except Exception:
                    pass
            self._show_idle()
        except Exception:
            pass
