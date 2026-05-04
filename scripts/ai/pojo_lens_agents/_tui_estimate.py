from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.screen import Screen
    from textual.widgets import Button, Footer, Header, Input, OptionList, RichLog, Static
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


# ── EstimateScreen / EstimateResultScreen ──────────────────────────────────────

class EstimateScreen(Screen):  # type: ignore[type-arg,misc]
    """Entry point for --estimate / --dry-run: enter plan path + choose mode."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    _MODES = [
        ("estimate", "ESTIMATE  — token/cost ranges without a retained run  (fast)"),
        ("dry-run",  "DRY RUN   — tighter prompt-assembly estimate, no retained run"),
    ]

    def __init__(self, plan_path: str = "") -> None:
        super().__init__()
        self._plan_path = plan_path

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static("[ ESTIMATE / DRY RUN ]  Cost and token projection", id="card-title")
            yield Static("Plan file path:", classes="field-label")
            yield Input(value=self._plan_path, placeholder="ai/orchestrator/tasks/my-plan.json", id="plan-input")
            yield Static("Mode:", classes="field-label")
            yield OptionList(*[label for _, label in self._MODES], id="mode-list")
            with Horizontal(id="btns"):
                yield Button("RUN →", id="btn-run", variant="primary")
                yield Button("CANCEL", id="btn-cancel")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  ESTIMATE"
        self.query_one("#mode-list", OptionList).highlighted = 0
        if not self._plan_path:
            self.query_one("#plan-input", Input).focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-run":
            self._submit()
        elif event.button.id == "btn-cancel":
            self.action_go_back()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        self._submit()

    def _submit(self) -> None:
        path = self.query_one("#plan-input", Input).value.strip()
        idx  = int(self.query_one("#mode-list", OptionList).highlighted or 0)
        mode, _ = self._MODES[idx]
        if path:
            self.dismiss(None)
            self.app.push_screen(EstimateResultScreen(path, mode=mode))  # type: ignore[attr-defined]
        else:
            self.app.notify("Enter a plan file path first.", title="Required")  # type: ignore[attr-defined]

    def action_go_back(self) -> None:
        self.dismiss(None)


class EstimateResultScreen(Screen):  # type: ignore[type-arg,misc]
    """Display output from --estimate or --dry-run."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def __init__(self, plan_path: str, *, mode: str = "estimate") -> None:
        super().__init__()
        self._plan_path = plan_path
        self._mode      = mode

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static(f"[ {self._mode.upper()} ]", id="est-title")
            yield Static(self._plan_path, id="est-plan")
        yield RichLog(id="est-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = f"POJOLENS  //  {self._mode.upper()}"
        self.run_worker(self._run_estimate, thread=True, name="estimate")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#est-log", RichLog).write(text))

    def _run_estimate(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return
        runtime_root = str(getattr(self.app, "_runtime_root", DEFAULT_RUNTIME_ROOT))
        agents       = str(getattr(self.app, "_agents",       DEFAULT_AGENTS_PATH))
        claude_bin   = str(getattr(self.app, "_claude_bin",   "claude"))

        cmd = ["run", self._plan_path,
               "--runtime-root", runtime_root, "--agents", agents,
               "--provider-bin", claude_bin, "--json"]
        if self._mode == "estimate":
            cmd.append("--estimate")
        elif self._mode == "dry-run":
            cmd.append("--dry-run")

        self._log(f"[#00e5ff][ SIGNAL ] {self._mode} starting...[/]")
        try:
            args = parse_args_fn(cmd)
        except Exception as exc:
            self._log(f"[#ff2244]Arg error: {exc}[/]")
            return

        buf     = _io.StringIO()
        old_out = sys.stdout
        sys.stdout = buf  # type: ignore[assignment]
        try:
            payload: dict[str, Any] = handlers.get("run", lambda a: {})(args)
        except Exception as exc:
            payload = {}
            self._log(f"[#ff2244]Error: {exc}[/]")
        finally:
            sys.stdout = old_out
            captured = buf.getvalue().strip()

        if captured:
            for line in captured.splitlines():
                self._log(line)

        if payload:
            self._log("")
            self._log(f"[bold #00e5ff]═══ {self._mode.upper()} RESULT ═══[/]")

            estimate = payload.get("estimate") or payload.get("costEstimate") or {}
            if isinstance(estimate, dict) and estimate:
                self._log("[bold #00e5ff]── Cost / Token Estimate ──[/]")
                for k, v in sorted(estimate.items()):
                    self._log(f"  [#00e5ff]{k}:[/] {v}")

            tasks = list(payload.get("tasks") or payload.get("taskEstimates") or [])
            if tasks:
                self._log("")
                self._log("[bold #00e5ff]── Per-Task Estimates ──[/]")
                for t in tasks:
                    tid  = str(t.get("taskId") or t.get("id") or "?")
                    mdl  = str(t.get("model") or t.get("modelProfile") or "-")
                    cost = t.get("estimatedCostUsd") or t.get("costUsd") or t.get("cost")
                    toks = t.get("estimatedTokens") or t.get("tokens")
                    c_s  = f"[#ffaa00]${cost:.5f}[/]" if isinstance(cost, (int, float)) else "-"
                    t_s  = f"{toks:,}" if isinstance(toks, int) else "-"
                    self._log(f"  [#00e5ff]▸ {tid}[/] [{mdl}]  cost {c_s}  tokens {t_s}")

            total = payload.get("totalEstimatedCostUsd") or payload.get("totalCostUsd")
            if total is not None:
                self._log("")
                self._log(f"[bold #ffaa00]Total estimated: ${total:.5f}[/]")

        self._log("")
        self._log(f"[dim #2a5a3a][ EXIT ] {self._mode} complete[/]")
