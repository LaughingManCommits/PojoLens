from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any


TERMINAL_TASK_PHASES = {"task-finished", "task-failed", "task-blocked"}


def default_trace_output_path(manifest_path: Path) -> Path:
    return manifest_path.parent / "trace" / "export.json"


def _checkpoint_timestamp(
    payload: dict[str, Any] | None,
    *,
    manifest_path: Path,
    parse_iso_datetime,
    datetime_to_iso,
) -> str | None:
    if not isinstance(payload, dict):
        return None
    for key in ("generatedAt", "updatedAt", "ts"):
        parsed = parse_iso_datetime(payload.get(key))
        if parsed is not None:
            return datetime_to_iso(parsed)
    summary_path_text = str(payload.get("summaryPath", "")).strip()
    if summary_path_text:
        summary_path = Path(summary_path_text)
        if summary_path.exists():
            return datetime_to_iso(
                datetime.fromtimestamp(summary_path.stat().st_mtime, tz=timezone.utc).astimezone()
            )
    return datetime_to_iso(
        datetime.fromtimestamp(manifest_path.stat().st_mtime, tz=timezone.utc).astimezone()
    )


def _task_ids_in_order(manifest: dict[str, Any], records: list[Any]) -> list[str]:
    ordered = []
    seen: set[str] = set()
    plan_payload = manifest.get("plan")
    if isinstance(plan_payload, dict):
        for task_id in plan_payload.get("taskIds", []) or []:
            normalized = str(task_id).strip()
            if normalized and normalized not in seen:
                ordered.append(normalized)
                seen.add(normalized)
    for record in records:
        if record.id not in seen:
            ordered.append(record.id)
            seen.add(record.id)
    return ordered


