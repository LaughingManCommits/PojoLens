from __future__ import annotations

import copy
import json
import textwrap
from typing import Any


def evaluate_prompt_budget(
    *,
    prompt_chars: int,
    prompt_estimated_tokens: int,
    max_chars: int | None,
    max_estimated_tokens: int | None,
    prompt_budget_result_factory,
) -> Any:
    violations: list[str] = []
    if max_chars is not None and prompt_chars > max_chars:
        violations.append(f"prompt_chars={prompt_chars} exceeds maxPromptChars={max_chars}")
    if max_estimated_tokens is not None and prompt_estimated_tokens > max_estimated_tokens:
        violations.append(
            "prompt_estimated_tokens="
            f"{prompt_estimated_tokens} exceeds maxPromptEstimatedTokens={max_estimated_tokens}"
        )
    return prompt_budget_result_factory(
        max_chars=max_chars,
        max_estimated_tokens=max_estimated_tokens,
        exceeded=bool(violations),
        violations=violations,
    )


def prompt_budget_failure_summary(result: Any) -> str:
    details = "; ".join(result.violations) if result.violations else "configured budget violation"
    return f"Prompt budget exceeded: {details}"


def resolved_max_prompt_chars(task: Any | None, agent: Any) -> int | None:
    if task is not None and task.max_prompt_chars is not None:
        return task.max_prompt_chars
    return agent.max_prompt_chars


def resolved_max_prompt_estimated_tokens(task: Any | None, agent: Any) -> int | None:
    if task is not None and task.max_prompt_estimated_tokens is not None:
        return task.max_prompt_estimated_tokens
    return agent.max_prompt_estimated_tokens


def effective_context_mode(task: Any, agent: Any, *, default_context_mode: str) -> str:
    return task.context_mode or agent.context_mode or default_context_mode


def effective_dependency_materialization_mode(task: Any, *, default_mode: str) -> str:
    return task.dependency_materialization or default_mode


def resolved_model_profile(task: Any, agent: Any, *, model_to_profile: dict[str, str]) -> str | None:
    explicit_model = task.model or agent.model
    if explicit_model:
        return model_to_profile.get(explicit_model)
    return task.model_profile or agent.model_profile


def resolved_model(
    task: Any,
    agent: Any,
    *,
    model_to_profile: dict[str, str],
    model_profile_to_model: dict[str, str],
) -> str | None:
    explicit_model = task.model or agent.model
    if explicit_model:
        return explicit_model
    profile = resolved_model_profile(task, agent, model_to_profile=model_to_profile)
    if profile:
        return model_profile_to_model[profile]
    return None


def normalize_effort_override(value: str | None, *, location: str, error_factory) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    if not normalized:
        return None
    if any(char.isspace() for char in normalized):
        raise error_factory(f"{location}: effort override must not contain whitespace")
    return normalized


def resolve_effort(
    task: Any | None,
    agent: Any,
    *,
    run_override: str | None = None,
) -> tuple[str | None, str]:
    if run_override:
        return run_override, "override"
    if task is not None and task.effort:
        return task.effort, "task"
    if agent.effort:
        return agent.effort, "agent"
    return None, "default"


def effective_plan_model_profiles(
    plan: Any,
    agents: dict[str, Any],
    *,
    model_to_profile: dict[str, str],
) -> dict[str, str | None]:
    return {
        task.id: resolved_model_profile(task, agents[task.agent], model_to_profile=model_to_profile)
        for task in plan.tasks
    }


def effective_plan_models(
    plan: Any,
    agents: dict[str, Any],
    *,
    model_to_profile: dict[str, str],
    model_profile_to_model: dict[str, str],
) -> dict[str, str | None]:
    return {
        task.id: resolved_model(
            task,
            agents[task.agent],
            model_to_profile=model_to_profile,
            model_profile_to_model=model_profile_to_model,
        )
        for task in plan.tasks
    }


