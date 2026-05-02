from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from uuid import uuid4


def run_loaded_plan(
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, Any],
    plan: Any,
    *,
    claude_bin: str,
    runtime_root: Path,
    max_parallel: int,
    continue_on_error: bool,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    initial_records: dict[str, Any] | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    existing_run_id: str | None = None,
    existing_run_dir: Path | None = None,
    existing_workspaces_dir: Path | None = None,
    write_plan_snapshot: bool = True,
    normalize_worker_validation_mode: Callable[..., str | None] = None,
    normalize_effort_override: Callable[..., str | None] = None,
    effective_plan_worker_validation_modes: Callable[..., dict[str, str]] = None,
    effective_plan_worker_validation_mode_sources: Callable[..., dict[str, str]] = None,
    effective_plan_output_profiles: Callable[..., dict[str, str]] = None,
    effective_plan_output_profile_sources: Callable[..., dict[str, str]] = None,
    effective_plan_efforts: Callable[..., dict[str, str | None]] = None,
    effective_plan_effort_sources: Callable[..., dict[str, str | None]] = None,
    effective_task_skills: Callable[..., list[str]] = None,
    topological_batches: Callable[[list[Any]], Any] = None,
    validate_scope_contract: Callable[[Any, dict[str, Any]], None] = None,
    ensure_claude_available: Callable[[str], None] = None,
    write_selected_plan_snapshot: Callable[[Path, Any], None] = None,
    agent_payload_for_claude: Callable[..., dict[str, Any]] = None,
    append_run_event: Callable[..., None] = None,
    task_branch_context_id: Callable[[Any, dict[str, Any] | None], str] = None,
    evaluate_run_governance: Callable[[dict[str, Any], Any], dict[str, Any]] = None,
    blocked_record: Callable[..., Any] = None,
    effective_workspace_mode: Callable[[Any, Any], str] = None,
    write_manifest: Callable[..., None] = None,
    select_parallel_ready_batch: Callable[..., list[Any]] = None,
    execute_task: Callable[..., Any] = None,
    aggregate_usage: Callable[[dict[str, Any]], dict[str, Any]] = None,
    effective_plan_model_profiles: Callable[[Any, dict[str, Any]], dict[str, str]] = None,
    effective_plan_models: Callable[[Any, dict[str, Any]], dict[str, str]] = None,
    complex_model_task_ids: Callable[[dict[str, str]], list[str]] = None,
    analyze_plan_topology: Callable[[Any, dict[str, Any]], dict[str, Any]] = None,
    serialize_run_policy: Callable[[Any], dict[str, Any]] = None,
    summarized_worker_validation_mode: Callable[[list[str]], str] = None,
    summarize_branch_contexts: Callable[[list[Any]], dict[str, Any]] = None,
    default_workspaces_dir: Callable[..., Path] = None,
    slugify: Callable[[str], str] = None,
    error_factory: type[Exception] = RuntimeError,
) -> dict[str, Any]:
    worker_validation_override = (
        normalize_worker_validation_mode(worker_validation_mode, location="run worker validation mode override")
        if worker_validation_mode
        else None
    )
    normalized_effort_override = normalize_effort_override(effort_override, location="run effort override")
    task_worker_validation_modes = effective_plan_worker_validation_modes(plan, agents, run_override=worker_validation_override)
    task_worker_validation_mode_sources = effective_plan_worker_validation_mode_sources(plan, agents, run_override=worker_validation_override)
    task_output_profiles = effective_plan_output_profiles(plan, agents)
    task_output_profile_sources = effective_plan_output_profile_sources(plan, agents)
    task_efforts = effective_plan_efforts(plan, agents, run_override=normalized_effort_override)
    task_effort_sources = effective_plan_effort_sources(plan, agents, run_override=normalized_effort_override)
    topological_batches(plan.tasks)
    validate_scope_contract(plan, agents)
    if not dry_run:
        ensure_claude_available(claude_bin)
    runtime_root = runtime_root.resolve()
    run_id = existing_run_id or (f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}" f"-{slugify(plan.name)}-{uuid4().hex[:8]}")
    run_dir = existing_run_dir.resolve() if existing_run_dir is not None else runtime_root / "runs" / run_id
    workspaces_dir = (
        existing_workspaces_dir.resolve()
        if existing_workspaces_dir is not None
        else default_workspaces_dir(runtime_root=runtime_root, run_id=run_id)
    )
    run_dir.mkdir(parents=True, exist_ok=True)
    workspaces_dir.mkdir(parents=True, exist_ok=True)
    if write_plan_snapshot:
        write_selected_plan_snapshot(run_dir, plan)
    seeded_task_ids = sorted(initial_records or {})
    agents_json_by_task_id = {
        task.id: agent_payload_for_claude(
            agents,
            selected_names=[task.agent],
            resolved_skills_by_name={
                task.agent: effective_task_skills(task, agents[task.agent]),
            },
        )
        for task in plan.tasks
    }
    records: dict[str, Any] = dict(initial_records or {})
    run_events: list[dict[str, Any]] = []
    append_run_event(
        run_events,
        phase="run-start",
        task_ids=[task.id for task in plan.tasks],
        branch_context_ids=[task_branch_context_id(task, records) for task in plan.tasks],
        details={"dryRun": dry_run, "maxParallel": max(max_parallel, 1), "resume": existing_run_id is not None, "retryOfRunId": retry_of_run_id, "seededTaskIds": seeded_task_ids},
    )
    pending = {task.id: task for task in plan.tasks if task.id not in records}
    fail_fast_triggered = False
    stop_scheduling_reason: str | None = None
    while pending:
        run_governance = evaluate_run_governance(records, plan.run_policy)
        if run_governance["shouldStopScheduling"] and stop_scheduling_reason is None:
            first_alert = run_governance["blockingAlerts"][0]
            stop_scheduling_reason = f"Run policy stop triggered: {first_alert['message']}"
        newly_blocked = False
        for task_id, task in list(pending.items()):
            if not task.depends_on:
                continue
            dependency_records = [records.get(dependency) for dependency in task.depends_on]
            if any(record is None for record in dependency_records):
                continue
            if any(record.status not in {"completed", "planned"} for record in dependency_records if record is not None):
                records[task_id] = blocked_record(
                    task,
                    task.agent,
                    agents[task.agent],
                    effective_workspace_mode(task, agents[task.agent]),
                    reason="Dependency failed or was blocked.",
                    dependency_records=records,
                    worker_validation_mode=worker_validation_override,
                    effort_override=normalized_effort_override,
                )
                append_run_event(
                    run_events,
                    phase="task-blocked",
                    task_id=task.id,
                    parent_task_ids=task.depends_on,
                    branch_context_id=records[task_id].branch_context_id,
                    status="blocked",
                    message="Dependency failed or was blocked.",
                )
                pending.pop(task_id)
                newly_blocked = True
        if newly_blocked:
            write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events)
            continue
        if fail_fast_triggered or stop_scheduling_reason is not None:
            for task_id, task in list(pending.items()):
                records[task_id] = blocked_record(
                    task,
                    task.agent,
                    agents[task.agent],
                    effective_workspace_mode(task, agents[task.agent]),
                    reason=stop_scheduling_reason or "Coordinator stopped scheduling new tasks after a worker failure.",
                    dependency_records=records,
                    worker_validation_mode=worker_validation_override,
                    effort_override=normalized_effort_override,
                )
                append_run_event(
                    run_events,
                    phase="task-blocked",
                    task_id=task.id,
                    parent_task_ids=task.depends_on,
                    branch_context_id=records[task_id].branch_context_id,
                    status="blocked",
                    message=stop_scheduling_reason or "Coordinator stopped scheduling new tasks after a worker failure.",
                )
                pending.pop(task_id)
            break
        ready = [task for task in pending.values() if all(records.get(dependency_id) is not None for dependency_id in task.depends_on)]
        if not ready:
            unresolved = ", ".join(sorted(pending))
            raise error_factory(f"No schedulable tasks remain; unresolved tasks: {unresolved}")
        batch = select_parallel_ready_batch(plan, ready, agents, max_parallel=max(max_parallel, 1))
        append_run_event(run_events, phase="batch-ready", task_ids=[task.id for task in batch], branch_context_ids=[task_branch_context_id(task, records) for task in batch], details={"pendingTaskIds": sorted(pending)})
        with ThreadPoolExecutor(max_workers=max(1, min(max_parallel, len(batch)))) as executor:
            future_map = {
                executor.submit(
                    execute_task,
                    run_dir,
                    runtime_root,
                    workspaces_dir,
                    plan,
                    agents,
                    task,
                    records,
                    claude_bin=claude_bin,
                    agents_json=agents_json_by_task_id[task.id],
                    dry_run=dry_run,
                    worker_validation_mode=worker_validation_override,
                    effort_override=normalized_effort_override,
                ): task
                for task in batch
            }
            for future in as_completed(future_map):
                task = future_map[future]
                records[task.id] = future.result()
                for attempt_error in (getattr(records[task.id], "attempt_errors", None) or []):
                    append_run_event(
                        run_events,
                        phase="task-retry",
                        task_id=task.id,
                        parent_task_ids=task.depends_on,
                        branch_context_id=records[task.id].branch_context_id,
                        status="retry",
                        message=attempt_error.get("error", ""),
                        details=attempt_error,
                    )
                append_run_event(run_events, phase="task-finished", task_id=task.id, parent_task_ids=task.depends_on, branch_context_id=records[task.id].branch_context_id, status=records[task.id].status, message=records[task.id].summary)
                pending.pop(task.id, None)
                write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events)
                if not continue_on_error and records[task.id].status not in {"completed", "planned"}:
                    fail_fast_triggered = True
                    stop_scheduling_reason = "Coordinator stopped scheduling new tasks after a worker failure."
    append_run_event(run_events, phase="run-finished", details={"remainingTaskIds": sorted(pending)})
    write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events)
    status_counts: dict[str, int] = {}
    for record in records.values():
        status_counts[record.status] = status_counts.get(record.status, 0) + 1
    usage_totals = aggregate_usage(records)
    run_governance = evaluate_run_governance(records, plan.run_policy)
    task_model_profiles = effective_plan_model_profiles(plan, agents)
    task_models = effective_plan_models(plan, agents)
    complex_model_tasks = complex_model_task_ids(task_model_profiles)
    topology = analyze_plan_topology(plan, agents)
    payload = {
        "runId": run_id,
        "plan": plan.name,
        "goal": plan.goal,
        "dryRun": dry_run,
        "workerValidationMode": summarized_worker_validation_mode(list(task_worker_validation_modes.values())),
        "workerValidationModeOverride": worker_validation_override,
        "effortOverride": normalized_effort_override,
        "taskWorkerValidationModes": task_worker_validation_modes,
        "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
        "taskOutputProfiles": task_output_profiles,
        "taskOutputProfileSources": task_output_profile_sources,
        "taskEfforts": task_efforts,
        "taskEffortSources": task_effort_sources,
        "taskModels": task_models,
        "taskModelProfiles": task_model_profiles,
        "taskResolvedSkills": {
            task.id: effective_task_skills(task, agents[task.agent])
            for task in plan.tasks
        },
        "complexModelTaskIds": complex_model_tasks,
        "complexModelTaskCount": len(complex_model_tasks),
        "topology": topology,
        "runtimeRoot": str(runtime_root),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "runPolicy": serialize_run_policy(plan.run_policy),
        "runGovernance": run_governance,
        "statusCounts": status_counts,
        "usageTotals": usage_totals,
        "branchSummary": summarize_branch_contexts(list(records.values())),
        "events": list(run_events),
        "tasks": [asdict(records[task.id]) for task in plan.tasks],
    }
    if retry_of_run_id:
        payload["retryOfRunId"] = retry_of_run_id
        payload["requestedTaskIds"] = list(requested_task_ids or [])
        payload["retriedTaskIds"] = list(retried_task_ids or [])
        payload["seededTaskIds"] = seeded_task_ids
    return payload


