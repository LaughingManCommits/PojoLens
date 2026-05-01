from __future__ import annotations

import difflib
import shutil
from dataclasses import asdict
from pathlib import Path
from typing import Any, Callable


def decode_text_or_none(content: bytes) -> str | None:
    try:
        return content.decode("utf-8")
    except UnicodeDecodeError:
        return None


def diff_file_against_workspace(
    record: Any,
    relative_path: str,
    *,
    context_lines: int,
    root: Path,
    resolve_relative_path: Callable[..., tuple[str, Path]],
    read_bytes: Callable[[Path], bytes],
    error_factory: type[Exception],
) -> tuple[dict[str, Any], str | None]:
    normalized, repo_file = resolve_relative_path(
        root,
        relative_path,
        location=f"{record.id}: review path",
    )
    summary: dict[str, Any] = {
        "path": normalized,
        "status": "unchanged",
        "isBinary": False,
        "addedLines": 0,
        "removedLines": 0,
        "patchable": False,
    }
    if record.workspace_mode == "repo":
        summary["status"] = "unsupported"
        summary["reason"] = "repo-mode runs do not preserve an isolated review baseline"
        return summary, None
    if not record.workspace_path:
        raise error_factory(f"{record.id}: workspace path missing for reviewable task output")
    workspace_root = Path(record.workspace_path)
    if not workspace_root.exists():
        raise error_factory(
            f"{record.id}: workspace path '{workspace_root}' does not exist for review/export"
        )
    _, workspace_file = resolve_relative_path(
        workspace_root,
        normalized,
        location=f"{record.id}: workspace review path",
    )
    repo_exists = repo_file.exists()
    workspace_exists = workspace_file.exists()
    if not repo_exists and not workspace_exists:
        summary["status"] = "missing"
        return summary, None
    repo_bytes = read_bytes(repo_file) if repo_exists else b""
    workspace_bytes = read_bytes(workspace_file) if workspace_exists else b""
    if repo_exists and workspace_exists and repo_bytes == workspace_bytes:
        return summary, None
    if not repo_exists and workspace_exists:
        summary["status"] = "added"
    elif repo_exists and not workspace_exists:
        summary["status"] = "deleted"
    else:
        summary["status"] = "modified"
    repo_text = decode_text_or_none(repo_bytes) if repo_exists else ""
    workspace_text = decode_text_or_none(workspace_bytes) if workspace_exists else ""
    if (repo_exists and repo_text is None) or (workspace_exists and workspace_text is None):
        summary["isBinary"] = True
        return summary, None
    diff_lines = list(
        difflib.unified_diff(
            repo_text.splitlines(),
            workspace_text.splitlines(),
            fromfile=f"a/{normalized}",
            tofile=f"b/{normalized}",
            n=max(context_lines, 0),
            lineterm="",
        )
    )
    added_lines = 0
    removed_lines = 0
    for line in diff_lines:
        if line.startswith(("+++", "---", "@@")):
            continue
        if line.startswith("+"):
            added_lines += 1
        elif line.startswith("-"):
            removed_lines += 1
    summary["addedLines"] = added_lines
    summary["removedLines"] = removed_lines
    summary["patchable"] = True
    return summary, ("\n".join(diff_lines) + "\n") if diff_lines else None


def task_review_summary(
    record: Any,
    *,
    context_lines: int,
    dedupe_strings: Callable[[list[str]], list[str]],
    diff_file_against_workspace_fn: Callable[..., tuple[dict[str, Any], str | None]],
) -> tuple[dict[str, Any], list[str]]:
    reviewed_paths = dedupe_strings(record.actual_files_touched or record.files_touched)
    file_summaries: list[dict[str, Any]] = []
    patch_chunks: list[str] = []
    changed_files = 0
    binary_files = 0
    added_lines = 0
    removed_lines = 0
    for relative_path in reviewed_paths:
        summary, patch_text = diff_file_against_workspace_fn(
            record,
            relative_path,
            context_lines=context_lines,
        )
        if summary["status"] not in {"unchanged", "missing"}:
            changed_files += 1
        if summary.get("isBinary"):
            binary_files += 1
        added_lines += int(summary.get("addedLines", 0) or 0)
        removed_lines += int(summary.get("removedLines", 0) or 0)
        file_summaries.append(summary)
        if patch_text:
            patch_chunks.append(patch_text)
    return (
        {
            "id": record.id,
            "title": record.title,
            "agent": record.agent,
            "status": record.status,
            "summary": record.summary,
            "workspaceMode": record.workspace_mode,
            "workspacePath": record.workspace_path,
            "dependencyMaterializationMode": record.dependency_materialization_mode,
            "dependencyLayersApplied": [asdict(layer) for layer in record.dependency_layers_applied],
            "protectedPathViolations": record.protected_path_violations,
            "writeScopeViolations": record.write_scope_violations,
            "unknownFields": record.unknown_fields,
            "filesReported": record.files_touched,
            "filesObserved": record.actual_files_touched,
            "diffStats": {
                "filesReviewed": len(reviewed_paths),
                "filesChanged": changed_files,
                "binaryFiles": binary_files,
                "addedLines": added_lines,
                "removedLines": removed_lines,
            },
            "files": file_summaries,
        },
        patch_chunks,
    )


