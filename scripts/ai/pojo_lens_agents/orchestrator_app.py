#!/usr/bin/env python3
from __future__ import annotations

import asyncio
import copy
import difflib
import importlib
import os
import shlex
import shutil
import subprocess
import sys
from dataclasses import replace
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[3]
SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

class _LazyModuleProxy:
    def __init__(self, module_name: str) -> None:
        self._module_name = module_name
        self._module: Any | None = None

    def _load(self) -> Any:
        if self._module is None:
            self._module = importlib.import_module(self._module_name)
        return self._module

    def __getattr__(self, name: str) -> Any:
        return getattr(self._load(), name)


config_loader_layer = _LazyModuleProxy("pojo_lens_agents.config_loader")
governance_layer = _LazyModuleProxy("pojo_lens_agents.governance")
cost_estimation_layer = _LazyModuleProxy("pojo_lens_agents.cost_estimation")
evals_layer = _LazyModuleProxy("pojo_lens_agents.evals")
hitl_layer = _LazyModuleProxy("pojo_lens_agents.hitl")
manifest_io_layer = _LazyModuleProxy("pojo_lens_agents.manifest_io")
retry_policy_layer = _LazyModuleProxy("pojo_lens_agents.retry_policy")
run_ledger_layer = _LazyModuleProxy("pojo_lens_agents.run_ledger")
task_fingerprint_layer = _LazyModuleProxy("pojo_lens_agents.task_fingerprint")
runtime_admin_layer = _LazyModuleProxy("pojo_lens_agents.runtime_admin")
run_ops_layer = _LazyModuleProxy("pojo_lens_agents.run_ops")
run_store_layer = _LazyModuleProxy("pojo_lens_agents.run_store")
sdk_provider_layer = _LazyModuleProxy("pojo_lens_agents.sdk_provider")
diff_run_layer = _LazyModuleProxy("pojo_lens_agents.diff_run")
trace_export_layer = _LazyModuleProxy("pojo_lens_agents.trace_export")
otel_layer = _LazyModuleProxy("pojo_lens_agents.otel_spans")
tui_layer = _LazyModuleProxy("pojo_lens_agents.tui_app")
wizard_layer = _LazyModuleProxy("pojo_lens_agents.wizard")
validate_cli_layer = _LazyModuleProxy("pojo_lens_agents.validate_cli")
validation_ops_layer = _LazyModuleProxy("pojo_lens_agents.validation_ops")

from pojo_lens_agents.cli_parser import parse_args
from pojo_lens_agents.command_dispatch import _worker_run_exit_code, dispatch_main
from pojo_lens_agents.orchestrator_contracts import *
from pojo_lens_agents.orchestrator_utils import *
from pojo_lens_agents.plan_support import *
from pojo_lens_agents.plan_support import _effective_tool_lists
from pojo_lens_agents.provider_worker import *
from pojo_lens_agents.workspace_run_review import *

def effective_workspace_mode(task: TaskDefinition, agent: AgentDefinition) -> str:
    return task_execution_layer.effective_workspace_mode(task, agent)


def default_workspaces_dir(*, runtime_root: Path, run_id: str) -> Path:
    return run_store_layer.default_workspaces_dir(
        runtime_root=runtime_root,
        run_id=run_id,
        repo_root=current_root(),
    )


def blocked_record(
    task: TaskDefinition,
    agent_name: str,
    agent: AgentDefinition,
    workspace_mode: str,
    *,
    reason: str,
    dependency_records: dict[str, TaskRunRecord] | None = None,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
) -> TaskRunRecord:
    return task_execution_layer.blocked_record(
        task,
        agent_name,
        agent,
        workspace_mode,
        reason=reason,
        dependency_records=dependency_records,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        deps={
            "iso_now": iso_now,
            "resolve_worker_validation_mode": resolve_worker_validation_mode,
            "resolve_output_profile": resolve_output_profile,
            "resolve_effort": resolve_effort,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "effective_task_skills": effective_task_skills,
            "task_run_record_factory": TaskRunRecord,
            "task_branch_context_id": task_branch_context_id,
            "task_branch_parent_context_ids": task_branch_parent_context_ids,
            "resolved_model": resolved_model,
            "resolved_model_profile": resolved_model_profile,
            "prompt_budget_result_factory": PromptBudgetResult,
            "resolved_max_prompt_chars": resolved_max_prompt_chars,
            "resolved_max_prompt_estimated_tokens": resolved_max_prompt_estimated_tokens,
        },
    )


def planned_record(
    task: TaskDefinition,
    agent_name: str,
    agent: AgentDefinition,
    workspace_mode: str,
    workspace_path: str,
    *,
    summary: str,
    dependency_records: dict[str, TaskRunRecord] | None = None,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
) -> TaskRunRecord:
    return task_execution_layer.planned_record(
        task,
        agent_name,
        agent,
        workspace_mode,
        workspace_path,
        summary=summary,
        dependency_records=dependency_records,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        deps={
            "iso_now": iso_now,
            "resolve_worker_validation_mode": resolve_worker_validation_mode,
            "resolve_output_profile": resolve_output_profile,
            "resolve_effort": resolve_effort,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "effective_task_skills": effective_task_skills,
            "task_run_record_factory": TaskRunRecord,
            "task_branch_context_id": task_branch_context_id,
            "task_branch_parent_context_ids": task_branch_parent_context_ids,
            "resolved_model": resolved_model,
            "resolved_model_profile": resolved_model_profile,
            "prompt_budget_result_factory": PromptBudgetResult,
        },
    )


def artifact_file_size(path: Path | None) -> int:
    return workspace_review_layer.artifact_file_size(path)