def effective_plan_efforts(
    plan: Any,
    agents: dict[str, Any],
    *,
    run_override: str | None = None,
) -> dict[str, str | None]:
    return {
        task.id: resolve_effort(task, agents[task.agent], run_override=run_override)[0]
        for task in plan.tasks
    }


def effective_plan_effort_sources(
    plan: Any,
    agents: dict[str, Any],
    *,
    run_override: str | None = None,
) -> dict[str, str]:
    return {
        task.id: resolve_effort(task, agents[task.agent], run_override=run_override)[1]
        for task in plan.tasks
    }


def complex_model_task_ids(task_model_profiles: dict[str, str | None]) -> list[str]:
    return [task_id for task_id, profile in task_model_profiles.items() if profile == "complex"]


def dependency_handoff(record: Any, *, deps: dict[str, Any]) -> str:
    summary, _ = deps["truncate_text"](record.summary, deps["default_dependency_summary_char_limit"])
    parts = [f"`{record.id}` ({record.status}): {summary}"]
    parts.append(f"context: `{record.branch_context_id}`")
    notes = deps["dedupe_strings"](record.notes)
    if notes:
        visible_notes: list[str] = []
        for note in notes[:deps["default_dependency_detail_item_limit"]]:
            note_text, _ = deps["truncate_text"](note, deps["default_dependency_detail_char_limit"])
            visible_notes.append(note_text)
        hidden_note_count = len(notes) - len(visible_notes)
        if hidden_note_count > 0:
            visible_notes.append(f"... ({hidden_note_count} more notes omitted)")
        parts.append("key notes: " + " ; ".join(visible_notes))
    elif deps["worker_field_unknown"](record, "notes"):
        parts.append("key notes: unknown")
    follow_ups = deps["dedupe_strings"](record.follow_ups)
    if follow_ups:
        visible_follow_ups: list[str] = []
        for follow_up in follow_ups[:1]:
            follow_up_text, _ = deps["truncate_text"](follow_up, deps["default_dependency_detail_char_limit"])
            visible_follow_ups.append(follow_up_text)
        hidden_follow_up_count = len(follow_ups) - len(visible_follow_ups)
        if hidden_follow_up_count > 0:
            visible_follow_ups.append(f"... ({hidden_follow_up_count} more follow-ups omitted)")
        parts.append("next: " + " ; ".join(visible_follow_ups))
    elif deps["worker_field_unknown"](record, "followUps"):
        parts.append("next: unknown")
    return " | ".join(parts)


def dependency_summary(records: dict[str, Any], task: Any, *, deps: dict[str, Any]) -> str:
    if not task.depends_on:
        return "- none"
    include_review_context = task.agent == deps["reviewer_agent_name"]
    blocks: list[str] = []
    for dependency_id in task.depends_on:
        record = records[dependency_id]
        lines = [f"- {dependency_handoff(record, deps=deps)}"]
        if include_review_context:
            lines.extend(deps["dependency_review_context"](record))
        blocks.append("\n".join(lines))
    return "\n".join(blocks)


