#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import textwrap
from dataclasses import asdict
from pathlib import Path
from typing import Any

from pojo_lens_agents import governance as governance_layer
from pojo_lens_agents import planner_ops as planner_ops_layer
from pojo_lens_agents import prompt_contracts as prompt_contracts_layer
from pojo_lens_agents import provider as provider_layer
from pojo_lens_agents import worker_contracts as worker_contracts_layer
from pojo_lens_agents.orchestrator_contracts import (
    DEFAULT_CONTEXT_MODE,
    DEFAULT_OUTPUT_PROFILE,
    DEFAULT_TASKS_DIR,
    DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT,
    DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT,
    DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT,
    DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
    DEFAULT_PROMPT_SECTION_ITEM_LIMIT,
    DEFAULT_TASK_TIMEOUT_SEC,
    DEFAULT_WORKER_VALIDATION_MODE,
    MAX_RUN_SUMMARY_TOP_TASKS,
    MAX_WORKER_FINDINGS,
    MAX_WORKER_FINDING_MESSAGE_CHARS,
    MAX_WORKER_FOLLOW_UPS,
    MAX_WORKER_FOLLOW_UP_CHARS,
    MAX_WORKER_NOTES,
    MAX_WORKER_NOTE_CHARS,
    MAX_WORKER_SUMMARY_CHARS,
    MAX_WORKER_VALIDATION_COMMANDS,
    MAX_WORKER_VALIDATION_COMMAND_CHARS,
    MAX_WORKER_VALIDATION_INTENTS,
    MAX_WORKER_VALIDATION_INTENT_ARG_CHARS,
    LEAN_MAX_WORKER_SUMMARY_CHARS,
    LEAN_MAX_WORKER_NOTES,
    LEAN_MAX_WORKER_NOTE_CHARS,
    LEAN_MAX_WORKER_FOLLOW_UPS,
    LEAN_MAX_WORKER_FOLLOW_UP_CHARS,
    LEAN_MAX_WORKER_VALIDATION_INTENTS,
    LEAN_MAX_WORKER_VALIDATION_INTENT_ARG_CHARS,
    MODEL_PROFILE_TO_MODEL,
    MODEL_TO_PROFILE,
    OrchestratorError,
    PLAN_RESULT_SCHEMA,
    PLANNER_TASK_ID,
    PromptBudgetResult,
    PromptRenderResult,
    PromptSection,
    PromptSectionMetric,
    ReviewFinding,
    REVIEWER_AGENT_NAME,
    REVIEWER_FINDING_SEVERITIES,
    ROOT,
    SLOP_LOG_LOCK,
    SLOP_PROGRESS_INTERVAL_SEC,
    SLOP_PROGRESS_DOTS,
    TaskDefinition,
    TaskPlan,
    TaskRunRecord,
    ValidationIntent,
    VALIDATION_ALLOWED_EXECUTABLES,
    VALIDATION_ALLOWED_RELATIVE_PREFIXES,
    VALIDATION_ALLOWED_SCRIPT_SUFFIXES,
    VALIDATION_INTENT_KINDS,
    WORKER_RESULT_SCHEMA,
    WORKER_STATUSES,
    WORKER_UNKNOWNABLE_FIELDS,
    AgentDefinition,
    DependencyLayerRecord,
    SlopLogAction,
    RunPolicy,
)
from pojo_lens_agents.orchestrator_utils import compact_text, dedupe_strings, emit_slop_log, estimate_tokens, format_bullet_list, planner_wait_action, render_prompt, slop_log_action, slugify, task_wait_action, truncate_multiline_text, truncate_text, validation_wait_action, workspace_prep_action, write_json
from pojo_lens_agents.plan_support import agent_payload_for_claude, effective_task_write_scope, load_agents, normalize_dependency_materialization_mode, normalize_relative_path, normalize_worker_validation_mode, prompt_task_read_paths, resolve_output_profile, serialize_run_policy
from pojo_lens_agents.workspace_run_review import dependency_review_context


def current_root() -> Path:
    app = sys.modules.get("pojo_lens_agents.orchestrator_app")
    return Path(getattr(app, "ROOT", ROOT))