def _make_execute_record(
    task: TaskDefinition,
    workspace_mode: str,
    prepared_workspace: Path,
    started_at: str,
    model_name: str | None,
    model_profile: str | None,
    prompt_chars: int,
    prompt_estimated_tokens: int,
    prompt_render: PromptRenderResult,
    prompt_budget: PromptBudgetResult,
    prompt_path: Path,
    command_path: Path,
    dependency_materialization_mode: str,
    prepared_dependency_layers: list[DependencyLayerRecord],
    dependency_records: dict[str, TaskRunRecord],
    effort: str | None,
    effort_source: str,
    effective_validation_mode: str,
    validation_resolution: WorkerValidationModeResolution,
    *,
    status: str,
    summary: str,
    files_touched: list[str] = (),
    validation_intents: list[ValidationIntent] = (),
    validation_commands: list[str] = (),
    follow_ups: list[str] = (),
    follow_up_tasks: list[dict[str, Any]] = (),
    notes: list[str] = (),
    usage: dict[str, Any] | None = None,
    return_code: int | None = None,
    stdout_path: str | None = None,
    stderr_path: str | None = None,
    result_path: str | None = None,
    stdout_bytes: int = 0,
    stderr_bytes: int = 0,
    result_bytes: int = 0,
    unknown_fields: list[str] = (),
) -> TaskRunRecord:
    return TaskRunRecord(
        id=task.id,
        title=task.title,
        agent=task.agent,
        resolved_skills=effective_task_skills(task, agents[task.agent]) if "agents" in locals() else [],
        branch_context_id=task_branch_context_id(task, dependency_records),
        branch_parent_context_ids=task_branch_parent_context_ids(task, dependency_records),
        status=status,
        summary=summary,
        workspace_mode=workspace_mode,
        workspace_path=str(prepared_workspace),
        started_at=started_at,
        finished_at=iso_now(),
        files_touched=list(files_touched),
        actual_files_touched=[],
        protected_path_violations=[],
        write_scope_violations=[],
        validation_intents=list(validation_intents),
        validation_commands=list(validation_commands),
        follow_ups=list(follow_ups),
        follow_up_tasks=[dict(item) for item in follow_up_tasks],
        notes=list(notes),
        model=model_name,
        model_profile=model_profile,
        prompt_chars=prompt_chars,
        prompt_estimated_tokens=prompt_estimated_tokens,
        prompt_sections=prompt_render.sections,
        prompt_budget=prompt_budget,
        usage=usage,
        return_code=return_code,
        prompt_path=str(prompt_path),
        command_path=str(command_path),
        stdout_path=stdout_path,
        stderr_path=stderr_path,
        result_path=result_path,
        stdout_bytes=stdout_bytes,
        stderr_bytes=stderr_bytes,
        result_bytes=result_bytes,
        unknown_fields=list(unknown_fields),
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=prepared_dependency_layers,
        worker_validation_mode=effective_validation_mode,
        worker_validation_mode_source=validation_resolution.source,
        effort=effort,
        effort_source=effort_source,
        injected_from=task.injected_from,
    )


async def execute_task(
    run_dir: Path,
    runtime_root: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord],
    *,
    claude_bin: str,
    agents_json: str,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
) -> TaskRunRecord:
    return await task_execution_layer.execute_task(
        run_dir,
        runtime_root,
        workspaces_dir,
        plan,
        agents,
        task,
        dependency_records,
        claude_bin=claude_bin,
        agents_json=agents_json,
        dry_run=dry_run,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        deps={
            "root": ROOT,
            "resolve_worker_validation_mode": resolve_worker_validation_mode,
            "resolve_output_profile": resolve_output_profile,
            "resolve_effort": resolve_effort,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "resolved_model": resolved_model,
            "resolved_model_profile": resolved_model_profile,
            "effective_workspace_mode": effective_workspace_mode,
            "effective_task_write_scope": effective_task_write_scope,
            "effective_task_skills": effective_task_skills,
            "worker_prompt": worker_prompt,
            "dependency_summary": dependency_summary,
            "evaluate_prompt_budget": evaluate_prompt_budget,
            "resolved_max_prompt_chars": resolved_max_prompt_chars,
            "resolved_max_prompt_estimated_tokens": resolved_max_prompt_estimated_tokens,
            "effective_tool_lists": _effective_tool_lists,
            "claude_command": claude_command,
            "task_output_schema_json": task_output_schema_json,
            "write_text": write_text,
            "write_json": write_json,
            "iso_now": iso_now,
            "prompt_budget_failure_summary": prompt_budget_failure_summary,
            "snapshot_workspace_files": snapshot_workspace_files,
            "diff_workspace_snapshots": diff_workspace_snapshots,
            "provider_mode": sdk_provider_layer.detect_provider_mode,
            "run_sdk_provider": sdk_provider_layer.run_sdk_provider,
            "run_subprocess": run_subprocess_async,
            "task_wait_action": task_wait_action,
            "extract_json_payload": extract_json_payload,
            "extract_usage": extract_usage,
            "artifact_file_size": artifact_file_size,
            "apply_workspace_audit": apply_workspace_audit,
            "apply_repository_isolation_audit": apply_repository_isolation_audit,
            "coerce_worker_result": coerce_worker_result,
            "coerce_validation_intent_payload": coerce_validation_intent_payload,
            "reviewer_finding_factory": ReviewFinding,
            "prepare_workspace": prepare_workspace,
            "error_factory": OrchestratorError,
            "asdict": asdict,
            "task_run_record_factory": TaskRunRecord,
            "task_branch_context_id": task_branch_context_id,
            "task_branch_parent_context_ids": task_branch_parent_context_ids,
        },
    )


async def execute_task_with_retry(
    run_dir: Path,
    runtime_root: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord],
    *,
    claude_bin: str,
    agents_json: str,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    max_task_retries: int | None = None,
) -> TaskRunRecord:
    """Execute task with automatic retry for transient failures.

    Calls the module-level execute_task so tests can patch it normally.
    """
    agent = agents[task.agent]
    max_retries = retry_policy_layer.resolved_max_retries(
        task, agent, run_override=max_task_retries
    )
    attempt_errors: list[dict[str, Any]] = []

    for attempt_idx in range(max_retries + 1):
        record = await execute_task(
            run_dir,
            runtime_root,
            workspaces_dir,
            plan,
            agents,
            task,
            dependency_records,
            claude_bin=claude_bin,
            agents_json=agents_json,
            dry_run=dry_run,
            worker_validation_mode=worker_validation_mode,
            effort_override=effort_override,
        )
        record.attempt = attempt_idx + 1
        record.attempt_errors = list(attempt_errors)
        if record.status != "failed" or dry_run:
            return record
        if attempt_idx >= max_retries:
            return record
        failure_kind = retry_policy_layer.classify_failure(record)
        if failure_kind == "permanent":
            return record
        delay_sec = retry_policy_layer.backoff_delay_sec(attempt_idx)
        attempt_errors.append({
            "attempt": attempt_idx + 1,
            "status": record.status,
            "error": record.summary,
            "returnCode": record.return_code,
            "failureKind": failure_kind,
            "delayMs": int(delay_sec * 1000),
        })
        await asyncio.sleep(delay_sec)

    return record


