from __future__ import annotations

import os
import shlex
import subprocess
from dataclasses import asdict
from pathlib import Path
from typing import Any, Callable

DOC_TEXT_SUFFIXES = frozenset({".md", ".txt", ".adoc", ".rst"})
DOC_TEXT_FILENAMES = frozenset({"readme", "changelog", "contributing", "license"})
DOCS_CONSISTENCY_COMMAND = "scripts/docs/check-doc-consistency.ps1"


def is_doc_text_path(path_value: str) -> bool:
    path = Path(path_value)
    return path.suffix.lower() in DOC_TEXT_SUFFIXES or path.name.lower() in DOC_TEXT_FILENAMES


def effective_changed_paths(record: Any) -> list[str]:
    actual_paths = [str(path).strip() for path in (record.actual_files_touched or []) if str(path).strip()]
    if actual_paths:
        return actual_paths
    return [str(path).strip() for path in (record.files_touched or []) if str(path).strip()]


def validation_execution_target(
    record: Any,
    *,
    execution_scope: str,
    validate_run_execution_scopes: set[str],
    root: Path,
    error_factory: type[Exception],
) -> dict[str, Any]:
    if execution_scope not in validate_run_execution_scopes:
        raise error_factory(f"validate-run: unsupported execution scope '{execution_scope}'")
    if execution_scope == "repo":
        return {
            "cwd": str(root),
            "workspaceMode": "repo",
            "workspacePath": str(root),
            "accepted": True,
            "reason": None,
        }
    if record.workspace_mode == "repo":
        return {
            "cwd": str(root),
            "workspaceMode": "repo",
            "workspacePath": str(root),
            "accepted": True,
            "reason": None,
        }
    if not record.workspace_path:
        return {
            "cwd": None,
            "workspaceMode": record.workspace_mode,
            "workspacePath": None,
            "accepted": False,
            "reason": f"{record.id}: task workspace path is missing",
        }
    workspace_root = Path(record.workspace_path).resolve()
    if not workspace_root.exists():
        return {
            "cwd": str(workspace_root),
            "workspaceMode": record.workspace_mode,
            "workspacePath": str(workspace_root),
            "accepted": False,
            "reason": f"{record.id}: task workspace '{workspace_root}' does not exist",
        }
    return {
        "cwd": str(workspace_root),
        "workspaceMode": record.workspace_mode,
        "workspacePath": str(workspace_root),
        "accepted": True,
        "reason": None,
    }


