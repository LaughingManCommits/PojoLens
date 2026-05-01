from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable


def summarize_branch_contexts(records: list[Any]) -> dict[str, Any]:
    by_context: dict[str, dict[str, Any]] = {}
    root_context_ids: list[str] = []
    leaf_context_ids: list[str] = []
    all_parent_context_ids: set[str] = set()
    for record in records:
        by_context[record.branch_context_id] = {
            "taskId": record.id,
            "status": record.status,
            "parentContextIds": list(record.branch_parent_context_ids),
        }
        if not record.branch_parent_context_ids:
            root_context_ids.append(record.branch_context_id)
        for parent_context_id in record.branch_parent_context_ids:
            all_parent_context_ids.add(parent_context_id)
    for record in records:
        if record.branch_context_id not in all_parent_context_ids:
            leaf_context_ids.append(record.branch_context_id)
    return {
        "contextCount": len(by_context),
        "rootContextIds": root_context_ids,
        "leafContextIds": leaf_context_ids,
        "contexts": by_context,
    }


def summarize_run_events(events_payload: Any) -> dict[str, Any]:
    events = events_payload if isinstance(events_payload, list) else []
    phase_counts: dict[str, int] = {}
    task_ids_referenced: list[str] = []
    parent_task_ids_referenced: list[str] = []
    branch_context_ids_referenced: list[str] = []
    latest_phase: str | None = None
    latest_ts: str | None = None
    event_count = 0
    for event in events:
        if not isinstance(event, dict):
            continue
        event_count += 1
        phase = str(event.get("phase", "")).strip()
        if phase:
            phase_counts[phase] = phase_counts.get(phase, 0) + 1
            latest_phase = phase
        ts = str(event.get("ts", "")).strip()
        if ts:
            latest_ts = ts
        task_id = str(event.get("taskId", "")).strip()
        if task_id and task_id not in task_ids_referenced:
            task_ids_referenced.append(task_id)
        branch_context_id = str(event.get("branchContextId", "")).strip()
        if branch_context_id and branch_context_id not in branch_context_ids_referenced:
            branch_context_ids_referenced.append(branch_context_id)
        for referenced_task_id in event.get("taskIds", []) or []:
            normalized_task_id = str(referenced_task_id).strip()
            if normalized_task_id and normalized_task_id not in task_ids_referenced:
                task_ids_referenced.append(normalized_task_id)
        for referenced_context_id in event.get("branchContextIds", []) or []:
            normalized_context_id = str(referenced_context_id).strip()
            if normalized_context_id and normalized_context_id not in branch_context_ids_referenced:
                branch_context_ids_referenced.append(normalized_context_id)
        for parent_task_id in event.get("parentTaskIds", []) or []:
            normalized_parent_task_id = str(parent_task_id).strip()
            if (
                normalized_parent_task_id
                and normalized_parent_task_id not in parent_task_ids_referenced
            ):
                parent_task_ids_referenced.append(normalized_parent_task_id)
    return {
        "eventCount": event_count,
        "phaseCounts": phase_counts,
        "latestPhase": latest_phase,
        "latestTs": latest_ts,
        "taskIdsReferenced": task_ids_referenced,
        "parentTaskIdsReferenced": parent_task_ids_referenced,
        "branchContextIdsReferenced": branch_context_ids_referenced,
    }


def summarize_approval_checkpoints(manifest: dict[str, Any]) -> dict[str, Any]:
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
    promotion_allowed = None
    promotion_dry_run = None
    promotion_applied = False
    promotion_files = None
    if promotion_payload is not None:
        promotion_allowed = bool(promotion_payload.get("promotionAllowed", False))
        promotion_dry_run = bool(promotion_payload.get("dryRun", False))
        promotion_files = int(promotion_payload.get("filesPromoted", 0) or 0)
        promotion_applied = promotion_allowed and not promotion_dry_run and promotion_files > 0
    validation_passed = None
    if validation_payload is not None:
        validation_passed = bool(validation_payload.get("allPassed", False))
    return {
        "reviewRecorded": review_payload is not None,
        "reviewSummaryPath": review_payload.get("summaryPath") if review_payload is not None else None,
        "validationRecorded": validation_payload is not None,
        "validationPassed": validation_passed,
        "validationSummaryPath": validation_payload.get("summaryPath") if validation_payload is not None else None,
        "promotionRecorded": promotion_payload is not None,
        "promotionAllowed": promotion_allowed,
        "promotionDryRun": promotion_dry_run,
        "promotionApplied": promotion_applied,
        "promotionFilesPromoted": promotion_files,
        "promotionSummaryPath": promotion_payload.get("summaryPath") if promotion_payload is not None else None,
    }


