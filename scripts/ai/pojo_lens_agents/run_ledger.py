from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

LEDGER_MAX_AGE_DAYS = 90


@dataclass
class RunLedgerTaskEntry:
    id: str
    status: str
    failure_kind: str | None
    cost_usd: float | None
    modules: list[str]


@dataclass
class RunLedgerEntry:
    run_id: str
    plan_name: str
    generated_at: str
    task_count: int
    tasks: list[RunLedgerTaskEntry]
    reviewer_block_count: int
    planner_notes: str | None


def _modules_from_files(files: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for f in files:
        parts = Path(f).parts
        if parts:
            top = parts[0]
            if top not in seen:
                seen.add(top)
                result.append(top)
    return result


def _task_cost_usd(usage: dict[str, Any] | None) -> float | None:
    if not isinstance(usage, dict):
        return None
    for key in ("cost_usd", "totalCostUsd", "total_cost_usd"):
        val = usage.get(key)
        if val is not None:
            try:
                return float(val)
            except (TypeError, ValueError):
                pass
    return None


def _derive_failure_kind(task_dict: dict[str, Any]) -> str | None:
    summary = str(task_dict.get("summary", "")).lower()
    if "rate limit" in summary or "429" in summary or "timeout" in summary:
        return "transient"
    return "permanent"


def build_ledger_entry(
    run_payload: dict[str, Any],
    *,
    iso_now_fn=None,
    planner_notes: str | None = None,
) -> RunLedgerEntry:
    run_id = str(run_payload.get("runId", ""))
    plan_name = str(run_payload.get("plan", ""))
    generated_at = iso_now_fn() if iso_now_fn else datetime.now(timezone.utc).isoformat()

    task_dicts = run_payload.get("tasks") or []
    task_entries: list[RunLedgerTaskEntry] = []
    reviewer_block_count = 0

    for task_dict in task_dicts:
        if not isinstance(task_dict, dict):
            continue
        task_id = str(task_dict.get("id", ""))
        status = str(task_dict.get("status", ""))

        failure_kind: str | None = None
        if status == "failed":
            failure_kind = _derive_failure_kind(task_dict)

        if status == "blocked" and "reviewer" in str(task_dict.get("agent", "")):
            reviewer_block_count += 1

        cost_usd = _task_cost_usd(task_dict.get("usage"))
        actual_files = task_dict.get("actual_files_touched") or []
        modules = _modules_from_files([str(f) for f in actual_files if isinstance(f, str)])

        task_entries.append(RunLedgerTaskEntry(
            id=task_id,
            status=status,
            failure_kind=failure_kind,
            cost_usd=cost_usd,
            modules=modules,
        ))

    return RunLedgerEntry(
        run_id=run_id,
        plan_name=plan_name,
        generated_at=generated_at,
        task_count=len(task_entries),
        tasks=task_entries,
        reviewer_block_count=reviewer_block_count,
        planner_notes=planner_notes,
    )


def _entry_to_dict(entry: RunLedgerEntry) -> dict[str, Any]:
    return {
        "runId": entry.run_id,
        "planName": entry.plan_name,
        "generatedAt": entry.generated_at,
        "taskCount": entry.task_count,
        "tasks": [
            {
                "id": t.id,
                "status": t.status,
                "failureKind": t.failure_kind,
                "costUsd": t.cost_usd,
                "modules": t.modules,
            }
            for t in entry.tasks
        ],
        "reviewerBlockCount": entry.reviewer_block_count,
        "plannerNotes": entry.planner_notes,
    }


def _dict_to_entry(d: dict[str, Any]) -> RunLedgerEntry:
    tasks = []
    for t in (d.get("tasks") or []):
        if isinstance(t, dict):
            cost_raw = t.get("costUsd")
            tasks.append(RunLedgerTaskEntry(
                id=str(t.get("id", "")),
                status=str(t.get("status", "")),
                failure_kind=t.get("failureKind"),
                cost_usd=float(cost_raw) if cost_raw is not None else None,
                modules=[str(m) for m in (t.get("modules") or [])],
            ))
    return RunLedgerEntry(
        run_id=str(d.get("runId", "")),
        plan_name=str(d.get("planName", "")),
        generated_at=str(d.get("generatedAt", "")),
        task_count=int(d.get("taskCount", 0)),
        tasks=tasks,
        reviewer_block_count=int(d.get("reviewerBlockCount", 0)),
        planner_notes=d.get("plannerNotes"),
    )


def append_ledger_entry(ledger_path: Path, entry: RunLedgerEntry) -> None:
    ledger_path.parent.mkdir(parents=True, exist_ok=True)
    with ledger_path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(_entry_to_dict(entry), separators=(",", ":")) + "\n")


def load_ledger_entries(
    ledger_path: Path,
    *,
    plan_name_prefix: str | None = None,
    limit: int | None = None,
    since: datetime | None = None,
) -> list[RunLedgerEntry]:
    if not ledger_path.exists():
        return []
    try:
        raw_lines = ledger_path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return []

    entries: list[RunLedgerEntry] = []
    for line in raw_lines:
        line = line.strip()
        if not line:
            continue
        try:
            d = json.loads(line)
        except json.JSONDecodeError:
            continue
        if not isinstance(d, dict):
            continue
        try:
            entry = _dict_to_entry(d)
        except Exception:
            continue
        if plan_name_prefix and not entry.plan_name.startswith(plan_name_prefix):
            continue
        if since is not None:
            try:
                ts = datetime.fromisoformat(entry.generated_at)
                if ts.tzinfo is None:
                    ts = ts.replace(tzinfo=timezone.utc)
                if ts < since:
                    continue
            except (ValueError, TypeError):
                pass
        entries.append(entry)

    entries.reverse()
    if limit is not None and limit > 0:
        entries = entries[:limit]
    return entries


