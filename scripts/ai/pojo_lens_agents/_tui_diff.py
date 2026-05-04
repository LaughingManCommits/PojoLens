from __future__ import annotations

import re
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

try:
    from rich.markup import escape as _rich_escape
except ImportError:  # pragma: no cover
    def _rich_escape(s: str) -> str:  # type: ignore[misc]
        return s.replace("[", "\\[")


# ── Theme-matched diff colors ──────────────────────────────────────────────────
# All values come from _MTX_VARS so the diff looks native to the operator theme.

_D_REMOVED_FG  = "#ff2244"  # $red
_D_REMOVED_BG  = "#1a0008"  # $bg_panel tinted dark red
_D_REMOVED_HI  = "#3a0015"  # word-level highlight bg (removed)
_D_ADDED_FG    = "#00ff41"  # $green
_D_ADDED_BG    = "#001a08"  # $bg_panel tinted dark green
_D_ADDED_HI    = "#003a15"  # word-level highlight bg (added)
_D_HEADER_FG   = "#00e5ff"  # $cyan  — file separator + hunk header
_D_PATH_REM    = "#ff2244"  # $red   — --- a/path
_D_PATH_ADD    = "#00ff41"  # $green — +++ b/path
_D_META_FG     = "#2a5a3a"  # $text_dim — index / mode / binary metadata
_D_CONTEXT_FG  = "#1a4a2a"  # $green_dim — context lines (unmodified)
_D_HUNK_CTX    = "#a0ffa0"  # $green_body — function hint after @@


# ── Diff parsing helpers ───────────────────────────────────────────────────────

def _parse_unified_diff(text: str) -> dict[str, list[str]]:
    """Split a unified diff string into {filename: [raw_lines]}."""
    files: dict[str, list[str]] = {}
    current_file: str | None = None
    current_lines: list[str] = []

    for line in text.splitlines():
        if line.startswith("diff --git ") or re.match(r"^diff -\S+ \S+ \S+", line):
            if current_file is not None:
                files[current_file] = current_lines
            parts = line.split()
            if line.startswith("diff --git ") and len(parts) >= 4:
                bside = parts[-1]
                current_file = bside[2:] if bside.startswith("b/") else bside
            else:
                current_file = parts[-1] if parts else line
            current_lines = [line]
        elif current_file is not None:
            current_lines.append(line)

    if current_file is not None and current_lines:
        files[current_file] = current_lines

    return files


def _file_change_stats(lines: list[str]) -> tuple[int, int]:
    """Return (insertions, deletions) for a file's diff lines."""
    ins  = sum(1 for l in lines if l.startswith("+") and not l.startswith("+++"))
    dels = sum(1 for l in lines if l.startswith("-") and not l.startswith("---"))
    return ins, dels


def _word_diff_line(prefix: str, this_text: str, other_text: str) -> str:
    """Render a +/- line with word-level highlights using theme colors."""
    import difflib

    toks_a = re.split(r"(\W+)", this_text)
    toks_b = re.split(r"(\W+)", other_text)
    sm = difflib.SequenceMatcher(None, toks_a, toks_b, autojunk=False)

    if prefix == "-":
        fg, bg, hi_bg = _D_REMOVED_FG, _D_REMOVED_BG, _D_REMOVED_HI
    else:
        fg, bg, hi_bg = _D_ADDED_FG,   _D_ADDED_BG,   _D_ADDED_HI

    parts: list[str] = [f"[bold {fg} on {bg}]{prefix}[/]"]
    for op, i1, i2, _j1, _j2 in sm.get_opcodes():
        chunk = _rich_escape("".join(toks_a[i1:i2]))
        if not chunk:
            continue
        if op == "equal":
            parts.append(f"[{fg} on {bg}]{chunk}[/]")
        else:
            parts.append(f"[bold {fg} on {hi_bg}]{chunk}[/]")

    return "".join(parts)


