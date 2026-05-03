"""
Guided wizard flow for pojolens-agents.

Ownership:
  - WizardPrompter interface and all prompter implementations live here.
  - Wizard LOGIC (plan discovery, intent resolution, 7-step flow) lives here.
  - All Textual widget/app/screen classes live in tui_console.py;
    TextualWizardPrompter imports _ChoiceApp/_ConfirmApp/_InputApp from there.
  - textual_is_available() is the canonical copy from tui_app.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Callable

try:  # pragma: no cover - optional pretty console formatting
    from rich.console import Console
except ImportError:  # pragma: no cover - rich is optional outside textual installs
    Console = None  # type: ignore[assignment]

from pojo_lens_agents.tui_app import textual_is_available


KNOWN_COMMANDS = {
    "config",
    "console",
    "validate",
    "plan",
    "run",
    "resume",
    "retry",
    "review",
    "export-patch",
    "diff-run",
    "export-trace",
    "promote",
    "cleanup",
    "inventory",
    "status",
    "evaluate-run",
    "evaluate-corpus",
    "prune",
    "validate-run",
    "summarize-ledger",
    "wizard",
}


@dataclass(frozen=True)
class PlanPreview:
    path: str
    name: str
    goal: str
    task_count: int


@dataclass(frozen=True)
class PromptChoice:
    label: str
    value: str
    detail: str = ""


class WizardPrompter:
    used_textual = False

    def show_message(self, message: str) -> None:
        raise NotImplementedError

    def choose(self, title: str, choices: list[PromptChoice], *, default_index: int = 0) -> str:
        raise NotImplementedError

    def confirm(self, question: str, *, default: bool = True) -> bool:
        raise NotImplementedError

    def ask_text(self, question: str, *, default: str = "") -> str:
        raise NotImplementedError


class ConsoleWizardPrompter(WizardPrompter):
    def __init__(self) -> None:
        self._console = Console() if Console is not None else None

    def show_message(self, message: str) -> None:
        if self._console is not None:
            self._console.print(message)
        else:
            print(message)

    def choose(self, title: str, choices: list[PromptChoice], *, default_index: int = 0) -> str:
        self.show_message(title)
        for index, choice in enumerate(choices, start=1):
            detail = f" - {choice.detail}" if choice.detail else ""
            self.show_message(f"  {index}. {choice.label}{detail}")
        default_number = max(min(default_index + 1, len(choices)), 1)
        while True:
            raw = input(f"Select [default {default_number}]: ").strip()
            if not raw:
                return choices[default_number - 1].value
            if raw.isdigit():
                picked = int(raw)
                if 1 <= picked <= len(choices):
                    return choices[picked - 1].value
            self.show_message("Enter a valid choice number.")

    def confirm(self, question: str, *, default: bool = True) -> bool:
        suffix = "Y/n" if default else "y/N"
        while True:
            raw = input(f"{question} [{suffix}]: ").strip().lower()
            if not raw:
                return default
            if raw in {"y", "yes"}:
                return True
            if raw in {"n", "no"}:
                return False
            self.show_message("Enter yes or no.")

    def ask_text(self, question: str, *, default: str = "") -> str:
        prompt = f"{question}"
        if default:
            prompt += f" [{default}]"
        prompt += ": "
        raw = input(prompt).strip()
        return raw or default


# Textual prompt screen classes live in tui_console (all Textual UI lives there).
# Import lazily so wizard can be imported without Textual installed.
if textual_is_available():  # pragma: no cover
    from pojo_lens_agents.tui_console import _ChoiceApp, _ConfirmApp, _InputApp


class TextualWizardPrompter(WizardPrompter):  # pragma: no cover - interactive path
    used_textual = True

    def show_message(self, message: str) -> None:
        if Console is not None:
            Console().print(message)
        else:
            print(message)

    def choose(self, title: str, choices: list[PromptChoice], *, default_index: int = 0) -> str:
        result = asyncio.run(_ChoiceApp(title, choices, default_index=default_index).run_async())
        if result is None:
            raise RuntimeError("Wizard aborted.")
        return result

    def confirm(self, question: str, *, default: bool = True) -> bool:
        result = asyncio.run(_ConfirmApp(question, default=default).run_async())
        if result is None:
            raise RuntimeError("Wizard aborted.")
        return bool(result)

    def ask_text(self, question: str, *, default: str = "") -> str:
        result = asyncio.run(_InputApp(question, default=default).run_async())
        if result is None:
            raise RuntimeError("Wizard aborted.")
        return str(result)


class NonInteractiveWizardPrompter(WizardPrompter):
    def show_message(self, message: str) -> None:
        _ = message

    def choose(self, title: str, choices: list[PromptChoice], *, default_index: int = 0) -> str:
        _ = title
        if not choices:
            raise RuntimeError("No choices available.")
        index = max(min(default_index, len(choices) - 1), 0)
        return choices[index].value

    def confirm(self, question: str, *, default: bool = True) -> bool:
        _ = question
        return default

    def ask_text(self, question: str, *, default: str = "") -> str:
        _ = question
        return default


def preprocess_argv(raw_argv: list[str]) -> list[str]:
    if not raw_argv:
        return ["wizard"]
    first = str(raw_argv[0] or "").strip()
    if first and not first.startswith("-") and first not in KNOWN_COMMANDS:
        return ["wizard", "--goal", " ".join(raw_argv)]
    return raw_argv


def discover_plan_previews(tasks_dir: Path) -> list[PlanPreview]:
    previews: list[PlanPreview] = []
    for path in sorted(tasks_dir.glob("*.json")):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if not isinstance(payload, dict):
            continue
        previews.append(
            PlanPreview(
                path=str(path.resolve()),
                name=str(payload.get("name", path.stem) or path.stem),
                goal=str(payload.get("goal", "") or "").strip(),
                task_count=len(payload.get("tasks", []) or []),
            )
        )
    return previews


def _tokenize(text: str) -> set[str]:
    tokens = {
        token.strip().lower()
        for token in "".join(char if char.isalnum() else " " for char in text).split()
        if len(token.strip()) >= 3
    }
    return tokens


def local_goal_matches(goal: str, previews: list[PlanPreview], *, limit: int = 5) -> list[dict[str, Any]]:
    goal_tokens = _tokenize(goal)
    scored: list[dict[str, Any]] = []
    for preview in previews:
        preview_tokens = _tokenize(f"{preview.name} {preview.goal} {Path(preview.path).stem}")
        overlap = len(goal_tokens & preview_tokens)
        score = overlap / max(len(goal_tokens), 1)
        scored.append(
            {
                "planPath": preview.path,
                "planName": preview.name,
                "goal": preview.goal,
                "taskCount": preview.task_count,
                "score": round(score, 3),
                "overlapCount": overlap,
            }
        )
    scored.sort(key=lambda item: (item["score"], item["overlapCount"], item["planName"]), reverse=True)
    return scored[:limit]


def intent_output_schema_json() -> str:
    return json.dumps(
        {
            "type": "object",
            "additionalProperties": False,
            "required": ["mode", "summary"],
            "properties": {
                "mode": {"type": "string", "enum": ["match", "generate"]},
                "summary": {"type": "string"},
                "matchedPlanPath": {"type": ["string", "null"]},
                "matchedPlanName": {"type": ["string", "null"]},
                "taskPlan": {"type": ["object", "null"]},
            },
        }
    )


def _intent_prompt(goal: str, previews: list[PlanPreview]) -> str:
    lines = [
        "Resolve the operator goal to an existing tracked orchestrator plan when possible.",
        "Return JSON only.",
        f"Goal: {goal}",
        "Tracked plans:",
    ]
    for preview in previews[:20]:
        lines.append(f"- path={preview.path} | name={preview.name} | goal={preview.goal}")
    lines.extend(
        [
            "Rules:",
            "- Prefer mode='match' when one tracked plan is a clear fit.",
            "- Use mode='generate' only when no tracked plan fits.",
            "- For generate, return a minimal valid task plan object under taskPlan.",
            "- Keep generated plans conservative and small.",
        ]
    )
    return "\n".join(lines)


def resolve_goal_with_claude(
    goal: str,
    previews: list[PlanPreview],
    *,
    args: argparse.Namespace,
    deps: dict[str, Any],
) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    agents = deps["load_agents"](agents_path)
    planner_agent_name = str(getattr(args, "planner_agent", "planner") or "planner")
    if planner_agent_name not in agents:
        raise deps["error_factory"](f"Unknown planner agent '{planner_agent_name}'")
    planner_agent = agents[planner_agent_name]
    deps["ensure_claude_available"](args.claude_bin)
    prompt = _intent_prompt(goal, previews)
    command = deps["claude_command"](
        args.claude_bin,
        deps["agent_payload_for_claude"](agents, selected_names=[planner_agent_name]),
        planner_agent_name,
        prompt,
        intent_output_schema_json(),
        model="claude-haiku-4-5-20251001",
        effort="low",
        permission_mode=planner_agent.permission_mode,
        allowed_tools=planner_agent.allowed_tools,
        disallowed_tools=planner_agent.disallowed_tools,
        max_budget_usd=planner_agent.max_budget_usd,
    )
    completed = deps["run_subprocess"](
        command,
        cwd=deps["root"],
        timeout_sec=planner_agent.timeout_sec,
        progress_action=None,
    )
    payload = deps["extract_json_payload"](completed.stdout)
    if not isinstance(payload, dict):
        raise deps["error_factory"]("Wizard intent resolution did not return a JSON object")
    mode = str(payload.get("mode", "") or "").strip()
    if mode not in {"match", "generate"}:
        raise deps["error_factory"]("Wizard intent resolution returned an unsupported mode")
    return {
        "mode": mode,
        "summary": str(payload.get("summary", "") or "").strip(),
        "matchedPlanPath": str(payload.get("matchedPlanPath", "") or "").strip() or None,
        "matchedPlanName": str(payload.get("matchedPlanName", "") or "").strip() or None,
        "taskPlan": payload.get("taskPlan"),
        "model": "claude-haiku-4-5-20251001",
        "effort": "low",
    }


def _clarification_output_schema_json() -> str:
    return json.dumps(
        {
            "type": "object",
            "additionalProperties": False,
            "required": ["questions", "refinedGoal"],
            "properties": {
                "questions": {
                    "type": "array",
                    "items": {"type": "string"},
                    "maxItems": 3,
                },
                "refinedGoal": {"type": "string"},
            },
        }
    )


def _clarification_prompt(goal: str, previews: list[PlanPreview]) -> str:
    lines = [
        "You are a planning assistant. Determine if the operator goal needs clarification before selecting or generating a task plan.",
        "Return JSON only.",
        f"Goal: {goal}",
        "Tracked plans (context):",
    ]
    for preview in previews[:10]:
        lines.append(f"- name={preview.name} | goal={preview.goal}")
    lines.extend(
        [
            "Rules:",
            "- If the goal is clear enough to proceed, return an empty questions array and a refinedGoal that sharpens the original.",
            "- If the goal needs clarification, return up to 3 focused questions and a best-guess refinedGoal.",
            "- Questions must be specific and answerable by the operator.",
            "- Never ask for information that is already implied by the goal.",
        ]
    )
    return "\n".join(lines)


def clarify_goal_with_claude(
    goal: str,
    previews: list[PlanPreview],
    *,
    args: argparse.Namespace,
    deps: dict[str, Any],
) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    agents = deps["load_agents"](agents_path)
    planner_agent_name = str(getattr(args, "planner_agent", "planner") or "planner")
    if planner_agent_name not in agents:
        return {"questions": [], "refinedGoal": goal}
    planner_agent = agents[planner_agent_name]
    deps["ensure_claude_available"](args.claude_bin)
    prompt = _clarification_prompt(goal, previews)
    command = deps["claude_command"](
        args.claude_bin,
        deps["agent_payload_for_claude"](agents, selected_names=[planner_agent_name]),
        planner_agent_name,
        prompt,
        _clarification_output_schema_json(),
        model="claude-haiku-4-5-20251001",
        effort="low",
        permission_mode=planner_agent.permission_mode,
        allowed_tools=planner_agent.allowed_tools,
        disallowed_tools=planner_agent.disallowed_tools,
        max_budget_usd=planner_agent.max_budget_usd,
    )
    completed = deps["run_subprocess"](
        command,
        cwd=deps["root"],
        timeout_sec=planner_agent.timeout_sec,
        progress_action=None,
    )
    result = deps["extract_json_payload"](completed.stdout)
    if not isinstance(result, dict):
        return {"questions": [], "refinedGoal": goal}
    questions = [str(q) for q in (result.get("questions") or []) if q][:3]
    refined_goal = str(result.get("refinedGoal", "") or "").strip() or goal
    return {"questions": questions, "refinedGoal": refined_goal}


def _run_clarification_loop(
    goal: str,
    previews: list[PlanPreview],
    prompter: WizardPrompter,
    *,
    args: argparse.Namespace,
    deps: dict[str, Any],
) -> tuple[str, list[dict[str, str]]]:
    answers: list[dict[str, str]] = []
    current_goal = goal
    try:
        result = clarify_goal_with_claude(current_goal, previews, args=args, deps=deps)
    except Exception:
        return current_goal, answers
    questions = result.get("questions", [])
    current_goal = result.get("refinedGoal", current_goal) or current_goal
    for question in questions:
        answer = prompter.ask_text(question, default="")
        answers.append({"question": question, "answer": answer})
        if answer:
            current_goal = f"{current_goal}; {answer}"
    return current_goal, answers


def _format_staged_plan_summary(plan_path: str, validate_payload: dict[str, Any]) -> str:
    plan_name = str(validate_payload.get("planName", "") or Path(plan_path).stem)
    task_count = int(validate_payload.get("taskCount", 0) or 0)
    tasks = list(validate_payload.get("tasks", []) or [])
    lines = [
        f"[bold]Plan:[/bold] {plan_name}",
        f"[bold]Tasks:[/bold] {task_count}",
    ]
    for task in tasks[:10]:
        task_id = str(task.get("id", "") or "")
        agent = str(task.get("agent", "") or "")
        if task_id:
            lines.append("  • " + task_id + (f" [{agent}]" if agent else ""))
    if len(tasks) > 10:
        lines.append(f"  … and {len(tasks) - 10} more")
    return "\n".join(lines)


_CHECKPOINT_PROCEED = "proceed"
_CHECKPOINT_REVISE = "revise"
_CHECKPOINT_STOP = "stop"


def _plan_approval_checkpoint(
    plan_summary: str,
    prompter: WizardPrompter,
    *,
    interactive: bool,
) -> str:
    if not interactive:
        return _CHECKPOINT_PROCEED
    choices = [
        PromptChoice(label="Proceed to run", value=_CHECKPOINT_PROCEED),
        PromptChoice(label="Revise goal and re-plan", value=_CHECKPOINT_REVISE),
        PromptChoice(label="Stop", value=_CHECKPOINT_STOP),
    ]
    prompter.show_message(plan_summary)
    return prompter.choose("Plan ready — how to proceed?", choices, default_index=0)


def _namespace(**kwargs: Any) -> SimpleNamespace:
    return SimpleNamespace(**kwargs)


def _status_reason(status_counts: dict[str, int]) -> str:
    if status_counts.get("failed", 0) > 0:
        return "failed"
    if status_counts.get("blocked", 0) > 0:
        return "blocked"
    return "completed"


def _next_actions_for_run(run_payload: dict[str, Any], *, retry_supported: bool) -> list[str]:
    actions = [
        f"Open run dir: {run_payload.get('runDir')}",
        f"Inspect manifest: {Path(str(run_payload.get('runDir'))).joinpath('manifest.json')}",
    ]
    if retry_supported and run_payload.get("statusCounts", {}).get("failed", 0):
        actions.append(f"Retry failed tasks from {run_payload.get('runDir')}")
    if run_payload.get("statusCounts", {}).get("blocked", 0):
        actions.append("Review blocked task reasons before resuming or retrying.")
    return actions


def _child_run_display_flags(
    *,
    interactive: bool,
    watch_requested: bool,
    tui_requested: bool,
    textual_available: bool,
) -> tuple[bool, bool]:
    if not interactive:
        return False, False
    if watch_requested:
        return False, True
    if tui_requested:
        return True, False
    if textual_available:
        return True, False
    return False, True


def choose_prompter(*, interactive: bool, textual_requested: bool, textual_available: bool) -> WizardPrompter:
    if not interactive:
        return NonInteractiveWizardPrompter()
    if textual_requested and textual_available:
        return TextualWizardPrompter()
    return ConsoleWizardPrompter()


def _generated_plan_path(runtime_root: Path, goal: str, slugify: Callable[[str], str]) -> Path:
    slug = slugify(goal)[:48] or "wizard-goal"
    return runtime_root / "generated-plans" / f"generated-wizard-{slug}.json"


def wizard_command(args: argparse.Namespace, *, deps: dict[str, Any]) -> dict[str, Any]:
    root = deps["root"]
    runtime_root = Path(args.runtime_root).resolve()
    interactive = not bool(getattr(args, "json", False)) and bool(getattr(sys.stdin, "isatty", lambda: False)()) and bool(getattr(sys.stderr, "isatty", lambda: False)())
    textual_available = bool(deps["textual_available"]())
    child_tui, child_watch = _child_run_display_flags(
        interactive=interactive,
        watch_requested=bool(getattr(args, "watch", False)),
        tui_requested=bool(getattr(args, "tui", False)),
        textual_available=textual_available,
    )
    prompter = choose_prompter(
        interactive=interactive,
        textual_requested=bool(getattr(args, "tui", False) or (interactive and textual_available and not bool(getattr(args, "watch", False)))),
        textual_available=textual_available,
    )
    payload: dict[str, Any] = {
        "interactive": interactive,
        "usedTextual": bool(prompter.used_textual),
        "mode": "plan",
        "steps": [],
        "nextActions": [],
    }
    if getattr(args, "resume_run_ref", ""):
        payload["mode"] = "resume"
    elif getattr(args, "retry_run_ref", ""):
        payload["mode"] = "retry"

    inventory_payload = deps["inventory_handler"](
        _namespace(
            runtime_root=str(Path(args.runtime_root).resolve()),
            limit=5,
            dry_run=True,
            json=False,
            verbose=False,
            claude_bin=args.claude_bin,
        )
    )
    payload["inventory"] = inventory_payload

    plan_previews = discover_plan_previews(root / "ai" / "orchestrator" / "tasks")
    payload["planChoices"] = [
        {
            "path": item.path,
            "name": item.name,
            "goal": item.goal,
            "taskCount": item.task_count,
        }
        for item in plan_previews
    ]

    plan_path: str | None = None
    generated_plan_payload: dict[str, Any] | None = None
    goal_text = str(getattr(args, "goal", "") or "").strip()
    if not goal_text:
        goal_words = list(getattr(args, "goal_words", []) or [])
        goal_text = " ".join(str(item) for item in goal_words).strip()

    if payload["mode"] == "plan":
        _dry_run_arg = bool(getattr(args, "dry_run", False))
        _explicit_plan = str(getattr(args, "plan", "") or "").strip()
        _goal_active = goal_text
        _clarification_answers: list[dict[str, str]] = []

        if interactive and _goal_active and not _dry_run_arg and not _explicit_plan:
            try:
                _goal_active, _clarification_answers = _run_clarification_loop(
                    _goal_active, plan_previews, prompter, args=args, deps=deps
                )
            except Exception:
                pass
        payload["clarification"] = {"refinedGoal": _goal_active, "answers": _clarification_answers}

        for _plan_round in range(3):
            plan_path = str(Path(_explicit_plan).resolve()) if _explicit_plan else None
            generated_plan_payload = None

            if plan_path is None and _goal_active:
                local_matches = local_goal_matches(_goal_active, plan_previews)
                intent_payload = {
                    "goal": _goal_active,
                    "localMatches": local_matches,
                    "mode": "match" if local_matches and float(local_matches[0]["score"]) >= 0.2 else "generate",
                }
                if not _dry_run_arg:
                    try:
                        intent_payload = resolve_goal_with_claude(_goal_active, plan_previews, args=args, deps=deps)
                    except Exception as exc:
                        intent_payload["warning"] = str(exc)
                payload["intentResolution"] = intent_payload
                if intent_payload["mode"] == "match" and intent_payload.get("matchedPlanPath"):
                    plan_path = str(Path(str(intent_payload["matchedPlanPath"])).resolve())
                elif intent_payload["mode"] == "generate" and isinstance(intent_payload.get("taskPlan"), dict):
                    generated_plan_payload = dict(intent_payload["taskPlan"])
                    generated_plan_path = _generated_plan_path(runtime_root, _goal_active, deps["slugify"])
                    payload["generatedPlanPath"] = str(generated_plan_path)
                    if not _dry_run_arg:
                        deps["write_json"](generated_plan_path, generated_plan_payload)
                        plan_path = str(generated_plan_path)

            if plan_path is None:
                _plan_choices = [
                    PromptChoice(
                        label=preview.name,
                        value=preview.path,
                        detail=f"{preview.task_count} tasks | {preview.goal[:100]}",
                    )
                    for preview in plan_previews
                ]
                if not _plan_choices:
                    raise deps["error_factory"]("No tracked plans found under ai/orchestrator/tasks")
                plan_path = prompter.choose("Select a tracked plan", _plan_choices, default_index=0)
            payload["selectedPlanPath"] = plan_path

            validate_payload = deps["validate_handler"](
                _namespace(
                    agents=args.agents,
                    task_plan=plan_path,
                    dry_run=True,
                    json=False,
                    fingerprint_only=False,
                    verbose=False,
                    claude_bin=args.claude_bin,
                )
            )
            payload["preflight"] = validate_payload
            payload["steps"] = [s for s in payload["steps"] if s["name"] != "preflight"]
            payload["steps"].append(
                {
                    "name": "preflight",
                    "status": "completed",
                    "summary": f"Validated {validate_payload.get('planName')} with {validate_payload.get('taskCount')} tasks.",
                }
            )

            _plan_summary = _format_staged_plan_summary(plan_path, validate_payload)
            _checkpoint = _plan_approval_checkpoint(_plan_summary, prompter, interactive=interactive)
            payload["planCheckpoint"] = _checkpoint

            if _checkpoint == _CHECKPOINT_PROCEED:
                break
            elif _checkpoint == _CHECKPOINT_STOP:
                payload["steps"].append(
                    {"name": "done", "status": "stopped", "summary": "Operator stopped before run."}
                )
                return payload
            else:
                _revised = prompter.ask_text("Revised goal", default=_goal_active) if interactive else _goal_active
                _goal_active = _revised.strip() or _goal_active
                _explicit_plan = ""
                if interactive and _goal_active and not _dry_run_arg:
                    try:
                        _goal_active, _extra = _run_clarification_loop(
                            _goal_active, plan_previews, prompter, args=args, deps=deps
                        )
                        _clarification_answers.extend(_extra)
                    except Exception:
                        pass
                payload["clarification"] = {"refinedGoal": _goal_active, "answers": _clarification_answers}

        if interactive and not _dry_run_arg:
            dry_run_first = prompter.confirm("Dry run first?", default=True)
            max_parallel_text = prompter.ask_text("Max parallel", default=str(args.max_parallel))
        else:
            dry_run_first = _dry_run_arg
            max_parallel_text = str(args.max_parallel)
        try:
            max_parallel = max(int(max_parallel_text), 1)
        except ValueError:
            max_parallel = max(int(args.max_parallel), 1)
        payload["preflightDecisions"] = {
            "dryRun": dry_run_first,
            "maxParallel": max_parallel,
        }
        run_args = _namespace(
            agents=args.agents,
            task_plan=plan_path,
            claude_bin=args.claude_bin,
            runtime_root=args.runtime_root,
            max_parallel=max_parallel,
            continue_on_error=False,
            dry_run=dry_run_first,
            worker_validation_mode="",
            effort="",
            selected_tasks=[],
            max_task_retries=None,
            hitl=False,
            hitl_mode="batch",
            hitl_auto_approve=False,
            otel_endpoint="",
            estimate=False,
            follow_up_mode="",
            reuse_unchanged=False,
            watch=child_watch,
            tui=child_tui,
            json=False,
            verbose=False,
            tpm_limit=getattr(args, "tpm_limit", None),
            rpm_limit=getattr(args, "rpm_limit", None),
        )
        run_payload = deps["run_handler"](run_args)
    elif payload["mode"] == "resume":
        run_ref = str(getattr(args, "resume_run_ref", "") or "").strip()
        if not run_ref:
            raise deps["error_factory"]("--resume requires a run reference")
        payload["selectedRunRef"] = run_ref
        run_payload = deps["resume_handler"](
            _namespace(
                run_ref=run_ref,
                agents="",
                claude_bin=args.claude_bin,
                max_parallel=max(int(args.max_parallel), 1),
                effort="",
                selected_tasks=[],
                continue_on_error=False,
                max_task_retries=None,
                follow_up_mode="",
                hitl=False,
                hitl_mode="batch",
                hitl_auto_approve=False,
                otel_endpoint="",
                worker_validation_mode="",
                reuse_unchanged=False,
                dry_run=bool(getattr(args, "dry_run", False)),
                watch=child_watch,
                tui=child_tui,
                json=False,
                verbose=False,
                tpm_limit=getattr(args, "tpm_limit", None),
                rpm_limit=getattr(args, "rpm_limit", None),
            )
        )
    else:
        run_ref = str(getattr(args, "retry_run_ref", "") or "").strip()
        if not run_ref:
            raise deps["error_factory"]("--retry requires a run reference")
        payload["selectedRunRef"] = run_ref
        run_payload = deps["retry_handler"](
            _namespace(
                run_ref=run_ref,
                agents="",
                claude_bin=args.claude_bin,
                runtime_root="",
                max_parallel=max(int(args.max_parallel), 1),
                effort="",
                selected_tasks=[],
                continue_on_error=False,
                max_task_retries=None,
                otel_endpoint="",
                worker_validation_mode="",
                reuse_unchanged=False,
                dry_run=bool(getattr(args, "dry_run", False)),
                watch=child_watch,
                tui=child_tui,
                json=False,
                verbose=False,
                tpm_limit=getattr(args, "tpm_limit", None),
                rpm_limit=getattr(args, "rpm_limit", None),
            )
        )
    payload["run"] = run_payload
    payload["statusCounts"] = dict(run_payload.get("statusCounts", {}))
    payload["steps"].append(
        {
            "name": "run",
            "status": _status_reason(run_payload.get("statusCounts", {})),
            "summary": f"Run {run_payload.get('runId')} finished with {run_payload.get('statusCounts', {})}.",
        }
    )
    payload["receipt"] = {
        "runId": run_payload.get("runId"),
        "runDir": run_payload.get("runDir"),
        "statusCounts": run_payload.get("statusCounts", {}),
        "totalCostUsd": ((run_payload.get("usageTotals") or {}).get("totalCostUsd")),
    }

    if run_payload.get("statusCounts", {}).get("failed", 0) or run_payload.get("statusCounts", {}).get("blocked", 0):
        payload["nextActions"] = _next_actions_for_run(run_payload, retry_supported=True)
        return payload

    if bool(run_payload.get("dryRun")):
        payload["steps"].append({"name": "done", "status": "completed", "summary": "Dry-run wizard receipt prepared."})
        return payload

    run_ref = str(run_payload.get("runDir") or "")
    status_payload = deps["status_handler"](
        _namespace(
            run_ref=run_ref,
            selected_tasks=[],
            json=False,
            verbose=False,
            claude_bin=args.claude_bin,
        )
    )
    payload["status"] = status_payload

    if status_payload.get("reviewSummary", {}).get("changedTaskCount", 0) > 0:
        do_review = prompter.confirm("Review changes?", default=True) if interactive else False
        if do_review:
            review_payload = deps["review_handler"](
                _namespace(
                    run_ref=run_ref,
                    selected_tasks=[],
                    context_lines=3,
                    dry_run=False,
                    json=False,
                    verbose=False,
                    claude_bin=args.claude_bin,
                )
            )
            payload["review"] = review_payload
            payload["steps"].append(
                {
                    "name": "review",
                    "status": "completed",
                    "summary": f"Reviewed {review_payload.get('summary', {}).get('changedFileCount', 0)} changed files.",
                }
            )
        else:
            payload["steps"].append({"name": "review", "status": "skipped", "summary": "Operator skipped review."})

        diff_run_handler = deps.get("diff_run_handler")
        if diff_run_handler is not None:
            diff_stat_payload = diff_run_handler(
                _namespace(
                    run_ref=run_ref,
                    selected_tasks=[],
                    selected_task_csv="",
                    path_filters=[],
                    path_filters_csv="",
                    context_lines=3,
                    stat=True,
                    json=False,
                    verbose=False,
                    claude_bin=args.claude_bin,
                )
            )
            payload["diffStat"] = {
                key: value for key, value in diff_stat_payload.items() if key != "_consoleText"
            }
            if interactive:
                stat_text = str(diff_stat_payload.get("_consoleText", "") or "").strip()
                if stat_text:
                    prompter.show_message(stat_text)
                if int((diff_stat_payload.get("summary") or {}).get("changedFileCount", 0) or 0) > 0:
                    show_full_diff = prompter.confirm("Show full diff?", default=False)
                    if show_full_diff:
                        diff_full_payload = diff_run_handler(
                            _namespace(
                                run_ref=run_ref,
                                selected_tasks=[],
                                selected_task_csv="",
                                path_filters=[],
                                path_filters_csv="",
                                context_lines=3,
                                stat=False,
                                json=False,
                                verbose=False,
                                claude_bin=args.claude_bin,
                            )
                        )
                        payload["diffFull"] = {
                            key: value for key, value in diff_full_payload.items() if key != "_consoleText"
                        }
                        full_text = str(diff_full_payload.get("_consoleText", "") or "").strip()
                        if full_text:
                            prompter.show_message(full_text)

    promote_preview = deps["promote_handler"](
        _namespace(
            run_ref=run_ref,
            selected_tasks=[],
            dry_run=True,
            json=False,
            verbose=False,
            claude_bin=args.claude_bin,
        )
    )
    payload["promotePreview"] = promote_preview
    if promote_preview.get("promotionAllowed"):
        do_promote = prompter.confirm("Promote to repo?", default=False) if interactive else False
        if do_promote:
            promote_payload = deps["promote_handler"](
                _namespace(
                    run_ref=run_ref,
                    selected_tasks=[],
                    dry_run=False,
                    json=False,
                    verbose=False,
                    claude_bin=args.claude_bin,
                )
            )
            payload["promote"] = promote_payload
            payload["steps"].append(
                {
                    "name": "promote",
                    "status": "completed",
                    "summary": f"Promoted {promote_payload.get('filesPromoted', 0)} files.",
                }
            )
            payload["receipt"]["promotedFiles"] = promote_payload.get("filesPromoted", 0)
            do_validate = prompter.confirm("Run post-promotion validation?", default=True) if interactive else False
            if do_validate:
                validation_payload = deps["validate_run_handler"](
                    _namespace(
                        run_ref=run_ref,
                        selected_tasks=[],
                        include_statuses=[],
                        execution_scope="repo",
                        allow_unsafe_commands=False,
                        intents_only=False,
                        continue_on_error=False,
                        timeout_sec=deps["default_task_timeout_sec"],
                        dry_run=False,
                        json=False,
                        verbose=False,
                        claude_bin=args.claude_bin,
                    )
                )
                payload["validation"] = validation_payload
                payload["steps"].append(
                    {
                        "name": "validation",
                        "status": _status_reason(validation_payload.get("statusCounts", {})),
                        "summary": f"Post-promotion validation completed with {validation_payload.get('statusCounts', {})}.",
                    }
                )
        else:
            payload["steps"].append({"name": "promote", "status": "skipped", "summary": "Operator skipped promotion."})
    else:
        payload["steps"].append(
            {
                "name": "promote",
                "status": "blocked",
                "summary": "Promotion is blocked.",
            }
        )
        payload["nextActions"] = list(promote_preview.get("blockedReasons", []))
        payload["nextActions"].extend(_next_actions_for_run(run_payload, retry_supported=True))
        return payload

    payload["steps"].append({"name": "done", "status": "completed", "summary": "Wizard receipt prepared."})
    return payload
