from __future__ import annotations

import sys
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.reactive import reactive
    from textual.screen import Screen
    from textual.widgets import Button, DataTable, Footer, Header, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]


# ── DiffReviewScreen ──────────────────────────────────────────────────────────

class DiffReviewScreen(Screen):  # type: ignore[type-arg,misc]
    """Review workspace diffs and promote changes for a retained run."""

    BINDINGS = [
        Binding("escape", "go_back",        "Back",          show=True),
        Binding("p",      "promote_run",    "Promote",       show=True),
        Binding("e",      "export_patch",   "Export Patch",  show=True),
        Binding("c",      "coord_validate", "Coord. Val.",   show=True),
    ]

    _diff_status: reactive[str] = reactive("loading diff...")

    def __init__(self, run_ref: str) -> None:
        super().__init__()
        self._run_ref = run_ref
        self._file_entries: list[dict[str, Any]] = []

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ DIFF REVIEW ]  Workspace changes", id="diff-title")
            yield Static(self._run_ref, id="diff-ref")
            yield Static("[ SIGNAL ] loading...", id="diff-status")
        with Horizontal(id="split-view"):
            yield DataTable(id="file-list")
            yield RichLog(id="diff-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("PROMOTE",        id="btn-promote",  variant="primary")
            yield Button("EXPORT PATCH",   id="btn-export")
            yield Button("COORD. VALID.",  id="btn-coord")
            yield Button("BACK",           id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  DIFF REVIEW"
        table = self.query_one("#file-list", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("File", key="file", width=28)
        table.add_column("Task", key="task", width=8)
        self.run_worker(self._load_diff, thread=True, name="load-diff")

    def watch__diff_status(self, s: str) -> None:
        self.query_one("#diff-status", Static).update(f"[#00ff41][ SIGNAL ] {s}[/]")

    def _set_status(self, s: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_diff_status", s))

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#diff-log", RichLog).write(text))

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-promote":
            self.action_promote_run()
        elif event.button.id == "btn-export":
            self.action_export_patch()
        elif event.button.id == "btn-coord":
            self.action_coord_validate()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _load_diff(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        # Review summary
        self._set_status("loading review summary...")
        if "review" in handlers:
            try:
                args    = parse_args_fn(["review", self._run_ref, "--json"])
                buf     = _io.StringIO()
                old_out = sys.stdout
                sys.stdout = buf  # type: ignore[assignment]
                try:
                    review_payload: dict[str, Any] = handlers["review"](args)
                except Exception as exc:
                    review_payload = {}
                    self._log(f"[#ff2244]review error: {exc}[/]")
                finally:
                    sys.stdout = old_out
                    captured = buf.getvalue().strip()
                if captured:
                    self._log("[bold #00e5ff]═══ REVIEW SUMMARY ═══[/]")
                    for line in captured.splitlines():
                        self._log(line)
                elif review_payload:
                    self._log("[bold #00e5ff]═══ REVIEW SUMMARY ═══[/]")
                    for k, v in sorted(review_payload.items()):
                        if k != "_consoleText" and v is not None:
                            self._log(f"  [#00e5ff]{k}:[/] {str(v)[:80]}")
            except Exception as exc:
                self._log(f"[#ffaa00]review load: {exc}[/]")

        # Diff output
        self._set_status("loading diff output...")
        if "diff-run" in handlers:
            try:
                args    = parse_args_fn(["diff-run", self._run_ref, "--json"])
                buf     = _io.StringIO()
                old_out = sys.stdout
                sys.stdout = buf  # type: ignore[assignment]
                try:
                    diff_payload: dict[str, Any] = handlers["diff-run"](args)
                except Exception as exc:
                    diff_payload = {}
                    self._log(f"[#ff2244]diff-run error: {exc}[/]")
                finally:
                    sys.stdout = old_out
                    captured = buf.getvalue().strip()

                if captured:
                    self._log("")
                    self._log("[bold #00e5ff]═══ WORKSPACE DIFF ═══[/]")
                    for line in captured.splitlines():
                        if line.startswith("+") and not line.startswith("+++"):
                            self._log(f"[#00ff41]{line}[/]")
                        elif line.startswith("-") and not line.startswith("---"):
                            self._log(f"[#ff2244]{line}[/]")
                        elif line.startswith("@@"):
                            self._log(f"[#00e5ff]{line}[/]")
                        else:
                            self._log(line)

                files = list(diff_payload.get("changedFiles") or diff_payload.get("files") or [])
                if files:
                    self._file_entries = files

                    def _fill(fs: list[dict[str, Any]]) -> None:
                        table = self.query_one("#file-list", DataTable)
                        for f in fs[:100]:
                            fname = str(f.get("path") or f.get("file") or "-")
                            task  = str(f.get("taskId") or "-")
                            table.add_row(fname[-28:], task[:8], key=fname)

                    self.app.call_from_thread(lambda: _fill(files))

            except Exception as exc:
                self._log(f"[#ff2244]diff-run: {exc}[/]")

        self._set_status("[P] Promote  [E] Export Patch  [C] Coord. Validate  [Esc] Back")

    def action_promote_run(self) -> None:
        from pojo_lens_agents._tui_ledger import ResumeRetryScreen
        self.app.push_screen(ResumeRetryScreen(self._run_ref, mode="promote"))  # type: ignore[attr-defined]

    def action_export_patch(self) -> None:
        self.run_worker(self._do_export_patch, thread=True, name="export-patch")

    def action_coord_validate(self) -> None:
        self.run_worker(self._do_coord_validate, thread=True, name="coord-validate")

    def _do_export_patch(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None or "export-patch" not in handlers:
            self._log("[#ff2244]export-patch handler not available.[/]")
            return
        try:
            args    = parse_args_fn(["export-patch", self._run_ref])
            buf     = _io.StringIO()
            old_out = sys.stdout
            sys.stdout = buf  # type: ignore[assignment]
            try:
                payload: dict[str, Any] = handlers["export-patch"](args)
            except Exception as exc:
                payload = {}
                self._log(f"[#ff2244]export-patch error: {exc}[/]")
            finally:
                sys.stdout = old_out
                captured = buf.getvalue().strip()
            if captured:
                for line in captured.splitlines():
                    self._log(line)
            patch_path = str(payload.get("patchPath") or payload.get("path") or "")
            if patch_path:
                self._log(f"[bold #00ff41]patch exported: {patch_path}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]export-patch: {exc}[/]")

    def _do_coord_validate(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None or "validate-run" not in handlers:
            self._log("[#ff2244]validate-run handler not available.[/]")
            return
        self._set_status("[ SIGNAL ] running coordinator validation...")
        try:
            args    = parse_args_fn(["validate-run", self._run_ref, "--json"])
            buf     = _io.StringIO()
            old_out = sys.stdout
            sys.stdout = buf  # type: ignore[assignment]
            try:
                payload = handlers["validate-run"](args)
            except Exception as exc:
                payload = {}
                self._log(f"[#ff2244]validate-run error: {exc}[/]")
            finally:
                sys.stdout = old_out
                captured = buf.getvalue().strip()
            if captured:
                self._log("[bold #00e5ff]═══ COORDINATOR VALIDATION ═══[/]")
                for line in captured.splitlines():
                    self._log(line)
            valid = payload.get("valid") or payload.get("passed")
            if valid is True:
                self._set_status("[ EXIT ] coord. validation PASSED")
                self._log("[bold #00ff41]✓ COORDINATOR VALIDATION PASSED[/]")
            elif valid is False:
                self._set_status("[ EXIT ] coord. validation FAILED")
                self._log("[bold #ff2244]✗ COORDINATOR VALIDATION FAILED[/]")
            else:
                self._set_status("[ EXIT ] coord. validation complete")
        except Exception as exc:
            self._log(f"[#ff2244]coord validate: {exc}[/]")
