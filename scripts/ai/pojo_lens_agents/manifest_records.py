from __future__ import annotations

from typing import Any

from pydantic import ValidationError

from pojo_lens_agents.orchestrator_models import (
    TaskRunRecordModel,
    dump_contract,
    validation_error_summary,
)


def count_statuses(values: list[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    return counts


def task_branch_parent_context_ids(
    task: Any,
    dependency_records: dict[str, Any] | None = None,
) -> list[str]:
    dependency_records = dependency_records or {}
    parent_context_ids: list[str] = []
    for dependency_id in task.depends_on:
        dependency_record = dependency_records.get(dependency_id)
        context_id = dependency_record.branch_context_id if dependency_record is not None else dependency_id
        if context_id and context_id not in parent_context_ids:
            parent_context_ids.append(context_id)
    return parent_context_ids


def task_branch_context_id(
    task: Any,
    dependency_records: dict[str, Any] | None = None,
) -> str:
    parent_context_ids = task_branch_parent_context_ids(task, dependency_records)
    if not parent_context_ids:
        return task.id
    return f"{task.id}<-{','.join(parent_context_ids)}"


def coerce_task_run_record(payload: Any, *, location: str, deps: dict[str, Any]) -> Any:
    if not isinstance(payload, dict):
        raise deps["error_factory"](f"{location}: expected task record object")
    raw_budget_payload = payload.get("prompt_budget")
    budget_payload: dict[str, Any] = raw_budget_payload if isinstance(raw_budget_payload, dict) else {}
    record = deps["task_run_record_factory"](
        id=str(payload.get("id", "")),
        title=str(payload.get("title", "")),
        agent=str(payload.get("agent", "")),
        resolved_skills=[str(item) for item in payload.get("resolved_skills", []) or []],
        branch_context_id=str(payload.get("branch_context_id", payload.get("id", ""))),
        branch_parent_context_ids=[str(item) for item in payload.get("branch_parent_context_ids", []) or []],
        status=str(payload.get("status", "")),
        summary=str(payload.get("summary", "")),
        workspace_mode=str(payload.get("workspace_mode", "")),
        workspace_path=str(payload.get("workspace_path", "")),
        started_at=str(payload.get("started_at", "")),
        finished_at=str(payload.get("finished_at", "")),
        files_touched=[str(item) for item in payload.get("files_touched", []) or []],
        actual_files_touched=[str(item) for item in payload.get("actual_files_touched", []) or []],
        protected_path_violations=[str(item) for item in payload.get("protected_path_violations", []) or []],
        write_scope_violations=[str(item) for item in payload.get("write_scope_violations", []) or []],
        validation_commands=[str(item) for item in payload.get("validation_commands", []) or []],
        follow_ups=[str(item) for item in payload.get("follow_ups", []) or []],
        notes=[str(item) for item in payload.get("notes", []) or []],
        model=str(payload["model"]) if payload.get("model") is not None else None,
        model_profile=str(payload["model_profile"]) if payload.get("model_profile") is not None else None,
        output_profile=(
            deps["normalize_output_profile"](
                payload.get("output_profile"),
                location=f"{location}:output_profile",
            )
            or deps["default_output_profile"]
        ),
        output_profile_source=deps["normalize_output_profile_source"](
            payload.get("output_profile_source"),
            location=f"{location}:output_profile_source",
        ),
        prompt_chars=int(payload.get("prompt_chars", 0) or 0),
        prompt_estimated_tokens=int(payload.get("prompt_estimated_tokens", 0) or 0),
        prompt_sections=[
            deps["prompt_section_metric_factory"](
                name=str(section.get("name", "")),
                heading=str(section.get("heading", "")),
                chars=int(section.get("chars", 0) or 0),
                estimated_tokens=int(section.get("estimated_tokens", 0) or 0),
                item_count=int(section.get("item_count", 0) or 0),
                truncated=bool(section.get("truncated", False)),
            )
            for section in payload.get("prompt_sections", []) or []
            if isinstance(section, dict)
        ],
        prompt_budget=deps["prompt_budget_result_factory"](
            max_chars=int(budget_payload["max_chars"]) if budget_payload.get("max_chars") is not None else None,
            max_estimated_tokens=(
                int(budget_payload["max_estimated_tokens"])
                if budget_payload.get("max_estimated_tokens") is not None
                else None
            ),
            exceeded=bool(budget_payload.get("exceeded", False)),
            violations=[str(item) for item in budget_payload.get("violations", [])],
        ),
        usage=payload.get("usage") if isinstance(payload.get("usage"), dict) else None,
        return_code=int(payload["return_code"]) if payload.get("return_code") is not None else None,
        prompt_path=str(payload.get("prompt_path", "")),
        command_path=str(payload.get("command_path", "")),
        stdout_path=str(payload["stdout_path"]) if payload.get("stdout_path") is not None else None,
        stderr_path=str(payload["stderr_path"]) if payload.get("stderr_path") is not None else None,
        result_path=str(payload["result_path"]) if payload.get("result_path") is not None else None,
        stdout_bytes=int(payload.get("stdout_bytes", 0) or 0),
        stderr_bytes=int(payload.get("stderr_bytes", 0) or 0),
        result_bytes=int(payload.get("result_bytes", 0) or 0),
        validation_intents=[
            deps["coerce_validation_intent_payload"](
                item,
                location=f"{location}:validation_intents",
            )
            for item in payload.get("validation_intents", []) or []
        ],
        unknown_fields=deps["normalized_worker_unknown_fields"](payload.get("unknown_fields")),
        dependency_materialization_mode=(
            deps["normalize_dependency_materialization_mode"](
                payload.get("dependency_materialization_mode"),
                location=f"{location}:dependency_materialization_mode",
            )
            or deps["default_dependency_materialization_mode"]
        ),
        dependency_layers_applied=[
            deps["coerce_dependency_layer_record_payload"](
                item,
                location=f"{location}:dependency_layers_applied[{index}]",
            )
            for index, item in enumerate(payload.get("dependency_layers_applied", []) or [], start=1)
        ],
        worker_validation_mode=deps["normalize_worker_validation_mode"](
            payload.get("worker_validation_mode"),
            location=f"{location}:worker_validation_mode",
        ),
        worker_validation_mode_source=deps["normalize_worker_validation_mode_source"](
            payload.get("worker_validation_mode_source"),
            location=f"{location}:worker_validation_mode_source",
        ),
        effort=deps["normalize_effort_override"](
            payload.get("effort"),
            location=f"{location}:effort",
        ),
        effort_source=deps["require_optional_string"](payload, "effort_source", location=location),
        reviewer_findings=[
            deps["reviewer_finding_factory"](
                severity=str(item.get("severity", "")),
                message=str(item.get("message", "")),
            )
            for item in payload.get("reviewer_findings", []) or []
            if isinstance(item, dict)
        ],
        attempt=int(payload.get("attempt", 1) or 1),
        attempt_errors=[
            item
            for item in payload.get("attempt_errors", []) or []
            if isinstance(item, dict)
        ],
    )
    try:
        TaskRunRecordModel.model_validate(dump_contract(record))
    except ValidationError as exc:
        raise deps["error_factory"](
            f"{location}: typed task record validation failed: {validation_error_summary(exc)}"
        ) from exc
    return record


def selected_run_records(
    manifest: dict[str, Any],
    selected_ids: list[str],
    *,
    coerce_task_run_record,
    error_factory,
) -> list[Any]:
    raw_tasks = manifest.get("tasks", {})
    if not isinstance(raw_tasks, dict):
        raise error_factory("Manifest does not contain task records")
    selected = set(selected_ids)
    records = []
    for task_id in sorted(raw_tasks):
        if selected and task_id not in selected:
            continue
        records.append(coerce_task_run_record(raw_tasks[task_id], location=f"manifest:tasks[{task_id}]"))
    missing = sorted(selected.difference(record.id for record in records))
    if missing:
        raise error_factory(f"Unknown task ids in run manifest: {missing}")
    return records