def ensure_claude_available(claude_bin: str) -> None:
    if shutil.which(claude_bin) is None:
        raise OrchestratorError(f"Claude CLI '{claude_bin}' is not available on PATH")


def ensure_provider_available(claude_bin: str, provider_mode: str = "subprocess") -> None:
    """Check that the selected provider is usable before starting a run."""
    if provider_mode == "sdk":
        from pojo_lens_agents.sdk_provider import sdk_available
        import os
        if not sdk_available():
            raise OrchestratorError(
                "SDK provider selected but anthropic package is not installed. "
                "Run: pip install 'pojolens-agents[sdk]'"
            )
        if not os.environ.get("ANTHROPIC_API_KEY"):
            raise OrchestratorError(
                "SDK provider selected but ANTHROPIC_API_KEY is not set."
            )
    else:
        ensure_claude_available(claude_bin)
def planner_output_path(name: str, explicit_path: str) -> Path:
    return planner_ops_layer.planner_output_path(
        name,
        explicit_path,
        default_tasks_dir=DEFAULT_TASKS_DIR,
        slugify=slugify,
    )


def planner_prompt(
    goal: str,
    name: str,
    files: list[str],
    constraints: list[str],
    validation: list[str],
    agents: dict[str, AgentDefinition],
) -> PromptRenderResult:
    return planner_ops_layer.planner_prompt(
        goal,
        name,
        files,
        constraints,
        validation,
        agents,
        planner_task_id=PLANNER_TASK_ID,
        default_prompt_item_char_limit=DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
        format_bullet_list=format_bullet_list,
        prompt_section_factory=PromptSection,
        render_prompt=render_prompt,
    )


