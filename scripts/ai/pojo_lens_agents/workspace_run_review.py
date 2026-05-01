#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

from pojo_lens_agents import governance as governance_layer
from pojo_lens_agents import manifest_records as manifest_records_layer
from pojo_lens_agents import path_safety as path_safety_layer
from pojo_lens_agents import prompt_contracts as prompt_contracts_layer
from pojo_lens_agents import review_ops as review_ops_layer
from pojo_lens_agents import run_store as run_store_layer
from pojo_lens_agents import run_summary as run_summary_layer
from pojo_lens_agents import task_execution as task_execution_layer
from pojo_lens_agents import validation_ops as validation_ops_layer
from pojo_lens_agents import worker_contracts as worker_contracts_layer
from pojo_lens_agents import workspace_review as workspace_review_layer
from pojo_lens_agents.orchestrator_contracts import (
    DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT,
    DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT,
    DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    DEFAULT_REVIEW_DEPENDENCY_CONTEXT_LINES,
    DEFAULT_REVIEW_DEPENDENCY_PATCH_CHAR_LIMIT,
    DependencyLayerOperation,
    DependencyLayerRecord,
    MAX_HYDRATED_FILE_BYTES,
    OrchestratorError,
    PromptBudgetResult,
    PromptSectionMetric,
    PROTECTED_PATH_EXACT,
    PROTECTED_PATH_PREFIXES,
    PromotionBlockedError,
    ReviewFinding,
    REVIEWER_AGENT_NAME,
    ROOT,
    SPARSE_COPY_BASE_FILES,
    TaskDefinition,
    TaskRunRecord,
    ValidationIntent,
    VALIDATION_INTENT_KINDS,
    MAX_WORKER_VALIDATION_INTENT_ARG_CHARS,
    WORKER_VALIDATION_MODES,
    WORKER_UNKNOWNABLE_FIELDS,
    WORKSPACE_AUDIT_IGNORE_DIR_NAMES,
    WorkspacePreparationResult,
    AgentDefinition,
)
from pojo_lens_agents.orchestrator_utils import dedupe_strings, emit_slop_log, format_issue_block, read_bytes, read_json, slugify, summarize_paths, truncate_multiline_text, truncate_text, workspace_prep_action, write_json, write_text
from pojo_lens_agents.plan_support import analyze_copy_hydration_inputs, effective_task_write_scope, normalize_dependency_materialization_mode, normalize_relative_path, normalize_worker_validation_mode, normalize_worker_validation_mode_source, paths_outside_scope


def effective_dependency_materialization_mode(task: TaskDefinition) -> str:
    return prompt_contracts_layer.effective_dependency_materialization_mode(
        task,
        default_mode=DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    )


def current_root() -> Path:
    app = sys.modules.get("pojo_lens_agents.orchestrator_app")
    return Path(getattr(app, "ROOT", ROOT))


def path_is_relative_to(path: Path, parent: Path) -> bool:
    return path_safety_layer.path_is_relative_to(path, parent)