def manifest_payload(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, AgentDefinition],
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    records: dict[str, TaskRunRecord],
    *,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
    run_events: list[dict[str, Any]] | None = None,
    follow_up_behavior: str | None = None,
    follow_up_behavior_override: str | None = None,
) -> dict[str, Any]:
    return manifest_io_layer.manifest_payload(
        run_id,
        plan_path,
        agents_path,
        agents,
        runtime_root,
        run_dir,
        workspaces_dir,
        plan,
        records,
        dry_run=dry_run,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        retry_of_run_id=retry_of_run_id,
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
        seeded_task_ids=seeded_task_ids,
        run_events=run_events,
        follow_up_behavior=follow_up_behavior,
        follow_up_behavior_override=follow_up_behavior_override,
        deps={
            "root": ROOT,
            "iso_now": iso_now,
            "asdict": asdict,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "normalize_effort_override": normalize_effort_override,
            "effective_plan_worker_validation_modes": effective_plan_worker_validation_modes,
            "effective_plan_worker_validation_mode_sources": effective_plan_worker_validation_mode_sources,
            "effective_plan_output_profiles": effective_plan_output_profiles,
            "effective_plan_output_profile_sources": effective_plan_output_profile_sources,
            "effective_plan_efforts": effective_plan_efforts,
            "effective_plan_effort_sources": effective_plan_effort_sources,
            "effective_plan_model_profiles": effective_plan_model_profiles,
            "effective_plan_models": effective_plan_models,
            "effective_task_skills": effective_task_skills,
            "complex_model_task_ids": complex_model_task_ids,
            "analyze_plan_topology": analyze_plan_topology,
            "load_model_pricing": lambda: cost_estimation_layer.load_model_pricing(
                read_json=read_json,
                error_factory=OrchestratorError,
            ),
            "estimate_plan_cost": lambda plan, agents, **kwargs: cost_estimation_layer.estimate_plan_cost(
                plan,
                agents,
                topological_batches=topological_batches,
                estimate_tokens=estimate_tokens,
                error_factory=OrchestratorError,
                **kwargs,
            ),
            "aggregate_usage": aggregate_usage,
            "evaluate_run_governance": evaluate_run_governance,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
            "summarized_worker_validation_mode": summarized_worker_validation_mode,
            "serialize_run_policy": serialize_run_policy,
            "error_factory": OrchestratorError,
        },
    )


def write_manifest(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, AgentDefinition],
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    records: dict[str, TaskRunRecord],
    *,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
    run_events: list[dict[str, Any]] | None = None,
    follow_up_behavior: str | None = None,
    follow_up_behavior_override: str | None = None,
) -> None:
    manifest_io_layer.write_manifest(
        run_id,
        plan_path,
        agents_path,
        agents,
        runtime_root,
        run_dir,
        workspaces_dir,
        plan,
        records,
        dry_run=dry_run,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        retry_of_run_id=retry_of_run_id,
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
        seeded_task_ids=seeded_task_ids,
        run_events=run_events,
        follow_up_behavior=follow_up_behavior,
        follow_up_behavior_override=follow_up_behavior_override,
        deps={
            "write_json": write_json,
            "root": ROOT,
            "iso_now": iso_now,
            "asdict": asdict,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "normalize_effort_override": normalize_effort_override,
            "effective_plan_worker_validation_modes": effective_plan_worker_validation_modes,
            "effective_plan_worker_validation_mode_sources": effective_plan_worker_validation_mode_sources,
            "effective_plan_output_profiles": effective_plan_output_profiles,
            "effective_plan_output_profile_sources": effective_plan_output_profile_sources,
            "effective_plan_efforts": effective_plan_efforts,
            "effective_plan_effort_sources": effective_plan_effort_sources,
            "effective_plan_model_profiles": effective_plan_model_profiles,
            "effective_plan_models": effective_plan_models,
            "effective_task_skills": effective_task_skills,
            "complex_model_task_ids": complex_model_task_ids,
            "analyze_plan_topology": analyze_plan_topology,
            "load_model_pricing": lambda: cost_estimation_layer.load_model_pricing(
                read_json=read_json,
                error_factory=OrchestratorError,
            ),
            "estimate_plan_cost": lambda plan, agents, **kwargs: cost_estimation_layer.estimate_plan_cost(
                plan,
                agents,
                topological_batches=topological_batches,
                estimate_tokens=estimate_tokens,
                error_factory=OrchestratorError,
                **kwargs,
            ),
            "aggregate_usage": aggregate_usage,
            "evaluate_run_governance": evaluate_run_governance,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
            "summarized_worker_validation_mode": summarized_worker_validation_mode,
            "serialize_run_policy": serialize_run_policy,
            "error_factory": OrchestratorError,
        },
    )


def write_selected_plan_snapshot(run_dir: Path, plan: TaskPlan) -> None:
    manifest_io_layer.write_selected_plan_snapshot(
        run_dir,
        plan,
        serialize_run_policy=serialize_run_policy,
        write_json=write_json,
    )


def coerce_follow_up_task(
    payload: Any,
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    emitter_task_id: str,
    existing_task_ids: set[str],
    location: str,
) -> TaskDefinition:
    task = load_task_definition(payload, agents, location=location)
    if task.id in existing_task_ids:
        raise OrchestratorError(f"{location}: task id '{task.id}' already exists in the selected plan")
    dependency_ids: list[str] = []
    for dependency_id in [emitter_task_id, *task.depends_on]:
        if dependency_id == task.id:
            raise OrchestratorError(f"{location}: injected task cannot depend on itself")
        if dependency_id not in existing_task_ids:
            raise OrchestratorError(
                f"{location}: injected task dependency '{dependency_id}' is not available in the current plan"
            )
        if dependency_id not in dependency_ids:
            dependency_ids.append(dependency_id)
    return replace(
        task,
        depends_on=dependency_ids,
        injected_from=emitter_task_id,
    )