def derive_run_lifecycle_state(
    *,
    records: list[Any],
    summary_base: dict[str, Any],
    promotion_readiness: dict[str, Any],
    approval_summary: dict[str, Any],
) -> tuple[str, str]:
    if summary_base["hasFailures"]:
        return "failed", "At least one retained task failed."
    if summary_base["hasBlocked"]:
        return "blocked", "At least one retained task is blocked."
    if summary_base["isResumable"]:
        return "awaiting_execution", "Run still has planned or incomplete retained tasks."

    changed_completed_task_ids = sorted(
        record.id
        for record in records
        if record.status == "completed" and (record.actual_files_touched or record.files_touched)
    )
    if not changed_completed_task_ids:
        return "completed", "Run completed without promotable file changes."
    if approval_summary["promotionApplied"]:
        return "completed", "Coordinator promotion has already been applied."
    if not approval_summary["reviewRecorded"]:
        return "awaiting_review", "Completed changes are present but coordinator validation has not been recorded yet."
    if not approval_summary["validationRecorded"]:
        return "awaiting_validation", "Coordinator review is recorded, but validation has not been run yet."
    if not bool(approval_summary["validationPassed"]):
        return "awaiting_validation", "Coordinator validation is present but not yet passing."
    if promotion_readiness["allowed"] and promotion_readiness["filesPromotable"] > 0:
        if approval_summary["promotionRecorded"] and not bool(approval_summary["promotionAllowed"]):
            return "awaiting_promotion", "Validated changes still need coordinator promotion after the latest promotion refusal."
        return "awaiting_promotion", "Validated changes are ready for coordinator promotion."
    return "completed", "Coordinator validation passed, but there are no promotable retained file changes."