def default_patch_output_path(manifest_path: Path, task_ids: list[str], *, slugify: Callable[[str], str]) -> Path:
    review_dir = manifest_path.parent / "review"
    if not task_ids:
        filename = "combined.patch"
    elif len(task_ids) == 1:
        filename = f"{slugify(task_ids[0])}.patch"
    else:
        filename = f"selected-{slugify('-'.join(task_ids))}.patch"
    return review_dir / filename


def review_run(
    *,
    manifest_path: Path,
    manifest: dict[str, Any],
    records: list[Any],
    context_lines: int,
    default_dependency_materialization_mode: str,
    write_run_checkpoint: Callable[..., dict[str, Any]],
    task_review_summary_fn: Callable[..., tuple[dict[str, Any], list[str]]],
) -> dict[str, Any]:
    task_payloads = []
    changed_task_count = 0
    changed_files = 0
    protected_violations = 0
    write_scope_violations = 0
    validation_suggestion_count = 0
    dependency_materialization_modes: dict[str, int] = {}
    for record in records:
        review_payload, _ = task_review_summary_fn(record, context_lines=context_lines)
        task_payloads.append(review_payload)
        if int(review_payload["diffStats"]["filesChanged"]) > 0:
            changed_task_count += 1
        changed_files += int(review_payload["diffStats"]["filesChanged"])
        protected_violations += len(review_payload["protectedPathViolations"])
        write_scope_violations += len(review_payload["writeScopeViolations"])
        validation_suggestion_count += len(record.validation_intents) + len(record.validation_commands)
        mode = str(review_payload["dependencyMaterializationMode"] or default_dependency_materialization_mode)
        dependency_materialization_modes[mode] = dependency_materialization_modes.get(mode, 0) + 1
    payload = {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "runDir": str(manifest_path.parent),
        "taskCount": len(task_payloads),
        "summary": {
            "changedTaskCount": changed_task_count,
            "changedFileCount": changed_files,
            "protectedPathViolationCount": protected_violations,
            "writeScopeViolationCount": write_scope_violations,
            "validationSuggestionCount": validation_suggestion_count,
            "dependencyMaterializationModes": dependency_materialization_modes,
        },
        "tasks": task_payloads,
    }
    write_run_checkpoint(
        manifest_path,
        manifest,
        checkpoint_name="coordinatorReview",
        directory_name="review",
        payload=payload,
    )
    return payload


def export_patch(
    *,
    manifest_path: Path,
    manifest: dict[str, Any],
    records: list[Any],
    context_lines: int,
    out: str | None,
    default_patch_output_path_fn: Callable[[Path, list[str]], Path],
    write_text: Callable[[Path, str | None], None],
    dedupe_strings: Callable[[list[str]], list[str]],
    task_review_summary_fn: Callable[..., tuple[dict[str, Any], list[str]]],
) -> dict[str, Any]:
    patch_chunks: list[str] = []
    exported_task_ids: list[str] = []
    binary_files: list[str] = []
    changed_files = 0
    for record in records:
        review_payload, task_patches = task_review_summary_fn(record, context_lines=context_lines)
        if any(file_payload.get("isBinary") for file_payload in review_payload["files"]):
            binary_files.extend(
                file_payload["path"]
                for file_payload in review_payload["files"]
                if file_payload.get("isBinary")
            )
        if review_payload["diffStats"]["filesChanged"]:
            exported_task_ids.append(record.id)
        changed_files += int(review_payload["diffStats"]["filesChanged"])
        patch_chunks.extend(task_patches)
    output_path = Path(out).resolve() if out else default_patch_output_path_fn(
        manifest_path,
        exported_task_ids or [record.id for record in records],
    )
    patch_text = "".join(patch_chunks)
    if patch_text:
        write_text(output_path, patch_text)
    return {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "patchPath": str(output_path),
        "taskIds": exported_task_ids or [record.id for record in records],
        "filesChanged": changed_files,
        "binaryFilesSkipped": dedupe_strings(binary_files),
        "patchBytes": output_path.stat().st_size if output_path.exists() else 0,
    }