def run_loaded_plan(
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, AgentDefinition],
    plan: TaskPlan,
    *,
    claude_bin: str,
    runtime_root: Path,
    max_parallel: int,
    continue_on_error: bool,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    initial_records: dict[str, TaskRunRecord] | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    follow_up_behavior_override: str | None = None,
    existing_run_id: str | None = None,
    existing_run_dir: Path | None = None,
    existing_workspaces_dir: Path | None = None,
    write_plan_snapshot: bool = True,
    max_task_retries: int | None = None,
    hitl: bool = False,
    hitl_mode: str | None = None,
    hitl_auto_approve: bool = False,
    otel_endpoint: str | None = None,
    reuse_unchanged: bool = False,
    prior_completed_records: dict[str, TaskRunRecord] | None = None,
    watch: bool = False,
    tui: bool = False,
) -> dict[str, Any]:
    _max_retries = max_task_retries

    async def _execute_task_with_retry(
        run_dir: Path,
        runtime_root_: Path,
        workspaces_dir: Path,
        plan_: Any,
        agents_: dict[str, Any],
        task: Any,
        dependency_records: dict[str, Any],
        *,
        claude_bin: str,
        agents_json: str,
        dry_run: bool,
        worker_validation_mode: str | None = None,
        effort_override: str | None = None,
    ) -> Any:
        return await execute_task_with_retry(
            run_dir,
            runtime_root_,
            workspaces_dir,
            plan_,
            agents_,
            task,
            dependency_records,
            claude_bin=claude_bin,
            agents_json=agents_json,
            dry_run=dry_run,
            worker_validation_mode=worker_validation_mode,
            effort_override=effort_override,
            max_task_retries=_max_retries,
        )

    _active_append_run_event = append_run_event
    if watch:
        _active_append_run_event = _make_watch_append_run_event(append_run_event)

    async def _run_inner(
        *,
        _append_run_event: Any,
        _wait_for_hitl_decision: Any,
        _wait_for_hitl_decision_async: Any = None,
    ) -> dict[str, Any]:
        return await run_ops_layer.run_loaded_plan(
            plan_path,
            agents_path,
            agents,
            plan,
            claude_bin=claude_bin,
            runtime_root=runtime_root,
            max_parallel=max_parallel,
            continue_on_error=continue_on_error,
            dry_run=dry_run,
            worker_validation_mode=worker_validation_mode,
            effort_override=effort_override,
            initial_records=initial_records,
            retry_of_run_id=retry_of_run_id,
            requested_task_ids=requested_task_ids,
            retried_task_ids=retried_task_ids,
            follow_up_behavior_override=follow_up_behavior_override,
            existing_run_id=existing_run_id,
            existing_run_dir=existing_run_dir,
            existing_workspaces_dir=existing_workspaces_dir,
            write_plan_snapshot=write_plan_snapshot,
            hitl_override=hitl,
            hitl_mode_override=hitl_mode,
            hitl_auto_approve=hitl_auto_approve,
            normalize_worker_validation_mode=normalize_worker_validation_mode,
            normalize_effort_override=normalize_effort_override,
            effective_plan_worker_validation_modes=effective_plan_worker_validation_modes,
            effective_plan_worker_validation_mode_sources=effective_plan_worker_validation_mode_sources,
            effective_plan_output_profiles=effective_plan_output_profiles,
            effective_plan_output_profile_sources=effective_plan_output_profile_sources,
            effective_plan_efforts=effective_plan_efforts,
            effective_plan_effort_sources=effective_plan_effort_sources,
            effective_task_skills=effective_task_skills,
            topological_batches=topological_batches,
            validate_scope_contract=validate_scope_contract,
            ensure_claude_available=lambda bin: ensure_provider_available(bin, sdk_provider_layer.detect_provider_mode()),
            write_selected_plan_snapshot=write_selected_plan_snapshot,
            agent_payload_for_claude=agent_payload_for_claude,
            append_run_event=_append_run_event,
            task_branch_context_id=task_branch_context_id,
            evaluate_run_governance=evaluate_run_governance,
            blocked_record=blocked_record,
            effective_workspace_mode=effective_workspace_mode,
            write_manifest=write_manifest,
            select_parallel_ready_batch=select_parallel_ready_batch,
            execute_task=_execute_task_with_retry,
            aggregate_usage=aggregate_usage,
            effective_plan_model_profiles=effective_plan_model_profiles,
            effective_plan_models=effective_plan_models,
            complex_model_task_ids=complex_model_task_ids,
            analyze_plan_topology=analyze_plan_topology,
            load_model_pricing=lambda: cost_estimation_layer.load_model_pricing(
                read_json=read_json,
                error_factory=OrchestratorError,
            ),
            estimate_plan_cost=lambda plan, agents, **kwargs: cost_estimation_layer.estimate_plan_cost(
                plan,
                agents,
                topological_batches=topological_batches,
                estimate_tokens=estimate_tokens,
                error_factory=OrchestratorError,
                **kwargs,
            ),
            serialize_run_policy=serialize_run_policy,
            summarized_worker_validation_mode=summarized_worker_validation_mode,
            summarize_branch_contexts=summarize_branch_contexts,
            coerce_follow_up_task=coerce_follow_up_task,
            default_workspaces_dir=default_workspaces_dir,
            slugify=slugify,
            resolve_hitl_policy=hitl_layer.resolve_hitl_policy,
            should_trigger_hitl_gate=hitl_layer.should_trigger_hitl_gate,
            hitl_gate_context_factory=hitl_layer.HitlGateContext,
            wait_for_hitl_decision=_wait_for_hitl_decision,
            wait_for_hitl_decision_async=_wait_for_hitl_decision_async,
            write_text=write_text,
            otel_endpoint=otel_endpoint,
            manifest_payload_builder=manifest_payload,
            build_trace_payload=trace_export_layer.export_trace_payload,
            summarize_run_manifest=summarize_run_manifest,
            parse_iso_datetime=parse_iso_datetime,
            datetime_to_iso=datetime_to_iso,
            emit_otel_trace=otel_layer.emit_otel_trace_from_custom_payload,
            reuse_unchanged=reuse_unchanged,
            prior_completed_records=prior_completed_records,
            compute_task_fingerprint=lambda task, agent, dep_recs, read_paths, model, effort: task_fingerprint_layer.compute_task_fingerprint(
                task, agent, dep_recs, read_paths, ROOT,
                resolved_model=model, resolved_effort=effort,
            ),
            effective_task_read_paths=effective_task_read_paths,
            error_factory=OrchestratorError,
        )

    if not tui:
        payload = asyncio.run(
            _run_inner(
                _append_run_event=_active_append_run_event,
                _wait_for_hitl_decision=hitl_layer.wait_for_hitl_decision,
            )
        )
    else:
        async def _run_with_tui() -> dict[str, Any]:
            event_queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
            app = tui_layer.OrchestratorApp(
                event_queue=event_queue,
                task_models={
                    task.id: resolved_model(task, agents[task.agent]) or "-"
                    for task in plan.tasks
                },
                plan_name=plan.name,
            )
            active_append = _make_tui_append_run_event(_active_append_run_event, event_queue)
            app_task = asyncio.create_task(app.run_async())
            try:
                payload_inner = await _run_inner(
                    _append_run_event=active_append,
                    _wait_for_hitl_decision=hitl_layer.wait_for_hitl_decision,
                    _wait_for_hitl_decision_async=app.wait_for_hitl_decision,
                )
            finally:
                if not app_task.done():
                    app.call_after_refresh(app.exit)
                await app_task
            return payload_inner

        payload = asyncio.run(_run_with_tui())
    try:
        ledger_entry = run_ledger_layer.build_ledger_entry(payload, iso_now_fn=iso_now)
        run_ledger_layer.append_ledger_entry(DEFAULT_LEDGER_PATH, ledger_entry)
    except Exception:
        pass
    return payload