def _render_diff_lines(lines: list[str]) -> list[str]:
    """Convert raw unified diff lines to themed Rich markup strings.

    Color palette matches _MTX_VARS so the diff is native to the operator theme.
    Adjacent -/+ blocks get inline word-level diffs.
    """
    result: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]

        # ── file separator / header ───────────────────────────────────────────
        if line.startswith("diff --git ") or re.match(r"^diff -\S+ \S+ \S+", line):
            parts = line.split()
            fname = ""
            if line.startswith("diff --git ") and len(parts) >= 4:
                bside = parts[-1]
                fname = bside[2:] if bside.startswith("b/") else bside
            result.append(f"[{_D_CONTEXT_FG}]{'─' * 60}[/]")
            result.append(f"[bold {_D_HEADER_FG}]▶ {_rich_escape(fname or line)}[/]")

        # ── index / mode / binary metadata ───────────────────────────────────
        elif re.match(r"^(index |new file|deleted file|old mode|new mode|Binary)", line):
            result.append(f"[{_D_META_FG}]{_rich_escape(line)}[/]")

        # ── --- a/path ────────────────────────────────────────────────────────
        elif line.startswith("--- "):
            content = line[6:] if line.startswith("--- a/") else line[4:]
            result.append(f"[{_D_PATH_REM}]--- {_rich_escape(content)}[/]")

        # ── +++ b/path ────────────────────────────────────────────────────────
        elif line.startswith("+++ "):
            content = line[6:] if line.startswith("+++ b/") else line[4:]
            result.append(f"[{_D_PATH_ADD}]+++ {_rich_escape(content)}[/]")

        # ── @@ hunk header ────────────────────────────────────────────────────
        elif line.startswith("@@ "):
            m = re.match(r"(@@ -\d+(?:,\d+)? \+\d+(?:,\d+)? @@)(.*)", line)
            if m:
                hunk  = _rich_escape(m.group(1))
                ctx   = m.group(2).strip()
                extra = f" [{_D_HUNK_CTX}]{_rich_escape(ctx)}[/]" if ctx else ""
                result.append(f"[bold {_D_HEADER_FG}]{hunk}[/]{extra}")
            else:
                result.append(f"[bold {_D_HEADER_FG}]{_rich_escape(line)}[/]")

        # ── removed block (possibly followed by added block) ──────────────────
        elif line.startswith("-") and not line.startswith("---"):
            del_block: list[str] = []
            while i < len(lines) and lines[i].startswith("-") and not lines[i].startswith("---"):
                del_block.append(lines[i])
                i += 1
            add_block: list[str] = []
            while i < len(lines) and lines[i].startswith("+") and not lines[i].startswith("+++"):
                add_block.append(lines[i])
                i += 1

            pairs = min(len(del_block), len(add_block))
            for j in range(pairs):
                result.append(_word_diff_line("-", del_block[j][1:], add_block[j][1:]))
                result.append(_word_diff_line("+", add_block[j][1:], del_block[j][1:]))
            for j in range(pairs, len(del_block)):
                content = _rich_escape(del_block[j][1:])
                result.append(
                    f"[bold {_D_REMOVED_FG} on {_D_REMOVED_BG}]-[/]"
                    f"[{_D_REMOVED_FG} on {_D_REMOVED_BG}]{content}[/]"
                )
            for j in range(pairs, len(add_block)):
                content = _rich_escape(add_block[j][1:])
                result.append(
                    f"[bold {_D_ADDED_FG} on {_D_ADDED_BG}]+[/]"
                    f"[{_D_ADDED_FG} on {_D_ADDED_BG}]{content}[/]"
                )
            continue

        # ── orphan added block ────────────────────────────────────────────────
        elif line.startswith("+") and not line.startswith("+++"):
            content = _rich_escape(line[1:])
            result.append(
                f"[bold {_D_ADDED_FG} on {_D_ADDED_BG}]+[/]"
                f"[{_D_ADDED_FG} on {_D_ADDED_BG}]{content}[/]"
            )

        # ── context line ──────────────────────────────────────────────────────
        else:
            content = line[1:] if line.startswith(" ") else line
            result.append(f"[{_D_CONTEXT_FG}] {_rich_escape(content)}[/]")

        i += 1

    return result


