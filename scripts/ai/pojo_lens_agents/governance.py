from __future__ import annotations

from typing import Any, Callable, Protocol


class RecordLike(Protocol):
    id: str
    status: str
    model: str | None
    prompt_estimated_tokens: int
    usage: dict[str, Any] | None
    stdout_bytes: int
    stderr_bytes: int
    result_bytes: int


class RunPolicyLike(Protocol):
    run_budget_usd: float | None
    budget_behavior: str
    max_task_stdout_bytes: int | None
    max_task_stderr_bytes: int | None
    max_task_result_bytes: int | None
    artifact_behavior: str


def task_cost_usd(record: RecordLike) -> float:
    if not record.usage:
        return 0.0
    return float(record.usage.get("totalCostUsd", 0.0) or 0.0)


def aggregate_usage(records: dict[str, RecordLike]) -> dict[str, Any]:
    totals: dict[str, Any] = {
        "tasksWithUsage": 0,
        "promptEstimatedTokens": 0,
        "inputTokens": 0,
        "outputTokens": 0,
        "cacheReadInputTokens": 0,
        "cacheCreationInputTokens": 0,
        "totalCostUsd": 0.0,
        "perModel": {},
    }
    for record in records.values():
        totals["promptEstimatedTokens"] += record.prompt_estimated_tokens
        if not record.usage:
            continue
        totals["tasksWithUsage"] += 1
        totals["inputTokens"] += int(record.usage.get("inputTokens", 0) or 0)
        totals["outputTokens"] += int(record.usage.get("outputTokens", 0) or 0)
        totals["cacheReadInputTokens"] += int(record.usage.get("cacheReadInputTokens", 0) or 0)
        totals["cacheCreationInputTokens"] += int(record.usage.get("cacheCreationInputTokens", 0) or 0)
        totals["totalCostUsd"] += float(record.usage.get("totalCostUsd", 0.0) or 0.0)
        model_usage = record.usage.get("modelUsage", {})
        if not isinstance(model_usage, dict):
            continue
        per_model = totals["perModel"]
        for model_name, model_record in model_usage.items():
            if not isinstance(model_record, dict):
                continue
            bucket = per_model.setdefault(
                model_name,
                {
                    "inputTokens": 0,
                    "outputTokens": 0,
                    "cacheReadInputTokens": 0,
                    "cacheCreationInputTokens": 0,
                    "costUsd": 0.0,
                },
            )
            bucket["inputTokens"] += int(model_record.get("inputTokens", 0) or 0)
            bucket["outputTokens"] += int(model_record.get("outputTokens", 0) or 0)
            bucket["cacheReadInputTokens"] += int(model_record.get("cacheReadInputTokens", 0) or 0)
            bucket["cacheCreationInputTokens"] += int(model_record.get("cacheCreationInputTokens", 0) or 0)
            bucket["costUsd"] += float(model_record.get("costUSD", 0.0) or 0.0)
    totals["totalCostUsd"] = round(totals["totalCostUsd"], 6)
    for model_totals in totals["perModel"].values():
        model_totals["costUsd"] = round(float(model_totals["costUsd"]), 6)
    return totals


def aggregate_artifacts(
    records: dict[str, RecordLike],
    *,
    top_task_limit: int,
) -> dict[str, Any]:
    totals: dict[str, Any] = {
        "tasksWithArtifacts": 0,
        "stdoutBytes": 0,
        "stderrBytes": 0,
        "resultBytes": 0,
        "totalBytes": 0,
        "largestTasks": [],
    }
    task_summaries: list[dict[str, Any]] = []
    for record in records.values():
        task_total = record.stdout_bytes + record.stderr_bytes + record.result_bytes
        totals["stdoutBytes"] += record.stdout_bytes
        totals["stderrBytes"] += record.stderr_bytes
        totals["resultBytes"] += record.result_bytes
        totals["totalBytes"] += task_total
        if task_total > 0:
            totals["tasksWithArtifacts"] += 1
        task_summaries.append(
            {
                "taskId": record.id,
                "status": record.status,
                "stdoutBytes": record.stdout_bytes,
                "stderrBytes": record.stderr_bytes,
                "resultBytes": record.result_bytes,
                "totalBytes": task_total,
            }
        )
    task_summaries.sort(
        key=lambda item: (
            int(item["totalBytes"]),
            str(item["taskId"]),
        ),
        reverse=True,
    )
    totals["largestTasks"] = [
        item for item in task_summaries[:top_task_limit] if int(item["totalBytes"]) > 0
    ]
    return totals