def prune_ledger_entries(
    ledger_path: Path,
    *,
    max_age_days: int = LEDGER_MAX_AGE_DAYS,
) -> dict[str, Any]:
    if not ledger_path.exists():
        return {"pruned": 0, "kept": 0, "maxAgeDays": max_age_days, "ledgerPath": str(ledger_path)}

    cutoff = datetime.now(timezone.utc) - timedelta(days=max_age_days)
    try:
        raw_lines = ledger_path.read_text(encoding="utf-8").splitlines(keepends=True)
    except OSError:
        return {"pruned": 0, "kept": 0, "maxAgeDays": max_age_days, "ledgerPath": str(ledger_path)}

    kept_lines: list[str] = []
    pruned = 0
    kept = 0
    for line in raw_lines:
        stripped = line.strip()
        if not stripped:
            continue
        prune = False
        try:
            d = json.loads(stripped)
            ts_str = str(d.get("generatedAt", ""))
            ts = datetime.fromisoformat(ts_str)
            if ts.tzinfo is None:
                ts = ts.replace(tzinfo=timezone.utc)
            if ts < cutoff:
                prune = True
        except (json.JSONDecodeError, ValueError, TypeError):
            pass
        if prune:
            pruned += 1
        else:
            kept_lines.append(line if line.endswith("\n") else line + "\n")
            kept += 1

    ledger_path.write_text("".join(kept_lines), encoding="utf-8")
    return {"pruned": pruned, "kept": kept, "maxAgeDays": max_age_days, "ledgerPath": str(ledger_path)}


def summarize_ledger_entries(entries: list[RunLedgerEntry]) -> dict[str, Any]:
    if not entries:
        return {
            "totalRuns": 0,
            "totalTasks": 0,
            "completedTasks": 0,
            "successRate": None,
            "averageCostUsd": None,
            "commonFailureKinds": [],
            "highCostTasksByModule": [],
        }

    total_tasks = 0
    completed_tasks = 0
    failure_kind_counts: dict[str, int] = {}
    total_cost = 0.0
    cost_count = 0
    module_cost: dict[str, float] = {}
    module_task_count: dict[str, int] = {}

    for entry in entries:
        for task in entry.tasks:
            total_tasks += 1
            if task.status == "completed":
                completed_tasks += 1
            if task.failure_kind:
                failure_kind_counts[task.failure_kind] = failure_kind_counts.get(task.failure_kind, 0) + 1
            if task.cost_usd is not None:
                total_cost += task.cost_usd
                cost_count += 1
                for module in task.modules:
                    module_cost[module] = module_cost.get(module, 0.0) + task.cost_usd
                    module_task_count[module] = module_task_count.get(module, 0) + 1

    success_rate = (completed_tasks / total_tasks) if total_tasks > 0 else None
    avg_cost = (total_cost / cost_count) if cost_count > 0 else None

    common_failure_kinds = sorted(
        [{"kind": k, "count": c} for k, c in failure_kind_counts.items()],
        key=lambda x: -x["count"],
    )
    high_cost_modules = sorted(
        [
            {"module": m, "totalCostUsd": round(c, 6), "taskCount": module_task_count[m]}
            for m, c in module_cost.items()
        ],
        key=lambda x: -x["totalCostUsd"],
    )[:10]

    return {
        "totalRuns": len(entries),
        "totalTasks": total_tasks,
        "completedTasks": completed_tasks,
        "successRate": round(success_rate, 4) if success_rate is not None else None,
        "averageCostUsd": round(avg_cost, 6) if avg_cost is not None else None,
        "commonFailureKinds": common_failure_kinds,
        "highCostTasksByModule": high_cost_modules,
    }


def format_ledger_context(entries: list[RunLedgerEntry], plan_name: str) -> str:
    if not entries:
        return f"No prior runs found for plan '{plan_name}'."

    lines: list[str] = [f"Prior run evidence for plan '{plan_name}' ({len(entries)} run(s)):"]
    for entry in entries:
        completed = sum(1 for t in entry.tasks if t.status == "completed")
        failed = sum(1 for t in entry.tasks if t.status == "failed")
        blocked = sum(1 for t in entry.tasks if t.status == "blocked")
        costs = [t.cost_usd for t in entry.tasks if t.cost_usd is not None]
        cost_str = f", total=${sum(costs):.4f}" if costs else ""
        lines.append(
            f"  [{entry.generated_at[:10]}] run={entry.run_id[:12]}"
            f" tasks={entry.task_count}"
            f" (ok={completed} fail={failed} blocked={blocked}{cost_str})"
        )
        for t in [x for x in entry.tasks if x.status == "failed"][:3]:
            mods = ",".join(t.modules[:3]) if t.modules else "unknown"
            lines.append(f"    - failed: {t.id} [{mods}]")
        if entry.planner_notes:
            lines.append(f"    notes: {entry.planner_notes}")
    return "\n".join(lines)