def worker_prompt(
    plan: Any,
    task: Any,
    agent: Any,
    workspace_mode: str,
    workspace_path,
    dependency_text: str,
    *,
    dependency_materialization_mode: str,
    dependency_layers_applied: list[Any] | None,
    dry_run: bool,
    worker_validation_mode: str,
    deps: dict[str, Any],
) -> Any:
    context_mode = effective_context_mode(task, agent, default_context_mode=deps["default_context_mode"])
    dependency_materialization_mode = deps["normalize_dependency_materialization_mode"](
        dependency_materialization_mode,
        location=f"task '{task.id}' dependency materialization mode",
    ) or deps["default_dependency_materialization_mode"]
    dependency_layers_applied = list(dependency_layers_applied or [])
    worker_validation_mode = deps["normalize_worker_validation_mode"](
        worker_validation_mode,
        location=f"task '{task.id}' worker validation mode",
    )
    _ = worker_validation_mode
    read_paths = deps["prompt_task_read_paths"](plan, task, context_mode=context_mode)
    write_paths = deps["effective_task_write_scope"](task)
    constraints = deps["dedupe_strings"](plan.shared_context.constraints + task.constraints)
    validation_hints = (
        deps["dedupe_strings"](task.validation)
        if context_mode == "minimal"
        else deps["dedupe_strings"](plan.shared_context.validation + task.validation)
    )
    workspace_rule = {
        "copy": "Isolated sparse filesystem copy seeded from declared read context and existing write-scope files. Undeclared repo files are unavailable. Edit only inside this copy.",
        "repo": "Live repo root. Treat this as high-risk and avoid incidental edits.",
        "worktree": "Detached git worktree rooted at HEAD. Root-repo uncommitted changes are not present.",
    }[workspace_mode]
    read_path_lines, read_path_count, read_paths_truncated = deps["format_bullet_list"](
        read_paths,
        empty_line="- none",
        code_format=True,
    )
    write_path_lines, write_path_count, write_paths_truncated = deps["format_bullet_list"](
        write_paths,
        empty_line="- read-only task; do not make edits",
        code_format=True,
    )
    constraint_lines, constraint_count, constraints_truncated = deps["format_bullet_list"](
        constraints,
        empty_line="- none",
    )
    rendered_validation_hints: list[str] = []
    for hint in validation_hints:
        policy = deps["validation_command_policy"](hint)
        intent = policy.get("intent") if isinstance(policy, dict) else None
        if policy.get("accepted") and isinstance(intent, dict):
            kind = str(intent.get("kind", "")).strip()
            entrypoint = str(intent.get("entrypoint", "")).strip()
            if kind and entrypoint:
                rendered_validation_hints.append(
                    f"`{hint}` -> `{kind}` `{entrypoint}` (mirror exactly; keep args unchanged)"
                )
                continue
        rendered_validation_hints.append(f"`{hint}`")
    validation_lines, validation_count, validation_truncated = deps["format_bullet_list"](
        rendered_validation_hints,
        empty_line="- none",
        code_format=False,
    )
    materialization_items = [
        f"{layer.task_id} ({len(layer.operations)} ops from {layer.workspace_mode})"
        for layer in dependency_layers_applied
    ]
    if dependency_materialization_mode == "summary-only":
        materialization_lines = "Mode: `summary-only`\nApplied layers: none"
        materialization_count = 0
        materialization_truncated = False
    else:
        layer_lines, layer_count, layers_truncated = deps["format_bullet_list"](
            materialization_items,
            empty_line=(
                "- none applied yet in this dry-run; live execution will materialize reviewed "
                "dependency state before the worker runs"
                if dry_run
                else "- none"
            ),
        )
        materialization_lines = "Mode: `apply-reviewed`\nApplied layers:\n" + layer_lines
        materialization_count = layer_count
        materialization_truncated = layers_truncated
    worker_rules, rule_count, rules_truncated = deps["format_bullet_list"](
        [
            "Treat this prompt plus the declared workspace as the full contract. Do not assume hidden coordinator memory or undeclared repo files.",
            "Use only this workspace plus the declared `readPaths` and `writePaths`.",
            "In copy/worktree mode, do not inspect the source repo root, other task workspaces, or prior run artifacts.",
            "Treat `writePaths` as the edit contract. Dependency outputs are the coordinator handoff.",
            "If dependency layers are materialized, workspace state overrides prompt summaries for those upstream files.",
            "When parameter defaults, range handling, or normalization change, record the exact rule in `notes`.",
            "For parameter-behavior tests, derive assertions from the implementation and written contract. If behavior is ambiguous, return `blocked`.",
            "If user-visible example or API behavior changes, update matching README/docs in scope or call out the missing doc work in `followUps`.",
            "If validation hints already show an approved command, mirror that exact entrypoint and args in `validationIntents`. Do not swap `mvn` and `mvnw` or invent alternate wrappers.",
            "Suggest only coordinator-accepted validation shapes: `repo-script` for `scripts/...` or `mvnw(.cmd)`, `tool` for approved executables like `git`, `java`, `mvn`, `py`, or `pytest`.",
            "If no approved validation shape fits, emit `[]`. Do not invent scripts or use `grep`, `findstr`, or shell fragments.",
            "State blockers explicitly, and do not claim edits or validation you did not actually perform.",
            "Do not edit `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*`. Keep changes tightly scoped to the task.",
        ],
        empty_line="- none",
        max_items=16,
    )
    return deps["render_prompt"](
        [
            deps["prompt_section_factory"](name="coordinator_goal", heading="Coordinator goal", body=plan.goal),
            deps["prompt_section_factory"](
                name="shared_summary",
                heading="Shared context summary",
                body=plan.shared_context.summary,
            ),
            deps["prompt_section_factory"](
                name="worker_rules",
                heading="Worker rules",
                body=worker_rules,
                item_count=rule_count,
                truncated=rules_truncated,
            ),
            deps["prompt_section_factory"](name="task_identity", heading="Task id/title", body=f"{task.id} / {task.title}"),
            deps["prompt_section_factory"](name="task_objective", heading="Task objective", body=task.prompt),
            deps["prompt_section_factory"](
                name="read_paths",
                heading="Read context",
                body=read_path_lines,
                item_count=read_path_count,
                truncated=read_paths_truncated,
            ),
            deps["prompt_section_factory"](
                name="write_paths",
                heading="Write scope",
                body=write_path_lines,
                item_count=write_path_count,
                truncated=write_paths_truncated,
            ),
            deps["prompt_section_factory"](
                name="constraints",
                heading="Constraints",
                body=constraint_lines,
                item_count=constraint_count,
                truncated=constraints_truncated,
            ),
            deps["prompt_section_factory"](
                name="dependency_outputs",
                heading="Dependency outputs",
                body=dependency_text,
                item_count=len(task.depends_on),
            ),
            deps["prompt_section_factory"](
                name="dependency_materialization",
                heading="Dependency materialization",
                body=materialization_lines,
                item_count=materialization_count,
                truncated=materialization_truncated,
            ),
            deps["prompt_section_factory"](
                name="validation_hints",
                heading="Validation hints",
                body=validation_lines,
                item_count=validation_count,
                truncated=validation_truncated,
            ),
            deps["prompt_section_factory"](
                name="execution_context",
                heading="Execution context",
                body=textwrap.dedent(
                    f"""\
                    Current working directory: {(
                        "live repo root"
                        if workspace_mode == "repo"
                        else "isolated task workspace"
                        if workspace_mode == "copy"
                        else "detached worktree"
                    )}
                    Workspace mode: {workspace_mode}
                    Context mode: {context_mode}
                    Contract: {workspace_rule}
                    """
                ).strip(),
            ),
        ]
    )


def worker_result_schema(worker_validation_mode: str, *, normalize_worker_validation_mode, worker_result_schema_payload: dict[str, Any]) -> dict[str, Any]:
    normalize_worker_validation_mode(
        worker_validation_mode,
        location="worker result schema",
    )
    return copy.deepcopy(worker_result_schema_payload)


def task_output_schema_json(
    worker_validation_mode: str,
    *,
    normalize_worker_validation_mode,
    worker_result_schema_payload: dict[str, Any],
) -> str:
    return json.dumps(
        worker_result_schema(
            worker_validation_mode,
            normalize_worker_validation_mode=normalize_worker_validation_mode,
            worker_result_schema_payload=worker_result_schema_payload,
        ),
        separators=(",", ":"),
    )


def plan_output_schema_json(plan_result_schema_payload: dict[str, Any]) -> str:
    return json.dumps(plan_result_schema_payload, separators=(",", ":"))
