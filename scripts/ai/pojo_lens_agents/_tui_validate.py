from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, VerticalScroll
    from textual.screen import ModalScreen, Screen
    from textual.widgets import Button, DataTable, Footer, Header, Input, LoadingIndicator, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]

try:
    from pojo_lens_agents.orchestrator_contracts import (
        DEFAULT_AGENTS_PATH,
    )
except ImportError:  # pragma: no cover
    DEFAULT_AGENTS_PATH = Path("ai/orchestrator/agents.json")

from pojo_lens_agents._tui_helpers import _status_color
from pojo_lens_agents._tui_wizard_run import PlanRunScreen as RunPlanScreen  # noqa: F401


# ── ValidatePlanDialog / ValidateRunScreen ────────────────────────────────────

class ValidatePlanDialog(ModalScreen):  # type: ignore[type-arg,misc]
    """Modal: enter a plan path, dismiss with path string or None."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def compose(self) -> ComposeResult:
        with Container(id="card"):
            yield Static("[ VALIDATE ]  Enter plan file path", id="card-title")
            yield Input(
                placeholder="ai/orchestrator/tasks/my-plan.json",
                id="plan-path-input",
            )
            with Horizontal(id="btns"):
                yield Button("VALIDATE →", id="btn-validate", variant="primary")
                yield Button("CANCEL",     id="btn-cancel")

    def on_mount(self) -> None:
        self.query_one("#plan-path-input", Input).focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-validate":
            path = self.query_one("#plan-path-input", Input).value.strip()
            if path:
                self.dismiss(path)
        elif event.button.id == "btn-cancel":
            self.action_go_back()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        path = self.query_one("#plan-path-input", Input).value.strip()
        if path:
            self.dismiss(path)

    def action_go_back(self) -> None:
        self.dismiss(None)


class ValidateRunScreen(Screen):  # type: ignore[type-arg,misc]
    """Run validation on a plan file and display a structured results screen."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def __init__(self, plan_path: str) -> None:
        super().__init__()
        self._plan_path = plan_path

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ VALIDATE ]  running…", id="val-title")
            yield Static(self._plan_path, id="val-path")
        yield LoadingIndicator(id="val-loading")
        with VerticalScroll(id="val-body"):
            # ── result banner ──────────────────────────────────────────────
            yield Static("", id="val-banner")
            # ── plan ──────────────────────────────────────────────────────
            yield Static("[ PLAN ]", classes="val-hdr")
            with Horizontal(classes="val-row"):
                yield Static("name",    classes="val-label")
                yield Static("", id="f-plan-name",  classes="val-value")
            with Horizontal(classes="val-row"):
                yield Static("tasks",   classes="val-label")
                yield Static("", id="f-task-count", classes="val-value")
            with Horizontal(classes="val-row"):
                yield Static("batches", classes="val-label")
                yield Static("", id="f-batches",    classes="val-value")
            with Horizontal(classes="val-row"):
                yield Static("policy",  classes="val-label")
                yield Static("", id="f-policy",     classes="val-value")
            yield Static("[ AGENTS ]", classes="val-hdr")
            with Horizontal(classes="val-row"):
                yield Static("used",    classes="val-label")
                yield Static("", id="f-agent-names", classes="val-value")
            yield Static("[ TASK DETAILS ]", classes="val-hdr")
            yield DataTable(id="tasks-table", show_cursor=False)
            with Container(id="issues-section"):
                yield Static("[ ISSUES ]", classes="val-hdr-err")
                yield Static("", id="issues-list", classes="val-issues")
            with Container(id="cost-section"):
                yield Static("[ COST ESTIMATE ]", classes="val-hdr")
                with Horizontal(classes="val-row"):
                    yield Static("total",   classes="val-label")
                    yield Static("", id="f-cost-total",  classes="val-value")
                with Horizontal(classes="val-row"):
                    yield Static("per task", classes="val-label")
                    yield Static("", id="f-cost-per",    classes="val-value")
                with Horizontal(classes="val-row"):
                    yield Static("other",   classes="val-label")
                    yield Static("", id="f-cost-other",  classes="val-value")
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  VALIDATE"
        table = self.query_one("#tasks-table", DataTable)
        table.add_column("id",      key="id",      width=18)
        table.add_column("agent",   key="agent",   width=14)
        table.add_column("model",   key="model",   width=16)
        table.add_column("effort",  key="effort",  width=8)
        table.add_column("skills",  key="skills",  width=20)
        table.add_column("profile", key="profile", width=12)
        self.query_one("#val-body").display = False
        self.run_worker(self._run_validation, thread=True, name="validate")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    # ── result population ──────────────────────────────────────────────────────

    def _populate_results(self, payload: dict) -> None:
        try:
            self.query_one("#val-loading").display = False
            self.query_one("#val-body").display    = True
        except Exception:
            pass

        # Collect all issues
        topo       = payload.get("topology") or {}
        cost_data  = payload.get("costEstimate") or {}
        errors_all = list(payload.get("errors") or [])
        warns_all  = list(payload.get("warnings") or [])
        warns_all.extend(list(topo.get("warnings") or []))
        if isinstance(cost_data, dict):
            warns_all.extend(list(cost_data.get("warnings") or []))
        conflicts = payload.get("parallelConflicts")
        if isinstance(conflicts, dict):
            for tid, detail in conflicts.items():
                errors_all.append(f"parallel conflict on '{tid}': {detail}")
        elif isinstance(conflicts, list):
            errors_all.extend(str(c) for c in conflicts)

        # Determine status
        has_errors = bool(errors_all)
        has_warns  = bool(warns_all)
        if has_errors:
            banner = "[bold #ff2244]  ✗  PLAN HAS ERRORS  [/]"
            title  = "[bold #ff2244][ VALIDATE ]  ✗ ERRORS[/]"
        elif has_warns:
            banner = "[bold #ffaa00]  ⚠  VALID WITH WARNINGS  [/]"
            title  = "[bold #ffaa00][ VALIDATE ]  ⚠ WARNINGS[/]"
        else:
            banner = "[bold #00ff41]  ✓  PLAN IS VALID  [/]"
            title  = "[bold #00ff41][ VALIDATE ]  ✓ VALID[/]"

        self._safe_update("#val-banner", banner)
        self._safe_update("#val-title",  title)

        # Plan summary
        plan_name = str(payload.get("planName") or Path(self._plan_path).stem)
        task_cnt  = str(payload.get("taskCount") or "—")
        batches   = payload.get("batches") or []
        batch_str = f"{len(batches)} wave{'s' if len(batches) != 1 else ''}" if batches else "—"
        rp        = payload.get("runPolicy") or {}
        pol_parts: list[str] = []
        if rp.get("hitlMode"):     pol_parts.append(f"hitl={rp['hitlMode']}")
        if rp.get("runBudgetUsd"): pol_parts.append(f"budget=${rp['runBudgetUsd']}")
        self._safe_update("#f-plan-name",  plan_name)
        self._safe_update("#f-task-count", task_cnt)
        self._safe_update("#f-batches",    batch_str)
        self._safe_update("#f-policy",     "  ·  ".join(pol_parts) or "—")

        # Agents — only those actually assigned to tasks in this plan
        used_agents = sorted({str(t.get("agent") or "") for t in list(payload.get("tasks") or []) if t.get("agent")})
        self._safe_update("#f-agent-names", "  ·  ".join(used_agents) or "—")

        # Tasks table
        task_models   = payload.get("taskModels")          or {}
        task_efforts  = payload.get("taskEfforts")         or {}
        task_profiles = payload.get("taskOutputProfiles")  or {}
        try:
            table = self.query_one("#tasks-table", DataTable)
            for t in list(payload.get("tasks") or []):
                tid     = str(t.get("id")    or "")
                agent   = str(t.get("agent") or "")
                model   = str(task_models.get(tid,   t.get("model")         or ""))
                effort  = str(task_efforts.get(tid,  t.get("effort")        or ""))
                skills  = "  ".join((t.get("resolvedSkills") or t.get("skills") or [])[:3])
                profile = str(task_profiles.get(tid, t.get("outputProfile") or ""))
                table.add_row(tid[:18], agent[:14], model[:16],
                              effort[:8], skills[:20], profile[:12])
        except Exception:
            pass

        # Issues section
        try:
            sec = self.query_one("#issues-section")
            if has_errors or has_warns:
                sec.display = True
                lines = (
                    [f"[#ff2244]✗[/]  {e}" for e in errors_all] +
                    [f"[#ffaa00]⚠[/]  {w}" for w in warns_all]
                )
                self._safe_update("#issues-list", "\n".join(lines))
            else:
                sec.display = False
        except Exception:
            pass

        # Cost section
        try:
            sec = self.query_one("#cost-section")
            if isinstance(cost_data, dict) and cost_data:
                sec.display = True
                total = cost_data.get("totalCostUsd") or cost_data.get("estimatedCostUsd")
                per_t = cost_data.get("perTaskCostUsd") or cost_data.get("perTask")
                other = {k: v for k, v in cost_data.items()
                         if k not in ("warnings", "totalCostUsd", "estimatedCostUsd",
                                      "perTaskCostUsd", "perTask")}
                self._safe_update("#f-cost-total", f"${total:.4f}" if isinstance(total, (int, float)) else "—")
                self._safe_update("#f-cost-per",   f"${per_t:.4f}" if isinstance(per_t,  (int, float)) else "—")
                self._safe_update("#f-cost-other",
                                  "  ·  ".join(f"{k}={v}" for k, v in list(other.items())[:4]) or "—")
            else:
                sec.display = False
        except Exception:
            pass

    def _safe_update(self, widget_id: str, text: str) -> None:
        try:
            self.query_one(widget_id, Static).update(text)
        except Exception:
            pass

    # ── worker ─────────────────────────────────────────────────────────────────

    def _run_validation(self) -> None:
        import io as _io

        handlers      = getattr(self.app, "_handlers",     {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        agents        = str(getattr(self.app, "_agents",     DEFAULT_AGENTS_PATH))
        claude_bin    = str(getattr(self.app, "_claude_bin", "claude"))

        def _fail(msg: str) -> None:
            self.app.call_from_thread(self._populate_results, {"errors": [msg]})

        if parse_args_fn is None:
            _fail("No parse_args_fn on app.")
            return

        cmd = ["validate", self._plan_path, "--agents", agents, "--provider-bin", claude_bin]
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            _fail(f"Arg parse error: {exc}")
            return

        handler = handlers.get("validate")
        if handler is None:
            _fail("No 'validate' handler registered.")
            return

        buf = _io.StringIO()
        old_stdout, sys.stdout = sys.stdout, buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {"errors": [f"Validation exception: {exc}"]}
        finally:
            sys.stdout = old_stdout

        if not isinstance(payload, dict):
            payload = {}

        self.app.call_from_thread(self._populate_results, payload)


ValidatePlanScreen = ValidatePlanDialog  # backward-compat alias