def _resolve_tui_mode(*, requested: bool, watch: bool, json_output: bool, stderr_isatty: bool, textual_available: bool) -> tuple[bool, bool, str | None]:
    if requested and (watch or json_output):
        return False, watch, "--tui is ignored when --watch or --json is set."
    auto_requested = (not requested) and (not watch) and (not json_output) and stderr_isatty
    wants_tui = requested or auto_requested
    if not wants_tui:
        return False, watch, None
    if textual_available:
        return True, watch, None
    warning = "textual is not installed; falling back to --watch. Install 'pojolens-agents[tui]' for the dashboard."
    return False, True, warning


def run_plan(args: argparse.Namespace) -> dict[str, Any]:
    otel_endpoint = otel_layer.resolve_otel_endpoint(getattr(args, "otel_endpoint", ""))
    args.otel_endpoint = otel_endpoint or ""
    args.tui, args.watch, tui_warning = _resolve_tui_mode(
        requested=bool(getattr(args, "tui", False)),
        watch=bool(getattr(args, "watch", False)),
        json_output=bool(getattr(args, "json", False)),
        stderr_isatty=bool(getattr(sys.stderr, "isatty", lambda: False)()),
        textual_available=bool(tui_layer.textual_is_available()),
    )
    if tui_warning:
        print(tui_warning, file=sys.stderr, flush=True)
    return run_ops_layer.run_plan(
        args,
        load_agents=load_agents,
        load_task_plan=load_task_plan,
        selected_plan=selected_plan,
        run_loaded_plan_fn=run_loaded_plan,
        effective_plan_output_profiles=effective_plan_output_profiles,
        effective_plan_output_profile_sources=effective_plan_output_profile_sources,
        effective_plan_efforts=effective_plan_efforts,
        effective_plan_effort_sources=effective_plan_effort_sources,
        effective_plan_models=effective_plan_models,
        effective_plan_model_profiles=effective_plan_model_profiles,
        effective_task_skills=effective_task_skills,
        complex_model_task_ids=complex_model_task_ids,
        analyze_plan_topology=analyze_plan_topology,
        serialize_run_policy=serialize_run_policy,
        load_model_pricing=lambda: cost_estimation_layer.load_model_pricing(
            read_json=read_json,
            error_factory=OrchestratorError,
        ),
        estimate_plan_cost=lambda plan, agents, **kwargs: cost_estimation_layer.estimate_plan_cost(
            plan,
            agents,
            topological_batches=topological_batches,
            estimate_tokens=estimate_tokens,
            error_factory=OrchestratorError,
            **kwargs,
        ),
    )


def resume_run(args: argparse.Namespace) -> dict[str, Any]:
    otel_endpoint = otel_layer.resolve_otel_endpoint(getattr(args, "otel_endpoint", ""))
    args.otel_endpoint = otel_endpoint or ""
    args.tui, args.watch, tui_warning = _resolve_tui_mode(
        requested=bool(getattr(args, "tui", False)),
        watch=bool(getattr(args, "watch", False)),
        json_output=bool(getattr(args, "json", False)),
        stderr_isatty=bool(getattr(sys.stderr, "isatty", lambda: False)()),
        textual_available=bool(tui_layer.textual_is_available()),
    )
    if tui_warning:
        print(tui_warning, file=sys.stderr, flush=True)
    return run_ops_layer.resume_run(
        args,
        root=ROOT,
        load_run_manifest=load_run_manifest,
        selected_run_records=selected_run_records,
        manifest_run_dir=manifest_run_dir,
        manifest_workspaces_dir=manifest_workspaces_dir,
        manifest_required_path=manifest_required_path,
        load_agents=load_agents,
        manifest_selected_plan_path=manifest_selected_plan_path,
        load_task_plan=load_task_plan,
        manifest_worker_validation_override=manifest_worker_validation_override,
        manifest_follow_up_behavior_override=manifest_follow_up_behavior_override,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        selected_plan=selected_plan,
        planned_record=planned_record,
        effective_workspace_mode=effective_workspace_mode,
        normalize_effort_override=normalize_effort_override,
        run_loaded_plan_fn=run_loaded_plan,
        error_factory=OrchestratorError,
    )


