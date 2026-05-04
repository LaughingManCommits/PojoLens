from __future__ import annotations

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
    from textual.widgets import Button, Footer, Header, Input, RichLog, Static
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


# ── RunPlanScreen ──────────────────────────────────────────────────────────────

class RunPlanScreen(Screen):  # type: ignore[type-arg,misc]
    """Execute a saved plan — runs in background, streams output log."""

    BINDINGS = [
        Binding("escape", "go_back", "Back", show=True),
    ]

    _run_status: reactive[str] = reactive("[ CONSTRUCT ] initializing run...")

    def __init__(self, plan_path: str, *, dry_run: bool = False) -> None:
        super().__init__()
        self._plan_path = plan_path
        self._dry_run = dry_run

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            prefix = "DRY RUN" if self._dry_run else "RUN"
            yield Static(f"[ {prefix} ]  Executing plan", id="run-title")
            yield Static(self._plan_path, id="run-plan")
            yield Static("[ SIGNAL ] initializing...", id="run-status")
        yield RichLog(id="run-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK TO PLANS", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  RUN"
        self.app.sub_title = "DRY RUN" if self._dry_run else "LIVE RUN"
        self.run_worker(self._execute_run, thread=True, name="plan-exec")

    def watch__run_status(self, status: str) -> None:
        self.query_one("#run-status", Static).update(f"[#00ff41][ SIGNAL ] {status}[/]")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#run-log", RichLog).write(text))

    def _set_status(self, s: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_run_status", s))

    def _execute_run(self) -> None:
        import io as _io
        handlers = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn available on app.[/]")
            return

        runtime_root = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))
        agents       = str(getattr(self.app, "_agents", DEFAULT_AGENTS_PATH))
        claude_bin   = str(getattr(self.app, "_claude_bin", "claude"))

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

        self._set_status("[ OPERATOR ] launching run...")
        self._log(f"[#00e5ff][ SIGNAL ] run started: {self._plan_path}[/]")
        handler = handlers.get("run")
        if handler is None:
            self._log("[#ff2244]No 'run' handler available.[/]")
            return

        buf = _io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]Run error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log("[dim #2a5a3a]═══ RUN RESULT ═══[/]")
            status = str(payload.get("status") or "")
            if status:
                sc = _status_color(status)
                self._log(f"[bold {sc}]status: {status}[/]")
            # Show status counts
            counts = dict(payload.get("statusCounts") or {})
            for k, v in sorted(counts.items()):
                sc = _status_color(k)
                self._log(f"  [{sc}]{k}: {v}[/{sc}]")
            run_dir = str(payload.get("runDir") or "")
            if run_dir:
                self._log(f"[dim]run dir: {run_dir}[/]")

        self._set_status("[ EXIT ] run complete")
        self._log("[bold #00ff41]═══ EXECUTION COMPLETE ═══[/]")


# ── ValidatePlanScreen / ValidateRunScreen ─────────────────────────────────────

class ValidatePlanScreen(Screen):  # type: ignore[type-arg,misc]
    """Enter a plan path and validate it."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static("[ VALIDATE ]  Enter plan file path", id="card-title")
            yield Input(
                placeholder="ai/orchestrator/tasks/my-plan.json",
                id="plan-path-input",
            )
            with Horizontal(id="btns"):
                yield Button("VALIDATE →", id="btn-validate", variant="primary")
                yield Button("CANCEL",     id="btn-cancel")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#plan-path-input", Input).focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-validate":
            path = self.query_one("#plan-path-input", Input).value.strip()
            if path:
                self.app.push_screen(ValidateRunScreen(path))  # type: ignore[attr-defined]
        elif event.button.id == "btn-cancel":
            self.action_go_back()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        path = self.query_one("#plan-path-input", Input).value.strip()
        if path:
            self.app.push_screen(ValidateRunScreen(path))  # type: ignore[attr-defined]

    def action_go_back(self) -> None:
        self.dismiss(None)


class ValidateRunScreen(Screen):  # type: ignore[type-arg,misc]
    """Run validation on a plan file, display grouped results."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def __init__(self, plan_path: str) -> None:
        super().__init__()
        self._plan_path = plan_path

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ VALIDATE ]  Scanning plan...", id="val-title")
            yield Static(self._plan_path, id="val-path")
        yield RichLog(id="val-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  VALIDATE"
        self.run_worker(self._run_validation, thread=True, name="validate")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#val-log", RichLog).write(text))

    def _run_validation(self) -> None:
        import io as _io

        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        agents     = str(getattr(self.app, "_agents",     DEFAULT_AGENTS_PATH))
        claude_bin = str(getattr(self.app, "_claude_bin", "claude"))

        self._log("[dim #2a5a3a][ SIGNAL ] validating workspace boundaries...[/]")

        cmd = ["validate", self._plan_path, "--agents", agents, "--provider-bin", claude_bin]
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            self._log(f"[#ff2244]Arg parse error: {exc}[/]")
            return

        handler = handlers.get("validate")
        if handler is None:
            self._log("[#ff2244]No 'validate' handler available.[/]")
            return

        buf = _io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]Validation error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log("[bold #00e5ff]═══ VALIDATION RESULT ═══[/]")
            valid  = payload.get("valid")
            errors = list(payload.get("errors") or [])
            warns  = list(payload.get("warnings") or [])

            if valid is True:
                self._log("[bold #00ff41]✓  PLAN IS VALID[/]")
            elif valid is False:
                self._log("[bold #ff2244]✗  PLAN HAS ERRORS[/]")
            else:
                self._log("[dim]validation status unknown[/]")

            if errors:
                self._log("")
                self._log("[bold #ff2244]── Critical Errors ──[/]")
                for e in errors:
                    self._log(f"  [#ff2244]✗ {e}[/]")
            if warns:
                self._log("")
                self._log("[bold #ffaa00]── Warnings ──[/]")
                for w in warns:
                    self._log(f"  [#ffaa00]⚠ {w}[/]")

            # Show cost estimate if present
            cost = payload.get("costEstimate") or payload.get("estimate")
            if cost:
                self._log("")
                self._log("[bold #00e5ff]── Cost Estimate ──[/]")
                if isinstance(cost, dict):
                    for k, v in cost.items():
                        self._log(f"  {k}: {v}")
                else:
                    self._log(f"  {cost}")

        self._log("")
        self._log("[dim #2a5a3a][ EXIT ] validation complete[/]")

        # Update title based on result
        valid = payload.get("valid") if payload else None
        if valid is True:
            self.app.call_from_thread(lambda: setattr(self.query_one("#val-title", Static), "update",
                                                  lambda _: None))
            self.app.call_from_thread(
                lambda: self.query_one("#val-title", Static).update(
                    "[bold #00ff41][ VALIDATE ]  ✓ VALID[/]"
                )
            )
        elif valid is False:
            self.app.call_from_thread(
                lambda: self.query_one("#val-title", Static).update(
                    "[bold #ff2244][ VALIDATE ]  ✗ ERRORS FOUND[/]"
                )
            )
