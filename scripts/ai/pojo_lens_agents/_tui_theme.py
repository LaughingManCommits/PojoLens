from __future__ import annotations

_MTX_VARS: dict[str, str] = {
    "bg":         "#050508",
    "bg_panel":   "#07070f",
    "bg_input":   "#04040c",
    "green":      "#00ff41",
    "green_body": "#a0ffa0",
    "green_dim":  "#1a4a2a",
    "cyan":       "#00e5ff",
    "amber":      "#ffaa00",
    "red":        "#ff2244",
    "text_dim":   "#2a5a3a",
    "border_dim": "#1a3a1a",
    "purple":     "#bf5fff",
}

_BANNER_ART = (
    " ██████╗  ██╗      █████╗  ███╗   ██╗\n"
    " ██╔══██╗ ██║     ██╔══██╗ ████╗  ██║\n"
    " ██████╔╝ ██║     ███████║ ██╔██╗ ██║\n"
    " ██╔═══╝  ██║     ██╔══██║ ██║╚██╗██║\n"
    " ██║      ███████╗██║  ██║ ██║ ╚████║\n"
    " ╚═╝      ╚══════╝╚═╝  ╚═╝ ╚═╝  ╚═══╝"
)

_EFFORT_OPTIONS = [
    ("medium", "MEDIUM  —  Sonnet     recommended · balanced"),
    ("low",    "LOW     —  Haiku      faster · cheaper"),
    ("high",   "HIGH    —  Opus       thorough · expensive  ⚠"),
]

_WORKSPACE_OPTIONS = [
    ("copy",     "COPY      —  isolated sparse filesystem copy  (safest)"),
    ("worktree", "WORKTREE  —  detached git worktree at HEAD"),
    ("repo",     "REPO      —  live repo root  ⚠ HIGH RISK"),
]

_HITL_OPTIONS = [
    ("batch",      "BATCH       —  gate before every batch with pending tasks"),
    ("on-failure", "ON-FAILURE  —  gate only after a failed batch"),
    ("always",     "ALWAYS      —  gate before every task batch (strict)"),
    ("none",       "NONE        —  disable all gates"),
]

_BUDGET_BEHAVIOR_OPTIONS = [
    ("warn", "WARN  —  continue but surface warnings when budget exceeded"),
    ("stop", "STOP  —  block unscheduled batches once budget exceeded  ✓ safe"),
]

_FOLLOW_UP_OPTIONS = [
    ("ignore", "IGNORE  —  discard followUpTasks from workers  (default)"),
    ("inject", "INJECT  —  queue followUpTasks between batches"),
]

_CLARIF_QUESTIONS = [
    "What specific files, modules, or components should agents focus on?",
    "Are there constraints, patterns, or coding standards that must be preserved?",
    "What is the success criterion — how will you know the task is done correctly?",
]

_WORKSPACE_WARN = {
    "repo": "⚠  REPO mode uses the live repo root — no isolation. Proceed only for safe read-only plans.",
    "worktree": "Worktree mode requires a clean git repo. Ensure no uncommitted changes.",
}


# ── Shared CSS ────────────────────────────────────────────────────────────────
# NOTE: canonical source is tui_operator.tcss — this string is kept for
# external callers that import _SHARED_CSS directly.

_SHARED_CSS = """
Screen {
    background: $bg;
    color: $green_body;
}
Header {
    background: $bg_panel;
    color: $green;
    border-bottom: heavy $green 30%;
}
Footer {
    background: $bg_input;
    color: $text_dim;
    border-top: solid $green 20%;
}
.panel {
    background: $bg_panel;
    border: heavy $green 25%;
    padding: 1 2;
    margin: 0 0 1 0;
}
.panel-title {
    color: $cyan;
    text-style: bold;
    margin-bottom: 1;
}
.section-rule {
    color: $green_dim;
}
.dim {
    color: $text_dim;
}
.warn {
    color: $amber;
}
.error {
    color: $red;
}
.ok {
    color: $green;
}
.accent {
    color: $cyan;
}
Button {
    margin: 0 1;
    background: $bg_panel;
    color: $green;
    border: solid $green 50%;
    min-width: 12;
}
Button:hover {
    background: $green_dim;
    color: $green;
}
Button.-primary {
    background: $green_dim;
    color: $green;
    border: heavy $green 80%;
}
Button.-primary:hover {
    background: #1a6a3a;
}
Button.-warning {
    color: $amber;
    border: solid $amber 60%;
}
Button.-warning:hover {
    background: #2a1a00;
}
Button.-error {
    color: $red;
    border: solid $red 60%;
}
Button.-error:hover {
    background: #2a0010;
}
Input {
    background: $bg_input;
    color: $green;
    border: solid $green 40%;
}
Input:focus {
    border: solid $cyan;
    color: $cyan;
}
OptionList {
    background: $bg;
    border: solid $green_dim;
    color: $green_body;
}
OptionList > .option-list--option-highlighted {
    background: $green_dim;
    color: $green;
    text-style: bold;
}
DataTable {
    background: $bg_panel;
    color: $green_body;
}
DataTable > .datatable--header {
    background: $bg_input;
    color: $cyan;
    text-style: bold;
}
DataTable > .datatable--cursor {
    background: $green_dim;
}
DataTable > .datatable--even-row {
    background: $bg_panel;
}
DataTable > .datatable--odd-row {
    background: $bg;
}
RichLog {
    background: $bg;
    padding: 0 1;
    color: $green_body;
    scrollbar-color: $green 25%;
    scrollbar-background: $bg;
}
LoadingIndicator {
    color: $green;
    background: $bg;
}
"""