def export_trace_payload(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    records: list[Any],
    summarize_run_manifest,
    parse_iso_datetime,
    datetime_to_iso,
) -> dict[str, Any]:
    run_summary, _ = summarize_run_manifest(
        manifest_path,
        manifest,
        now=datetime.now(timezone.utc).astimezone(),
    )
    run_id = str(manifest.get("runId", "")).strip()
    task_ids_in_order = _task_ids_in_order(manifest, records)
    records_by_id = {record.id: record for record in records}
    visible_task_ids = {record.id for record in records}
    events = [event for event in (manifest.get("events") or []) if isinstance(event, dict)]
    event_ts_by_index: dict[int, str] = {}
    task_terminal_event_by_id: dict[str, tuple[int, dict[str, Any]]] = {}
    batch_event_indexes: list[int] = []
    batch_index_by_task_id: dict[str, int] = {}
    batch_ids_by_task_id: dict[str, str] = {}
    context_to_task_id = {record.branch_context_id: record.id for record in records}

    for index, event in enumerate(events):
        parsed_ts = parse_iso_datetime(event.get("ts"))
        if parsed_ts is not None:
            event_ts_by_index[index] = datetime_to_iso(parsed_ts)
        phase = str(event.get("phase", "")).strip()
        if phase == "batch-ready":
            batch_event_indexes.append(index)
        if phase in TERMINAL_TASK_PHASES:
            task_id = str(event.get("taskId", "")).strip()
            if task_id:
                task_terminal_event_by_id[task_id] = (index, event)

    run_start_ts = None
    run_end_ts = None
    for event in events:
        phase = str(event.get("phase", "")).strip()
        parsed_ts = parse_iso_datetime(event.get("ts"))
        if parsed_ts is None:
            continue
        normalized_ts = datetime_to_iso(parsed_ts)
        if phase == "run-start" and run_start_ts is None:
            run_start_ts = normalized_ts
        if phase == "run-finished":
            run_end_ts = normalized_ts
    if run_start_ts is None:
        run_start_ts = run_summary.get("generatedAt") or run_summary.get("lastUpdatedAt")
    if run_end_ts is None:
        run_end_ts = run_summary.get("lastUpdatedAt") or run_start_ts

    spans: list[dict[str, Any]] = []
    run_span_id = f"run:{run_id}"
    spans.append(
        {
            "id": run_span_id,
            "kind": "run",
            "name": str(run_summary.get("plan") or run_id or "run"),
            "parentSpanIds": [],
            "startTs": run_start_ts,
            "endTs": run_end_ts,
            "status": str(run_summary.get("lifecycleState", "unknown")),
            "attributes": {
                "goal": run_summary.get("goal"),
                "dryRun": bool(manifest.get("dryRun", False)),
                "taskCount": int(run_summary.get("taskCount", 0) or 0),
                "topologyBatchCount": int(run_summary.get("topologyBatchCount", 0) or 0),
                "topologyMaxParallelWidth": int(run_summary.get("topologyMaxParallelWidth", 0) or 0),
                "governanceStatus": run_summary.get("governanceStatus"),
                "promotionReady": bool(run_summary.get("promotionReady", False)),
                "traceSummary": run_summary.get("traceSummary"),
                "branchSummary": run_summary.get("branchSummary"),
            },
        }
    )

    for batch_number, event_index in enumerate(batch_event_indexes, start=1):
        event = events[event_index]
        batch_task_ids = [
            task_id
            for task_id in [str(task_id).strip() for task_id in event.get("taskIds", []) or []]
            if task_id and task_id in visible_task_ids
        ]
        if not batch_task_ids:
            continue
        batch_span_id = f"batch:{run_id}:{batch_number}"
        batch_start_ts = event_ts_by_index.get(event_index, run_start_ts)
        batch_end_candidates = [batch_start_ts]
        batch_statuses: list[str] = []
        for task_id in batch_task_ids:
            batch_index_by_task_id[task_id] = batch_number
            batch_ids_by_task_id[task_id] = batch_span_id
            record = records_by_id.get(task_id)
            if record is not None:
                if record.finished_at:
                    batch_end_candidates.append(record.finished_at)
                batch_statuses.append(record.status)
            terminal_event = task_terminal_event_by_id.get(task_id)
            if terminal_event is not None:
                batch_end_candidates.append(
                    event_ts_by_index.get(terminal_event[0], batch_start_ts)
                )
        batch_end_ts = max(item for item in batch_end_candidates if item)
        if any(status == "failed" for status in batch_statuses):
            batch_status = "failed"
        elif any(status == "blocked" for status in batch_statuses):
            batch_status = "blocked"
        elif batch_statuses and all(status == "completed" for status in batch_statuses):
            batch_status = "completed"
        else:
            batch_status = "scheduled"
        spans.append(
            {
                "id": batch_span_id,
                "kind": "batch",
                "name": f"batch-{batch_number}",
                "parentSpanIds": [run_span_id],
                "startTs": batch_start_ts,
                "endTs": batch_end_ts,
                "status": batch_status,
                "attributes": {
                    "batchIndex": batch_number,
                    "taskIds": batch_task_ids,
                    "branchContextIds": [
                        str(item).strip()
                        for item in event.get("branchContextIds", []) or []
                        if str(item).strip()
                    ],
                    "pendingTaskIds": [
                        str(item).strip()
                        for item in (
                            event.get("details", {}).get("pendingTaskIds", [])
                            if isinstance(event.get("details"), dict)
                            else []
                        )
                        if str(item).strip()
                    ],
                    "eventIndex": event_index,
                },
            }
        )

    for task_id in task_ids_in_order:
        if task_id not in visible_task_ids:
            continue
        record = records_by_id[task_id]
        dependency_task_ids: list[str] = []
        for parent_context_id in record.branch_parent_context_ids:
            parent_task_id = context_to_task_id.get(parent_context_id)
            if parent_task_id and parent_task_id not in dependency_task_ids:
                dependency_task_ids.append(parent_task_id)
        terminal_event = task_terminal_event_by_id.get(task_id)
        if terminal_event is not None:
            for parent_task_id in terminal_event[1].get("parentTaskIds", []) or []:
                normalized = str(parent_task_id).strip()
                if normalized and normalized not in dependency_task_ids:
                    dependency_task_ids.append(normalized)
        parent_span_ids = []
        batch_span_id = batch_ids_by_task_id.get(task_id)
        if batch_span_id:
            parent_span_ids.append(batch_span_id)
        else:
            parent_span_ids.append(run_span_id)
        for dependency_task_id in dependency_task_ids:
            if dependency_task_id in visible_task_ids:
                parent_span_ids.append(f"task:{run_id}:{dependency_task_id}")
        parent_span_ids = list(dict.fromkeys(parent_span_ids))
        task_start_ts = record.started_at or run_start_ts
        task_end_ts = record.finished_at or task_start_ts
        if terminal_event is not None:
            task_end_ts = event_ts_by_index.get(terminal_event[0], task_end_ts)
        spans.append(
            {
                "id": f"task:{run_id}:{task_id}",
                "kind": "task",
                "name": record.title,
                "parentSpanIds": parent_span_ids,
                "startTs": task_start_ts,
                "endTs": task_end_ts,
                "status": record.status,
                "attributes": {
                    "taskId": record.id,
                    "agent": record.agent,
                    "summary": record.summary,
                    "branchContextId": record.branch_context_id,
                    "branchParentContextIds": list(record.branch_parent_context_ids),
                    "dependencyTaskIds": dependency_task_ids,
                    "workspaceMode": record.workspace_mode,
                    "workspacePath": record.workspace_path,
                    "model": record.model,
                    "modelProfile": record.model_profile,
                    "effort": record.effort,
                    "effortSource": record.effort_source,
                    "outputProfile": record.output_profile,
                    "outputProfileSource": record.output_profile_source,
                    "promptEstimatedTokens": int(record.prompt_estimated_tokens or 0),
                    "usage.totalCostUsd": (
                        float(record.usage.get("totalCostUsd", 0) or 0)
                        if isinstance(record.usage, dict)
                        else 0.0
                    ),
                    "usage.inputTokens": (
                        int(record.usage.get("inputTokens", 0) or 0)
                        if isinstance(record.usage, dict)
                        else 0
                    ),
                    "usage.outputTokens": (
                        int(record.usage.get("outputTokens", 0) or 0)
                        if isinstance(record.usage, dict)
                        else 0
                    ),
                    "usage.cacheReadTokens": (
                        int(record.usage.get("cacheReadInputTokens", 0) or 0)
                        if isinstance(record.usage, dict)
                        else 0
                    ),
                    "filesTouched": list(record.files_touched),
                    "actualFilesTouched": list(record.actual_files_touched),
                    "validationIntentCount": len(record.validation_intents),
                    "validationCommandCount": len(record.validation_commands),
                    "resultPath": record.result_path,
                    "stdoutPath": record.stdout_path,
                    "stderrPath": record.stderr_path,
                    "batchIndex": batch_index_by_task_id.get(task_id),
                    "terminalEventIndex": terminal_event[0] if terminal_event is not None else None,
                },
            }
        )

    review_payload = manifest.get("coordinatorReview") if isinstance(manifest.get("coordinatorReview"), dict) else None
    validation_payload = (
        manifest.get("coordinatorValidation")
        if isinstance(manifest.get("coordinatorValidation"), dict)
        else None
    )
    promotion_payload = (
        manifest.get("coordinatorPromotion")
        if isinstance(manifest.get("coordinatorPromotion"), dict)
        else None
    )
    review_span_id = None
    if review_payload is not None:
        review_span_id = f"approval:{run_id}:review"
        spans.append(
            {
                "id": review_span_id,
                "kind": "approval",
                "name": "coordinator-review",
                "parentSpanIds": [run_span_id],
                "startTs": _checkpoint_timestamp(
                    review_payload,
                    manifest_path=manifest_path,
                    parse_iso_datetime=parse_iso_datetime,
                    datetime_to_iso=datetime_to_iso,
                ),
                "endTs": _checkpoint_timestamp(
                    review_payload,
                    manifest_path=manifest_path,
                    parse_iso_datetime=parse_iso_datetime,
                    datetime_to_iso=datetime_to_iso,
                ),
                "status": "recorded",
                "attributes": {
                    "summaryPath": review_payload.get("summaryPath"),
                    "taskCount": int(review_payload.get("taskCount", 0) or 0),
                    "changedTaskCount": int(
                        review_payload.get("summary", {}).get("changedTaskCount", 0)
                        if isinstance(review_payload.get("summary"), dict)
                        else 0
                    ),
                    "changedFileCount": int(
                        review_payload.get("summary", {}).get("changedFileCount", 0)
                        if isinstance(review_payload.get("summary"), dict)
                        else 0
                    ),
                },
            }
        )
    validation_span_id = None
    if validation_payload is not None:
        validation_span_id = f"validation:{run_id}"
        spans.append(
            {
                "id": validation_span_id,
                "kind": "validation",
                "name": "coordinator-validation",
                "parentSpanIds": [review_span_id or run_span_id],
                "startTs": _checkpoint_timestamp(
                    validation_payload,
                    manifest_path=manifest_path,
                    parse_iso_datetime=parse_iso_datetime,
                    datetime_to_iso=datetime_to_iso,
                ),
                "endTs": _checkpoint_timestamp(
                    validation_payload,
                    manifest_path=manifest_path,
                    parse_iso_datetime=parse_iso_datetime,
                    datetime_to_iso=datetime_to_iso,
                ),
                "status": "passed" if bool(validation_payload.get("allPassed", False)) else "failed",
                "attributes": {
                    "summaryPath": validation_payload.get("summaryPath"),
                    "executionScope": validation_payload.get("executionScope"),
                    "dryRun": bool(validation_payload.get("dryRun", False)),
                    "commandCount": int(validation_payload.get("commandCount", 0) or 0),
                    "acceptedCommandCount": int(validation_payload.get("acceptedCommandCount", 0) or 0),
                    "rejectedCommandCount": int(validation_payload.get("rejectedCommandCount", 0) or 0),
                    "selectedTaskIds": list(validation_payload.get("selectedTaskIds", []) or []),
                },
            }
        )
    if promotion_payload is not None:
        promotion_status = "blocked"
        if bool(promotion_payload.get("promotionAllowed", False)):
            if bool(promotion_payload.get("dryRun", False)):
                promotion_status = "allowed"
            elif int(promotion_payload.get("filesPromoted", 0) or 0) > 0:
                promotion_status = "applied"
            else:
                promotion_status = "allowed"
        spans.append(
            {
                "id": f"approval:{run_id}:promotion",
                "kind": "approval",
                "name": "coordinator-promotion",
                "parentSpanIds": [validation_span_id or review_span_id or run_span_id],
                "startTs": _checkpoint_timestamp(
                    promotion_payload,
                    manifest_path=manifest_path,
                    parse_iso_datetime=parse_iso_datetime,
                    datetime_to_iso=datetime_to_iso,
                ),
                "endTs": _checkpoint_timestamp(
                    promotion_payload,
                    manifest_path=manifest_path,
                    parse_iso_datetime=parse_iso_datetime,
                    datetime_to_iso=datetime_to_iso,
                ),
                "status": promotion_status,
                "attributes": {
                    "summaryPath": promotion_payload.get("summaryPath"),
                    "dryRun": bool(promotion_payload.get("dryRun", False)),
                    "promotionAllowed": bool(promotion_payload.get("promotionAllowed", False)),
                    "blockedReasons": list(promotion_payload.get("blockedReasons", []) or []),
                    "filesPromotable": int(promotion_payload.get("filesPromotable", 0) or 0),
                    "filesPromoted": int(promotion_payload.get("filesPromoted", 0) or 0),
                    "promotableTaskIds": list(promotion_payload.get("promotableTaskIds", []) or []),
                },
            }
        )

    kind_counts: dict[str, int] = {}
    status_counts: dict[str, int] = {}
    for span in spans:
        kind = str(span["kind"])
        status = str(span["status"])
        kind_counts[kind] = kind_counts.get(kind, 0) + 1
        status_counts[status] = status_counts.get(status, 0) + 1

    return {
        "traceFormat": "pojo-lens-orchestrator-trace/v1",
        "exportedAt": datetime.now(timezone.utc).astimezone().isoformat(),
        "runId": run_id,
        "manifestPath": str(manifest_path),
        "runDir": str(manifest_path.parent),
        "taskFilter": [record.id for record in records],
        "spanCount": len(spans),
        "kindCounts": kind_counts,
        "statusCounts": status_counts,
        "runSummary": {
            "lifecycleState": run_summary.get("lifecycleState"),
            "promotionReady": bool(run_summary.get("promotionReady", False)),
            "traceSummary": run_summary.get("traceSummary"),
            "branchSummary": run_summary.get("branchSummary"),
        },
        "spans": spans,
    }


