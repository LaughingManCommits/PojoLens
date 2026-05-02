from __future__ import annotations

import argparse
import shutil
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


def cleanup_loaded_run(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    root: Path,
    manifest_run_dir,
    manifest_workspaces_dir,
    selected_run_records,
    dedupe_strings,
    error_factory,
) -> dict[str, Any]:
    run_dir = manifest_run_dir(manifest_path, manifest)
    workspaces_dir = manifest_workspaces_dir(manifest, run_dir=run_dir)
    records = selected_run_records(manifest, [])
    removed_worktrees: list[str] = []
    missing_worktrees_skipped: list[str] = []
    for workspace_path in dedupe_strings(
        [record.workspace_path for record in records if record.workspace_mode == "worktree" and record.workspace_path]
    ):
        workspace = Path(workspace_path).resolve()
        if not workspace.exists():
            missing_worktrees_skipped.append(str(workspace))
            continue
        completed = subprocess.run(
            ["git", "worktree", "remove", "--force", str(workspace)],
            cwd=root,
            capture_output=True,
            text=True,
            check=False,
        )
        if completed.returncode != 0:
            raise error_factory(
                f"Failed to remove detached worktree '{workspace}': "
                f"{completed.stderr.strip() or completed.stdout.strip()}"
            )
        removed_worktrees.append(str(workspace))
    if removed_worktrees:
        completed = subprocess.run(
            ["git", "worktree", "prune"],
            cwd=root,
            capture_output=True,
            text=True,
            check=False,
        )
        if completed.returncode != 0:
            raise error_factory(
                f"Failed to prune worktree metadata: {completed.stderr.strip() or completed.stdout.strip()}"
            )
    if run_dir.exists():
        shutil.rmtree(run_dir)
    if workspaces_dir.exists():
        shutil.rmtree(workspaces_dir)
    return {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "removedRunDir": not run_dir.exists(),
        "removedWorkspacesDir": not workspaces_dir.exists(),
        "removedWorktrees": removed_worktrees,
        "missingWorktreesSkipped": missing_worktrees_skipped,
    }


def runtime_manifest_entries(*, runtime_root: Path, load_run_manifest) -> list[tuple[Path, dict[str, Any]]]:
    runs_dir = runtime_root / "runs"
    if not runs_dir.exists():
        return []
    entries: list[tuple[Path, dict[str, Any]]] = []
    for run_dir in sorted(path for path in runs_dir.iterdir() if path.is_dir()):
        manifest_path = run_dir / "manifest.json"
        if not manifest_path.exists():
            continue
        entries.append(load_run_manifest(str(manifest_path)))
    return entries


def cleanup_run(args: argparse.Namespace, *, load_run_manifest, cleanup_loaded_run_fn) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    return cleanup_loaded_run_fn(manifest_path, manifest)


def status_run(args: argparse.Namespace, *, deps: dict[str, Any]) -> dict[str, Any]:
    manifest_path, manifest = deps["load_run_manifest"](args.run_ref)
    summary, _ = deps["summarize_run_manifest"](
        manifest_path,
        manifest,
        now=datetime.now(timezone.utc).astimezone(),
    )
    task_efforts = summary.get("taskEfforts", {})
    task_effort_sources = summary.get("taskEffortSources", {})
    task_output_profiles = summary.get("taskOutputProfiles", {})
    task_output_profile_sources = summary.get("taskOutputProfileSources", {})
    unexpectedly_verbose_task_ids = set(summary.get("unexpectedlyVerboseTaskIds", []) or [])
    records = deps["selected_run_records"](manifest, args.selected_tasks)
    task_payloads: list[dict[str, Any]] = []
    review_summary = {
        "changedTaskCount": 0,
        "changedFileCount": 0,
        "protectedPathViolationCount": 0,
        "writeScopeViolationCount": 0,
        "validationSuggestionCount": 0,
    }
    for record in records:
        review_payload, _ = deps["task_review_summary"](record, context_lines=0)
        review_summary["changedTaskCount"] += 1 if int(review_payload["diffStats"]["filesChanged"]) > 0 else 0
        review_summary["changedFileCount"] += int(review_payload["diffStats"]["filesChanged"])
        review_summary["protectedPathViolationCount"] += len(record.protected_path_violations)
        review_summary["writeScopeViolationCount"] += len(record.write_scope_violations)
        review_summary["validationSuggestionCount"] += len(record.validation_intents) + len(record.validation_commands)
        task_payloads.append(
            {
                "id": record.id,
                "title": record.title,
                "agent": record.agent,
                "branchContextId": record.branch_context_id,
                "branchParentContextIds": list(record.branch_parent_context_ids),
                "outputProfile": record.output_profile or task_output_profiles.get(record.id),
                "outputProfileSource": (
                    record.output_profile_source
                    if record.output_profile_source is not None
                    else task_output_profile_sources.get(record.id)
                ),
                "unexpectedlyVerbose": record.id in unexpectedly_verbose_task_ids,
                "effort": record.effort if record.effort is not None else task_efforts.get(record.id),
                "effortSource": (
                    record.effort_source
                    if record.effort_source is not None
                    else task_effort_sources.get(record.id)
                ),
                "status": record.status,
                "summary": record.summary,
                "filesChanged": int(review_payload["diffStats"]["filesChanged"]),
                "protectedPathViolationCount": len(record.protected_path_violations),
                "writeScopeViolationCount": len(record.write_scope_violations),
                "validationSuggestionCount": len(record.validation_intents) + len(record.validation_commands),
                "filesPromotable": int(deps["task_promotion_operations"](record)[0]["filesPromotable"]),
            }
        )
    return {
        "run": summary,
        "traceSummary": summary["traceSummary"],
        "branchSummary": summary["branchSummary"],
        "reviewSummary": review_summary,
        "taskCount": len(task_payloads),
        "tasks": task_payloads,
    }


