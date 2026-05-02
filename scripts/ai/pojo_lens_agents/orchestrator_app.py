#!/usr/bin/env python3
from __future__ import annotations

import copy
import difflib
import importlib
import os
import shlex
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
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


governance_layer = _LazyModuleProxy("pojo_lens_agents.governance")
evals_layer = _LazyModuleProxy("pojo_lens_agents.evals")
manifest_io_layer = _LazyModuleProxy("pojo_lens_agents.manifest_io")
runtime_admin_layer = _LazyModuleProxy("pojo_lens_agents.runtime_admin")
run_ops_layer = _LazyModuleProxy("pojo_lens_agents.run_ops")
run_store_layer = _LazyModuleProxy("pojo_lens_agents.run_store")
trace_export_layer = _LazyModuleProxy("pojo_lens_agents.trace_export")
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
    )


def execute_task(
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
    return task_execution_layer.execute_task(
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
            "run_subprocess": run_subprocess,
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
            "aggregate_usage": aggregate_usage,
            "evaluate_run_governance": evaluate_run_governance,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
            "summarized_worker_validation_mode": summarized_worker_validation_mode,
            "serialize_run_policy": serialize_run_policy,
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
            "aggregate_usage": aggregate_usage,
            "evaluate_run_governance": evaluate_run_governance,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
            "summarized_worker_validation_mode": summarized_worker_validation_mode,
            "serialize_run_policy": serialize_run_policy,
        },
    )


def write_selected_plan_snapshot(run_dir: Path, plan: TaskPlan) -> None:
    manifest_io_layer.write_selected_plan_snapshot(
        run_dir,
        plan,
        serialize_run_policy=serialize_run_policy,
        write_json=write_json,
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
    existing_run_id: str | None = None,
    existing_run_dir: Path | None = None,
    existing_workspaces_dir: Path | None = None,
    write_plan_snapshot: bool = True,
) -> dict[str, Any]:
    return run_ops_layer.run_loaded_plan(
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
        existing_run_id=existing_run_id,
        existing_run_dir=existing_run_dir,
        existing_workspaces_dir=existing_workspaces_dir,
        write_plan_snapshot=write_plan_snapshot,
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
        ensure_claude_available=ensure_claude_available,
        write_selected_plan_snapshot=write_selected_plan_snapshot,
        agent_payload_for_claude=agent_payload_for_claude,
        append_run_event=append_run_event,
        task_branch_context_id=task_branch_context_id,
        evaluate_run_governance=evaluate_run_governance,
        blocked_record=blocked_record,
        effective_workspace_mode=effective_workspace_mode,
        write_manifest=write_manifest,
        select_parallel_ready_batch=select_parallel_ready_batch,
        execute_task=execute_task,
        aggregate_usage=aggregate_usage,
        effective_plan_model_profiles=effective_plan_model_profiles,
        effective_plan_models=effective_plan_models,
        complex_model_task_ids=complex_model_task_ids,
        analyze_plan_topology=analyze_plan_topology,
        serialize_run_policy=serialize_run_policy,
        summarized_worker_validation_mode=summarized_worker_validation_mode,
        summarize_branch_contexts=summarize_branch_contexts,
        default_workspaces_dir=default_workspaces_dir,
        slugify=slugify,
        error_factory=OrchestratorError,
    )


def run_plan(args: argparse.Namespace) -> dict[str, Any]:
    return run_ops_layer.run_plan(
        args,
        load_agents=load_agents,
        load_task_plan=load_task_plan,
        selected_plan=selected_plan,
        run_loaded_plan_fn=run_loaded_plan,
    )


def resume_run(args: argparse.Namespace) -> dict[str, Any]:
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
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        selected_plan=selected_plan,
        planned_record=planned_record,
        effective_workspace_mode=effective_workspace_mode,
        normalize_effort_override=normalize_effort_override,
        run_loaded_plan_fn=run_loaded_plan,
        error_factory=OrchestratorError,
    )


def retry_run(args: argparse.Namespace) -> dict[str, Any]:
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
    )


def runtime_manifest_entries(runtime_root: Path) -> list[tuple[Path, dict[str, Any]]]:
    return runtime_admin_layer.runtime_manifest_entries(
        runtime_root=runtime_root,
        load_run_manifest=load_run_manifest,
    )


def export_trace(args: argparse.Namespace) -> dict[str, Any]:
    return trace_export_layer.export_trace_run(
        args,
        deps={
            "load_run_manifest": load_run_manifest,
            "selected_run_records": selected_run_records,
            "summarize_run_manifest": summarize_run_manifest,
            "parse_iso_datetime": parse_iso_datetime,
            "datetime_to_iso": datetime_to_iso,
            "write_json": write_json,
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
            "serialize_run_policy": serialize_run_policy,
            "effective_task_read_paths": effective_task_read_paths,
            "effective_task_write_scope": effective_task_write_scope,
            "effective_task_skills": effective_task_skills,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
        },
    )




def main() -> int:
    return dispatch_main(
        parse_args(),
        handlers={
            'validate': validate_command,
            'plan': plan_with_claude,
            'run': run_plan,
            'resume': resume_run,
            'retry': retry_run,
            'review': review_run,
            'export-patch': export_patch,
            'export-trace': export_trace,
            'promote': promote_run,
            'cleanup': cleanup_run,
            'inventory': inventory_runs,
            'status': status_run,
            'evaluate-run': evaluate_run_quality,
            'evaluate-corpus': evaluate_run_corpus,
            'prune': prune_runs,
            'validate-run': validate_run,
        },
    )


if __name__ == '__main__':
    sys.exit(main())
