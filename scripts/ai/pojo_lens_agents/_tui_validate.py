from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.screen import ModalScreen, Screen
    from textual.widgets import Button, Footer, Header, Input, RichLog, Static
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


ValidatePlanScreen = ValidatePlanDialog  # backward-compat alias