def collect_validation_commands(
    records: list[Any],
    *,
    included_statuses: set[str],
    execution_scope: str,
    default_validate_run_execution_scope: str,
    validation_execution_target_fn: Callable[..., dict[str, Any]],
    dedupe_strings: Callable[[list[str]], list[str]],
    validation_intent_command_text: Callable[[Any], str],
    validation_intent_policy: Callable[[Any], dict[str, Any]],
    validation_command_policy: Callable[[str], dict[str, Any]],
    worker_field_unknown: Callable[[Any, str], bool],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    task_payloads: list[dict[str, Any]] = []
    suggestions_by_command: dict[tuple[str, str | None], dict[str, Any]] = {}
    suggestion_order: list[tuple[str, str | None]] = []
    for record in records:
        execution_target = validation_execution_target_fn(record, execution_scope=execution_scope)
        commands = dedupe_strings(record.validation_commands)
        rendered_intents = dedupe_strings([validation_intent_command_text(intent) for intent in record.validation_intents])
        changed_paths = effective_changed_paths(record)
        docs_only_touched_files = bool(changed_paths) and all(
            is_doc_text_path(path_value) for path_value in changed_paths
        )
        has_docs_validation_suggestion = (
            DOCS_CONSISTENCY_COMMAND in commands or DOCS_CONSISTENCY_COMMAND in rendered_intents
        )
        included = record.status in included_statuses
        task_payloads.append(
            {
                "id": record.id,
                "status": record.status,
                "summary": record.summary,
                "unknownFields": record.unknown_fields,
                "validationIntents": [asdict(intent) for intent in record.validation_intents],
                "renderedValidationIntents": rendered_intents,
                "validationCommands": commands,
                "validationCommandsKnown": not worker_field_unknown(record, "validationCommands"),
                "legacyValidationCommandCount": len(commands),
                "legacyValidationCommandsPresent": bool(commands),
                "includedForValidation": included,
                "workspaceMode": record.workspace_mode,
                "workspacePath": record.workspace_path,
                "executionScope": execution_scope,
                "executionCwd": execution_target.get("cwd"),
                "executionTargetAccepted": bool(execution_target.get("accepted", False)),
                "executionTargetReason": execution_target.get("reason"),
                "changedPaths": changed_paths,
                "docsOnlyTouchedFiles": docs_only_touched_files,
                "docsValidationRecommended": (
                    included
                    and execution_scope == "repo"
                    and docs_only_touched_files
                    and not has_docs_validation_suggestion
                ),
                "excludedReason": None if included else f"status '{record.status}' is excluded by the current validation policy",
            }
        )
        if not included:
            continue
        for intent in record.validation_intents:
            command_text = validation_intent_command_text(intent)
            aggregate_key = (
                command_text,
                str(execution_target.get("cwd") or "") if execution_scope == "task-workspace" else default_validate_run_execution_scope,
            )
            payload = suggestions_by_command.get(aggregate_key)
            if payload is None:
                payload = {
                    "sourceKind": "intent",
                    "command": command_text,
                    "taskIds": [],
                    "policy": validation_intent_policy(intent),
                    "intent": asdict(intent),
                    "compatibilityOnly": False,
                    "executionScope": execution_scope,
                    "cwd": execution_target.get("cwd"),
                    "workspaceMode": execution_target.get("workspaceMode"),
                    "workspacePath": execution_target.get("workspacePath"),
                    "executionTargetAccepted": bool(execution_target.get("accepted", False)),
                    "executionTargetReason": execution_target.get("reason"),
                }
                suggestions_by_command[aggregate_key] = payload
                suggestion_order.append(aggregate_key)
            payload["taskIds"].append(record.id)
        for command in commands:
            policy = validation_command_policy(command)
            aggregate_key = (
                command,
                str(execution_target.get("cwd") or "") if execution_scope == "task-workspace" else default_validate_run_execution_scope,
            )
            payload = suggestions_by_command.get(aggregate_key)
            if payload is None:
                payload = {
                    "sourceKind": "command",
                    "command": command,
                    "taskIds": [],
                    "policy": policy,
                    "intent": None,
                    "compatibilityOnly": True,
                    "normalizedIntent": policy.get("intent") if isinstance(policy.get("intent"), dict) else None,
                    "executionScope": execution_scope,
                    "cwd": execution_target.get("cwd"),
                    "workspaceMode": execution_target.get("workspaceMode"),
                    "workspacePath": execution_target.get("workspacePath"),
                    "executionTargetAccepted": bool(execution_target.get("accepted", False)),
                    "executionTargetReason": execution_target.get("reason"),
                }
                suggestions_by_command[aggregate_key] = payload
                suggestion_order.append(aggregate_key)
            payload["taskIds"].append(record.id)
        if execution_scope == "repo" and docs_only_touched_files and not has_docs_validation_suggestion:
            aggregate_key = (DOCS_CONSISTENCY_COMMAND, default_validate_run_execution_scope)
            payload = suggestions_by_command.get(aggregate_key)
            if payload is None:
                payload = {
                    "sourceKind": "coordinator-helper",
                    "helperKind": "docs-consistency",
                    "command": DOCS_CONSISTENCY_COMMAND,
                    "taskIds": [],
                    "policy": validation_command_policy(DOCS_CONSISTENCY_COMMAND),
                    "intent": None,
                    "compatibilityOnly": False,
                    "executionScope": execution_scope,
                    "cwd": execution_target.get("cwd"),
                    "workspaceMode": "repo",
                    "workspacePath": str(Path(default_validate_run_execution_scope)),
                    "executionTargetAccepted": True,
                    "executionTargetReason": None,
                }
                suggestions_by_command[aggregate_key] = payload
                suggestion_order.append(aggregate_key)
            payload["taskIds"].append(record.id)
    command_payloads = []
    for aggregate_key in suggestion_order:
        payload = suggestions_by_command[aggregate_key]
        payload["taskIds"] = dedupe_strings([str(task_id) for task_id in payload["taskIds"]])
        command_payloads.append(payload)
    return task_payloads, command_payloads


def run_shell_command_text(
    command_text: str,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: Any,
    run_process: Callable[..., subprocess.CompletedProcess[str]],
) -> subprocess.CompletedProcess[str]:
    try:
        tokens = shlex.split(command_text, posix=(os.name != "nt"))
    except ValueError:
        tokens = None
    timeout_error = f"Validation command timed out after {timeout_sec} seconds: {command_text}"
    if tokens:
        return run_process(
            tokens,
            cwd=cwd,
            timeout_sec=timeout_sec,
            shell=False,
            progress_action=progress_action,
            timeout_error=timeout_error,
        )
    return run_process(
        command_text,
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=True,
        progress_action=progress_action,
        timeout_error=timeout_error,
    )


def run_validation_intent(
    intent: Any,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: Any,
    run_process: Callable[..., subprocess.CompletedProcess[str]],
    validation_intent_execution_tokens: Callable[[Any], list[str]],
    validation_intent_command_text: Callable[[Any], str],
) -> subprocess.CompletedProcess[str]:
    return run_process(
        validation_intent_execution_tokens(intent),
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=False,
        progress_action=progress_action,
        timeout_error=("Validation intent timed out after " f"{timeout_sec} seconds: {validation_intent_command_text(intent)}"),
    )


def write_run_checkpoint(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    checkpoint_name: str,
    directory_name: str,
    payload: dict[str, Any],
    write_json: Callable[[Path, object], None],
) -> dict[str, Any]:
    checkpoint_dir = manifest_path.parent / directory_name
    summary_path = checkpoint_dir / "summary.json"
    write_json(summary_path, payload)
    updated_manifest = dict(manifest)
    updated_manifest[checkpoint_name] = {**payload, "summaryPath": str(summary_path)}
    write_json(manifest_path, updated_manifest)
    return updated_manifest


def write_coordinator_validation_summary(
    manifest_path: Path,
    manifest: dict[str, Any],
    summary: dict[str, Any],
    *,
    write_run_checkpoint_fn: Callable[..., dict[str, Any]],
) -> dict[str, Any]:
    return write_run_checkpoint_fn(
        manifest_path,
        manifest,
        checkpoint_name="coordinatorValidation",
        directory_name="validation",
        payload=summary,
    )


def validate_run(
    *,
    manifest_path: Path,
    manifest: dict[str, Any],
    run_dir: Path,
    records: list[Any],
    intents_only: bool,
    execution_scope: str,
    included_statuses: set[str],
    allow_unsafe_commands: bool,
    continue_on_error: bool,
    timeout_sec: int,
    dry_run: bool,
    root: Path,
    default_validate_run_execution_scope: str,
    collect_validation_commands_fn: Callable[..., tuple[list[dict[str, Any]], list[dict[str, Any]]]],
    coerce_validation_intent_payload: Callable[..., Any],
    run_validation_intent_fn: Callable[..., subprocess.CompletedProcess[str]],
    run_shell_command_text_fn: Callable[..., subprocess.CompletedProcess[str]],
    write_text: Callable[[Path, str | None], None],
    write_json: Callable[[Path, object], None],
    slugify: Callable[[str], str],
    validation_wait_action: Callable[[str, str], Any],
    count_statuses: Callable[[list[str]], dict[str, int]],
    write_coordinator_validation_summary_fn: Callable[[Path, dict[str, Any], dict[str, Any]], dict[str, Any]],
) -> dict[str, Any]:
    task_payloads, commands = collect_validation_commands_fn(
        records,
        included_statuses=included_statuses,
        execution_scope=execution_scope,
    )
    validation_dir = run_dir / "validation"
    command_results: list[dict[str, Any]] = []
    stop_after_failure = False
    for index, command_payload in enumerate(commands, start=1):
        command_text = str(command_payload["command"])
        source_kind = str(command_payload.get("sourceKind", "command"))
        intent_payload = command_payload.get("intent")
        normalized_intent_payload = command_payload.get("normalizedIntent")
        execution_intent_payload = (
            intent_payload if isinstance(intent_payload, dict) else normalized_intent_payload if isinstance(normalized_intent_payload, dict) else None
        )
        execution_cwd = command_payload.get("cwd")
        execution_target_accepted = bool(command_payload.get("executionTargetAccepted", False))
        execution_target_reason = command_payload.get("executionTargetReason")
        quality_policy = command_payload["policy"] if isinstance(command_payload.get("policy"), dict) else {"accepted": True, "reason": None, "entrypoint": None}
        policy = dict(quality_policy)
        intents_only_rejected = source_kind == "command" and intents_only
        if intents_only_rejected:
            policy = {
                "accepted": False,
                "reason": "raw validationCommands are compatibility-only under --intents-only; emit structured validationIntents instead",
                "entrypoint": quality_policy.get("entrypoint"),
            }
        policy_accepted = bool(policy.get("accepted", False))
        policy_override = bool(allow_unsafe_commands and not policy_accepted and not intents_only_rejected)
        result_payload: dict[str, Any] = {
            "index": index,
            "sourceKind": source_kind,
            "helperKind": command_payload.get("helperKind"),
            "command": command_text,
            "taskIds": list(command_payload["taskIds"]),
            "intent": intent_payload if isinstance(intent_payload, dict) else None,
            "normalizedIntent": normalized_intent_payload if isinstance(normalized_intent_payload, dict) else None,
            "compatibilityOnly": bool(command_payload.get("compatibilityOnly", source_kind == "command")),
            "executionScope": execution_scope,
            "cwd": execution_cwd,
            "workspaceMode": command_payload.get("workspaceMode"),
            "workspacePath": command_payload.get("workspacePath"),
            "executionTargetAccepted": execution_target_accepted,
            "executionTargetReason": execution_target_reason,
            "intentsOnlyRejected": intents_only_rejected,
            "executionKind": "argv" if execution_intent_payload is not None else "shell",
            "qualityPolicyAccepted": bool(quality_policy.get("accepted", False)),
            "qualityPolicyReason": quality_policy.get("reason"),
            "policyAccepted": policy_accepted,
            "policyReason": policy.get("reason"),
            "policyOverride": policy_override,
            "entrypoint": policy.get("entrypoint"),
            "status": "planned" if dry_run else "completed",
            "returnCode": None,
            "stdoutPath": None,
            "stderrPath": None,
        }
        if not execution_target_accepted:
            result_payload["status"] = "rejected"
            command_results.append(result_payload)
            continue
        if not policy_accepted and not policy_override:
            result_payload["status"] = "rejected"
            command_results.append(result_payload)
            continue
        if stop_after_failure:
            result_payload["status"] = "skipped"
            command_results.append(result_payload)
            continue
        if dry_run:
            command_results.append(result_payload)
            continue
        command_dir = validation_dir / f"{index:02d}-{slugify(command_text[:48])}"
        stdout_path = command_dir / "stdout.txt"
        stderr_path = command_dir / "stderr.txt"
        command_path = command_dir / "command.txt"
        write_text(command_path, command_text + "\n")
        if execution_intent_payload is not None:
            write_json(command_dir / "intent.json", execution_intent_payload)
        command_cwd = Path(str(execution_cwd)).resolve()
        try:
            if execution_intent_payload is not None:
                completed = run_validation_intent_fn(
                    coerce_validation_intent_payload(execution_intent_payload, location=f"validate-run:{index}:intent"),
                    cwd=command_cwd,
                    timeout_sec=max(timeout_sec, 1),
                    progress_action=validation_wait_action(command_text, "intent"),
                )
            else:
                completed = run_shell_command_text_fn(
                    command_text,
                    cwd=command_cwd,
                    timeout_sec=max(timeout_sec, 1),
                    progress_action=validation_wait_action(command_text, "command"),
                )
            write_text(stdout_path, completed.stdout)
            write_text(stderr_path, completed.stderr)
            result_payload["returnCode"] = completed.returncode
            result_payload["stdoutPath"] = str(stdout_path)
            result_payload["stderrPath"] = str(stderr_path)
            if completed.returncode != 0:
                result_payload["status"] = "failed"
                if not continue_on_error:
                    stop_after_failure = True
            command_results.append(result_payload)
        except Exception as exc:
            write_text(stderr_path, str(exc) + "\n")
            result_payload["status"] = "failed"
            result_payload["returnCode"] = None
            result_payload["stdoutPath"] = str(stdout_path) if stdout_path.exists() else None
            result_payload["stderrPath"] = str(stderr_path)
            command_results.append(result_payload)
            if not continue_on_error:
                stop_after_failure = True
    summary = {
        "generatedAt": __import__("datetime").datetime.now().astimezone().isoformat(),
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "repoRoot": str(root),
        "executionScope": execution_scope,
        "dryRun": dry_run,
        "intentsOnly": intents_only,
        "selectedTaskIds": [record.id for record in records],
        "includedStatuses": sorted(included_statuses),
        "taskCount": len(task_payloads),
        "tasks": task_payloads,
        "includedTaskIds": [payload["id"] for payload in task_payloads if payload["includedForValidation"]],
        "excludedTaskIds": [payload["id"] for payload in task_payloads if not payload["includedForValidation"]],
        "legacyValidationCommandCount": sum(int(payload.get("legacyValidationCommandCount", 0) or 0) for payload in task_payloads),
        "includedLegacyValidationCommandCount": sum(int(payload.get("legacyValidationCommandCount", 0) or 0) for payload in task_payloads if payload["includedForValidation"]),
        "legacyValidationCommandTaskIds": [payload["id"] for payload in task_payloads if payload.get("legacyValidationCommandsPresent")],
        "includedLegacyValidationCommandTaskIds": [payload["id"] for payload in task_payloads if payload["includedForValidation"] and payload.get("legacyValidationCommandsPresent")],
        "commandCount": len(command_results),
        "acceptedCommandCount": sum(1 for payload in command_results if payload.get("policyAccepted") and payload.get("executionTargetAccepted")),
        "rejectedCommandCount": sum(1 for payload in command_results if payload["status"] == "rejected"),
        "intentsOnlyRejectedCommandCount": sum(1 for payload in command_results if payload.get("intentsOnlyRejected")),
        "allowUnsafeCommands": bool(allow_unsafe_commands),
        "suggestedCommands": [payload["command"] for payload in commands],
        "suggestedValidationIntents": [payload["intent"] for payload in commands if isinstance(payload.get("intent"), dict)],
        "commands": command_results,
        "statusCounts": count_statuses([str(payload["status"]) for payload in command_results]),
        "allPassed": all(payload["status"] in {"planned", "completed"} for payload in command_results),
    }
    write_coordinator_validation_summary_fn(manifest_path, manifest, summary)
    return summary