def resolve_relative_path(root: Path, relative_path: str, *, location: str) -> tuple[str, Path]:
    return path_safety_layer.resolve_relative_path(
        root,
        relative_path,
        location=location,
        error_factory=OrchestratorError,
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


def normalized_worker_unknown_fields(payload: Any) -> list[str]:
    return worker_contracts_layer.normalized_worker_unknown_fields(
        payload,
        worker_unknownable_fields=list(WORKER_UNKNOWNABLE_FIELDS),
    )


def worker_field_unknown(record: TaskRunRecord, field_name: str) -> bool:
    return worker_contracts_layer.worker_field_unknown(record, field_name)


def normalize_effort_override(value: str | None, *, location: str) -> str | None:
    return prompt_contracts_layer.normalize_effort_override(
        value,
        location=location,
        error_factory=OrchestratorError,
    )


def require_optional_string(payload: dict[str, Any], key: str, *, location: str) -> str | None:
    value = payload.get(key)
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise OrchestratorError(f"{location}: expected string for '{key}'")
    return value.strip()


def ensure_claude_available(claude_bin: str) -> None:
    if shutil.which(claude_bin) is None:
        raise OrchestratorError(f"Claude CLI '{claude_bin}' is not available on PATH")


def ensure_clean_for_worktrees() -> None:
    root = current_root()
    if not (root / ".git").exists():
        return
    completed = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        raise OrchestratorError("Failed to inspect git status for worktree mode")
    if completed.stdout.strip():
        raise OrchestratorError(
            "worktree mode requires a clean repo because detached worktrees start from HEAD only; "
            "use workspaceMode='copy' to isolate current uncommitted changes"
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


def path_is_relative_to(path: Path, parent: Path) -> bool:
    return path_safety_layer.path_is_relative_to(path, parent)


def resolve_relative_path(root: Path, relative_path: str, *, location: str) -> tuple[str, Path]:
    return path_safety_layer.resolve_relative_path(
        root,
        relative_path,
        location=location,
        error_factory=OrchestratorError,
    )


def hydrate_copy_workspace(workspace_path: Path, file_paths: list[str]) -> None:
    workspace_review_layer.hydrate_copy_workspace(
        source_root=current_root(),
        workspace_path=workspace_path,
        file_paths=file_paths,
        base_files=SPARSE_COPY_BASE_FILES,
        max_file_bytes=MAX_HYDRATED_FILE_BYTES,
        path_is_relative_to=path_is_relative_to,
    )


def prepare_workspace(
    plan: TaskPlan,
    task: TaskDefinition,
    workspace_mode: str,
    workspace_path: Path,
    runtime_root: Path,
    dependency_records: dict[str, TaskRunRecord],
) -> WorkspacePreparationResult:
    return task_execution_layer.prepare_workspace(
        plan,
        task,
        workspace_mode,
        workspace_path,
        runtime_root,
        dependency_records,
        deps={
            "root": current_root(),
            "shutil": shutil,
            "subprocess": subprocess,
            "workspace_preparation_result_factory": WorkspacePreparationResult,
            "workspace_prep_action": workspace_prep_action,
            "emit_slop_log": emit_slop_log,
            "analyze_copy_hydration_inputs": analyze_copy_hydration_inputs,
            "summarize_paths": summarize_paths,
            "format_issue_block": format_issue_block,
            "hydrate_copy_workspace": hydrate_copy_workspace,
            "materialize_dependency_layers": materialize_dependency_layers,
            "ensure_clean_for_worktrees": ensure_clean_for_worktrees,
            "error_factory": OrchestratorError,
        },
    )


def snapshot_workspace_files(workspace_root: Path) -> dict[str, str]:
    return workspace_review_layer.snapshot_workspace_files(
        workspace_root,
        ignore_dir_names=WORKSPACE_AUDIT_IGNORE_DIR_NAMES,
    )


def diff_workspace_snapshots(before: dict[str, str], after: dict[str, str]) -> list[str]:
    return workspace_review_layer.diff_workspace_snapshots(before, after)


def summarize_paths(paths: list[str], *, limit: int = 4) -> str:
    visible = paths[:limit]
    if not visible:
        return "none"
    summary = ", ".join(visible)
    hidden = len(paths) - len(visible)
    if hidden > 0:
        summary += f", ... ({hidden} more)"
    return summary


def protected_path_violations(paths: list[str]) -> list[str]:
    return path_safety_layer.protected_path_violations(
        paths,
        exact_paths=PROTECTED_PATH_EXACT,
        path_prefixes=PROTECTED_PATH_PREFIXES,
        error_factory=OrchestratorError,
    )


def write_scope_violations(paths: list[str], declared_scope: list[str]) -> list[str]:
    return paths_outside_scope(paths, declared_scope)


def apply_workspace_audit(
    record: TaskRunRecord,
    *,
    reported_files: list[str],
    actual_files: list[str],
    declared_write_scope: list[str],
) -> TaskRunRecord:
    record.actual_files_touched = list(actual_files)
    record.files_touched = dedupe_strings(actual_files + reported_files)
    actual_only = [path for path in actual_files if path not in reported_files]
    if actual_only:
        if worker_field_unknown(record, "filesTouched"):
            record.notes.append(
                "Workspace diff resolved worker-unknown `filesTouched`: "
                f"{summarize_paths(actual_only)}"
            )
        else:
            record.notes.append(
                "Workspace diff found files not reported by the worker: "
                f"{summarize_paths(actual_only)}"
            )
    protected_violations = protected_path_violations(record.files_touched)
    record.protected_path_violations = protected_violations
    scope_violations = write_scope_violations(actual_files, declared_write_scope)
    record.write_scope_violations = scope_violations
    failure_reasons: list[str] = []
    if protected_violations:
        failure_reasons.append(
            "Protected-path violation: "
            f"{summarize_paths(protected_violations)}"
        )
    if scope_violations:
        failure_reasons.append(
            "Write-scope violation: "
            f"{summarize_paths(scope_violations)}"
        )
    if failure_reasons:
        record.status = "failed"
        record.summary = "; ".join(failure_reasons) + f". Original outcome: {record.summary}"
        follow_ups = [
            "Inspect and discard or manually review forbidden workspace edits before promotion.",
            "Inspect and discard or manually review out-of-scope workspace edits before promotion.",
        ]
        for follow_up in reversed(follow_ups):
            if follow_up not in record.follow_ups and (
                ("forbidden" in follow_up and protected_violations)
                or ("out-of-scope" in follow_up and scope_violations)
            ):
                record.follow_ups.insert(0, follow_up)
    return record


def apply_repository_isolation_audit(
    record: TaskRunRecord,
    *,
    workspace_mode: str,
    changed_repo_files: list[str],
) -> TaskRunRecord:
    if workspace_mode == "repo" or not changed_repo_files:
        return record
    summary = (
        "Repository isolation violation: worker modified live repo while running in "
        f"workspaceMode='{workspace_mode}': {summarize_paths(changed_repo_files)}"
    )
    record.status = "failed"
    record.summary = summary + f". Original outcome: {record.summary}"
    follow_up = (
        "Discard or restore live repo mutations, then rerun the task and promote only "
        "workspace-reviewed changes."
    )
    if follow_up not in record.follow_ups:
        record.follow_ups.insert(0, follow_up)
    return record


def resolve_manifest_path(run_ref: str) -> Path:
    return run_store_layer.resolve_manifest_path(
        run_ref,
        error_factory=OrchestratorError,
    )


def load_run_manifest(run_ref: str) -> tuple[Path, dict[str, Any]]:
    manifest_path = resolve_manifest_path(run_ref)
    payload = read_json(manifest_path)
    return manifest_path, run_store_layer.validate_manifest_payload(
        payload,
        manifest_path=manifest_path,
        error_factory=OrchestratorError,
    )


def manifest_required_path(manifest: dict[str, Any], key: str, *, location: str) -> Path:
    return run_store_layer.manifest_required_path(
        manifest,
        key,
        location=location,
        error_factory=OrchestratorError,
    )


def manifest_run_dir(manifest_path: Path, manifest: dict[str, Any]) -> Path:
    return run_store_layer.manifest_run_dir(manifest_path, manifest)


def manifest_workspaces_dir(manifest: dict[str, Any], *, run_dir: Path) -> Path:
    return run_store_layer.manifest_workspaces_dir(
        manifest,
        run_dir=run_dir,
        error_factory=OrchestratorError,
    )


def manifest_selected_plan_path(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    location: str,
) -> Path:
    return run_store_layer.manifest_selected_plan_path(
        manifest_path,
        manifest,
        location=location,
        error_factory=OrchestratorError,
    )


def manifest_worker_validation_override(
    manifest: dict[str, Any],
    *,
    location: str,
) -> str | None:
    override = require_optional_string(manifest, "workerValidationModeOverride", location=location)
    if override:
        return override
    legacy_mode = require_optional_string(manifest, "workerValidationMode", location=location)
    if legacy_mode in WORKER_VALIDATION_MODES:
        return legacy_mode
    return None


def count_statuses(values: list[str]) -> dict[str, int]:
    return manifest_records_layer.count_statuses(values)


def task_branch_parent_context_ids(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord] | None = None,
) -> list[str]:
    return manifest_records_layer.task_branch_parent_context_ids(
        task,
        dependency_records=dependency_records,
    )


def task_branch_context_id(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord] | None = None,
) -> str:
    return manifest_records_layer.task_branch_context_id(
        task,
        dependency_records=dependency_records,
    )