def run_plan(
    args: Any,
    *,
    load_agents: Callable[[Path], dict[str, Any]],
    load_task_plan: Callable[[Path, dict[str, Any]], Any],
    selected_plan: Callable[[Any, list[str]], Any],
    run_loaded_plan_fn: Callable[..., dict[str, Any]],
) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    plan_path = Path(args.task_plan).resolve()
    agents = load_agents(agents_path)
    plan = selected_plan(load_task_plan(plan_path, agents), args.selected_tasks)
    return run_loaded_plan_fn(
        plan_path,
        agents_path,
        agents,
        plan,
        claude_bin=args.claude_bin,
        runtime_root=Path(args.runtime_root).resolve(),
        max_parallel=args.max_parallel,
        continue_on_error=args.continue_on_error,
        dry_run=args.dry_run,
        worker_validation_mode=args.worker_validation_mode,
        effort_override=getattr(args, "effort", None),
        max_task_retries=getattr(args, "max_task_retries", None),
    )


def resume_run(
    args: Any,
    *,
    root: Path,
    load_run_manifest: Callable[[str], tuple[Path, dict[str, Any]]],
    selected_run_records: Callable[[dict[str, Any], list[str]], list[Any]],
    manifest_run_dir: Callable[[Path, dict[str, Any]], Path],
    manifest_workspaces_dir: Callable[..., Path],
    manifest_required_path: Callable[..., Path],
    load_agents: Callable[[Path], dict[str, Any]],
    manifest_selected_plan_path: Callable[..., Path],
    load_task_plan: Callable[[Path, dict[str, Any]], Any],
    manifest_worker_validation_override: Callable[..., str | None],
    normalize_worker_validation_mode: Callable[..., str | None],
    selected_plan: Callable[[Any, list[str]], Any],
    planned_record: Callable[..., Any],
    effective_workspace_mode: Callable[[Any, Any], str],
    normalize_effort_override: Callable[..., str | None],
    run_loaded_plan_fn: Callable[..., dict[str, Any]],
    error_factory: type[Exception],
) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    previous_records = {record.id: record for record in selected_run_records(manifest, [])}
    if not previous_records:
        raise error_factory(f"{manifest_path}: run manifest contains no task records to resume")
    run_dir = manifest_run_dir(manifest_path, manifest)
    workspaces_dir = manifest_workspaces_dir(manifest, run_dir=run_dir)
    runtime_root = manifest_required_path(manifest, "runtimeRoot", location=str(manifest_path))
    agents_path = Path(args.agents).resolve() if args.agents else manifest_required_path(manifest, "agentsPath", location=str(manifest_path))
    if not agents_path.exists():
        raise error_factory(f"Resume source agents file '{agents_path}' does not exist")
    agents = load_agents(agents_path)
    source_plan_path = manifest_selected_plan_path(manifest_path, manifest, location=str(manifest_path))
    if not source_plan_path.exists():
        raise error_factory(f"Resume source plan '{source_plan_path}' does not exist")
    base_plan = load_task_plan(source_plan_path, agents)
    source_worker_validation_override = manifest_worker_validation_override(manifest, location=str(manifest_path))
    resume_worker_validation_mode = (
        normalize_worker_validation_mode(getattr(args, "worker_validation_mode", "") or source_worker_validation_override, location=f"resume source '{manifest_path}' worker validation mode")
        if (getattr(args, "worker_validation_mode", "") or source_worker_validation_override)
        else None
    )
    requested_task_ids = list(args.selected_tasks)
    if not requested_task_ids:
        requested_task_ids = [task.id for task in base_plan.tasks if previous_records.get(task.id) is None or previous_records[task.id].status != "completed"]
    if not requested_task_ids:
        raise error_factory("Run manifest contains no resumable tasks; use --task to pick tasks explicitly")
    resume_scope = selected_plan(base_plan, requested_task_ids)
    resume_scope_ids = {task.id for task in resume_scope.tasks}
    requested_set = set(requested_task_ids)
    initial_records: dict[str, Any] = {}
    resumed_task_ids: list[str] = []
    for task in base_plan.tasks:
        prior_record = previous_records.get(task.id)
        if task.id in resume_scope_ids:
            if task.id in requested_set or prior_record is None or prior_record.status != "completed":
                resumed_task_ids.append(task.id)
                continue
            if prior_record is not None:
                initial_records[task.id] = prior_record
            continue
        if prior_record is not None:
            initial_records[task.id] = prior_record
            continue
        initial_records[task.id] = planned_record(
            task,
            task.agent,
            agents[task.agent],
            effective_workspace_mode(task, agents[task.agent]),
            str((workspaces_dir / task.id).resolve()) if effective_workspace_mode(task, agents[task.agent]) != "repo" else str(root),
            summary="Pending from the existing run; not selected for this resume.",
            dependency_records=initial_records,
            worker_validation_mode=resume_worker_validation_mode,
            effort_override=normalize_effort_override(getattr(args, "effort", None), location="resume effort override"),
        )
    if not resumed_task_ids:
        raise error_factory("Nothing to resume; all selected tasks are already completed")
    payload = run_loaded_plan_fn(
        source_plan_path,
        agents_path,
        agents,
        base_plan,
        claude_bin=args.claude_bin,
        runtime_root=runtime_root,
        max_parallel=args.max_parallel,
        continue_on_error=args.continue_on_error,
        dry_run=args.dry_run,
        worker_validation_mode=resume_worker_validation_mode,
        effort_override=getattr(args, "effort", None),
        initial_records=initial_records,
        requested_task_ids=requested_task_ids,
        existing_run_id=str(manifest.get("runId", "")).strip() or None,
        existing_run_dir=run_dir,
        existing_workspaces_dir=workspaces_dir,
        write_plan_snapshot=False,
        max_task_retries=getattr(args, "max_task_retries", None),
    )
    payload["sourceManifestPath"] = str(manifest_path)
    payload["requestedTaskIds"] = requested_task_ids
    payload["resumedTaskIds"] = resumed_task_ids
    payload["preservedTaskIds"] = sorted(initial_records)
    payload["resumedInPlace"] = True
    return payload


