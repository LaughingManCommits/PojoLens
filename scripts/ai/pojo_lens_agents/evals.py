from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable


def evaluation_check(
    name: str,
    status: str,
    summary: str,
    *,
    evidence: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return {
        "name": name,
        "status": status,
        "summary": summary,
        "evidence": evidence or {},
    }


def summarize_evaluation_score(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    status_counts: dict[str, int] = {}
    for check in checks:
        status = str(check.get("status", "pass") or "pass")
        status_counts[status] = status_counts.get(status, 0) + 1
    weights = {"pass": 1.0, "warn": 0.5, "fail": 0.0}
    earned_points = sum(weights.get(str(check.get("status", "pass") or "pass"), 0.0) for check in checks)
    max_points = float(len(checks))
    score_percent = round((earned_points / max_points) * 100.0, 1) if max_points else 100.0
    status = "pass"
    if int(status_counts.get("fail", 0) or 0) > 0:
        status = "fail"
    elif int(status_counts.get("warn", 0) or 0) > 0:
        status = "warn"
    return {
        "status": status,
        "statusCounts": status_counts,
        "totalChecks": len(checks),
        "earnedPoints": round(earned_points, 3),
        "maxPoints": round(max_points, 3),
        "scorePercent": score_percent,
        "promotionReady": bool(run_summary.get("promotionReady", False)),
        "resumable": bool(run_summary.get("isResumable", False)),
        "taskCount": int(run_summary.get("taskCount", 0) or 0),
        "batchCount": int((run_summary.get("topologyBatchCount", 0) or 0)),
        "parallelWidth": int((run_summary.get("topologyMaxParallelWidth", 0) or 0)),
    }


def benchmark_dimensions_for_evaluation(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    by_name = {str(check.get("name", "")): check for check in checks}

    def worst_status(names: list[str]) -> str:
        rank = {"pass": 0, "warn": 1, "fail": 2}
        return max(
            (str(by_name.get(name, {}).get("status", "pass") or "pass") for name in names),
            key=lambda item: rank[item],
            default="pass",
        )

    decomposition_status = worst_status(["over-delegation", "reviewer-hops", "effort-fit", "output-discipline"])
    retry_status = str(by_name.get("retry-resume-contract", {}).get("status", "pass") or "pass")
    promotion_status = str(by_name.get("promotion-readiness", {}).get("status", "pass") or "pass")
    task_count = int(run_summary.get("taskCount", 0) or 0)
    batch_count = int(run_summary.get("topologyBatchCount", 0) or 0)
    parallel_width = int(run_summary.get("topologyMaxParallelWidth", 0) or 0)
    if task_count <= 1:
        parallel_status = "pass"
        parallel_summary = "Single-task run had no parallel opportunity requirement."
    elif parallel_width > 1:
        parallel_status = "pass"
        parallel_summary = "Run exposed concurrent-ready width greater than one."
    else:
        parallel_status = "warn"
        parallel_summary = "Run stayed fully serial; compare dependency shape and task split for missed parallel opportunity."
    return {
        "decompositionQuality": {
            "status": decomposition_status,
            "summary": "Composition quality across delegation, reviewer hops, effort fit, and output discipline.",
            "checkNames": ["over-delegation", "reviewer-hops", "effort-fit", "output-discipline"],
        },
        "retryCorrectness": {
            "status": retry_status,
            "summary": "Retry/resume metadata consistency.",
            "checkNames": ["retry-resume-contract"],
        },
        "reviewPromotionAccuracy": {
            "status": promotion_status,
            "summary": "Promotion-readiness accuracy against retained run state.",
            "checkNames": ["promotion-readiness"],
        },
        "parallelEfficiency": {
            "status": parallel_status,
            "summary": parallel_summary,
            "evidence": {
                "taskCount": task_count,
                "batchCount": batch_count,
                "parallelWidth": parallel_width,
            },
        },
    }


def evaluate_loaded_run_quality(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    selected_tasks: list[str],
    summarize_run_manifest: Callable[..., tuple[dict[str, Any], datetime]],
    selected_run_records: Callable[[dict[str, Any], list[str]], list[Any]],
) -> dict[str, Any]:
    summary, _ = summarize_run_manifest(
        manifest_path,
        manifest,
        now=datetime.now(timezone.utc).astimezone(),
    )
    records = selected_run_records(manifest, selected_tasks)
    topology = manifest.get("topology") if isinstance(manifest.get("topology"), dict) else {}
    run_start_event = next(
        (
            event
            for event in (manifest.get("events") or [])
            if isinstance(event, dict) and str(event.get("phase", "")) == "run-start"
        ),
        None,
    )
    run_start_details = (
        run_start_event.get("details")
        if isinstance(run_start_event, dict) and isinstance(run_start_event.get("details"), dict)
        else {}
    )
    checks: list[dict[str, Any]] = []
    reviewer_task_count = int(topology.get("reviewerTaskCount", 0) or 0)
    write_task_count = int(topology.get("writeTaskCount", 0) or 0)
    if reviewer_task_count > 0 and write_task_count == 0 and len(records) > reviewer_task_count:
        checks.append(
            evaluation_check(
                "over-delegation",
                "warn",
                "Read-only run still allocated reviewer-only work.",
                evidence={"reviewerTaskCount": reviewer_task_count, "writeTaskCount": write_task_count, "taskCount": len(records)},
            )
        )
    else:
        checks.append(
            evaluation_check(
                "over-delegation",
                "pass",
                "Task split is proportionate to the run shape.",
                evidence={"reviewerTaskCount": reviewer_task_count, "writeTaskCount": write_task_count, "taskCount": len(records)},
            )
        )
    topology_warnings = [warning for warning in (topology.get("warnings") or []) if isinstance(warning, dict)]
    optional_reviewer_warnings = [warning for warning in topology_warnings if str(warning.get("kind", "")) == "read-only-review-optional"]
    if optional_reviewer_warnings:
        checks.append(
            evaluation_check(
                "reviewer-hops",
                "warn",
                "Topology marks at least one reviewer hop as optional.",
                evidence={"warnings": optional_reviewer_warnings},
            )
        )
    else:
        checks.append(
            evaluation_check(
                "reviewer-hops",
                "pass",
                "No optional reviewer hop warning was detected.",
                evidence={"warningCount": len(topology_warnings)},
            )
        )
    legacy_validation_tasks = sorted(record.id for record in records if record.validation_commands)
    if legacy_validation_tasks:
        checks.append(
            evaluation_check(
                "validation-suggestions",
                "warn",
                "Legacy raw validation commands are still present in the run record.",
                evidence={"taskIds": legacy_validation_tasks},
            )
        )
    else:
        checks.append(
            evaluation_check(
                "validation-suggestions",
                "pass",
                "Validation suggestions stayed on structured intents or were absent.",
                evidence={"intentTaskCount": sum(1 for record in records if record.validation_intents)},
            )
        )
    task_efforts = manifest.get("taskEfforts") if isinstance(manifest.get("taskEfforts"), dict) else summary.get("taskEfforts", {})
    task_model_profiles = manifest.get("taskModelProfiles") if isinstance(manifest.get("taskModelProfiles"), dict) else {}
    read_only_task_ids = [str(task_id) for task_id in topology.get("readOnlyTaskIds", []) or []]
    overspecified_effort_task_ids = sorted(
        task_id
        for task_id in read_only_task_ids
        if str(task_efforts.get(task_id, "") or "") in {"high", "xhigh"} and str(task_model_profiles.get(task_id, "") or "") in {"simple", "balanced"}
    )
    if overspecified_effort_task_ids:
        checks.append(
            evaluation_check(
                "effort-fit",
                "warn",
                "Read-only tasks are using high effort on non-complex model profiles.",
                evidence={
                    "taskIds": overspecified_effort_task_ids,
                    "taskEfforts": {task_id: task_efforts.get(task_id) for task_id in overspecified_effort_task_ids},
                    "taskModelProfiles": {task_id: task_model_profiles.get(task_id) for task_id in overspecified_effort_task_ids},
                },
            )
        )
    else:
        checks.append(
            evaluation_check(
                "effort-fit",
                "pass",
                "Resolved effort looks proportionate to the retained task shape.",
                evidence={"taskEffortCounts": summary.get("effortCounts", {})},
            )
        )
    verbose_task_ids = [str(task_id) for task_id in summary.get("unexpectedlyVerboseTaskIds", []) or []]
    if verbose_task_ids:
        checks.append(
            evaluation_check(
                "output-discipline",
                "warn",
                "One or more retained tasks were unexpectedly verbose for their resolved output profile.",
                evidence={
                    "taskIds": verbose_task_ids,
                    "taskOutputProfiles": {
                        task_id: summary.get("taskOutputProfiles", {}).get(task_id) for task_id in verbose_task_ids
                    },
                },
            )
        )
    else:
        checks.append(
            evaluation_check(
                "output-discipline",
                "pass",
                "Retained task outputs stayed within the expected profile shape.",
                evidence={"outputProfileCounts": summary.get("outputProfileCounts", {})},
            )
        )
    retry_of_run_id = str(manifest.get("retryOfRunId", "")).strip()
    requested_task_ids = [str(task_id) for task_id in manifest.get("requestedTaskIds", []) or []]
    retried_task_ids = [str(task_id) for task_id in manifest.get("retriedTaskIds", []) or []]
    seeded_task_ids = [str(task_id) for task_id in manifest.get("seededTaskIds", []) or []]
    is_resume = bool(run_start_details.get("resume", False))
    contract_status = "pass"
    contract_summary = "Resume/retry metadata is internally consistent."
    contract_evidence: dict[str, Any] = {
        "retryOfRunId": retry_of_run_id or None,
        "requestedTaskIds": requested_task_ids,
        "retriedTaskIds": retried_task_ids,
        "seededTaskIds": seeded_task_ids,
        "resume": is_resume,
    }
    if retry_of_run_id and not retried_task_ids:
        contract_status = "fail"
        contract_summary = "Retry run is missing retried task ids."
    elif retry_of_run_id and is_resume:
        contract_status = "fail"
        contract_summary = "Run metadata claims both retry and in-place resume."
    elif requested_task_ids and retry_of_run_id and not set(retried_task_ids).issubset(set(requested_task_ids)):
        contract_status = "fail"
        contract_summary = "Retried task ids are not a subset of the requested retry scope."
    checks.append(evaluation_check("retry-resume-contract", contract_status, contract_summary, evidence=contract_evidence))
    promotion_summary = summary["promotionSummary"]
    changed_completed_tasks = sorted(record.id for record in records if record.status == "completed" and (record.actual_files_touched or record.files_touched))
    promotion_status = "pass"
    promotion_check_summary = "Promotion readiness is consistent with task state and promotable files."
    if summary["promotionReady"] and (summary["hasFailures"] or summary["hasBlocked"]):
        promotion_status = "fail"
        promotion_check_summary = "Promotion is marked ready even though the run still has failed or blocked tasks."
    elif not summary["promotionReady"] and promotion_summary["allowed"] and promotion_summary["filesPromotable"] > 0:
        promotion_status = "fail"
        promotion_check_summary = "Promotion summary reports promotable files but the run summary is not marked ready."
    elif (not summary["promotionReady"] and changed_completed_tasks and not summary["hasFailures"] and not summary["hasBlocked"] and promotion_summary["filesPromotable"] == 0):
        promotion_status = "warn"
        promotion_check_summary = "Completed tasks changed files, but nothing is promotable; review ownership and scope evidence."
    checks.append(
        evaluation_check(
            "promotion-readiness",
            promotion_status,
            promotion_check_summary,
            evidence={"promotionReady": summary["promotionReady"], "promotionSummary": promotion_summary, "changedCompletedTaskIds": changed_completed_tasks},
        )
    )
    status_rank = {"pass": 0, "warn": 1, "fail": 2}
    overall_status = max((check["status"] for check in checks), key=lambda item: status_rank[item], default="pass")
    score_summary = summarize_evaluation_score(checks, run_summary=summary)
    benchmark_dimensions = benchmark_dimensions_for_evaluation(checks, run_summary=summary)
    return {
        "run": summary,
        "traceSummary": summary["traceSummary"],
        "branchSummary": summary["branchSummary"],
        "status": overall_status,
        "scoreSummary": score_summary,
        "benchmarkDimensions": benchmark_dimensions,
        "checkCount": len(checks),
        "warningCheckCount": sum(1 for check in checks if check["status"] == "warn"),
        "failingCheckCount": sum(1 for check in checks if check["status"] == "fail"),
        "checks": checks,
    }


def evaluate_run_corpus(
    *,
    runtime_root: Path,
    limit: int,
    runtime_manifest_entries: Callable[[Path], list[tuple[Path, dict[str, Any]]]],
    summarize_run_manifest: Callable[..., tuple[dict[str, Any], datetime]],
    evaluate_loaded_run_quality_fn: Callable[[Path, dict[str, Any], list[str]], dict[str, Any]],
) -> dict[str, Any]:
    now = datetime.now(timezone.utc).astimezone()
    entries = [(*summarize_run_manifest(manifest_path, manifest, now=now), manifest_path, manifest) for manifest_path, manifest in runtime_manifest_entries(runtime_root)]
    entries.sort(key=lambda item: item[1], reverse=True)
    visible = entries[:limit] if limit else entries
    run_evaluations = [evaluate_loaded_run_quality_fn(manifest_path, manifest, []) for _, _, manifest_path, manifest in visible]
    corpus_status_counts: dict[str, int] = {}
    score_status_counts: dict[str, int] = {}
    for item in run_evaluations:
        corpus_status = str(item["status"])
        score_status = str(item["scoreSummary"]["status"])
        corpus_status_counts[corpus_status] = corpus_status_counts.get(corpus_status, 0) + 1
        score_status_counts[score_status] = score_status_counts.get(score_status, 0) + 1
    dimension_status_counts: dict[str, dict[str, int]] = {}
    for item in run_evaluations:
        for dimension_name, dimension in item["benchmarkDimensions"].items():
            if not isinstance(dimension, dict):
                continue
            dimension_status_counts.setdefault(dimension_name, {})
            status = str(dimension.get("status", "pass") or "pass")
            dimension_status_counts[dimension_name][status] = dimension_status_counts[dimension_name].get(status, 0) + 1
    average_score_percent = round((sum(float(item["scoreSummary"]["scorePercent"]) for item in run_evaluations) / len(run_evaluations)), 1) if run_evaluations else 100.0
    return {
        "runtimeRoot": str(runtime_root),
        "runCount": len(entries),
        "shownRunCount": len(run_evaluations),
        "statusCounts": corpus_status_counts,
        "scoreStatusCounts": score_status_counts,
        "averageScorePercent": average_score_percent,
        "dimensionStatusCounts": dimension_status_counts,
        "runs": [
            {
                "runId": item["run"]["runId"],
                "plan": item["run"]["plan"],
                "generatedAt": item["run"]["generatedAt"],
                "status": item["status"],
                "scoreSummary": item["scoreSummary"],
                "benchmarkDimensions": item["benchmarkDimensions"],
                "traceSummary": item["traceSummary"],
                "branchSummary": item["branchSummary"],
                "flags": item["run"]["flags"],
            }
            for item in run_evaluations
        ],
    }