def summarize_branch_contexts(records: list[TaskRunRecord]) -> dict[str, Any]:
    return run_summary_layer.summarize_branch_contexts(records)


def task_cost_usd(record: TaskRunRecord) -> float:
    return governance_layer.task_cost_usd(record)


def coerce_task_run_record(payload: Any, *, location: str) -> TaskRunRecord:
    return manifest_records_layer.coerce_task_run_record(
        payload,
        location=location,
        deps={
            "error_factory": OrchestratorError,
            "task_run_record_factory": TaskRunRecord,
            "prompt_section_metric_factory": PromptSectionMetric,
            "prompt_budget_result_factory": PromptBudgetResult,
            "coerce_validation_intent_payload": coerce_validation_intent_payload,
            "normalized_worker_unknown_fields": normalized_worker_unknown_fields,
            "normalize_dependency_materialization_mode": normalize_dependency_materialization_mode,
            "default_dependency_materialization_mode": DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
            "coerce_dependency_layer_record_payload": coerce_dependency_layer_record_payload,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "normalize_worker_validation_mode_source": normalize_worker_validation_mode_source,
            "normalize_effort_override": normalize_effort_override,
            "require_optional_string": require_optional_string,
            "reviewer_finding_factory": ReviewFinding,
        },
    )


def selected_run_records(
    manifest: dict[str, Any],
    selected_ids: list[str],
) -> list[TaskRunRecord]:
    return manifest_records_layer.selected_run_records(
        manifest,
        selected_ids,
        coerce_task_run_record=coerce_task_run_record,
        error_factory=OrchestratorError,
    )