def evaluate_run_governance(
    records: dict[str, RecordLike],
    run_policy: RunPolicyLike,
    *,
    serialize_run_policy: Callable[[RunPolicyLike], dict[str, Any]],
    top_task_limit: int,
) -> dict[str, Any]:
    usage_totals = aggregate_usage(records)
    artifact_totals = aggregate_artifacts(records, top_task_limit=top_task_limit)
    highest_cost_tasks: list[dict[str, Any]] = []
    for record in sorted(
        records.values(),
        key=lambda item: (task_cost_usd(item), item.id),
        reverse=True,
    ):
        cost_usd = task_cost_usd(record)
        if cost_usd <= 0:
            continue
        highest_cost_tasks.append(
            {
                "taskId": record.id,
                "status": record.status,
                "model": record.model,
                "costUsd": round(cost_usd, 6),
                "inputTokens": int(record.usage.get("inputTokens", 0) or 0) if record.usage else 0,
                "outputTokens": int(record.usage.get("outputTokens", 0) or 0) if record.usage else 0,
            }
        )
        if len(highest_cost_tasks) >= top_task_limit:
            break
    alerts: list[dict[str, Any]] = []
    if run_policy.run_budget_usd is not None:
        total_cost = float(usage_totals.get("totalCostUsd", 0.0) or 0.0)
        if total_cost >= run_policy.run_budget_usd:
            alerts.append(
                {
                    "kind": "budget",
                    "severity": run_policy.budget_behavior,
                    "message": (
                        f"Run cost ${total_cost:.6f} reached configured budget "
                        f"${run_policy.run_budget_usd:.6f}"
                    ),
                    "actualUsd": round(total_cost, 6),
                    "limitUsd": run_policy.run_budget_usd,
                }
            )
    artifact_limits = {
        "stdout": run_policy.max_task_stdout_bytes,
        "stderr": run_policy.max_task_stderr_bytes,
        "result": run_policy.max_task_result_bytes,
    }
    for record in sorted(records.values(), key=lambda item: item.id):
        artifact_values = {
            "stdout": record.stdout_bytes,
            "stderr": record.stderr_bytes,
            "result": record.result_bytes,
        }
        for artifact_kind, limit in artifact_limits.items():
            actual_bytes = artifact_values[artifact_kind]
            if limit is None or actual_bytes <= limit:
                continue
            alerts.append(
                {
                    "kind": "artifact",
                    "severity": run_policy.artifact_behavior,
                    "message": (
                        f"Task '{record.id}' {artifact_kind} artifact {actual_bytes} B "
                        f"exceeds configured limit {limit} B"
                    ),
                    "taskId": record.id,
                    "artifact": artifact_kind,
                    "actualBytes": actual_bytes,
                    "limitBytes": limit,
                }
            )
    blocking_alerts = [alert for alert in alerts if alert.get("severity") == "stop"]
    status = "ok"
    if blocking_alerts:
        status = "stop"
    elif alerts:
        status = "warn"
    return {
        "status": status,
        "policy": serialize_run_policy(run_policy),
        "alertCount": len(alerts),
        "blockingAlertCount": len(blocking_alerts),
        "alerts": alerts,
        "blockingAlerts": blocking_alerts,
        "highestCostTasks": highest_cost_tasks,
        "artifactTotals": artifact_totals,
        "shouldStopScheduling": bool(blocking_alerts),
    }
