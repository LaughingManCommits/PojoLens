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
    from textual.reactive import reactive
    from textual.screen import Screen
    from textual.widgets import Button, Footer, Header, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]

try:
    from pojo_lens_agents.orchestrator_contracts import (
        DEFAULT_RUNTIME_ROOT,
        DEFAULT_AGENTS_PATH,
    )
except ImportError:  # pragma: no cover
    DEFAULT_RUNTIME_ROOT = Path(".claude-orchestrator")
    DEFAULT_AGENTS_PATH = Path("ai/orchestrator/agents.json")

from pojo_lens_agents._tui_helpers import _status_color

_SPINNER = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"]


# ── PlanRunScreen ──────────────────────────────────────────────────────────────

class PlanRunScreen(Screen):  # type: ignore[type-arg,misc]
    """Execute a plan or run the wizard to generate + execute.

    Plan run:   PlanRunScreen(plan_path, dry_run=False)
    Wizard run: PlanRunScreen(wizard_params={goal, effort, workspace_mode,
                              hitl, max_parallel, budget, provider,
                              budget_behavior, follow_up})

    App-level config (handlers, parse_args_fn, runtime_root, agents,
    claude_bin) is always read from self.app at runtime.
    """

    BINDINGS = [
        Binding("escape", "go_back", "Back",              show=True),
        Binding("h",      "go_home", "Home / Dashboard",  show=True),
    ]

    _run_status: reactive[str]  = reactive("[ CONSTRUCT ] initializing...")
    _running:    reactive[bool] = reactive(False)
    _spin_tick:  reactive[int]  = reactive(0)

    def __init__(
        self,
        plan_path: str = "",
        *,
        dry_run: bool = False,
        wizard_params: dict[str, Any] | None = None,
    ) -> None:
        super().__init__()
        self._plan_path          = plan_path
        self._dry_run            = dry_run
        self._wizard_params      = wizard_params
        self._runtime_root_path: Path | None = None

    def compose(self) -> ComposeResult:
        yield Header()
        wp = self._wizard_params
        with Container(id="top-bar"):
            if wp:
                yield Static(
                    "[ STEP 5/5 ]  WIZARD: GENERATING + EXECUTING PLAN",
                    id="run-title",
                )
                yield Static(
                    f"goal: {(wp.get('goal') or '')[:60]}  "
                    f"effort: {wp.get('effort', '')}  "
                    f"workspace: {wp.get('workspace_mode', '')}  "
                    f"provider: {wp.get('provider') or 'default'}  "
                    f"hitl: {wp.get('hitl', '')}  "
                    f"parallel: {wp.get('max_parallel', 2)}",
                    id="run-plan",
                )
            else:
                prefix = "DRY RUN" if self._dry_run else "RUN"
                yield Static(f"[ {prefix} ]  Executing plan", id="run-title")
                yield Static(self._plan_path, id="run-plan")
            yield Static("[ SIGNAL ] initializing...", id="run-status")
        with Horizontal(id="run-progress-bar"):
            yield Static("", id="run-prog-label")
            yield Static("", id="run-prog-bar")
            yield Static("", id="run-prog-cost")
        yield RichLog(id="run-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("⬡ MONITOR DASHBOARD", id="btn-home", variant="primary")
            yield Button("BACK",                 id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN"
        if self._wizard_params:
            self.app.sub_title = "WIZARD"  # type: ignore[attr-defined]
        else:
            self.app.sub_title = "DRY RUN" if self._dry_run else "LIVE RUN"  # type: ignore[attr-defined]
        self._runtime_root_path = Path(str(getattr(
            self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT
        ))).resolve()
        worker = self._run_wizard if self._wizard_params else self._execute_run
        self.run_worker(worker, thread=True, name="plan-run")
        self.set_interval(2.0, self._poll_progress)
        self.set_interval(0.2, self._tick_spinner)

    def watch__run_status(self, status: str) -> None:
        try:
            self.query_one("#run-status", Static).update(f"[#00ff41][ SIGNAL ] {status}[/]")
        except Exception:
            pass

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-home":
            self.action_go_home()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def action_go_home(self) -> None:
        self.app.go_home()  # type: ignore[attr-defined]

    def _log(self, text: str) -> None:
        try:
            self.app.call_from_thread(lambda: self.query_one("#run-log", RichLog).write(text))
        except Exception:
            pass

    def _set_status(self, s: str) -> None:
        try:
            self.app.call_from_thread(lambda: setattr(self, "_run_status", s))
        except Exception:
            pass

    # ── shared: live progress ──────────────────────────────────────────────────

    def _tick_spinner(self) -> None:
        if self._running:
            self._spin_tick = (int(self._spin_tick) + 1) % len(_SPINNER)  # type: ignore[assignment]

    def _poll_progress(self) -> None:
        if self._runtime_root_path is None:
            return
        runs_dir = self._runtime_root_path / "runs"
        if not runs_dir.exists():
            return
        manifests = sorted(
            runs_dir.glob("*/manifest.json"),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
        if not manifests:
            return
        try:
            data = json.loads(manifests[0].read_text(encoding="utf-8"))
        except Exception:
            return

        events     = list(data.get("events") or [])
        phases     = [e.get("phase", "") for e in events]
        tasks_dict = data.get("tasks") or {}
        total = len(tasks_dict) or int((data.get("topology") or {}).get("taskCount", 0))
        done  = sum(
            1 for e in events
            if e.get("phase") == "task-finished"
            and e.get("status") in {"completed", "skipped", "reused"}
        )
        failed = sum(
            1 for e in events
            if e.get("phase") == "task-finished"
            and e.get("status") == "failed"
        )
        state = "completed" if "run-finished" in phases else ("running" if "run-start" in phases else "")
        if not state:
            return

        cost = float((data.get("usageTotals") or {}).get("totalCostUsd", 0.0) or 0.0)
        if cost == 0.0:
            rg = data.get("runGovernance") or {}
            cost = sum(float(t.get("costUsd", 0.0)) for t in (rg.get("highestCostTasks") or []))

        filled   = int(done / total * 30) if total else 0
        sc_ok    = "#00ff41"
        sc_state = sc_ok if state == "completed" else "#ffaa00"
        bar      = f"[{sc_ok}]{'█' * filled}[/][dim]{'░' * (30 - filled)}[/]"
        spin     = _SPINNER[int(self._spin_tick)] if state == "running" else "✓"
        cost_str = f"${cost:.4f}" if cost else ""
        fail_str = f"  [#ff2244]✗{failed}[/]" if failed else ""

        try:
            self.query_one("#run-prog-label", Static).update(
                f"[{sc_state}]{spin}[/]  [{sc_state}]{state.upper()}[/]"
            )
            self.query_one("#run-prog-bar", Static).update(
                f"  {bar}  [{sc_ok}]{done}[/]{fail_str}/[dim]{total}[/]"
            )
            self.query_one("#run-prog-cost", Static).update(
                f"  [#ffaa00]{cost_str}[/]" if cost_str else ""
            )
        except Exception:
            pass

    # ── plan execution ─────────────────────────────────────────────────────────

    def _execute_run(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers",      {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn available on app.[/]")
            return

        runtime_root = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))
        agents       = str(getattr(self.app, "_agents",       DEFAULT_AGENTS_PATH))
        claude_bin   = str(getattr(self.app, "_claude_bin",   "claude"))

        cmd = ["run", self._plan_path]
        if self._dry_run:
            cmd.append("--dry-run")
        cmd += ["--runtime-root", runtime_root, "--agents", agents,
                "--provider-bin", claude_bin, "--json"]

        self._set_status("building args...")
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            self._log(f"[#ff2244]Arg parse error: {exc}[/]")
            return

        self._set_status("[ OPERATOR ] executing plan...")
        self._log(f"[#00e5ff][ SIGNAL ] run started: {self._plan_path}[/]")
        self._log("[dim]── progress bar updates every 2 s  ·  press [H] to monitor on home dashboard ──[/]")

        handler = handlers.get("run")
        if handler is None:
            self._log("[#ff2244]No 'run' handler registered.[/]")
            return

        self.app.call_from_thread(lambda: setattr(self, "_running", True))
        buf, old_stdout = _io.StringIO(), sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            import traceback as _tb
            payload = {}
            self._log(f"[#ff2244]Run error: {exc}[/]")
            self._log(f"[dim]{_tb.format_exc()[:400]}[/]")
        finally:
            sys.stdout = old_stdout
            captured   = buf.getvalue().strip()

        self.app.call_from_thread(lambda: setattr(self, "_running", False))

        if captured:
            self._log("")
            self._log("[dim #2a5a3a]── captured output ──[/]")
            for line in captured.splitlines()[:50]:
                self._log(line)

        if payload:
            self._log("")
            self._log("[dim #2a5a3a]═══ RUN RESULT ═══[/]")
            status = str(payload.get("status") or "")
            if status:
                sc = _status_color(status)
                self._log(f"[bold {sc}]status: {status}[/]")
            for k, v in sorted((dict(payload.get("statusCounts") or {})).items()):
                self._log(f"  [{_status_color(k)}]{k}: {v}[/{_status_color(k)}]")
            run_dir = str(payload.get("runDir") or "")
            if run_dir:
                self._log(f"[dim]run dir: {run_dir}[/]")
            cost = payload.get("totalCostUsd") or payload.get("total_cost_usd")
            if cost:
                self._log(f"[#00e5ff]cost:[/] [#ffaa00]${cost:.5f}[/]")

        self._set_status("[ EXIT ] run complete")
        self._log("[bold #00ff41]═══ EXECUTION COMPLETE — press [H] for dashboard ═══[/]")

    # ── wizard execution ───────────────────────────────────────────────────────

    def _run_wizard(self) -> None:
        import io as _io
        wp            = self._wizard_params or {}
        handlers      = getattr(self.app, "_handlers",      {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        runtime_root  = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))
        agents        = str(getattr(self.app, "_agents",       DEFAULT_AGENTS_PATH))
        claude_bin    = str(getattr(self.app, "_claude_bin",   "claude"))

        self._set_status("building wizard args...")
        self._log("[dim #2a5a3a]═══ WIZARD START ═══[/]")

        arg_list = ["wizard", "--json"]
        goal           = str(wp.get("goal")           or "")
        effort         = str(wp.get("effort")         or "")
        workspace_mode = str(wp.get("workspace_mode") or "")
        hitl           = str(wp.get("hitl")           or "")
        max_parallel   = int(wp.get("max_parallel",   2))
        budget         = wp.get("budget")
        budget_behavior = str(wp.get("budget_behavior", "warn"))
        follow_up      = str(wp.get("follow_up",       "ignore"))
        provider       = wp.get("provider")

        if goal:           arg_list += ["--goal",             goal]
        if effort:         arg_list += ["--planner-effort",   effort]
        if workspace_mode: arg_list += ["--workspace-mode",   workspace_mode]
        if hitl:           arg_list += ["--hitl",             hitl]
        arg_list += ["--max-parallel", str(max_parallel)]
        if budget is not None:
            arg_list += ["--run-budget-usd", str(budget)]
        if budget_behavior and budget_behavior != "warn":
            arg_list += ["--budget-behavior", budget_behavior]
        if follow_up and follow_up != "ignore":
            arg_list += ["--follow-up-behavior", follow_up]
        if provider:       arg_list += ["--default-provider", provider]
        if agents:         arg_list += ["--agents",           agents]
        if runtime_root:   arg_list += ["--runtime-root",     runtime_root]
        if claude_bin:     arg_list += ["--provider-bin",     claude_bin]

        self._set_status("parsing arguments...")
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return
        try:
            args = parse_args_fn(arg_list)
        except SystemExit as exc:
            self._log(f"[#ff2244]Arg parse failed: {exc}[/]")
            self._set_status("failed — arg parse error")
            return
        except Exception as exc:
            self._log(f"[#ff2244]Error building args: {exc}[/]")
            self._set_status("failed — args error")
            return

        self._set_status("[ CONSTRUCT ] invoking wizard agent...")
        self._log("[#00e5ff][ SIGNAL ] wizard agent dispatched[/]")

        handler = handlers.get("wizard")
        if handler is None:
            self._log("[#ff2244]wizard handler not found[/]")
            self._set_status("failed — no wizard handler")
            return

        self.app.call_from_thread(lambda: setattr(self, "_running", True))
        buf, old_stdout = _io.StringIO(), sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]Wizard error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured   = buf.getvalue().strip()

        self.app.call_from_thread(lambda: setattr(self, "_running", False))

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log("[dim #2a5a3a]═══ RESULT PAYLOAD ═══[/]")
            status = str(payload.get("status") or "")
            if status:
                sc = _status_color(status)
                self._log(f"[bold {sc}]status: {status}[/]")
            for step in (payload.get("steps") or []):
                sname, sstatus = str(step.get("name", "")), str(step.get("status", ""))
                sc = _status_color(sstatus)
                self._log(f"  [#00e5ff]▸[/] {sname}: [{sc}]{sstatus}[/{sc}]")
            plan_path = str(payload.get("planPath") or payload.get("savedPlanPath") or "")
            if plan_path:
                self._log(f"[#00ff41]plan: {plan_path}[/]")
            saved = str(payload.get("savedPlanPath") or "")
            if saved:
                self._log(f"[#00e5ff]saved to: {saved}[/]")

        self._set_status("[ EXIT ] wizard complete")
        self._log("")
        self._log("[bold #00ff41]═══ WIZARD COMPLETE — press [H] for dashboard ═══[/]")
        self.app.call_from_thread(  # type: ignore[attr-defined]
            lambda: setattr(self.app, "sub_title", "COMPLETE")
        )
