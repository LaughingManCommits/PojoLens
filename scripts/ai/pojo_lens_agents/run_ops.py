from __future__ import annotations

import asyncio
from dataclasses import asdict, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from uuid import uuid4


def _resolved_follow_up_behavior(plan: Any, *, override: str | None) -> str:
    if override:
        return override
    return str(getattr(plan.run_policy, "follow_up_behavior", "ignore") or "ignore")


def _inject_follow_up_tasks(
    *,
    plan: Any,
    agents: dict[str, Any],
    records: dict[str, Any],
    pending: dict[str, Any],
    completed_batch_task_ids: list[str],
    follow_up_behavior: str,
    batch_index: int,
    coerce_follow_up_task: Callable[..., Any],
    validate_scope_contract: Callable[[Any, dict[str, Any]], None],
    topological_batches: Callable[[list[Any]], Any],
    write_selected_plan_snapshot: Callable[[Path, Any], None],
    run_dir: Path,
    append_run_event: Callable[..., None],
    run_events: list[dict[str, Any]],
) -> tuple[Any, list[Any]]:
    if follow_up_behavior != "inject":
        return plan, []
    injected_tasks: list[Any] = []
    existing_task_ids = {task.id for task in plan.tasks}
    for emitter_task_id in completed_batch_task_ids:
        record = records.get(emitter_task_id)
        if record is None:
            continue
        for follow_up_index, proposal in enumerate(getattr(record, "follow_up_tasks", []) or [], start=1):
            try:
                injected_task = coerce_follow_up_task(
                    proposal,
                    plan,
                    agents,
                    emitter_task_id=emitter_task_id,
                    existing_task_ids=existing_task_ids,
                    location=f"worker result {emitter_task_id}:followUpTasks[{follow_up_index}]",
                )
                candidate_plan = replace(plan, tasks=[*plan.tasks, injected_task])
                topological_batches(candidate_plan.tasks)
                validate_scope_contract(candidate_plan, agents)
            except Exception as exc:
                append_run_event(
                    run_events,
                    phase="task-injection-rejected",
                    task_id=emitter_task_id,
                    branch_context_id=record.branch_context_id,
                    status="blocked",
                    message=str(exc),
                    details={
                        "emitterTaskId": emitter_task_id,
                        "followUpIndex": follow_up_index,
                        "batchIndex": batch_index,
                    },
                )
                continue
            plan = candidate_plan
            pending[injected_task.id] = injected_task
            injected_tasks.append(injected_task)
            existing_task_ids.add(injected_task.id)
            write_selected_plan_snapshot(run_dir, plan)
            append_run_event(
                run_events,
                phase="task-injected",
                task_id=injected_task.id,
                parent_task_ids=list(injected_task.depends_on),
                branch_context_id=injected_task.id,
                status="planned",
                message=f"Injected from task '{emitter_task_id}'.",
                details={
                    "emitterTaskId": emitter_task_id,
                    "followUpIndex": follow_up_index,
                    "batchIndex": batch_index,
                    "injectedFrom": emitter_task_id,
                },
            )
    return plan, injected_tasks