def dedupe_strings(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def estimate_tokens(text: str) -> int:
    return max(1, (len(text) + 3) // 4)


def compact_text(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def truncate_text(text: str, max_chars: int | None) -> tuple[str, bool]:
    compacted = compact_text(text)
    if max_chars is None or len(compacted) <= max_chars:
        return compacted, False
    if max_chars <= 3:
        return compacted[:max_chars], True
    return compacted[: max_chars - 3].rstrip() + "...", True


def truncate_multiline_text(text: str, max_chars: int | None) -> tuple[str, bool]:
    stripped = text.strip()
    if max_chars is None or len(stripped) <= max_chars:
        return stripped, False
    if max_chars <= 3:
        return stripped[:max_chars], True
    return stripped[: max_chars - 3].rstrip() + "...", True


def format_bullet_list(
    values: list[str],
    *,
    empty_line: str,
    code_format: bool = False,
    max_items: int = DEFAULT_PROMPT_SECTION_ITEM_LIMIT,
    max_chars: int | None = DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
) -> tuple[str, int, bool]:
    if not values:
        return empty_line, 0, False
    truncated = False
    visible_values = values[:max_items] if max_items > 0 else values
    lines: list[str] = []
    for value in visible_values:
        item_text, item_truncated = truncate_text(value, max_chars)
        if code_format:
            item_text = f"`{item_text}`"
        lines.append(f"- {item_text}")
        truncated = truncated or item_truncated
    hidden_count = len(values) - len(visible_values)
    if hidden_count > 0:
        lines.append(f"- ... ({hidden_count} more omitted)")
        truncated = True
    return "\n".join(lines), len(values), truncated


def render_prompt(sections: list[PromptSection]) -> PromptRenderResult:
    rendered_sections: list[str] = []
    metrics: list[PromptSectionMetric] = []
    for section in sections:
        rendered = f"{section.heading}:\n{section.body}"
        rendered_sections.append(rendered)
        metrics.append(
            PromptSectionMetric(
                name=section.name,
                heading=section.heading,
                chars=len(rendered),
                estimated_tokens=estimate_tokens(rendered),
                item_count=section.item_count,
                truncated=section.truncated,
            )
        )
    prompt = "\n\n".join(rendered_sections)
    return PromptRenderResult(
        text=prompt,
        sections=metrics,
        chars=len(prompt),
        estimated_tokens=estimate_tokens(prompt),
    )


def evaluate_prompt_budget(
    *,
    prompt_chars: int,
    prompt_estimated_tokens: int,
    max_chars: int | None,
    max_estimated_tokens: int | None,
) -> PromptBudgetResult:
    return prompt_contracts_layer.evaluate_prompt_budget(
        prompt_chars=prompt_chars,
        prompt_estimated_tokens=prompt_estimated_tokens,
        max_chars=max_chars,
        max_estimated_tokens=max_estimated_tokens,
        prompt_budget_result_factory=PromptBudgetResult,
    )


def prompt_budget_failure_summary(result: PromptBudgetResult) -> str:
    return prompt_contracts_layer.prompt_budget_failure_summary(result)


def resolved_max_prompt_chars(task: TaskDefinition | None, agent: AgentDefinition) -> int | None:
    return prompt_contracts_layer.resolved_max_prompt_chars(task, agent)


def resolved_max_prompt_estimated_tokens(task: TaskDefinition | None, agent: AgentDefinition) -> int | None:
    return prompt_contracts_layer.resolved_max_prompt_estimated_tokens(task, agent)


def effective_context_mode(task: TaskDefinition, agent: AgentDefinition) -> str:
    return prompt_contracts_layer.effective_context_mode(
        task,
        agent,
        default_context_mode=DEFAULT_CONTEXT_MODE,
    )


def effective_dependency_materialization_mode(task: TaskDefinition) -> str:
    return prompt_contracts_layer.effective_dependency_materialization_mode(
        task,
        default_mode=DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    )


def resolved_model_profile(task: TaskDefinition, agent: AgentDefinition) -> str | None:
    return prompt_contracts_layer.resolved_model_profile(
        task,
        agent,
        model_to_profile=MODEL_TO_PROFILE,
    )


def resolved_model(task: TaskDefinition, agent: AgentDefinition) -> str | None:
    return prompt_contracts_layer.resolved_model(
        task,
        agent,
        model_to_profile=MODEL_TO_PROFILE,
        model_profile_to_model=MODEL_PROFILE_TO_MODEL,
    )


def normalize_effort_override(value: str | None, *, location: str) -> str | None:
    return prompt_contracts_layer.normalize_effort_override(
        value,
        location=location,
        error_factory=OrchestratorError,
    )


def resolve_effort(
    task: TaskDefinition | None,
    agent: AgentDefinition,
    *,
    run_override: str | None = None,
) -> tuple[str | None, str]:
    return prompt_contracts_layer.resolve_effort(
        task,
        agent,
        run_override=run_override,
    )


def effective_plan_model_profiles(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str | None]:
    return prompt_contracts_layer.effective_plan_model_profiles(
        plan,
        agents,
        model_to_profile=MODEL_TO_PROFILE,
    )


def effective_plan_models(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str | None]:
    return prompt_contracts_layer.effective_plan_models(
        plan,
        agents,
        model_to_profile=MODEL_TO_PROFILE,
        model_profile_to_model=MODEL_PROFILE_TO_MODEL,
    )


def effective_plan_efforts(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str | None]:
    return prompt_contracts_layer.effective_plan_efforts(
        plan,
        agents,
        run_override=run_override,
    )


def effective_plan_effort_sources(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str]:
    return prompt_contracts_layer.effective_plan_effort_sources(
        plan,
        agents,
        run_override=run_override,
    )


def complex_model_task_ids(task_model_profiles: dict[str, str | None]) -> list[str]:
    return prompt_contracts_layer.complex_model_task_ids(task_model_profiles)


def dependency_handoff(record: TaskRunRecord) -> str:
    return prompt_contracts_layer.dependency_handoff(
        record,
        deps={
            "truncate_text": truncate_text,
            "dedupe_strings": dedupe_strings,
            "worker_field_unknown": worker_field_unknown,
            "default_dependency_summary_char_limit": DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT,
            "default_dependency_detail_item_limit": DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT,
            "default_dependency_detail_char_limit": DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT,
        },
    )


def dependency_summary(records: dict[str, TaskRunRecord], task: TaskDefinition) -> str:
    return prompt_contracts_layer.dependency_summary(
        records,
        task,
        deps={
            "reviewer_agent_name": REVIEWER_AGENT_NAME,
            "dependency_review_context": dependency_review_context,
            "truncate_text": truncate_text,
            "dedupe_strings": dedupe_strings,
            "worker_field_unknown": worker_field_unknown,
            "default_dependency_summary_char_limit": DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT,
            "default_dependency_detail_item_limit": DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT,
            "default_dependency_detail_char_limit": DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT,
        },
    )


def truncate_command_text(text: str, max_chars: int) -> tuple[str, bool]:
    return worker_contracts_layer.truncate_command_text(text, max_chars)


def strip_optional_quotes(token: str) -> str:
    return worker_contracts_layer.strip_optional_quotes(token)


def contains_unquoted_shell_operator(text: str) -> bool:
    return worker_contracts_layer.contains_unquoted_shell_operator(text)


def normalize_worker_text_list(
    payload: Any,
    *,
    key: str,
    max_items: int,
    max_chars: int,
    compact: bool,
) -> tuple[list[str], bool]:
    return worker_contracts_layer.normalize_worker_text_list(
        payload,
        key=key,
        max_items=max_items,
        max_chars=max_chars,
        compact=compact,
        truncate_text=truncate_text,
        error_factory=OrchestratorError,
    )


def normalize_worker_findings(payload: Any) -> list[Any]:
    return worker_contracts_layer.normalize_worker_findings(
        payload,
        max_items=MAX_WORKER_FINDINGS,
        max_chars=MAX_WORKER_FINDING_MESSAGE_CHARS,
        reviewer_finding_severities=REVIEWER_FINDING_SEVERITIES,
        reviewer_finding_factory=ReviewFinding,
        truncate_text=truncate_text,
        error_factory=OrchestratorError,
    )


def normalize_worker_files_touched(payload: Any) -> tuple[list[str], bool]:
    return worker_contracts_layer.normalize_worker_files_touched(
        payload,
        normalize_relative_path=normalize_relative_path,
        error_factory=OrchestratorError,
    )


def normalized_worker_unknown_fields(payload: Any) -> list[str]:
    return worker_contracts_layer.normalized_worker_unknown_fields(
        payload,
        worker_unknownable_fields=list(WORKER_UNKNOWNABLE_FIELDS),
    )


def worker_field_unknown(record: TaskRunRecord, field_name: str) -> bool:
    return worker_contracts_layer.worker_field_unknown(record, field_name)


def analyze_validation_command(command_text: str) -> dict[str, Any]:
    return worker_contracts_layer.analyze_validation_command(
        command_text,
        validation_allowed_executables=VALIDATION_ALLOWED_EXECUTABLES,
        validation_allowed_script_suffixes=VALIDATION_ALLOWED_SCRIPT_SUFFIXES,
        validation_allowed_relative_prefixes=VALIDATION_ALLOWED_RELATIVE_PREFIXES,
        validation_intent_factory=ValidationIntent,
        normalize_relative_path=normalize_relative_path,
        error_factory=OrchestratorError,
        asdict=asdict,
    )


def coerce_validation_intent_payload(payload: Any, *, location: str) -> ValidationIntent:
    return worker_contracts_layer.coerce_validation_intent_payload(
        payload,
        location=location,
        validation_intent_kinds=VALIDATION_INTENT_KINDS,
        max_worker_validation_intent_arg_chars=MAX_WORKER_VALIDATION_INTENT_ARG_CHARS,
        validation_intent_factory=ValidationIntent,
        normalize_relative_path=normalize_relative_path,
        truncate_text=truncate_text,
        error_factory=OrchestratorError,
    )


def normalize_worker_validation_intents(
    payload: Any,
    *,
    max_worker_validation_intents: int = MAX_WORKER_VALIDATION_INTENTS,
) -> list[ValidationIntent]:
    return worker_contracts_layer.normalize_worker_validation_intents(
        payload,
        max_worker_validation_intents=max_worker_validation_intents,
        coerce_validation_intent_payload=coerce_validation_intent_payload,
        error_factory=OrchestratorError,
    )


def validation_intent_policy(intent: ValidationIntent) -> dict[str, Any]:
    return worker_contracts_layer.validation_intent_policy(
        intent,
        validation_allowed_executables=VALIDATION_ALLOWED_EXECUTABLES,
        validation_allowed_script_suffixes=VALIDATION_ALLOWED_SCRIPT_SUFFIXES,
        validation_allowed_relative_prefixes=VALIDATION_ALLOWED_RELATIVE_PREFIXES,
    )


def render_command_tokens(tokens: list[str]) -> str:
    return worker_contracts_layer.render_command_tokens(tokens)


def validation_intent_command_text(intent: ValidationIntent) -> str:
    return worker_contracts_layer.validation_intent_command_text(intent)


def validation_intent_execution_tokens(intent: ValidationIntent) -> list[str]:
    return worker_contracts_layer.validation_intent_execution_tokens(intent, root=current_root())


def validation_command_policy(command_text: str) -> dict[str, Any]:
    return worker_contracts_layer.validation_command_policy(
        command_text,
        analyze_validation_command=analyze_validation_command,
    )


def worker_prompt(
    plan: TaskPlan,
    task: TaskDefinition,
    agent: AgentDefinition,
    workspace_mode: str,
    workspace_path: Path,
    dependency_text: str,
    *,
    dependency_materialization_mode: str = DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    dependency_layers_applied: list[DependencyLayerRecord] | None = None,
    dry_run: bool = False,
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE,
) -> PromptRenderResult:
    return prompt_contracts_layer.worker_prompt(
        plan,
        task,
        agent,
        workspace_mode,
        workspace_path,
        dependency_text,
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=dependency_layers_applied,
        dry_run=dry_run,
        worker_validation_mode=worker_validation_mode,
        deps={
            "default_context_mode": DEFAULT_CONTEXT_MODE,
            "default_dependency_materialization_mode": DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
            "normalize_dependency_materialization_mode": normalize_dependency_materialization_mode,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "resolve_output_profile": resolve_output_profile,
            "prompt_task_read_paths": prompt_task_read_paths,
            "effective_task_write_scope": effective_task_write_scope,
            "dedupe_strings": dedupe_strings,
            "format_bullet_list": format_bullet_list,
            "validation_command_policy": validation_command_policy,
            "render_prompt": render_prompt,
            "prompt_section_factory": PromptSection,
        },
    )


def worker_result_schema(worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE) -> dict[str, Any]:
    return prompt_contracts_layer.worker_result_schema(
        worker_validation_mode,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        worker_result_schema_payload=WORKER_RESULT_SCHEMA,
    )


def task_output_schema_json(worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE) -> str:
    return prompt_contracts_layer.task_output_schema_json(
        worker_validation_mode,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        worker_result_schema_payload=WORKER_RESULT_SCHEMA,
    )


def plan_output_schema_json() -> str:
    return prompt_contracts_layer.plan_output_schema_json(PLAN_RESULT_SCHEMA)


def claude_command(
    claude_bin: str,
    agents_json: str,
    agent_name: str,
    prompt: str,
    schema_json: str,
    *,
    model: str | None,
    effort: str | None,
    permission_mode: str | None,
    allowed_tools: list[str],
    disallowed_tools: list[str],
    max_budget_usd: float | None,
) -> list[str]:
    command = [
        claude_bin,
        "-p",
        "--no-session-persistence",
        "--output-format",
        "json",
        "--json-schema",
        schema_json,
        "--agents",
        agents_json,
        "--agent",
        agent_name,
    ]
    if model:
        command.extend(["--model", model])
    if effort:
        command.extend(["--effort", effort])
    if permission_mode:
        command.extend(["--permission-mode", permission_mode])
    if allowed_tools:
        command.extend(["--allowed-tools", ",".join(allowed_tools)])
    if disallowed_tools:
        command.extend(["--disallowed-tools", ",".join(disallowed_tools)])
    if max_budget_usd is not None:
        command.extend(["--max-budget-usd", f"{max_budget_usd:.2f}"])
    command.extend(["--", prompt])
    return command


def extract_json_payload(text: str) -> Any:
    return provider_layer.extract_json_payload(
        text,
        error_factory=OrchestratorError,
    )


def coerce_worker_result(
    payload: Any,
    *,
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE,
    output_profile: str = DEFAULT_OUTPUT_PROFILE,
) -> dict[str, Any]:
    return worker_contracts_layer.coerce_worker_result(
        payload,
        worker_validation_mode=worker_validation_mode,
        output_profile=output_profile,
        worker_statuses=WORKER_STATUSES,
        max_worker_summary_chars=MAX_WORKER_SUMMARY_CHARS,
        max_worker_validation_commands=MAX_WORKER_VALIDATION_COMMANDS,
        max_worker_validation_command_chars=MAX_WORKER_VALIDATION_COMMAND_CHARS,
        max_worker_follow_ups=MAX_WORKER_FOLLOW_UPS,
        max_worker_follow_up_chars=MAX_WORKER_FOLLOW_UP_CHARS,
        max_worker_notes=MAX_WORKER_NOTES,
        max_worker_note_chars=MAX_WORKER_NOTE_CHARS,
        max_worker_validation_intents=MAX_WORKER_VALIDATION_INTENTS,
        max_worker_validation_intent_arg_chars=MAX_WORKER_VALIDATION_INTENT_ARG_CHARS,
        lean_max_worker_summary_chars=LEAN_MAX_WORKER_SUMMARY_CHARS,
        lean_max_worker_follow_ups=LEAN_MAX_WORKER_FOLLOW_UPS,
        lean_max_worker_follow_up_chars=LEAN_MAX_WORKER_FOLLOW_UP_CHARS,
        lean_max_worker_notes=LEAN_MAX_WORKER_NOTES,
        lean_max_worker_note_chars=LEAN_MAX_WORKER_NOTE_CHARS,
        lean_max_worker_validation_intents=LEAN_MAX_WORKER_VALIDATION_INTENTS,
        lean_max_worker_validation_intent_arg_chars=LEAN_MAX_WORKER_VALIDATION_INTENT_ARG_CHARS,
        worker_output_limits=prompt_contracts_layer.worker_output_limits,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        normalize_worker_files_touched=normalize_worker_files_touched,
        normalize_worker_text_list=normalize_worker_text_list,
        normalize_worker_validation_intents=normalize_worker_validation_intents,
        normalize_worker_findings=normalize_worker_findings,
        truncate_text=truncate_text,
        asdict=asdict,
        error_factory=OrchestratorError,
    )


def extract_usage(payload: Any) -> dict[str, Any] | None:
    return provider_layer.extract_usage(payload)


def aggregate_usage(records: dict[str, TaskRunRecord]) -> dict[str, Any]:
    return governance_layer.aggregate_usage(records)


def aggregate_artifacts(records: dict[str, TaskRunRecord]) -> dict[str, Any]:
    return governance_layer.aggregate_artifacts(
        records,
        top_task_limit=MAX_RUN_SUMMARY_TOP_TASKS,
    )


def evaluate_run_governance(
    records: dict[str, TaskRunRecord],
    run_policy: RunPolicy,
) -> dict[str, Any]:
    return governance_layer.evaluate_run_governance(
        records,
        run_policy,
        serialize_run_policy=serialize_run_policy,
        top_task_limit=MAX_RUN_SUMMARY_TOP_TASKS,
    )


def slop_log_action(actor: str, phase: str, phrase: str, reason: str) -> SlopLogAction:
    normalized_phase = phase.strip().upper()
    if not normalized_phase:
        raise OrchestratorError("Slop log phase must not be empty")
    return SlopLogAction(
        actor=actor.strip().upper() or "AGENT",
        phase=normalized_phase,
        phrase=phrase.strip(),
        reason=reason.strip(),
    )


def emit_slop_log(action: SlopLogAction, *, frame: int | None = None) -> None:
    if not getattr(sys.stderr, "isatty", lambda: False)():
        return
    dots = ""
    if frame is not None:
        dots = f" {SLOP_PROGRESS_DOTS[frame % len(SLOP_PROGRESS_DOTS)]}"
    reason = f" ({action.reason})" if action.reason else ""
    with SLOP_LOG_LOCK:
        print(
            f"[{action.actor}][{action.phase}] {action.phrase}{dots}{reason}",
            file=sys.stderr,
            flush=True,
        )


def planner_wait_action(agent_name: str) -> SlopLogAction:
    return slop_log_action("planner", "PROCESS", "Slopchurning", f"waiting for {agent_name}")


def task_wait_action(task: TaskDefinition) -> SlopLogAction:
    return slop_log_action(task.id, "FLOW", "Slopsloshing", f"waiting for {task.agent}")


def workspace_prep_action(task: TaskDefinition, workspace_mode: str) -> SlopLogAction | None:
    if workspace_mode == "copy":
        return slop_log_action(task.id, "INGEST", "Slurping up slop", "hydrating copy workspace")
    if workspace_mode == "worktree":
        return slop_log_action(task.id, "INGEST", "Slophoovering", "creating detached worktree")
    return None


def validation_wait_action(command_text: str, source_kind: str) -> SlopLogAction:
    summarized = textwrap.shorten(" ".join(command_text.split()), width=88, placeholder="...")
    if source_kind == "intent":
        reason = f"running validation intent {summarized}"
        phrase = "Slopcessing"
    else:
        reason = f"running validation command {summarized}"
        phrase = "Slopcrunching"
    return slop_log_action("validate-run", "PROCESS", phrase, reason)


def coerce_plan_result(payload: Any) -> dict[str, Any]:
    return planner_ops_layer.coerce_plan_result(payload, error_factory=OrchestratorError)


def run_process(
    command: list[str] | str,
    *,
    cwd: Path,
    timeout_sec: int,
    shell: bool,
    progress_action: SlopLogAction | None = None,
    timeout_error: str,
) -> subprocess.CompletedProcess[str]:
    if progress_action is not None:
        emit_slop_log(progress_action, frame=1)
    return provider_layer.run_process(
        command,
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=shell,
        timeout_error=timeout_error,
        on_progress_frame=(
            (lambda frame: emit_slop_log(progress_action, frame=frame))
            if progress_action is not None
            else None
        ),
        progress_interval_sec=min(SLOP_PROGRESS_INTERVAL_SEC, 0.5),
        error_factory=OrchestratorError,
    )


def run_subprocess(
    command: list[str],
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    if progress_action is not None:
        emit_slop_log(progress_action, frame=1)
    return provider_layer.run_subprocess(
        command,
        cwd=cwd,
        timeout_sec=timeout_sec,
        timeout_error=f"Claude timed out after {timeout_sec} seconds",
        on_progress_frame=(
            (lambda frame: emit_slop_log(progress_action, frame=frame))
            if progress_action is not None
            else None
        ),
        progress_interval_sec=min(SLOP_PROGRESS_INTERVAL_SEC, 0.5),
        error_factory=OrchestratorError,
    )


def plan_with_claude(args: argparse.Namespace) -> dict[str, Any]:
    return planner_ops_layer.plan_with_claude(
        args,
        deps={
            "error_factory": OrchestratorError,
            "load_agents": load_agents,
            "ensure_claude_available": ensure_claude_available,
            "planner_task_id": PLANNER_TASK_ID,
            "default_prompt_item_char_limit": DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
            "format_bullet_list": format_bullet_list,
            "prompt_section_factory": PromptSection,
            "render_prompt": render_prompt,
            "normalize_effort_override": normalize_effort_override,
            "evaluate_prompt_budget": evaluate_prompt_budget,
            "model_profile_to_model": MODEL_PROFILE_TO_MODEL,
            "agent_payload_for_claude": agent_payload_for_claude,
            "default_tasks_dir": DEFAULT_TASKS_DIR,
            "slugify": slugify,
            "plan_output_schema_json": plan_output_schema_json,
            "claude_command": claude_command,
            "asdict": asdict,
            "prompt_budget_failure_summary": prompt_budget_failure_summary,
            "run_subprocess": run_subprocess,
            "planner_wait_action": planner_wait_action,
            "extract_json_payload": extract_json_payload,
            "write_json": write_json,
            "extract_usage": extract_usage,
            "root": current_root(),
        },
    )