def decode_text_or_none(content: bytes) -> str | None:
    return review_ops_layer.decode_text_or_none(content)


def diff_file_against_workspace(
    record: TaskRunRecord,
    relative_path: str,
    *,
    context_lines: int,
) -> tuple[dict[str, Any], str | None]:
    return review_ops_layer.diff_file_against_workspace(
        record,
        relative_path,
        context_lines=context_lines,
        root=current_root(),
        resolve_relative_path=resolve_relative_path,
        read_bytes=read_bytes,
        error_factory=OrchestratorError,
    )


def task_review_summary(record: TaskRunRecord, *, context_lines: int) -> tuple[dict[str, Any], list[str]]:
    return review_ops_layer.task_review_summary(
        record,
        context_lines=context_lines,
        dedupe_strings=dedupe_strings,
        diff_file_against_workspace_fn=diff_file_against_workspace,
    )


def dependency_review_context(record: TaskRunRecord) -> list[str]:
    changed_paths = dedupe_strings(record.actual_files_touched or record.files_touched)
    if not changed_paths:
        if worker_field_unknown(record, "filesTouched"):
            return ["  changed files: unknown"]
        return []
    try:
        review_payload, patch_chunks = task_review_summary(
            record,
            context_lines=DEFAULT_REVIEW_DEPENDENCY_CONTEXT_LINES,
        )
    except OrchestratorError as exc:
        reason, _ = truncate_text(str(exc), DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT)
        return [f"  changed files: unavailable ({reason})"]
    changed_files = [
        file_payload
        for file_payload in review_payload["files"]
        if str(file_payload.get("status", "")) in {"added", "modified", "deleted"}
    ]
    if not changed_files:
        return []
    visible_files: list[str] = []
    for file_payload in changed_files[:DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT]:
        path = str(file_payload.get("path", ""))
        status = str(file_payload.get("status", ""))
        if file_payload.get("isBinary"):
            visible_files.append(f"`{path}` ({status}, binary)")
            continue
        added = int(file_payload.get("addedLines", 0) or 0)
        removed = int(file_payload.get("removedLines", 0) or 0)
        visible_files.append(f"`{path}` ({status}, +{added}/-{removed})")
    hidden_files = len(changed_files) - len(visible_files)
    if hidden_files > 0:
        visible_files.append(f"... ({hidden_files} more changed files omitted)")
    lines = ["  changed files: " + " ; ".join(visible_files)]
    preview_chunks: list[str] = []
    preview_chars = 0
    for patch_chunk in patch_chunks[:DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT]:
        chunk = patch_chunk.strip()
        if not chunk:
            continue
        remaining_chars = DEFAULT_REVIEW_DEPENDENCY_PATCH_CHAR_LIMIT - preview_chars
        if remaining_chars <= 0:
            break
        preview_text, truncated = truncate_multiline_text(chunk, remaining_chars)
        if not preview_text:
            continue
        preview_chunks.append(preview_text)
        preview_chars += len(preview_text) + 1
        if truncated:
            break
    if preview_chunks:
        lines.append("  diff preview:")
        for preview_line in "\n\n".join(preview_chunks).splitlines():
            lines.append(f"    {preview_line}")
    return lines