def summarize_run_manifest(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    now: datetime | None,
    manifest_run_dir: Callable[[Path, dict[str, Any]], Path],
    manifest_workspaces_dir: Callable[..., Path],
    selected_run_records: Callable[[dict[str, Any], list[str]], list[Any]],
    count_statuses: Callable[[list[str]], dict[str, int]],
    summarize_promotion_readiness: Callable[[list[Any]], dict[str, Any]],
    parse_iso_datetime: Callable[[Any], datetime | None],
    datetime_to_iso: Callable[[datetime], str],
) -> tuple[dict[str, Any], datetime]:
    now = now or datetime.now(timezone.utc).astimezone()
    run_dir = manifest_run_dir(manifest_path, manifest)
    workspaces_dir = manifest_workspaces_dir(manifest, run_dir=run_dir)
    records = selected_run_records(manifest, [])
    plan_payload = manifest.get("plan")
    if isinstance(plan_payload, dict):
        plan_name = str(plan_payload.get("name", ""))
        plan_goal = str(plan_payload.get("goal", ""))
        plan_task_ids = [str(task_id) for task_id in plan_payload.get("taskIds", []) or []]
    else:
        plan_name = str(plan_payload or "")
        plan_goal = str(manifest.get("goal", ""))
        plan_task_ids = [record.id for record in records]
    status_counts = count_statuses([record.status for record in records])
    manifest_task_efforts = manifest.get("taskEfforts") if isinstance(manifest.get("taskEfforts"), dict) else {}
    manifest_task_effort_sources = (
        manifest.get("taskEffortSources") if isinstance(manifest.get("taskEffortSources"), dict) else {}
    )
    resolved_task_efforts = {
        record.id: record.effort if record.effort is not None else manifest_task_efforts.get(record.id)
        for record in records
    }
    resolved_task_effort_sources = {
        record.id: (
            record.effort_source if record.effort_source is not None else manifest_task_effort_sources.get(record.id)
        )
        for record in records
    }
    effort_counts = count_statuses([str(resolved_task_efforts.get(record.id) or "unset") for record in records])
    resume_candidate_task_ids = [record.id for record in records if record.status != "completed"]
    usage_totals = manifest.get("usageTotals") if isinstance(manifest.get("usageTotals"), dict) else {}
    run_governance = manifest.get("runGovernance") if isinstance(manifest.get("runGovernance"), dict) else {}
    topology = manifest.get("topology") if isinstance(manifest.get("topology"), dict) else {}
    artifact_totals = run_governance.get("artifactTotals") if isinstance(run_governance.get("artifactTotals"), dict) else {}
    trace_summary = summarize_run_events(manifest.get("events"))
    branch_summary = summarize_branch_contexts(records)
    promotion_readiness = summarize_promotion_readiness(records)
    approval_summary = summarize_approval_checkpoints(manifest)
    candidate_times = [datetime.fromtimestamp(manifest_path.stat().st_mtime, tz=timezone.utc).astimezone()]
    generated_at = parse_iso_datetime(manifest.get("generatedAt"))
    if generated_at is not None:
        candidate_times.append(generated_at)
    for record in records:
        for value in (record.started_at, record.finished_at):
            parsed = parse_iso_datetime(value)
            if parsed is not None:
                candidate_times.append(parsed)
    last_updated_at = max(candidate_times)
    age_days = max((now - last_updated_at).total_seconds(), 0.0) / 86400.0
    has_failures = status_counts.get("failed", 0) > 0
    has_blocked = status_counts.get("blocked", 0) > 0
    is_resumable = len(resume_candidate_task_ids) > 0
    is_costly = float(usage_totals.get("totalCostUsd", 0.0) or 0.0) > 0.0
    lifecycle_state, lifecycle_state_reason = derive_run_lifecycle_state(
        records=records,
        summary_base={
            "hasFailures": has_failures,
            "hasBlocked": has_blocked,
            "isResumable": is_resumable,
        },
        promotion_readiness=promotion_readiness,
        approval_summary=approval_summary,
    )
    flags: list[str] = []
    if has_failures:
        flags.append("failed")
    if has_blocked:
        flags.append("blocked")
    if is_resumable:
        flags.append("resumable")
    if promotion_readiness["allowed"] and promotion_readiness["filesPromotable"] > 0:
        flags.append("promotion-ready")
    if is_costly:
        flags.append("costly")
    if int(run_governance.get("blockingAlertCount", 0) or 0) > 0:
        flags.append("governance-blocked")
    flags.append(f"state:{lifecycle_state}")
    return {
        "runId": str(manifest.get("runId", "")),
        "manifestPath": str(manifest_path),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "plan": plan_name,
        "goal": plan_goal,
        "dryRun": bool(manifest.get("dryRun", False)),
        "retryOfRunId": str(manifest.get("retryOfRunId", "")).strip() or None,
        "generatedAt": datetime_to_iso(generated_at) if generated_at is not None else None,
        "lastUpdatedAt": datetime_to_iso(last_updated_at),
        "ageDays": round(age_days, 3),
        "taskCount": len(records),
        "taskIds": plan_task_ids or [record.id for record in records],
        "statusCounts": status_counts,
        "effortCounts": effort_counts,
        "hasFailures": has_failures,
        "hasBlocked": has_blocked,
        "isResumable": is_resumable,
        "resumeCandidateTaskIds": resume_candidate_task_ids,
        "resumeCandidateTaskCount": len(resume_candidate_task_ids),
        "allTasksCompleted": bool(records) and not resume_candidate_task_ids,
        "runDirExists": run_dir.exists(),
        "workspacesDirExists": workspaces_dir.exists(),
        "coordinatorValidationPresent": isinstance(manifest.get("coordinatorValidation"), dict),
        "promptEstimatedTokens": int(usage_totals.get("promptEstimatedTokens", 0) or 0),
        "totalCostUsd": float(usage_totals.get("totalCostUsd", 0.0) or 0.0),
        "taskEfforts": resolved_task_efforts,
        "taskEffortSources": resolved_task_effort_sources,
        "governanceStatus": str(run_governance.get("status", "ok") or "ok"),
        "governanceAlertCount": int(run_governance.get("alertCount", 0) or 0),
        "governanceBlockingAlertCount": int(run_governance.get("blockingAlertCount", 0) or 0),
        "totalArtifactBytes": int(artifact_totals.get("totalBytes", 0) or 0),
        "topologyBatchCount": int(topology.get("batchCount", 0) or 0),
        "topologyMaxParallelWidth": int(topology.get("maxParallelWidth", 0) or 0),
        "topologyWarningCount": int(topology.get("warningCount", 0) or 0),
        "isCostly": is_costly,
        "lifecycleState": lifecycle_state,
        "lifecycleStateReason": lifecycle_state_reason,
        "promotionReady": bool(promotion_readiness["allowed"] and promotion_readiness["filesPromotable"] > 0),
        "promotionSummary": promotion_readiness,
        "traceSummary": trace_summary,
        "branchSummary": branch_summary,
        "approvalSummary": approval_summary,
        "flags": flags,
    }, last_updated_at