def retry_run(args: argparse.Namespace) -> dict[str, Any]:
    otel_endpoint = otel_layer.resolve_otel_endpoint(getattr(args, "otel_endpoint", ""))
    args.otel_endpoint = otel_endpoint or ""
    args.tui, args.watch, tui_warning = _resolve_tui_mode(
        requested=bool(getattr(args, "tui", False)),
        watch=bool(getattr(args, "watch", False)),
        json_output=bool(getattr(args, "json", False)),
        stderr_isatty=bool(getattr(sys.stderr, "isatty", lambda: False)()),
        textual_available=bool(tui_layer.textual_is_available()),
    )
    if tui_warning:
        print(tui_warning, file=sys.stderr, flush=True)
    return run_ops_layer.retry_run(
        args,
        load_run_manifest=load_run_manifest,
        selected_run_records=selected_run_records,
        manifest_selected_plan_path=manifest_selected_plan_path,
        manifest_required_path=manifest_required_path,
        load_agents=load_agents,
        load_task_plan=load_task_plan,
        selected_plan=selected_plan,
        manifest_worker_validation_override=manifest_worker_validation_override,
        manifest_follow_up_behavior_override=manifest_follow_up_behavior_override,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        run_loaded_plan_fn=run_loaded_plan,
        error_factory=OrchestratorError,
    )


def parse_iso_datetime(value: Any) -> datetime | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone()


def datetime_to_iso(value: datetime) -> str:
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone().isoformat()