def default_patch_output_path(manifest_path: Path, task_ids: list[str]) -> Path:
    return review_ops_layer.default_patch_output_path(
        manifest_path,
        task_ids,
        slugify=slugify,
    )


def review_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    return review_ops_layer.review_run(
        manifest_path=manifest_path,
        manifest=manifest,
        records=records,
        context_lines=args.context_lines,
        default_dependency_materialization_mode=DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
        write_run_checkpoint=write_run_checkpoint,
        task_review_summary_fn=task_review_summary,
    )


def export_patch(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    return review_ops_layer.export_patch(
        manifest_path=manifest_path,
        manifest=manifest,
        records=records,
        context_lines=args.context_lines,
        out=args.out,
        default_patch_output_path_fn=default_patch_output_path,
        write_text=write_text,
        dedupe_strings=dedupe_strings,
        task_review_summary_fn=task_review_summary,
    )


def format_issue_block(header: str, issues: list[str]) -> str:
    visible = dedupe_strings(issues)
    return header if not visible else f"{header}:\n- " + "\n- ".join(visible)


def task_promotion_operations(record: TaskRunRecord) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    return review_ops_layer.task_promotion_operations(
        record,
        task_review_summary_fn=task_review_summary,
    )


def coerce_dependency_layer_operation_payload(
    payload: Any,
    *,
    location: str,
) -> DependencyLayerOperation:
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: expected dependency layer operation object")
    action = str(payload.get("action", "")).strip()
    if action not in {"added", "modified", "deleted"}:
        raise OrchestratorError(
            f"{location}: dependency layer action must be one of ['added', 'deleted', 'modified']"
        )
    return DependencyLayerOperation(
        path=normalize_relative_path(str(payload.get("path", "")), location=f"{location}:path"),
        action=action,
        is_binary=bool(payload.get("is_binary", payload.get("isBinary", False))),
    )


def coerce_dependency_layer_record_payload(
    payload: Any,
    *,
    location: str,
) -> DependencyLayerRecord:
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: expected dependency layer record object")
    task_id = str(payload.get("task_id", payload.get("taskId", ""))).strip()
    if not task_id:
        raise OrchestratorError(f"{location}: dependency layer task id is required")
    workspace_mode = str(payload.get("workspace_mode", payload.get("workspaceMode", ""))).strip()
    if workspace_mode not in {"copy", "worktree"}:
        raise OrchestratorError(
            f"{location}: dependency layer workspace mode must be 'copy' or 'worktree'"
        )
    workspace_path = str(payload.get("workspace_path", payload.get("workspacePath", ""))).strip()
    if not workspace_path:
        raise OrchestratorError(f"{location}: dependency layer workspace path is required")
    operations_payload = payload.get("operations", [])
    if operations_payload is None:
        operations_payload = []
    if not isinstance(operations_payload, list):
        raise OrchestratorError(f"{location}: dependency layer operations must be a list")
    return DependencyLayerRecord(
        task_id=task_id,
        workspace_mode=workspace_mode,
        workspace_path=workspace_path,
        operations=[
            coerce_dependency_layer_operation_payload(
                item,
                location=f"{location}:operations[{index}]",
            )
            for index, item in enumerate(operations_payload, start=1)
        ],
    )


def own_dependency_layer(record: TaskRunRecord) -> DependencyLayerRecord | None:
    return review_ops_layer.own_dependency_layer(
        record,
        task_promotion_operations_fn=task_promotion_operations,
        dependency_layer_record_factory=DependencyLayerRecord,
        dependency_layer_operation_factory=DependencyLayerOperation,
        error_factory=OrchestratorError,
    )


def dependency_layers_for_record(record: TaskRunRecord) -> list[DependencyLayerRecord]:
    return review_ops_layer.dependency_layers_for_record(
        record,
        own_dependency_layer_fn=own_dependency_layer,
    )


def dependency_layer_conflicts(task: TaskDefinition, records: dict[str, TaskRunRecord]) -> list[str]:
    owner_by_path: dict[str, str] = {}
    issues: list[str] = []
    for dependency_id in task.depends_on:
        record = records[dependency_id]
        touched_paths = dedupe_strings(
            [
                operation.path
                for layer in dependency_layers_for_record(record)
                for operation in layer.operations
            ]
        )
        for path in touched_paths:
            owner = owner_by_path.get(path)
            if owner is not None and owner != dependency_id:
                issues.append(
                    f"dependency materialization is ambiguous for '{path}' from '{owner}' and '{dependency_id}'"
                )
            else:
                owner_by_path[path] = dependency_id
    return dedupe_strings(issues)


def apply_workspace_operation(
    source_workspace_path: str,
    operation: DependencyLayerOperation,
    *,
    target_root: Path,
    location: str,
) -> None:
    normalized, target_file = resolve_relative_path(
        target_root,
        operation.path,
        location=f"{location}:target",
    )
    if operation.action == "deleted":
        if target_file.exists():
            target_file.unlink()
        return
    source_root = Path(source_workspace_path).resolve()
    if not source_root.exists():
        raise OrchestratorError(f"{location}: source workspace '{source_root}' does not exist")
    _, source_file = resolve_relative_path(
        source_root,
        normalized,
        location=f"{location}:source",
    )
    if not source_file.exists() or not source_file.is_file():
        raise OrchestratorError(
            f"{location}: expected materialized source file '{normalized}' in '{source_root}'"
        )
    target_file.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, target_file)


