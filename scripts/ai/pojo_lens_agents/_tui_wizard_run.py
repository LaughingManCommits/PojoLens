from __future__ import annotations

import sys
from typing import Any, Callable

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
    Text = None  # type: ignore[assignment]

from pojo_lens_agents._tui_helpers import _status_color


# ── PlanRunScreen ──────────────────────────────────────────────────────────────

class PlanRunScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 5: generate plan + run — shows live log of wizard output."""

    BINDINGS = [
        Binding("escape", "dismiss_screen", "Back / Stop", show=True),
    ]

    CSS = """
    PlanRunScreen {
        background: $bg;
    }
    #header-strip {
        height: auto;
        background: $bg_panel;
        border-bottom: heavy $green 25%;
        padding: 1 2;
    }
    #run-title {
        color: $cyan;
        text-style: bold;
    }
    #run-params {
        color: $text_dim;
        margin-top: 1;
    }
    #status-line {
        color: $green;
        margin-top: 1;
    }
    #log {
        height: 1fr;
        background: $bg;
        padding: 0 1;
    }
    #bottom-bar {
        height: 3;
        background: $bg_input;
        border-top: solid $green 25%;
        align: right middle;
        padding: 0 2;
    }
    """

    _status: reactive[str] = reactive("initializing...")

    def __init__(
        self,
        goal: str,
        effort: str,
        workspace_mode: str,
        hitl: str,
        max_parallel: int,
        budget: float | None,
        *,
        budget_behavior: str = "warn",
        follow_up: str = "ignore",
        handlers: dict[str, Any],
        parse_args_fn: Callable[..., Any],
        runtime_root: str,
        agents: str,
        claude_bin: str,
    ) -> None:
        super().__init__()
        self._goal            = goal
        self._effort          = effort
        self._workspace_mode  = workspace_mode
        self._hitl            = hitl
        self._max_parallel    = max_parallel
        self._budget          = budget
        self._budget_behavior = budget_behavior
        self._follow_up       = follow_up
        self._handlers        = handlers
        self._parse_args_fn   = parse_args_fn
        self._runtime_root    = runtime_root
        self._agents          = agents
        self._claude_bin      = claude_bin
        self._done            = False

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="header-strip"):
            yield Static(
                "[ STEP 5 / 5 ]  GENERATING  +  EXECUTING PLAN",
                id="run-title",
            )
            yield Static(
                f"goal: {self._goal[:80]}  "
                f"effort: {self._effort}  "
                f"workspace: {self._workspace_mode}  "
                f"hitl: {self._hitl}  "
                f"parallel: {self._max_parallel}",
                id="run-params",
            )
            yield Static("[bold #00ff41][ CONSTRUCT ] assembling prompt matrix...[/]", id="status-line")
        yield RichLog(id="log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="bottom-bar"):
            yield Button("BACK TO HOME", id="btn-home")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  PLAN RUN"
        self.app.sub_title = "CONSTRUCTING..."
        self.run_worker(self._run_wizard, thread=True, name="wizard-run")

    def watch__status(self, status: str) -> None:
        self.query_one("#status-line", Static).update(
            f"[bold #00ff41][ SIGNAL ] {status}[/]"
        )

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-home":
            self.action_dismiss_screen()

    def action_dismiss_screen(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(
            lambda: self.query_one("#log", RichLog).write(text)
        )

    def _set_status(self, status: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_status", status))

    def _run_wizard(self) -> None:
        import io as _io

        self._set_status("building wizard args...")
        self._log("[dim #2a5a3a]═══ WIZARD START ═══[/]")

        # Build arg list for parse_args_fn
        arg_list = ["wizard", "--json"]
        if self._goal:
            arg_list += ["--goal", self._goal]
        if self._effort:
            arg_list += ["--planner-effort", self._effort]
        if self._workspace_mode:
            arg_list += ["--workspace-mode", self._workspace_mode]
        if self._hitl:
            arg_list += ["--hitl", self._hitl]
        arg_list += ["--max-parallel", str(self._max_parallel)]
        if self._budget is not None:
            arg_list += ["--run-budget-usd", str(self._budget)]
        if self._budget_behavior and self._budget_behavior != "warn":
            arg_list += ["--budget-behavior", self._budget_behavior]
        if self._follow_up and self._follow_up != "ignore":
            arg_list += ["--follow-up-behavior", self._follow_up]
        if self._agents:
            arg_list += ["--agents", self._agents]
        if self._runtime_root:
            arg_list += ["--runtime-root", self._runtime_root]
        if self._claude_bin:
            arg_list += ["--provider-bin", self._claude_bin]

        self._set_status("parsing arguments...")
        try:
            args = self._parse_args_fn(arg_list)
        except SystemExit as exc:
            self._log(f"[#ff2244]>> arg parse failed: {exc}[/]")
            self._set_status("failed — arg parse error")
            return
        except Exception as exc:
            self._log(f"[#ff2244]>> error building args: {exc}[/]")
            self._set_status("failed — args error")
            return

        self._set_status("[ CONSTRUCT ] invoking wizard agent...")
        self._log("[#00e5ff][ SIGNAL ] wizard agent dispatched[/]")

        handler = self._handlers.get("wizard")
        if handler is None:
            self._log("[#ff2244]>> wizard handler not found in handlers dict[/]")
            self._set_status("failed — no wizard handler")
            return

        # Capture stdout from the wizard worker
        buf = _io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload = handler(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]>> wizard error: {exc}[/]")
        finally:
            sys.stdout = old_stdout
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        # Show JSON summary
        if payload:
            self._log("")
            self._log("[dim #2a5a3a]═══ RESULT PAYLOAD ═══[/]")
            status = str(payload.get("status") or "")
            if status:
                sc = _status_color(status)
                self._log(f"[bold {sc}]status: {status}[/]")

            steps = list(payload.get("steps") or [])
            for step in steps:
                sname   = str(step.get("name", ""))
                sstatus = str(step.get("status", ""))
                sc      = _status_color(sstatus)
                self._log(
                    f"  [#00e5ff]▸[/] {sname}: [{sc}]{sstatus}[/{sc}]"
                )

            plan_path = str(payload.get("planPath") or payload.get("savedPlanPath") or "")
            if plan_path:
                self._log(f"[#00ff41]plan: {plan_path}[/]")

            saved = str(payload.get("savedPlanPath") or "")
            if saved:
                self._log(f"[#00e5ff]saved to: {saved}[/]")

        self._set_status("[ EXIT ] run complete")
        self._log("")
        self._log("[bold #00ff41]═══ WIZARD COMPLETE — press BACK TO HOME ═══[/]")
        self._done = True
        self.app.call_from_thread(
            lambda: setattr(self.app, "sub_title", "COMPLETE")  # type: ignore[attr-defined]
        )