def inventory_runs(args: argparse.Namespace, *, deps: dict[str, Any]) -> dict[str, Any]:
    runtime_root = Path(args.runtime_root).resolve()
    now = datetime.now(timezone.utc).astimezone()
    entries = [
        (*deps["summarize_run_manifest"](manifest_path, manifest, now=now), manifest_path, manifest)
        for manifest_path, manifest in deps["runtime_manifest_entries"](runtime_root=runtime_root)
    ]
    entries.sort(key=lambda item: item[1], reverse=True)
    limit = max(int(args.limit), 0)
    visible = entries[:limit] if limit else entries
    run_summaries = [summary for summary, _, _, _ in visible]
    return {
        "runtimeRoot": str(runtime_root),
        "runCount": len(entries),
        "shownRunCount": len(run_summaries),
        "completedRunCount": sum(1 for summary, _, _, _ in entries if summary["allTasksCompleted"]),
        "resumableRunCount": sum(
            1 for summary, _, _, _ in entries if summary["resumeCandidateTaskCount"] > 0
        ),
        "failedRunCount": sum(1 for summary, _, _, _ in entries if summary["hasFailures"]),
        "blockedRunCount": sum(1 for summary, _, _, _ in entries if summary["hasBlocked"]),
        "promotionReadyRunCount": sum(1 for summary, _, _, _ in entries if summary["promotionReady"]),
        "costlyRunCount": sum(1 for summary, _, _, _ in entries if summary["isCostly"]),
        "runs": run_summaries,
    }


def prune_runs(args: argparse.Namespace, *, deps: dict[str, Any]) -> dict[str, Any]:
    runtime_root = Path(args.runtime_root).resolve()
    older_than_days = float(args.older_than_days)
    if older_than_days < 0:
        raise deps["error_factory"]("--older-than-days must be >= 0")
    keep = max(int(args.keep), 0)
    now = datetime.now(timezone.utc).astimezone()
    entries = [
        (*deps["summarize_run_manifest"](manifest_path, manifest, now=now), manifest_path, manifest)
        for manifest_path, manifest in deps["runtime_manifest_entries"](runtime_root=runtime_root)
    ]
    entries.sort(key=lambda item: item[1], reverse=True)
    keep_run_ids = {summary["runId"] for summary, _, _, _ in entries[:keep]}
    cutoff = now - timedelta(days=older_than_days)
    candidate_entries: list[tuple[dict[str, Any], datetime, Path, dict[str, Any]]] = []
    skipped_recent_run_ids: list[str] = []
    skipped_incomplete_run_ids: list[str] = []
    for summary, last_updated_at, manifest_path, manifest in entries:
        run_id = str(summary["runId"])
        if run_id in keep_run_ids:
            continue
        if last_updated_at > cutoff:
            skipped_recent_run_ids.append(run_id)
            continue
        if summary["resumeCandidateTaskCount"] > 0 and not args.include_incomplete:
            skipped_incomplete_run_ids.append(run_id)
            continue
        candidate_entries.append((summary, last_updated_at, manifest_path, manifest))
    removed: list[dict[str, Any]] = []
    failures: list[dict[str, Any]] = []
    for summary, _, manifest_path, manifest in candidate_entries:
        if args.dry_run:
            removed.append(
                {
                    "runId": summary["runId"],
                    "manifestPath": summary["manifestPath"],
                    "runDir": summary["runDir"],
                    "workspacesDir": summary["workspacesDir"],
                    "dryRun": True,
                }
            )
            continue
        try:
            removed.append(deps["cleanup_loaded_run"](manifest_path, manifest))
        except deps["error_factory"] as exc:
            failure = {"runId": summary["runId"], "error": str(exc)}
            failures.append(failure)
            if not args.continue_on_error:
                raise
    return {
        "runtimeRoot": str(runtime_root),
        "olderThanDays": older_than_days,
        "keep": keep,
        "includeIncomplete": bool(args.include_incomplete),
        "dryRun": bool(args.dry_run),
        "candidateRunIds": [summary["runId"] for summary, _, _, _ in candidate_entries],
        "removedRunIds": [str(payload.get("runId", "")) for payload in removed],
        "skippedRecentRunIds": skipped_recent_run_ids,
        "skippedIncompleteRunIds": skipped_incomplete_run_ids,
        "keptRunIds": sorted(keep_run_ids),
        "removed": removed,
        "failures": failures,
    }
