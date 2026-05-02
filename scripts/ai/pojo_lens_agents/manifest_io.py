from __future__ import annotations

from pathlib import Path
from typing import Any


def manifest_payload(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, Any],
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: Any,
    records: dict[str, Any],
    *,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
    run_events: list[dict[str, Any]] | None = None,
    deps: dict[str, Any],
) -> dict[str, Any]:
    worker_validation_override = (
        deps["normalize_worker_validation_mode"](
            worker_validation_mode,
            location="manifest worker validation mode override",
        )
        if worker_validation_mode
        else None
    )
    normalized_effort_override = deps["normalize_effort_override"](
        effort_override,
        location="manifest effort override",
    )
    task_worker_validation_modes = deps["effective_plan_worker_validation_modes"](
        plan,
        agents,
        run_override=worker_validation_override,
    )
    task_output_profiles = deps["effective_plan_output_profiles"](plan, agents)
    task_output_profile_sources = deps["effective_plan_output_profile_sources"](plan, agents)
    task_worker_validation_mode_sources = deps["effective_plan_worker_validation_mode_sources"](
        plan,
        agents,
        run_override=worker_validation_override,
    )
    task_efforts = deps["effective_plan_efforts"](
        plan,
        agents,
        run_override=normalized_effort_override,
    )
    task_effort_sources = deps["effective_plan_effort_sources"](
        plan,
        agents,
        run_override=normalized_effort_override,
    )
    task_model_profiles = deps["effective_plan_model_profiles"](plan, agents)
    task_models = deps["effective_plan_models"](plan, agents)
    task_resolved_skills = {
        task.id: deps["effective_task_skills"](task, agents[task.agent])
        for task in plan.tasks
    }
    complex_model_tasks = deps["complex_model_task_ids"](task_model_profiles)
    topology = deps["analyze_plan_topology"](plan, agents)
    usage_totals = deps["aggregate_usage"](records)
    run_governance = deps["evaluate_run_governance"](records, plan.run_policy)
    parallel_conflicts = deps["detect_parallel_scope_conflicts"](plan, agents)
    payload = {
        "runId": run_id,
        "generatedAt": deps["iso_now"](),
        "dryRun": dry_run,
        "workerValidationMode": deps["summarized_worker_validation_mode"](
            list(task_worker_validation_modes.values())
        ),
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
        "taskResolvedSkills": task_resolved_skills,
        "complexModelTaskIds": complex_model_tasks,
        "complexModelTaskCount": len(complex_model_tasks),
        "topology": topology,
        "repoRoot": str(deps["root"]),
        "planPath": str(plan_path),
        "agentsPath": str(agents_path),
        "runtimeRoot": str(runtime_root),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "runPolicy": deps["serialize_run_policy"](plan.run_policy),
        "runGovernance": run_governance,
        "plan": {
            "name": plan.name,
            "goal": plan.goal,
            "taskIds": [task.id for task in plan.tasks],
            "parallelConflicts": parallel_conflicts,
        },
        "events": list(run_events or []),
        "usageTotals": usage_totals,
        "tasks": {task_id: deps["asdict"](record) for task_id, record in sorted(records.items())},
    }
    if retry_of_run_id:
        payload["retryOfRunId"] = retry_of_run_id
        payload["requestedTaskIds"] = list(requested_task_ids or [])
        payload["retriedTaskIds"] = list(retried_task_ids or [])
        payload["seededTaskIds"] = list(seeded_task_ids or [])
    return payload


def write_manifest(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, Any],
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: Any,
    records: dict[str, Any],
    *,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
    run_events: list[dict[str, Any]] | None = None,
    deps: dict[str, Any],
) -> None:
    deps["write_json"](
        run_dir / "manifest.json",
        manifest_payload(
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
            worker_validation_mode=worker_validation_mode,
            effort_override=effort_override,
            retry_of_run_id=retry_of_run_id,
            requested_task_ids=requested_task_ids,
            retried_task_ids=retried_task_ids,
            seeded_task_ids=seeded_task_ids,
            run_events=run_events,
            deps=deps,
        ),
    )


def write_selected_plan_snapshot(run_dir: Path, plan: Any, *, serialize_run_policy, write_json) -> None:
    write_json(
        run_dir / "selected-plan.json",
        {
            "version": plan.version,
            "name": plan.name,
            "goal": plan.goal,
            **({"runPolicy": serialize_run_policy(plan.run_policy)} if serialize_run_policy(plan.run_policy) else {}),
            "sharedContext": {
                "summary": plan.shared_context.summary,
                "constraints": plan.shared_context.constraints,
                "readPaths": plan.shared_context.read_paths,
                "validation": plan.shared_context.validation,
            },
            "tasks": [
                {
                    "id": task.id,
                    "title": task.title,
                    "agent": task.agent,
                    "prompt": task.prompt,
                    "skills": task.skills,
                    "dependsOn": task.depends_on,
                    "readPaths": task.read_paths,
                    "writePaths": task.write_paths,
                    "constraints": task.constraints,
                    "validation": task.validation,
                    "workspaceMode": task.workspace_mode,
                    "model": task.model,
                    "modelProfile": task.model_profile,
                    "contextMode": task.context_mode,
                    "outputProfile": task.output_profile,
                    "dependencyMaterialization": task.dependency_materialization,
                    "workerValidationMode": task.worker_validation_mode,
                    "effort": task.effort,
                    "permissionMode": task.permission_mode,
                    "timeoutSec": task.timeout_sec,
                    "maxBudgetUsd": task.max_budget_usd,
                    "maxPromptChars": task.max_prompt_chars,
                    "maxPromptEstimatedTokens": task.max_prompt_estimated_tokens,
                    "allowedTools": task.allowed_tools,
                    "disallowedTools": task.disallowed_tools,
                }
                for task in plan.tasks
            ],
        },
    )