def retry_run(
    args: Any,
    *,
    load_run_manifest: Callable[[str], tuple[Path, dict[str, Any]]],
    selected_run_records: Callable[[dict[str, Any], list[str]], list[Any]],
    manifest_selected_plan_path: Callable[..., Path],
    manifest_required_path: Callable[..., Path],
    load_agents: Callable[[Path], dict[str, Any]],
    load_task_plan: Callable[[Path, dict[str, Any]], Any],
    selected_plan: Callable[[Any, list[str]], Any],
    manifest_worker_validation_override: Callable[..., str | None],
    normalize_worker_validation_mode: Callable[..., str | None],
    run_loaded_plan_fn: Callable[..., dict[str, Any]],
    error_factory: type[Exception],
) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    previous_records = {record.id: record for record in selected_run_records(manifest, [])}
    requested_task_ids = list(args.selected_tasks)
    if not requested_task_ids:
        requested_task_ids = [task_id for task_id, record in sorted(previous_records.items()) if record.status in {"failed", "blocked"}]
    if not requested_task_ids:
        raise error_factory("Run manifest contains no failed or blocked tasks to retry; use --task to pick tasks explicitly")
    plan_path = manifest_selected_plan_path(manifest_path, manifest, location=str(manifest_path))
    agents_path = Path(args.agents).resolve() if args.agents else manifest_required_path(manifest, "agentsPath", location=str(manifest_path))
    runtime_root = Path(args.runtime_root).resolve() if args.runtime_root else manifest_required_path(manifest, "runtimeRoot", location=str(manifest_path))
    if not plan_path.exists():
        raise error_factory(f"Retry source plan '{plan_path}' does not exist")
    if not agents_path.exists():
        raise error_factory(f"Retry source agents file '{agents_path}' does not exist")
    agents = load_agents(agents_path)
    retry_plan = selected_plan(load_task_plan(plan_path, agents), requested_task_ids)
    requested_set = set(requested_task_ids)
    initial_records: dict[str, Any] = {}
    retried_task_ids: list[str] = []
    for task in retry_plan.tasks:
        prior_record = previous_records.get(task.id)
        if task.id in requested_set:
            retried_task_ids.append(task.id)
        elif prior_record is not None and prior_record.status == "completed":
            initial_records[task.id] = prior_record
        else:
            retried_task_ids.append(task.id)
    if not retried_task_ids:
        raise error_factory("Nothing to retry; all selected tasks are already completed")
    source_worker_validation_override = manifest_worker_validation_override(manifest, location=str(manifest_path))
    payload = run_loaded_plan_fn(
        plan_path,
        agents_path,
        agents,
        retry_plan,
        claude_bin=args.claude_bin,
        runtime_root=runtime_root,
        max_parallel=args.max_parallel,
        continue_on_error=args.continue_on_error,
        dry_run=args.dry_run,
        worker_validation_mode=(normalize_worker_validation_mode(getattr(args, "worker_validation_mode", "") or source_worker_validation_override, location=f"retry source '{manifest_path}' worker validation mode") if (getattr(args, "worker_validation_mode", "") or source_worker_validation_override) else None),
        effort_override=getattr(args, "effort", None),
        initial_records=initial_records,
        retry_of_run_id=str(manifest.get("runId", "")),
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
        max_task_retries=getattr(args, "max_task_retries", None),
    )
    payload["sourceManifestPath"] = str(manifest_path)
    return payload