async def run_loaded_plan(
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
    follow_up_behavior_override: str | None = None,
    existing_run_id: str | None = None,
    existing_run_dir: Path | None = None,
    existing_workspaces_dir: Path | None = None,
    write_plan_snapshot: bool = True,
    hitl_override: bool = False,
    hitl_mode_override: str | None = None,
    hitl_auto_approve: bool = False,
    reuse_unchanged: bool = False,
    prior_completed_records: dict[str, Any] | None = None,
    compute_task_fingerprint: Callable[..., tuple[str, dict[str, Any]]] | None = None,
    effective_task_read_paths: Callable[[Any, Any], list[str]] | None = None,
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
    load_model_pricing: Callable[..., dict[str, Any]] | None = None,
    estimate_plan_cost: Callable[..., dict[str, Any]] | None = None,
    serialize_run_policy: Callable[[Any], dict[str, Any]] = None,
    summarized_worker_validation_mode: Callable[[list[str]], str] = None,
    summarize_branch_contexts: Callable[[list[Any]], dict[str, Any]] = None,
    coerce_follow_up_task: Callable[..., Any] | None = None,
    default_workspaces_dir: Callable[..., Path] = None,
    slugify: Callable[[str], str] = None,
    resolve_hitl_policy: Callable[..., Any] = None,
    should_trigger_hitl_gate: Callable[..., bool] = None,
    hitl_gate_context_factory: Callable[..., Any] = None,
    wait_for_hitl_decision: Callable[..., Any] = None,
    write_text: Callable[[Path, str], None] | None = None,
    otel_endpoint: str | None = None,
    manifest_payload_builder: Callable[..., dict[str, Any]] | None = None,
    build_trace_payload: Callable[..., dict[str, Any]] | None = None,
    summarize_run_manifest: Callable[..., Any] | None = None,
    parse_iso_datetime: Callable[..., Any] | None = None,
    datetime_to_iso: Callable[..., Any] | None = None,
    emit_otel_trace: Callable[..., dict[str, Any]] | None = None,
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
    task_model_profiles = effective_plan_model_profiles(plan, agents) if effective_plan_model_profiles is not None else {}
    task_models = effective_plan_models(plan, agents) if effective_plan_models is not None else {}
    resolved_follow_up_behavior = _resolved_follow_up_behavior(
        plan,
        override=follow_up_behavior_override,
    )
    hitl_policy = resolve_hitl_policy(
        plan.run_policy,
        hitl_override=hitl_override,
        hitl_mode_override=hitl_mode_override if hitl_override or hitl_mode_override else None,
        hitl_auto_approve=hitl_auto_approve,
    )
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
    batch_index = 0
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
            write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events, follow_up_behavior=resolved_follow_up_behavior, follow_up_behavior_override=follow_up_behavior_override)
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
        batch_index += 1
        append_run_event(run_events, phase="batch-ready", task_ids=[task.id for task in batch], branch_context_ids=[task_branch_context_id(task, records) for task in batch], details={"pendingTaskIds": sorted(pending)})

        # Compute fingerprints for all batch tasks when fingerprint callable is available
        task_fingerprints: dict[str, tuple[str, dict[str, Any]]] = {}
        if compute_task_fingerprint is not None and effective_task_read_paths is not None:
            for _t in batch:
                _dep_recs = {_d: records[_d] for _d in _t.depends_on if _d in records}
                _rp = effective_task_read_paths(plan, _t)
                try:
                    _fp, _fpi = compute_task_fingerprint(
                        _t, agents[_t.agent], _dep_recs, _rp,
                        task_models.get(_t.id), task_efforts.get(_t.id),
                    )
                    task_fingerprints[_t.id] = (_fp, _fpi)
                except Exception:
                    pass

        # Identify reusable tasks
        reused_ids: set[str] = set()
        if reuse_unchanged and prior_completed_records is not None:
            for _t in batch:
                _fp_data = task_fingerprints.get(_t.id)
                if _fp_data is None:
                    continue
                _fp, _fpi = _fp_data
                _prior = prior_completed_records.get(_t.id)
                if (
                    _prior is not None
                    and _prior.status == "completed"
                    and getattr(_prior, "fingerprint", None) == _fp
                ):
                    reused_ids.add(_t.id)

        completed_batch_task_ids: list[str] = []
        failed_batch_task_ids: list[str] = []

        # Emit reuse events and skip dispatch for unchanged tasks
        for _t in batch:
            if _t.id not in reused_ids:
                continue
            _fp, _fpi = task_fingerprints[_t.id]
            _prior = prior_completed_records[_t.id]
            records[_t.id] = replace(_prior, fingerprint=_fp, fingerprint_inputs=_fpi)
            completed_batch_task_ids.append(_t.id)
            pending.pop(_t.id, None)
            append_run_event(
                run_events,
                phase="task-reused",
                task_id=_t.id,
                parent_task_ids=list(_t.depends_on),
                branch_context_id=records[_t.id].branch_context_id,
                status="completed",
                message="Task reused: inputs unchanged from prior successful run.",
            )
            write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events, follow_up_behavior=resolved_follow_up_behavior, follow_up_behavior_override=follow_up_behavior_override)

        execute_batch = [_t for _t in batch if _t.id not in reused_ids]
        semaphore = asyncio.Semaphore(max(max_parallel, 1))

        async def _run_one(t):
            async with semaphore:
                return t, await execute_task(
                    run_dir,
                    runtime_root,
                    workspaces_dir,
                    plan,
                    agents,
                    t,
                    records,
                    claude_bin=claude_bin,
                    agents_json=agents_json_by_task_id[t.id],
                    dry_run=dry_run,
                    worker_validation_mode=worker_validation_override,
                    effort_override=normalized_effort_override,
                )

        batch_futures = [asyncio.ensure_future(_run_one(t)) for t in execute_batch]
        for coro in asyncio.as_completed(batch_futures):
            task, record = await coro
            if task.id in task_fingerprints:
                _fp, _fpi = task_fingerprints[task.id]
                records[task.id] = replace(record, fingerprint=_fp, fingerprint_inputs=_fpi)
            else:
                records[task.id] = record
            completed_batch_task_ids.append(task.id)
            if records[task.id].status not in {"completed", "planned"}:
                failed_batch_task_ids.append(task.id)
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
            write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events, follow_up_behavior=resolved_follow_up_behavior, follow_up_behavior_override=follow_up_behavior_override)
            if not continue_on_error and records[task.id].status not in {"completed", "planned"}:
                fail_fast_triggered = True
                stop_scheduling_reason = "Coordinator stopped scheduling new tasks after a worker failure."
        plan, injected_tasks = _inject_follow_up_tasks(
            plan=plan,
            agents=agents,
            records=records,
            pending=pending,
            completed_batch_task_ids=completed_batch_task_ids,
            follow_up_behavior=resolved_follow_up_behavior,
            batch_index=batch_index,
            coerce_follow_up_task=coerce_follow_up_task,
            validate_scope_contract=validate_scope_contract,
            topological_batches=topological_batches,
            write_selected_plan_snapshot=write_selected_plan_snapshot,
            run_dir=run_dir,
            append_run_event=append_run_event,
            run_events=run_events,
        )
        if injected_tasks:
            for injected_task in injected_tasks:
                agents_json_by_task_id[injected_task.id] = agent_payload_for_claude(
                    agents,
                    selected_names=[injected_task.agent],
                    resolved_skills_by_name={
                        injected_task.agent: effective_task_skills(injected_task, agents[injected_task.agent]),
                    },
                )
            write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events, follow_up_behavior=resolved_follow_up_behavior, follow_up_behavior_override=follow_up_behavior_override)
        if should_trigger_hitl_gate(
            hitl_policy,
            batch_index=batch_index,
            failed_task_ids=failed_batch_task_ids,
        ):
            gate_id = f"gate-{batch_index:03d}"
            context = hitl_gate_context_factory(
                gate_id=gate_id,
                mode=hitl_policy.mode,
                batch_index=batch_index,
                completed_batch_task_ids=completed_batch_task_ids,
                failed_task_ids=failed_batch_task_ids,
                pending_task_ids=sorted(pending),
                run_dir=run_dir,
                dry_run=dry_run,
            )
            append_run_event(
                run_events,
                phase="hitl-gate",
                task_ids=completed_batch_task_ids,
                branch_context_ids=[
                    records[task_id].branch_context_id
                    for task_id in completed_batch_task_ids
                    if task_id in records
                ],
                details={
                    "gateId": gate_id,
                    "mode": hitl_policy.mode,
                    "batchIndex": batch_index,
                    "pendingTaskIds": sorted(pending),
                    "failedTaskIds": failed_batch_task_ids,
                    "autoApprove": bool(hitl_policy.auto_approve),
                },
            )
            write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events, follow_up_behavior=resolved_follow_up_behavior, follow_up_behavior_override=follow_up_behavior_override)
            decision = wait_for_hitl_decision(
                context,
                auto_approve=hitl_policy.auto_approve,
                write_text=write_text,
            )
            append_run_event(
                run_events,
                phase="hitl-approved" if decision.approved else "hitl-aborted",
                task_ids=completed_batch_task_ids,
                branch_context_ids=[
                    records[task_id].branch_context_id
                    for task_id in completed_batch_task_ids
                    if task_id in records
                ],
                details={
                    "gateId": gate_id,
                    "mode": hitl_policy.mode,
                    "batchIndex": batch_index,
                    "action": decision.action,
                    "reason": decision.reason,
                    "source": decision.source,
                    "sentinelPath": decision.sentinel_path,
                },
            )
            write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events, follow_up_behavior=resolved_follow_up_behavior, follow_up_behavior_override=follow_up_behavior_override)
            if not decision.approved:
                fail_fast_triggered = True
                stop_scheduling_reason = f"HITL gate '{gate_id}' aborted by operator."
    append_run_event(run_events, phase="run-finished", details={"remainingTaskIds": sorted(pending)})
    write_manifest(run_id, plan_path, agents_path, agents, runtime_root, run_dir, workspaces_dir, plan, records, dry_run=dry_run, worker_validation_mode=worker_validation_override, effort_override=normalized_effort_override, retry_of_run_id=retry_of_run_id, requested_task_ids=requested_task_ids, retried_task_ids=retried_task_ids, seeded_task_ids=seeded_task_ids, run_events=run_events, follow_up_behavior=resolved_follow_up_behavior, follow_up_behavior_override=follow_up_behavior_override)
    status_counts: dict[str, int] = {}
    for record in records.values():
        status_counts[record.status] = status_counts.get(record.status, 0) + 1
    usage_totals = aggregate_usage(records)
    run_governance = evaluate_run_governance(records, plan.run_policy)
    task_model_profiles = effective_plan_model_profiles(plan, agents)
    task_models = effective_plan_models(plan, agents)
    complex_model_tasks = complex_model_task_ids(task_model_profiles)
    topology = analyze_plan_topology(plan, agents)
    cost_estimate = estimate_plan_cost(
        plan,
        agents,
        pricing=load_model_pricing(),
        task_models=task_models,
        task_model_profiles=task_model_profiles,
        task_efforts=task_efforts,
        prompt_estimated_tokens_by_task={
            task_id: int(record.prompt_estimated_tokens or 0)
            for task_id, record in records.items()
            if int(record.prompt_estimated_tokens or 0) > 0
        },
        topology=topology,
    )
    payload = {
        "runId": run_id,
        "plan": plan.name,
        "goal": plan.goal,
        "dryRun": dry_run,
        "followUpBehavior": resolved_follow_up_behavior,
        "followUpBehaviorOverride": follow_up_behavior_override,
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
        "hitlPolicy": {
            "enabled": bool(hitl_policy.enabled),
            "mode": hitl_policy.mode,
            "autoApprove": bool(hitl_policy.auto_approve),
            "source": "override" if hitl_override or hitl_mode_override else "runPolicy",
        },
        "runGovernance": run_governance,
        "statusCounts": status_counts,
        "usageTotals": usage_totals,
        "costEstimate": cost_estimate,
        "branchSummary": summarize_branch_contexts(list(records.values())),
        "events": list(run_events),
        "tasks": [asdict(records[task.id]) for task in plan.tasks],
    }
    if retry_of_run_id:
        payload["retryOfRunId"] = retry_of_run_id
        payload["requestedTaskIds"] = list(requested_task_ids or [])
        payload["retriedTaskIds"] = list(retried_task_ids or [])
        payload["seededTaskIds"] = seeded_task_ids
    if otel_endpoint:
        if dry_run:
            payload["otel"] = {
                "enabled": True,
                "endpoint": otel_endpoint,
                "emitted": False,
                "reason": "dry-run",
            }
        else:
            manifest_path = run_dir / "manifest.json"
            manifest = manifest_payload_builder(
                run_id,
                plan_path,
                agents_path,
                agents,
                runtime_root,
                run_dir,
                workspaces_dir,
                plan,
                records,
                dry_run=dry_run,
                worker_validation_mode=worker_validation_override,
                effort_override=normalized_effort_override,
                retry_of_run_id=retry_of_run_id,
                requested_task_ids=requested_task_ids,
                retried_task_ids=retried_task_ids,
                seeded_task_ids=seeded_task_ids,
                run_events=run_events,
                follow_up_behavior=resolved_follow_up_behavior,
                follow_up_behavior_override=follow_up_behavior_override,
            )
            trace_payload = build_trace_payload(
                manifest_path,
                manifest,
                records=[records[task.id] for task in plan.tasks if task.id in records],
                summarize_run_manifest=summarize_run_manifest,
                parse_iso_datetime=parse_iso_datetime,
                datetime_to_iso=datetime_to_iso,
            )
            payload["otel"] = emit_otel_trace(trace_payload, endpoint=otel_endpoint)
    else:
        payload["otel"] = {
            "enabled": False,
            "endpoint": None,
            "emitted": False,
            "reason": "disabled",
        }
    return payload