def task_promotion_operations(
    record: Any,
    *,
    task_review_summary_fn: Callable[..., tuple[dict[str, Any], list[str]]],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    review_payload, _ = task_review_summary_fn(record, context_lines=0)
    unsupported_paths: list[str] = []
    operations: list[dict[str, Any]] = []
    for file_payload in review_payload["files"]:
        status = str(file_payload.get("status", ""))
        if status == "unsupported":
            unsupported_paths.append(str(file_payload.get("path", "")))
            continue
        if status not in {"added", "modified", "deleted"}:
            continue
        operations.append(
            {
                "taskId": record.id,
                "path": str(file_payload["path"]),
                "action": status,
                "isBinary": bool(file_payload.get("isBinary", False)),
            }
        )
    return (
        {
            "id": record.id,
            "title": record.title,
            "agent": record.agent,
            "status": record.status,
            "summary": record.summary,
            "workspaceMode": record.workspace_mode,
            "workspacePath": record.workspace_path,
            "dependencyMaterializationMode": record.dependency_materialization_mode,
            "dependencyLayersApplied": [asdict(layer) for layer in record.dependency_layers_applied],
            "protectedPathViolations": record.protected_path_violations,
            "writeScopeViolations": record.write_scope_violations,
            "filesPromotable": len(operations),
            "unsupportedFiles": unsupported_paths,
            "operations": operations,
        },
        operations,
    )


def own_dependency_layer(
    record: Any,
    *,
    task_promotion_operations_fn: Callable[[Any], tuple[dict[str, Any], list[dict[str, Any]]]],
    dependency_layer_record_factory: Callable[..., Any],
    dependency_layer_operation_factory: Callable[..., Any],
    error_factory: type[Exception],
) -> Any | None:
    _, operations = task_promotion_operations_fn(record)
    if not operations:
        return None
    if record.workspace_mode not in {"copy", "worktree"}:
        raise error_factory(
            f"{record.id}: workspaceMode='{record.workspace_mode}' is not materializable"
        )
    if not record.workspace_path:
        raise error_factory(f"{record.id}: workspace path missing for dependency materialization")
    return dependency_layer_record_factory(
        task_id=record.id,
        workspace_mode=record.workspace_mode,
        workspace_path=record.workspace_path,
        operations=[
            dependency_layer_operation_factory(
                path=str(operation["path"]),
                action=str(operation["action"]),
                is_binary=bool(operation.get("isBinary", False)),
            )
            for operation in operations
        ],
    )


def dependency_layers_for_record(
    record: Any,
    *,
    own_dependency_layer_fn: Callable[[Any], Any | None],
) -> list[Any]:
    layers = list(record.dependency_layers_applied)
    own_layer = own_dependency_layer_fn(record)
    if own_layer is not None:
        layers.append(own_layer)
    return layers


def plan_promotion(
    records: list[Any],
    *,
    summarize_paths: Callable[[list[str]], str],
    format_issue_block: Callable[[str, list[str]], str],
    task_promotion_operations_fn: Callable[[Any], tuple[dict[str, Any], list[dict[str, Any]]]],
    blocked_error_factory: type[Exception],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, int]]:
    issues: list[str] = []
    task_payloads: list[dict[str, Any]] = []
    all_operations: list[dict[str, Any]] = []
    owners_by_path: dict[str, str] = {}
    counts = {"added": 0, "modified": 0, "deleted": 0}
    for record in records:
        task_payload, operations = task_promotion_operations_fn(record)
        task_payloads.append(task_payload)
        if record.protected_path_violations:
            issues.append(
                f"{record.id}: protected-path violations must be reviewed manually: "
                f"{summarize_paths(record.protected_path_violations)}"
            )
        if record.write_scope_violations:
            issues.append(
                f"{record.id}: write-scope violations must be reviewed manually: "
                f"{summarize_paths(record.write_scope_violations)}"
            )
        if task_payload["unsupportedFiles"]:
            issues.append(
                f"{record.id}: workspaceMode='{record.workspace_mode}' cannot be promoted for "
                f"{summarize_paths(task_payload['unsupportedFiles'])}"
            )
        if operations and record.status != "completed":
            issues.append(f"{record.id}: only completed tasks can be promoted, found status '{record.status}'")
        if operations and record.workspace_mode not in {"copy", "worktree"}:
            issues.append(
                f"{record.id}: workspaceMode='{record.workspace_mode}' is not promotable; use copy or worktree"
            )
        for operation in operations:
            owner = owners_by_path.get(operation["path"])
            if owner is not None and owner != record.id:
                issues.append(f"{record.id}: '{operation['path']}' is also changed by task '{owner}'")
            else:
                owners_by_path[operation["path"]] = record.id
            counts[str(operation["action"])] += 1
            all_operations.append(operation)
    if issues:
        raise blocked_error_factory(format_issue_block("Promotion blocked", issues))
    return task_payloads, all_operations, counts


