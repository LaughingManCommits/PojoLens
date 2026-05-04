from __future__ import annotations

from typing import Any, Callable

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.screen import Screen
    from textual.widgets import Button, Footer, Header, Input, OptionList, Rule, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

from pojo_lens_agents._tui_theme import (
    _CLARIF_QUESTIONS,
    _EFFORT_OPTIONS,
    _WORKSPACE_OPTIONS,
    _WORKSPACE_WARN,
    _HITL_OPTIONS,
    _BUDGET_BEHAVIOR_OPTIONS,
    _FOLLOW_UP_OPTIONS,
)


# ── GoalInputScreen ────────────────────────────────────────────────────────────

class GoalInputScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 1: enter goal or leave blank for saved plans."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
    ]

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 1 / 5 ]  ENTER GOAL",
                id="card-title",
            )
            yield Static(
                "Describe what you want the AI agents to accomplish.\n"
                "Be specific: mention files, technologies, and desired outcome.",
                id="desc",
            )
            yield Input(
                placeholder="e.g.  Add dark-mode toggle to the React settings panel",
                id="goal-input",
            )
            yield Static(
                "[dim]Tip: leave blank and press CONTINUE to browse saved plans.[/]",
                id="hint",
            )
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("CANCEL", id="btn-cancel")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#goal-input", Input).focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self._submit()
        elif event.button.id == "btn-cancel":
            self.action_cancel()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        self._submit()

    def _submit(self) -> None:
        goal = self.query_one("#goal-input", Input).value.strip()
        if not goal:
            from pojo_lens_agents._tui_plans import SavedPlansScreen
            self.app.push_screen(SavedPlansScreen())  # type: ignore[attr-defined]
            self.dismiss(None)
        else:
            self.dismiss(goal)

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── ClarificationScreen ────────────────────────────────────────────────────────

class ClarificationScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 1b: goal clarification loop.

    When `clarify_fn` is provided, an AI call generates up to 3 context-aware
    questions plus a refinedGoal.  Without it, three static fallback questions
    are used so the screen always works without a running model.
    """

    BINDINGS = [
        Binding("escape", "skip_all", "Skip",   show=True),
        Binding("enter",  "next_q",   "Submit", show=False),
    ]

    def __init__(
        self,
        goal: str,
        clarify_fn: Callable[[str], dict[str, Any]] | None = None,
    ) -> None:
        super().__init__()
        self._goal             = goal
        self._clarify_fn       = clarify_fn
        self._q_idx            = 0
        self._answers: list[tuple[str, str]] = []
        self._ai_questions: list[str] = []
        self._ai_refined_goal: str = ""

    @property
    def _questions(self) -> list[str]:
        return self._ai_questions if self._ai_questions else list(_CLARIF_QUESTIONS)

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 1b / 5 ]  GOAL CLARIFICATION",
                id="cl-title",
            )
            yield Static(
                "[#00e5ff][ AI ][/] Generating context-aware questions…"
                if self._clarify_fn else
                "[dim]Using standard questions — no AI backend configured.[/]",
                id="cl-wip",
            )
            with Container(id="cl-goal-box"):
                yield Static("GOAL:", id="cl-goal-label")
                yield Static(self._goal[:160], id="cl-goal-text")
            yield Rule(id="cl-separator")
            yield Static("", id="cl-q-label")
            yield Static("", id="cl-q-text")
            yield Static("", id="cl-progress")
            with Container(id="cl-answers"):
                yield Static("[ no answers yet ]", id="cl-answers-content")
            yield Input(
                placeholder="Your answer — or leave blank and press NEXT to skip this question",
                id="cl-input",
            )
            with Horizontal(id="btns"):
                yield Button("NEXT  →",  id="btn-next",  variant="primary")
                yield Button("SKIP ALL", id="btn-skip")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  CLARIFICATION"
        self.app.sub_title = "GOAL REFINEMENT"
        if self._clarify_fn is not None:
            fn = self._clarify_fn
            goal = self._goal
            self.run_worker(lambda: self._run_ai_clarify(fn, goal), thread=True, name="cl-ai")
        self._refresh_question()
        self.query_one("#cl-input", Input).focus()

    def _run_ai_clarify(self, fn: Callable[[str], dict[str, Any]], goal: str) -> None:
        try:
            result = fn(goal)
        except Exception as exc:
            self.app.call_from_thread(
                lambda: self.query_one("#cl-wip", Static).update(
                    f"[#ffaa00]AI clarification failed: {exc} — using standard questions[/]"
                )
            )
            return
        questions = [str(q) for q in (result.get("questions") or []) if q][:3]
        refined   = str(result.get("refinedGoal") or "").strip()
        self._ai_questions    = questions if questions else []
        self._ai_refined_goal = refined or goal

        def _update() -> None:
            self.query_one("#cl-wip", Static).update(
                "[#00ff41][ AI ][/] Questions generated."
                if questions else
                "[dim]AI returned no questions — using standard questions.[/]"
            )
            if refined and refined != goal:
                self.query_one("#cl-goal-text", Static).update(refined[:160])
            self._q_idx = 0
            self._refresh_question()

        self.app.call_from_thread(_update)

    def _refresh_question(self) -> None:
        qs    = self._questions
        total = len(qs)
        idx   = self._q_idx
        if idx >= total or not qs:
            return
        self.query_one("#cl-q-label", Static).update(
            f"[bold #00e5ff]Question {idx + 1} of {total}:[/]"
        )
        self.query_one("#cl-q-text", Static).update(
            f"[#a0ffa0]{qs[idx]}[/]"
        )
        self.query_one("#cl-progress", Static).update(
            f"[dim]{'▮' * (idx + 1)}{'▯' * (total - idx - 1)}  {idx + 1}/{total}[/]"
        )
        self.query_one("#cl-input", Input).value = ""

    def _refresh_answers(self) -> None:
        if not self._answers:
            self.query_one("#cl-answers-content", Static).update("[ no answers yet ]")
            return
        lines = []
        for q, a in self._answers:
            lines.append(f"[dim #00e5ff]Q:[/] [dim]{q[:60]}[/]")
            lines.append(f"  [#00ff41]A:[/] {a[:120]}")
        self.query_one("#cl-answers-content", Static).update("\n".join(lines))

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-next":
            self.action_next_q()
        elif event.button.id == "btn-skip":
            self.action_skip_all()

    def on_input_submitted(self, _: Input.Submitted) -> None:
        self.action_next_q()

    def action_next_q(self) -> None:
        qs = self._questions
        answer = self.query_one("#cl-input", Input).value.strip()
        if answer and self._q_idx < len(qs):
            self._answers.append((qs[self._q_idx], answer))
            self._refresh_answers()
        self._q_idx += 1
        if self._q_idx >= len(qs):
            self._finish()
        else:
            self._refresh_question()

    def _finish(self) -> None:
        base = self._ai_refined_goal or self._goal
        if self._answers:
            context  = "; ".join(a for _, a in self._answers)
            enhanced = f"{base}. Additional context: {context}"
        else:
            enhanced = base
        self.dismiss(enhanced)

    def action_skip_all(self) -> None:
        self.dismiss(self._ai_refined_goal or self._goal)


# ── EffortSelectScreen ─────────────────────────────────────────────────────────

class EffortSelectScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 2: choose planning effort / model profile."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
        Binding("enter",  "submit", "Select", show=True),
    ]

    def __init__(self, goal: str) -> None:
        super().__init__()
        self._goal = goal

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 2 / 5 ]  PLANNER EFFORT",
                id="card-title",
            )
            yield Static(
                "Choose the Claude model for plan generation.\n"
                "This controls cost, depth, and quality of the generated plan.",
                id="desc",
            )
            yield OptionList(
                *[label for _, label in _EFFORT_OPTIONS],
                id="effort-list",
            )
            yield Static(
                "⚠  Opus (High) is expensive. Prefer Medium for most tasks.",
                id="cost-note",
            )
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#effort-list", OptionList).highlighted = 0

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self.action_submit()
        elif event.button.id == "btn-back":
            self.action_cancel()

    def action_submit(self) -> None:
        idx = int(self.query_one("#effort-list", OptionList).highlighted or 0)
        effort, _ = _EFFORT_OPTIONS[idx]
        self.dismiss(effort)

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── WorkspaceModeScreen ────────────────────────────────────────────────────────

class WorkspaceModeScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 3: choose workspace isolation strategy."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
        Binding("enter",  "submit", "Select", show=True),
    ]

    def __init__(self, goal: str, effort: str) -> None:
        super().__init__()
        self._goal = goal
        self._effort = effort

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 3 / 5 ]  WORKSPACE MODE",
                id="card-title",
            )
            yield Static(
                "Choose how worker agents access the repository.\n"
                "COPY is the safest default — workers see only declared readPaths.",
                id="desc",
            )
            yield OptionList(
                *[label for _, label in _WORKSPACE_OPTIONS],
                id="ws-list",
            )
            yield Static("", id="ws-warning")
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#ws-list", OptionList).highlighted = 0

    def on_option_list_option_highlighted(self, _: OptionList.OptionHighlighted) -> None:
        idx = int(self.query_one("#ws-list", OptionList).highlighted or 0)
        mode, _ = _WORKSPACE_OPTIONS[idx]
        warn_text = _WORKSPACE_WARN.get(mode, "")
        self.query_one("#ws-warning", Static).update(
            f"[#ffaa00]{warn_text}[/]" if warn_text else ""
        )

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self.action_submit()
        elif event.button.id == "btn-back":
            self.action_cancel()

    def action_submit(self) -> None:
        idx = int(self.query_one("#ws-list", OptionList).highlighted or 0)
        mode, _ = _WORKSPACE_OPTIONS[idx]
        self.dismiss(mode)

    def action_cancel(self) -> None:
        self.dismiss(None)


# ── GovernanceScreen ───────────────────────────────────────────────────────────

class GovernanceScreen(Screen):  # type: ignore[type-arg,misc]
    """Wizard step 4: configure governance — HITL, budget, parallelism."""

    BINDINGS = [
        Binding("escape", "cancel", "Back", show=True),
    ]

    def __init__(self, goal: str, effort: str, workspace_mode: str) -> None:
        super().__init__()
        self._goal = goal
        self._effort = effort
        self._workspace_mode = workspace_mode

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="card"):
            yield Static(
                "[ STEP 4 / 5 ]  GOVERNANCE  (press CONTINUE to accept defaults)",
                id="card-title",
            )
            yield Rule(id="gov-rule")
            yield Static("HITL Gate Mode:", classes="field-label")
            yield Static(
                "always=every batch · batch=pending-tasks only · on-failure=after fail · none=disabled",
                classes="field-hint",
            )
            yield OptionList(
                *[label for _, label in _HITL_OPTIONS],
                id="hitl-list",
            )
            yield Static("Budget Behavior:", classes="field-label")
            yield Static(
                "warn=surface warning but continue · stop=block batches once limit hit",
                classes="field-hint",
            )
            yield OptionList(
                *[label for _, label in _BUDGET_BEHAVIOR_OPTIONS],
                id="budget-behavior-list",
            )
            yield Static("Follow-Up Task Behavior:", classes="field-label")
            yield Static(
                "ignore=discard worker followUpTasks · inject=queue them between batches",
                classes="field-hint",
            )
            yield OptionList(
                *[label for _, label in _FOLLOW_UP_OPTIONS],
                id="followup-list",
            )
            yield Rule()
            yield Static("Run Budget USD  (blank = unlimited):", classes="field-label")
            yield Input(placeholder="e.g.  0.50", id="budget-input")
            yield Static("Max Parallel Tasks:", classes="field-label")
            yield Input(value="2", id="parallel-input")
            with Horizontal(id="btns"):
                yield Button("CONTINUE  →", id="btn-continue", variant="primary")
                yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.query_one("#hitl-list",          OptionList).highlighted = 0
        self.query_one("#budget-behavior-list", OptionList).highlighted = 0
        self.query_one("#followup-list",       OptionList).highlighted = 0

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-continue":
            self._submit()
        elif event.button.id == "btn-back":
            self.action_cancel()

    def _submit(self) -> None:
        hitl_idx = int(self.query_one("#hitl-list", OptionList).highlighted or 0)
        hitl_mode, _ = _HITL_OPTIONS[hitl_idx]
        bb_idx = int(self.query_one("#budget-behavior-list", OptionList).highlighted or 0)
        budget_behavior, _ = _BUDGET_BEHAVIOR_OPTIONS[bb_idx]
        fu_idx = int(self.query_one("#followup-list", OptionList).highlighted or 0)
        follow_up, _ = _FOLLOW_UP_OPTIONS[fu_idx]
        budget_raw   = self.query_one("#budget-input",   Input).value.strip()
        parallel_raw = self.query_one("#parallel-input", Input).value.strip()
        try:
            max_parallel = max(1, int(parallel_raw))
        except (ValueError, TypeError):
            max_parallel = 2
        try:
            budget = float(budget_raw) if budget_raw else None
        except (ValueError, TypeError):
            budget = None
        self.dismiss({
            "hitl":            hitl_mode,
            "budget_behavior": budget_behavior,
            "follow_up":       follow_up,
            "max_parallel":    max_parallel,
            "budget":          budget,
        })

    def action_cancel(self) -> None:
        self.dismiss(None)