# ── DiffReviewScreen ──────────────────────────────────────────────────────────

class DiffReviewScreen(Screen):  # type: ignore[type-arg,misc]
    """Review workspace diffs and promote changes for a retained run."""

    BINDINGS = [
        Binding("escape", "go_back",        "Back",         show=True),
        Binding("a",      "show_all",        "All Files",    show=True),
        Binding("p",      "promote_run",     "Promote",      show=True),
        Binding("e",      "export_patch",    "Export Patch", show=True),
        Binding("c",      "coord_validate",  "Coord. Val.",  show=True),
    ]

    _diff_status: reactive[str] = reactive("loading diff...")

    def __init__(self, run_ref: str) -> None:
        super().__init__()
        self._run_ref      = run_ref
        self._file_entries: list[dict[str, Any]] = []
        self._file_diffs:   dict[str, list[str]] = {}

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ DIFF REVIEW ]  Workspace changes", id="diff-title")
            yield Static(self._run_ref, id="diff-ref")
            yield Static("[ SIGNAL ] loading...", id="diff-status")
        yield Static("", id="diff-stats")
        with Horizontal(id="split-view"):
            yield DataTable(id="file-list")
            yield RichLog(id="diff-log", markup=True, auto_scroll=False,
                          wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("PROMOTE",       id="btn-promote", variant="primary")
            yield Button("ALL [A]",       id="btn-all")
            yield Button("EXPORT PATCH",  id="btn-export")
            yield Button("COORD. VALID.", id="btn-coord")
            yield Button("BACK",          id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  DIFF REVIEW"
        table = self.query_one("#file-list", DataTable)
        table.cursor_type = "row"
        table.zebra_stripes = True
        table.add_column("File", key="file", width=22)
        table.add_column("+ins",  key="ins",  width=6)
        table.add_column("-del",  key="dels", width=6)
        self.run_worker(self._load_diff, thread=True, name="load-diff")

    def watch__diff_status(self, s: str) -> None:
        try:
            self.query_one("#diff-status", Static).update(f"[#00ff41][ SIGNAL ] {s}[/]")
        except Exception:
            pass

    def _set_status(self, s: str) -> None:
        self.app.call_from_thread(lambda: setattr(self, "_diff_status", s))

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#diff-log", RichLog).write(text))

    # ── file list selection → show that file's diff ────────────────────────────

    def on_data_table_row_selected(self, event: DataTable.RowSelected) -> None:
        fname = str(event.row_key.value) if event.row_key else None
        if fname and fname in self._file_diffs:
            self._render_file_diff(fname)

    def _render_file_diff(self, fname: str) -> None:
        lines = self._file_diffs.get(fname, [])
        log = self.query_one("#diff-log", RichLog)
        log.clear()
        if not lines:
            log.write(f"[dim]No diff data for: {_rich_escape(fname)}[/]")
            return
        for rendered_line in _render_diff_lines(lines):
            log.write(rendered_line)

    def action_show_all(self) -> None:
        """Render all files' diffs concatenated."""
        log = self.query_one("#diff-log", RichLog)
        log.clear()
        if not self._file_diffs:
            log.write("[dim]No diff data loaded yet.[/]")
            return
        for fname, raw_lines in self._file_diffs.items():
            for rendered_line in _render_diff_lines(raw_lines):
                log.write(rendered_line)
            log.write("")

    # ── buttons ────────────────────────────────────────────────────────────────

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-promote":
            self.action_promote_run()
        elif event.button.id == "btn-all":
            self.action_show_all()
        elif event.button.id == "btn-export":
            self.action_export_patch()
        elif event.button.id == "btn-coord":
            self.action_coord_validate()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    # ── diff loading ───────────────────────────────────────────────────────────

    def _load_diff(self) -> None:
        import io as _io
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)
        if parse_args_fn is None:
            self._log("[#ff2244]No parse_args_fn on app.[/]")
            return

        # ── review summary ─────────────────────────────────────────────────
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
                    for ln in captured.splitlines():
                        self._log(ln)
                elif review_payload:
                    self._log("[bold #00e5ff]═══ REVIEW SUMMARY ═══[/]")
                    for k, v in sorted(review_payload.items()):
                        if k != "_consoleText" and v is not None:
                            self._log(f"  [#00e5ff]{k}:[/] {str(v)[:80]}")
            except Exception as exc:
                self._log(f"[#ffaa00]review load: {exc}[/]")

        # ── diff output ────────────────────────────────────────────────────
        self._set_status("loading diff output...")
        if "diff-run" not in handlers:
            self._set_status("[P] Promote  [E] Export  [C] Coord. Val.  [Esc] Back")
            return

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

            # Prefer captured stdout (raw unified diff), fall back to payload field
            raw_diff = captured or str(
                diff_payload.get("diff") or diff_payload.get("unifiedDiff") or ""
            )

            # Parse into per-file sections and build file table
            file_diffs: dict[str, list[str]] = {}
            if raw_diff:
                file_diffs = _parse_unified_diff(raw_diff)

            # Also honour per-file diff objects in the payload
            for fe in (diff_payload.get("changedFiles") or diff_payload.get("files") or []):
                fname    = str(fe.get("path") or fe.get("file") or "")
                fdiff    = str(fe.get("diff") or fe.get("unifiedDiff") or "")
                if fname and fdiff and fname not in file_diffs:
                    file_diffs[fname] = fdiff.splitlines()

            self._file_diffs = file_diffs

            # Build aggregate stats
            total_ins = total_dels = 0
            rows: list[tuple[str, int, int]] = []
            for fname, flines in file_diffs.items():
                ins, dels = _file_change_stats(flines)
                total_ins  += ins
                total_dels += dels
                rows.append((fname, ins, dels))

            self._file_entries = [{"path": r[0]} for r in rows]

            def _fill(
                rows: list[tuple[str, int, int]] = rows,
                total_ins: int = total_ins,
                total_dels: int = total_dels,
            ) -> None:
                table = self.query_one("#file-list", DataTable)
                table.clear()
                for fname, ins, dels in rows:
                    table.add_row(
                        fname[-22:],
                        f"[{_D_ADDED_FG}]+{ins}[/]",
                        f"[{_D_REMOVED_FG}]-{dels}[/]",
                        key=fname,
                    )
                # Stats bar
                try:
                    self.query_one("#diff-stats", Static).update(
                        f"  [{_D_ADDED_FG}]+{total_ins} insertions[/]"
                        f"  [{_D_REMOVED_FG}]-{total_dels} deletions[/]"
                        f"  [{_D_META_FG}]across {len(rows)} file(s)"
                        f"  · click file or [A] all[/]"
                    )
                except Exception:
                    pass
                # Auto-show first file
                if rows:
                    self._render_file_diff(rows[0][0])

            self.app.call_from_thread(_fill)

            # If no structured diff, fall back to raw render in the log panel
            if not file_diffs and raw_diff:
                self._log("[bold #00e5ff]═══ WORKSPACE DIFF ═══[/]")
                for rendered_line in _render_diff_lines(raw_diff.splitlines()):
                    self._log(rendered_line)

        except Exception as exc:
            self._log(f"[#ff2244]diff-run: {exc}[/]")

        self._set_status("[A] All  [P] Promote  [E] Export  [C] Coord. Val.  [Esc] Back")

    # ── promote / export / coord-validate ─────────────────────────────────────

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
                for ln in captured.splitlines():
                    self._log(ln)
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
                for ln in captured.splitlines():
                    self._log(ln)
            valid = (payload or {}).get("valid") or (payload or {}).get("passed")
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