def export_trace_run(args: Any, *, deps: dict[str, Any]) -> dict[str, Any]:
    manifest_path, manifest = deps["load_run_manifest"](args.run_ref)
    records = deps["selected_run_records"](manifest, getattr(args, "selected_tasks", []))
    payload = export_trace_payload(
        manifest_path,
        manifest,
        records=records,
        summarize_run_manifest=deps["summarize_run_manifest"],
        parse_iso_datetime=deps["parse_iso_datetime"],
        datetime_to_iso=deps["datetime_to_iso"],
    )
    output_path = (
        Path(str(args.out)).resolve()
        if str(getattr(args, "out", "")).strip()
        else default_trace_output_path(manifest_path)
    )
    payload["tracePath"] = str(output_path)
    payload["dryRun"] = bool(getattr(args, "dry_run", False))
    endpoint = deps["resolve_otel_endpoint"](getattr(args, "otel_endpoint", ""))
    if endpoint:
        if payload["dryRun"]:
            payload["otel"] = {
                "enabled": True,
                "endpoint": endpoint,
                "emitted": False,
                "reason": "dry-run",
            }
        else:
            payload["otel"] = deps["emit_otel_trace"](payload, endpoint=endpoint)
    else:
        payload["otel"] = {
            "enabled": False,
            "endpoint": None,
            "emitted": False,
            "reason": "disabled",
        }
    if not payload["dryRun"]:
        deps["write_json"](output_path, payload)
    return payload