def cleanup_loaded_run(manifest_path: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    return runtime_admin_layer.cleanup_loaded_run(
        manifest_path,
        manifest,
        root=ROOT,
        manifest_run_dir=manifest_run_dir,
        manifest_workspaces_dir=manifest_workspaces_dir,
        selected_run_records=selected_run_records,
        dedupe_strings=dedupe_strings,
        error_factory=OrchestratorError,
    )


def cleanup_run(args: argparse.Namespace) -> dict[str, Any]:
    return runtime_admin_layer.cleanup_run(
        args,
        load_run_manifest=load_run_manifest,
        cleanup_loaded_run_fn=cleanup_loaded_run,
        prune_ledger_fn=run_ledger_layer.prune_ledger_entries,
        ledger_path=DEFAULT_LEDGER_PATH,
    )


def summarize_ledger(args: argparse.Namespace) -> dict[str, Any]:
    plan_name = str(getattr(args, "plan_name", "") or "")
    since_str = str(getattr(args, "since", "") or "")
    limit = int(getattr(args, "limit", 0) or 0)

    since = None
    if since_str:
        try:
            since = datetime.fromisoformat(since_str).replace(tzinfo=timezone.utc)
        except ValueError:
            raise OrchestratorError(f"Invalid --since date '{since_str}': expected YYYY-MM-DD")

    entries = run_ledger_layer.load_ledger_entries(
        DEFAULT_LEDGER_PATH,
        plan_name_prefix=plan_name or None,
        limit=limit or None,
        since=since,
    )
    summary = run_ledger_layer.summarize_ledger_entries(entries)
    summary["ledgerPath"] = str(DEFAULT_LEDGER_PATH)
    summary["planNameFilter"] = plan_name or None
    summary["sinceFilter"] = since_str or None
    summary["limit"] = limit or None
    return summary


def runtime_manifest_entries(runtime_root: Path) -> list[tuple[Path, dict[str, Any]]]:
    return runtime_admin_layer.runtime_manifest_entries(
        runtime_root=runtime_root,
        load_run_manifest=load_run_manifest,
    )


def export_trace(args: argparse.Namespace) -> dict[str, Any]:
    otel_endpoint = otel_layer.resolve_otel_endpoint(getattr(args, "otel_endpoint", ""))
    args.otel_endpoint = otel_endpoint or ""
    return trace_export_layer.export_trace_run(
        args,
        deps={
            "load_run_manifest": load_run_manifest,
            "selected_run_records": selected_run_records,
            "summarize_run_manifest": summarize_run_manifest,
            "parse_iso_datetime": parse_iso_datetime,
            "datetime_to_iso": datetime_to_iso,
            "write_json": write_json,
            "resolve_otel_endpoint": otel_layer.resolve_otel_endpoint,
            "emit_otel_trace": otel_layer.emit_otel_trace_from_custom_payload,
        },
    )


def summarize_run_events(events_payload: Any) -> dict[str, Any]:
    return run_summary_layer.summarize_run_events(events_payload)


def summarize_approval_checkpoints(manifest: dict[str, Any]) -> dict[str, Any]:
    return run_summary_layer.summarize_approval_checkpoints(manifest)


def derive_run_lifecycle_state(
    *,
    manifest: dict[str, Any],
    records: list[TaskRunRecord],
    summary_base: dict[str, Any],
    promotion_readiness: dict[str, Any],
    approval_summary: dict[str, Any],
) -> tuple[str, str]:
    _ = manifest
    return run_summary_layer.derive_run_lifecycle_state(
        records=records,
        summary_base=summary_base,
        promotion_readiness=promotion_readiness,
        approval_summary=approval_summary,
    )


def summarize_run_manifest(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    now: datetime | None = None,
) -> tuple[dict[str, Any], datetime]:
    return run_summary_layer.summarize_run_manifest(
        manifest_path,
        manifest,
        now=now,
        manifest_run_dir=manifest_run_dir,
        manifest_workspaces_dir=manifest_workspaces_dir,
        selected_run_records=selected_run_records,
        count_statuses=count_statuses,
        summarize_promotion_readiness=summarize_promotion_readiness,
        parse_iso_datetime=parse_iso_datetime,
        datetime_to_iso=datetime_to_iso,
    )


def append_run_event(
    events: list[dict[str, Any]],
    *,
    phase: str,
    task_id: str | None = None,
    task_ids: list[str] | None = None,
    parent_task_ids: list[str] | None = None,
    branch_context_id: str | None = None,
    branch_context_ids: list[str] | None = None,
    status: str | None = None,
    message: str | None = None,
    details: dict[str, Any] | None = None,
) -> None:
    payload: dict[str, Any] = {
        "ts": iso_now(),
        "phase": phase,
    }
    if task_id:
        payload["taskId"] = task_id
    if task_ids:
        payload["taskIds"] = list(task_ids)
    if parent_task_ids is not None:
        payload["parentTaskIds"] = list(parent_task_ids)
    if branch_context_id:
        payload["branchContextId"] = branch_context_id
    if branch_context_ids:
        payload["branchContextIds"] = list(branch_context_ids)
    if status:
        payload["status"] = status
    if message:
        payload["message"] = message
    if details:
        payload["details"] = details
    events.append(payload)


def status_run(args: argparse.Namespace) -> dict[str, Any]:
    return runtime_admin_layer.status_run(
        args,
        deps={
            "load_run_manifest": load_run_manifest,
            "summarize_run_manifest": summarize_run_manifest,
            "selected_run_records": selected_run_records,
            "task_review_summary": task_review_summary,
            "task_promotion_operations": task_promotion_operations,
        },
    )


def _evaluation_check(
    name: str,
    status: str,
    summary: str,
    *,
    evidence: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return evals_layer.evaluation_check(name, status, summary, evidence=evidence)


def summarize_evaluation_score(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    return evals_layer.summarize_evaluation_score(checks, run_summary=run_summary)


def benchmark_dimensions_for_evaluation(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    return evals_layer.benchmark_dimensions_for_evaluation(checks, run_summary=run_summary)


def evaluate_loaded_run_quality(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    selected_tasks: list[str],
) -> dict[str, Any]:
    return evals_layer.evaluate_loaded_run_quality(
        manifest_path,
        manifest,
        selected_tasks=selected_tasks,
        summarize_run_manifest=summarize_run_manifest,
        selected_run_records=selected_run_records,
    )


def evaluate_run_quality(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    return evaluate_loaded_run_quality(
        manifest_path,
        manifest,
        selected_tasks=list(args.selected_tasks),
    )


def inventory_runs(args: argparse.Namespace) -> dict[str, Any]:
    return runtime_admin_layer.inventory_runs(
        args,
        deps={
            "summarize_run_manifest": summarize_run_manifest,
            "runtime_manifest_entries": runtime_manifest_entries,
        },
    )


def evaluate_run_corpus(args: argparse.Namespace) -> dict[str, Any]:
    runtime_root = Path(args.runtime_root).resolve()
    limit = max(int(args.limit), 0)
    return evals_layer.evaluate_run_corpus(
        runtime_root=runtime_root,
        limit=limit,
        runtime_manifest_entries=runtime_manifest_entries,
        summarize_run_manifest=summarize_run_manifest,
        evaluate_loaded_run_quality_fn=lambda manifest_path, manifest, selected_tasks: evaluate_loaded_run_quality(
            manifest_path,
            manifest,
            selected_tasks=selected_tasks,
        ),
    )


def prune_runs(args: argparse.Namespace) -> dict[str, Any]:
    return runtime_admin_layer.prune_runs(
        args,
        deps={
            "error_factory": OrchestratorError,
            "summarize_run_manifest": summarize_run_manifest,
            "runtime_manifest_entries": runtime_manifest_entries,
            "cleanup_loaded_run": cleanup_loaded_run,
        },
    )


def collect_validation_commands(
    records: list[TaskRunRecord],
    *,
    included_statuses: set[str],
    execution_scope: str = DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    return validation_ops_layer.collect_validation_commands(
        records,
        included_statuses=included_statuses,
        execution_scope=execution_scope,
        default_validate_run_execution_scope=DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
        validation_execution_target_fn=validation_execution_target,
        dedupe_strings=dedupe_strings,
        validation_intent_command_text=validation_intent_command_text,
        validation_intent_policy=validation_intent_policy,
        validation_command_policy=validation_command_policy,
        worker_field_unknown=worker_field_unknown,
    )


def validation_execution_target(
    record: TaskRunRecord,
    *,
    execution_scope: str,
) -> dict[str, Any]:
    return validation_ops_layer.validation_execution_target(
        record,
        execution_scope=execution_scope,
        validate_run_execution_scopes=VALIDATE_RUN_EXECUTION_SCOPES,
        root=ROOT,
        error_factory=OrchestratorError,
    )


def run_shell_command_text(
    command_text: str,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return validation_ops_layer.run_shell_command_text(
        command_text,
        cwd=cwd,
        timeout_sec=timeout_sec,
        progress_action=progress_action,
        run_process=run_process,
    )


def run_validation_intent(
    intent: ValidationIntent,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return validation_ops_layer.run_validation_intent(
        intent,
        cwd=cwd,
        timeout_sec=timeout_sec,
        progress_action=progress_action,
        run_process=run_process,
        validation_intent_execution_tokens=validation_intent_execution_tokens,
        validation_intent_command_text=validation_intent_command_text,
    )


def write_run_checkpoint(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    checkpoint_name: str,
    directory_name: str,
    payload: dict[str, Any],
) -> dict[str, Any]:
    return validation_ops_layer.write_run_checkpoint(
        manifest_path,
        manifest,
        checkpoint_name=checkpoint_name,
        directory_name=directory_name,
        payload=payload,
        write_json=write_json,
    )


def write_coordinator_validation_summary(
    manifest_path: Path,
    manifest: dict[str, Any],
    summary: dict[str, Any],
) -> dict[str, Any]:
    return validation_ops_layer.write_coordinator_validation_summary(
        manifest_path,
        manifest,
        summary,
        write_run_checkpoint_fn=write_run_checkpoint,
    )


def validate_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    run_dir = manifest_run_dir(manifest_path, manifest)
    records = selected_run_records(manifest, args.selected_tasks)
    return validation_ops_layer.validate_run(
        manifest_path=manifest_path,
        manifest=manifest,
        run_dir=run_dir,
        records=records,
        intents_only=bool(getattr(args, "intents_only", False)),
        execution_scope=str(getattr(args, "execution_scope", DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE) or DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE),
        included_statuses={str(status) for status in (getattr(args, "include_statuses", None) or list(DEFAULT_VALIDATE_RUN_STATUSES))},
        allow_unsafe_commands=bool(args.allow_unsafe_commands),
        continue_on_error=bool(args.continue_on_error),
        timeout_sec=int(args.timeout_sec),
        dry_run=bool(args.dry_run),
        root=ROOT,
        default_validate_run_execution_scope=DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
        collect_validation_commands_fn=collect_validation_commands,
        coerce_validation_intent_payload=coerce_validation_intent_payload,
        run_validation_intent_fn=run_validation_intent,
        run_shell_command_text_fn=run_shell_command_text,
        write_text=write_text,
        write_json=write_json,
        slugify=slugify,
        validation_wait_action=validation_wait_action,
        count_statuses=count_statuses,
        write_coordinator_validation_summary_fn=write_coordinator_validation_summary,
    )


def validate_command(args: argparse.Namespace) -> dict[str, Any]:
    return validate_cli_layer.validate_command(
        args,
        deps={
            "load_agents": load_agents,
            "load_task_plan": load_task_plan,
            "validate_scope_contract": validate_scope_contract,
            "effective_plan_worker_validation_modes": effective_plan_worker_validation_modes,
            "effective_plan_worker_validation_mode_sources": effective_plan_worker_validation_mode_sources,
            "effective_plan_output_profiles": effective_plan_output_profiles,
            "effective_plan_output_profile_sources": effective_plan_output_profile_sources,
            "effective_plan_model_profiles": effective_plan_model_profiles,
            "effective_plan_models": effective_plan_models,
            "effective_plan_efforts": effective_plan_efforts,
            "effective_plan_effort_sources": effective_plan_effort_sources,
            "complex_model_task_ids": complex_model_task_ids,
            "analyze_plan_topology": analyze_plan_topology,
            "topological_batches": topological_batches,
            "load_model_pricing": lambda: cost_estimation_layer.load_model_pricing(
                read_json=read_json,
                error_factory=OrchestratorError,
            ),
            "estimate_plan_cost": lambda plan, agents, **kwargs: cost_estimation_layer.estimate_plan_cost(
                plan,
                agents,
                topological_batches=topological_batches,
                estimate_tokens=estimate_tokens,
                error_factory=OrchestratorError,
                **kwargs,
            ),
            "serialize_run_policy": serialize_run_policy,
            "effective_task_read_paths": effective_task_read_paths,
            "effective_task_write_scope": effective_task_write_scope,
            "effective_task_skills": effective_task_skills,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
            "compute_task_fingerprint": lambda task, agent, dep_recs, read_paths, model, effort: task_fingerprint_layer.compute_task_fingerprint(
                task, agent, dep_recs, read_paths, ROOT,
                resolved_model=model, resolved_effort=effort,
            ),
            "error_factory": OrchestratorError,
        },
    )




def _make_watch_append_run_event(base_fn: Any) -> Any:
    def _watch_append(events: list[dict[str, Any]], *, phase: str, **kwargs: Any) -> None:
        base_fn(events, phase=phase, **kwargs)
        if phase not in config_loader_layer.WATCH_PHASES:
            return
        from datetime import datetime as _dt
        ts = _dt.now().strftime("%H:%M:%S")
        line = config_loader_layer.format_watch_line(ts, phase, **kwargs)
        try:
            import shutil as _shutil
            width = _shutil.get_terminal_size((120, 24)).columns
        except Exception:
            width = 120
        print(line[:width], file=sys.stderr, flush=True)
    return _watch_append


def _make_tui_append_run_event(base_fn: Any, event_queue: asyncio.Queue[dict[str, Any]]) -> Any:
    def _tui_append(events: list[dict[str, Any]], *, phase: str, **kwargs: Any) -> None:
        previous_len = len(events)
        base_fn(events, phase=phase, **kwargs)
        if len(events) <= previous_len:
            return
        try:
            event_queue.put_nowait(dict(events[-1]))
        except Exception:
            pass
    return _tui_append


def diff_run_command(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    selected_tasks = diff_run_layer.split_csv_values(
        list(getattr(args, "selected_tasks", []) or []),
        getattr(args, "selected_task_csv", ""),
    )
    path_filters = diff_run_layer.split_csv_values(
        list(getattr(args, "path_filters", []) or []),
        getattr(args, "path_filters_csv", ""),
    )
    records = selected_run_records(manifest, selected_tasks)
    return diff_run_layer.diff_run(
        manifest_path=manifest_path,
        manifest=manifest,
        records=records,
        context_lines=int(getattr(args, "context_lines", 3) or 3),
        stat_only=bool(getattr(args, "stat", False)),
        path_filters=path_filters,
        dedupe_strings=dedupe_strings,
        diff_file_against_workspace_fn=diff_file_against_workspace,
    )


def config_command(args: Any) -> dict[str, Any]:
    config_path = str(getattr(args, "config", "") or "").strip() or None
    try:
        config_defaults = config_loader_layer.load_config(config_path)
    except Exception as exc:
        raise OrchestratorError(str(exc)) from exc
    return {
        "configPath": config_path or "(auto)",
        "defaults": config_defaults,
    }


def wizard_command(args: argparse.Namespace) -> dict[str, Any]:
    return wizard_layer.wizard_command(
        args,
        deps={
            "root": ROOT,
            "textual_available": tui_layer.textual_is_available,
            "slugify": slugify,
            "write_json": write_json,
            "error_factory": OrchestratorError,
            "load_agents": load_agents,
            "ensure_claude_available": lambda bin: ensure_provider_available(bin, sdk_provider_layer.detect_provider_mode()),
            "claude_command": claude_command,
            "agent_payload_for_claude": agent_payload_for_claude,
            "run_subprocess": run_process,
            "extract_json_payload": extract_json_payload,
            "inventory_handler": inventory_runs,
            "validate_handler": validate_command,
            "run_handler": run_plan,
            "resume_handler": resume_run,
            "retry_handler": retry_run,
            "status_handler": status_run,
            "review_handler": review_run,
            "diff_run_handler": diff_run_command,
            "promote_handler": promote_run,
            "validate_run_handler": validate_run,
            "default_task_timeout_sec": DEFAULT_TASK_TIMEOUT_SEC,
        },
    )


def _build_handlers() -> dict[str, Any]:
    return {
        'validate': validate_command,
        'plan': plan_with_claude,
        'run': run_plan,
        'resume': resume_run,
        'retry': retry_run,
        'review': review_run,
        'export-patch': export_patch,
        'diff-run': diff_run_command,
        'export-trace': export_trace,
        'promote': promote_run,
        'cleanup': cleanup_run,
        'inventory': inventory_runs,
        'status': status_run,
        'evaluate-run': evaluate_run_quality,
        'evaluate-corpus': evaluate_run_corpus,
        'prune': prune_runs,
        'validate-run': validate_run,
        'summarize-ledger': summarize_ledger,
        'config': config_command,
        'wizard': wizard_command,
    }


def main() -> int:
    args = parse_args()
    if args.command == "console":
        from pojo_lens_agents.cli_parser import parse_args as _child_parse_args
        from pojo_lens_agents.console import run_console_session
        return run_console_session(
            args,
            handlers=_build_handlers(),
            parse_args_fn=_child_parse_args,
        )
    return dispatch_main(args, _build_handlers())


if __name__ == '__main__':
    sys.exit(main())