def materialize_dependency_layers(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord],
    *,
    workspace_root: Path,
) -> list[DependencyLayerRecord]:
    if effective_dependency_materialization_mode(task) != "apply-reviewed":
        return []
    conflict_issues = dependency_layer_conflicts(task, dependency_records)
    if conflict_issues:
        raise OrchestratorError(format_issue_block(f"{task.id}: dependency materialization blocked", conflict_issues))
    applied_layers: list[DependencyLayerRecord] = []
    for dependency_id in task.depends_on:
        record = dependency_records[dependency_id]
        if record.status != "completed":
            raise OrchestratorError(
                f"{task.id}: dependency '{dependency_id}' must be completed before materialization"
            )
        for layer in dependency_layers_for_record(record):
            for index, operation in enumerate(layer.operations, start=1):
                apply_workspace_operation(
                    layer.workspace_path,
                    operation,
                    target_root=workspace_root,
                    location=f"{task.id}:dependency:{layer.task_id}:operation[{index}]",
                )
            applied_layers.append(layer)
    return applied_layers


def plan_promotion(records: list[TaskRunRecord]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, int]]:
    return review_ops_layer.plan_promotion(
        records,
        summarize_paths=summarize_paths,
        format_issue_block=format_issue_block,
        task_promotion_operations_fn=task_promotion_operations,
        blocked_error_factory=PromotionBlockedError,
        reviewer_agent_name=REVIEWER_AGENT_NAME,
    )


def summarize_promotion_readiness(records: list[TaskRunRecord]) -> dict[str, Any]:
    return review_ops_layer.summarize_promotion_readiness(
        records,
        task_promotion_operations_fn=task_promotion_operations,
        plan_promotion_fn=plan_promotion,
        blocked_error_factory=PromotionBlockedError,
        dedupe_strings=dedupe_strings,
        reviewer_agent_name=REVIEWER_AGENT_NAME,
    )


def apply_promotion_operation(record: TaskRunRecord, operation: dict[str, Any]) -> None:
    review_ops_layer.apply_promotion_operation(
        record,
        operation,
        root=current_root(),
        dependency_layer_operation_factory=DependencyLayerOperation,
        apply_workspace_operation_fn=apply_workspace_operation,
    )


def promote_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    return review_ops_layer.promote_run(
        manifest_path=manifest_path,
        manifest=manifest,
        records=records,
        dry_run=args.dry_run,
        root=ROOT,
        format_issue_block=format_issue_block,
        task_promotion_operations_fn=task_promotion_operations,
        summarize_promotion_readiness_fn=summarize_promotion_readiness,
        plan_promotion_fn=plan_promotion,
        apply_promotion_operation_fn=apply_promotion_operation,
        blocked_error_factory=PromotionBlockedError,
        write_run_checkpoint=write_run_checkpoint,
    )