def summarize_promotion_readiness(
    records: list[Any],
    *,
    task_promotion_operations_fn: Callable[[Any], tuple[dict[str, Any], list[dict[str, Any]]]],
    plan_promotion_fn: Callable[[list[Any]], tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, int]]],
    blocked_error_factory: type[Exception],
    dedupe_strings: Callable[[list[str]], list[str]],
) -> dict[str, Any]:
    try:
        task_payloads, operations, counts = plan_promotion_fn(records)
        return {
            "allowed": True,
            "blockedReasons": [],
            "promotableTaskIds": [payload["id"] for payload in task_payloads if payload["filesPromotable"]],
            "filesPromotable": len(operations),
            "operationCounts": counts,
        }
    except blocked_error_factory as exc:
        return {
            "allowed": False,
            "blockedReasons": dedupe_strings(
                [line[2:] if line.startswith("- ") else line for line in str(exc).splitlines() if line.strip() and not line.endswith(":")]
            ),
            "promotableTaskIds": [],
            "filesPromotable": 0,
            "operationCounts": {"added": 0, "modified": 0, "deleted": 0},
        }


def apply_workspace_operation(
    source_workspace_path: str,
    operation: Any,
    *,
    target_root: Path,
    location: str,
    resolve_relative_path: Callable[..., tuple[str, Path]],
    error_factory: type[Exception],
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
        raise error_factory(f"{location}: source workspace '{source_root}' does not exist")
    _, source_file = resolve_relative_path(
        source_root,
        normalized,
        location=f"{location}:source",
    )
    if not source_file.exists() or not source_file.is_file():
        raise error_factory(
            f"{location}: expected materialized source file '{normalized}' in '{source_root}'"
        )
    target_file.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, target_file)


def apply_promotion_operation(
    record: Any,
    operation: dict[str, Any],
    *,
    root: Path,
    dependency_layer_operation_factory: Callable[..., Any],
    apply_workspace_operation_fn: Callable[..., None],
) -> None:
    apply_workspace_operation_fn(
        str(record.workspace_path),
        dependency_layer_operation_factory(
            path=str(operation["path"]),
            action=str(operation["action"]),
            is_binary=bool(operation.get("isBinary", False)),
        ),
        target_root=root,
        location=f"{record.id}: promote",
    )


def promote_run(
    *,
    manifest_path: Path,
    manifest: dict[str, Any],
    records: list[Any],
    dry_run: bool,
    root: Path,
    format_issue_block: Callable[[str, list[str]], str],
    task_promotion_operations_fn: Callable[[Any], tuple[dict[str, Any], list[dict[str, Any]]]],
    summarize_promotion_readiness_fn: Callable[[list[Any]], dict[str, Any]],
    plan_promotion_fn: Callable[[list[Any]], tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, int]]],
    apply_promotion_operation_fn: Callable[[Any, dict[str, Any]], None],
    blocked_error_factory: type[Exception],
    write_run_checkpoint: Callable[..., dict[str, Any]],
) -> dict[str, Any]:
    readiness = summarize_promotion_readiness_fn(records)
    if not readiness["allowed"] and not dry_run:
        raise blocked_error_factory(format_issue_block("Promotion blocked", list(readiness["blockedReasons"])))
    task_payloads, operations, counts = (
        plan_promotion_fn(records)
        if readiness["allowed"]
        else ([task_promotion_operations_fn(record)[0] for record in records], [], {"added": 0, "modified": 0, "deleted": 0})
    )
    promotable_task_ids = [payload["id"] for payload in task_payloads if payload["filesPromotable"]] if readiness["allowed"] else []
    if not dry_run:
        records_by_id = {record.id: record for record in records}
        for operation in operations:
            apply_promotion_operation_fn(records_by_id[str(operation["taskId"])], operation)
    payload = {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "repoRoot": str(root),
        "dryRun": dry_run,
        "taskCount": len(task_payloads),
        "taskIds": [record.id for record in records],
        "promotionAllowed": bool(readiness["allowed"]),
        "blockedReasons": list(readiness["blockedReasons"]),
        "promotableTaskIds": promotable_task_ids,
        "promotedTaskIds": [] if dry_run else promotable_task_ids,
        "filesPromotable": len(operations),
        "filesPromoted": 0 if dry_run else len(operations),
        "operationCounts": counts,
        "tasks": task_payloads,
    }
    write_run_checkpoint(
        manifest_path,
        manifest,
        checkpoint_name="coordinatorPromotion",
        directory_name="promotion",
        payload=payload,
    )
    return payload