def run_plan(
    args: Any,
    *,
    load_agents: Callable[[Path], dict[str, Any]],
    load_task_plan: Callable[[Path, dict[str, Any]], Any],
    selected_plan: Callable[[Any, list[str]], Any],
    run_loaded_plan_fn: Callable[..., dict[str, Any]],
    effective_plan_output_profiles: Callable[[Any, dict[str, Any]], dict[str, str]] | None = None,
    effective_plan_output_profile_sources: Callable[[Any, dict[str, Any]], dict[str, str]] | None = None,
    effective_plan_efforts: Callable[[Any, dict[str, Any]], dict[str, str | None]] | None = None,
    effective_plan_effort_sources: Callable[[Any, dict[str, Any]], dict[str, str]] | None = None,
    effective_plan_models: Callable[[Any, dict[str, Any]], dict[str, str | None]] | None = None,
    effective_plan_model_profiles: Callable[[Any, dict[str, Any]], dict[str, str | None]] | None = None,
    effective_task_skills: Callable[[Any, Any], list[str]] | None = None,
    complex_model_task_ids: Callable[[dict[str, str | None]], list[str]] | None = None,
    analyze_plan_topology: Callable[[Any, dict[str, Any]], dict[str, Any]] | None = None,
    serialize_run_policy: Callable[[Any], dict[str, Any]] | None = None,
    load_model_pricing: Callable[..., dict[str, Any]] | None = None,
    estimate_plan_cost: Callable[..., dict[str, Any]] | None = None,
) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    plan_path = Path(args.task_plan).resolve()
    agents = load_agents(agents_path)
    plan = selected_plan(load_task_plan(plan_path, agents), args.selected_tasks)
    if bool(getattr(args, "estimate", False)):
        task_output_profiles = effective_plan_output_profiles(plan, agents)
        task_output_profile_sources = effective_plan_output_profile_sources(plan, agents)
        task_models = effective_plan_models(plan, agents)
        task_model_profiles = effective_plan_model_profiles(plan, agents)
        task_efforts = effective_plan_efforts(plan, agents)
        task_effort_sources = effective_plan_effort_sources(plan, agents)
        complex_model_tasks = complex_model_task_ids(task_model_profiles)
        topology = analyze_plan_topology(plan, agents)
        cost_estimate = estimate_plan_cost(
            plan,
            agents,
            pricing=load_model_pricing(),
            task_models=task_models,
            task_model_profiles=task_model_profiles,
            task_efforts=task_efforts,
            topology=topology,
        )
        topology = dict(topology)
        topology_warnings = list(topology.get("warnings", []))
        topology_warnings.extend(cost_estimate.get("warnings", []))
        topology["warnings"] = topology_warnings
        topology["warningCount"] = len(topology_warnings)
        return {
            "estimatedOnly": True,
            "dryRun": True,
            "plan": plan.name,
            "goal": plan.goal,
            "planPath": str(plan_path),
            "agentsPath": str(agents_path),
            "runPolicy": serialize_run_policy(plan.run_policy),
            "taskIds": [task.id for task in plan.tasks],
            "taskCount": len(plan.tasks),
            "taskOutputProfiles": task_output_profiles,
            "taskOutputProfileSources": task_output_profile_sources,
            "taskEfforts": task_efforts,
            "taskEffortSources": task_effort_sources,
            "taskModels": task_models,
            "taskModelProfiles": task_model_profiles,
            "complexModelTaskIds": complex_model_tasks,
            "complexModelTaskCount": len(complex_model_tasks),
            "topology": topology,
            "costEstimate": cost_estimate,
            "tasks": [
                {
                    "id": task.id,
                    "agent": task.agent,
                    "outputProfile": task_output_profiles[task.id],
                    "outputProfileSource": task_output_profile_sources[task.id],
                    "model": task_models[task.id],
                    "modelProfile": task_model_profiles[task.id],
                    "effort": task_efforts[task.id],
                    "effortSource": task_effort_sources[task.id],
                    "resolvedSkills": effective_task_skills(task, agents[task.agent]),
                }
                for task in plan.tasks
            ],
        }
    run_kwargs = {
        "claude_bin": args.claude_bin,
        "runtime_root": Path(args.runtime_root).resolve(),
        "max_parallel": args.max_parallel,
        "continue_on_error": args.continue_on_error,
        "dry_run": args.dry_run,
        "worker_validation_mode": args.worker_validation_mode,
        "effort_override": getattr(args, "effort", None),
        "max_task_retries": getattr(args, "max_task_retries", None),
        "hitl": bool(getattr(args, "hitl", False)),
        "hitl_mode": getattr(args, "hitl_mode", None) if bool(getattr(args, "hitl", False)) else None,
        "hitl_auto_approve": bool(getattr(args, "hitl_auto_approve", False)),
        "reuse_unchanged": bool(getattr(args, "reuse_unchanged", False)),
    }
    if getattr(args, "follow_up_mode", None):
        run_kwargs["follow_up_behavior_override"] = getattr(args, "follow_up_mode")
    otel_endpoint = getattr(args, "otel_endpoint", None)
    if otel_endpoint:
        run_kwargs["otel_endpoint"] = otel_endpoint
    return run_loaded_plan_fn(
        plan_path,
        agents_path,
        agents,
        plan,
        **run_kwargs,
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
    manifest_follow_up_behavior_override: Callable[..., str | None],
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
    source_follow_up_behavior_override = manifest_follow_up_behavior_override(manifest, location=str(manifest_path))
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
    hitl_kwargs = {}
    if any(hasattr(args, name) for name in ("hitl", "hitl_mode", "hitl_auto_approve")):
        hitl_kwargs = {
            "hitl": bool(getattr(args, "hitl", False)),
            "hitl_mode": getattr(args, "hitl_mode", None) if bool(getattr(args, "hitl", False)) else None,
            "hitl_auto_approve": bool(getattr(args, "hitl_auto_approve", False)),
        }
    otel_kwargs = {}
    if getattr(args, "otel_endpoint", None):
        otel_kwargs["otel_endpoint"] = getattr(args, "otel_endpoint")
    follow_up_kwargs = {}
    resolved_follow_up_override = getattr(args, "follow_up_mode", None) or source_follow_up_behavior_override
    if resolved_follow_up_override:
        follow_up_kwargs["follow_up_behavior_override"] = resolved_follow_up_override
    _reuse = bool(getattr(args, "reuse_unchanged", False))
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
        reuse_unchanged=_reuse,
        prior_completed_records={
            task_id: record
            for task_id, record in previous_records.items()
            if record.status == "completed"
        } if _reuse else None,
        **otel_kwargs,
        **hitl_kwargs,
        **follow_up_kwargs,
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
    manifest_follow_up_behavior_override: Callable[..., str | None],
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
    run_kwargs = {
        "claude_bin": args.claude_bin,
        "runtime_root": runtime_root,
        "max_parallel": args.max_parallel,
        "continue_on_error": args.continue_on_error,
        "dry_run": args.dry_run,
        "worker_validation_mode": (
            normalize_worker_validation_mode(getattr(args, "worker_validation_mode", "") or source_worker_validation_override, location=f"retry source '{manifest_path}' worker validation mode") if (getattr(args, "worker_validation_mode", "") or source_worker_validation_override) else None
        ),
        "effort_override": getattr(args, "effort", None),
        "initial_records": initial_records,
        "retry_of_run_id": str(manifest.get("runId", "")),
        "requested_task_ids": requested_task_ids,
        "retried_task_ids": retried_task_ids,
        "max_task_retries": getattr(args, "max_task_retries", None),
    }
    source_follow_up_behavior_override = manifest_follow_up_behavior_override(manifest, location=str(manifest_path))
    if source_follow_up_behavior_override:
        run_kwargs["follow_up_behavior_override"] = source_follow_up_behavior_override
    if getattr(args, "otel_endpoint", None):
        run_kwargs["otel_endpoint"] = getattr(args, "otel_endpoint")
    _reuse = bool(getattr(args, "reuse_unchanged", False))
    run_kwargs["reuse_unchanged"] = _reuse
    if _reuse:
        run_kwargs["prior_completed_records"] = {
            task_id: record
            for task_id, record in previous_records.items()
            if record.status == "completed"
        }
    payload = run_loaded_plan_fn(
        plan_path,
        agents_path,
        agents,
        retry_plan,
        **run_kwargs,
    )
    payload["sourceManifestPath"] = str(manifest_path)
    return payload
