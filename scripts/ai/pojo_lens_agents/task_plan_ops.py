from __future__ import annotations

from pathlib import Path
from typing import Any


def load_run_policy(payload: Any, *, location: str, deps: dict[str, Any]) -> Any:
    if payload is None:
        return deps["run_policy_factory"]()
    if not isinstance(payload, dict):
        raise deps["error_factory"](f"{location}: runPolicy must be an object")
    return deps["run_policy_factory"](
        run_budget_usd=deps["require_optional_float"](payload, "runBudgetUsd", location=location),
        budget_behavior=deps["normalize_run_policy_behavior"](
            deps["require_optional_string"](payload, "budgetBehavior", location=location),
            location=location,
            key="budgetBehavior",
            default=deps["default_run_budget_behavior"],
        ),
        max_task_stdout_bytes=deps["require_optional_int"](payload, "maxTaskStdoutBytes", location=location),
        max_task_stderr_bytes=deps["require_optional_int"](payload, "maxTaskStderrBytes", location=location),
        max_task_result_bytes=deps["require_optional_int"](payload, "maxTaskResultBytes", location=location),
        artifact_behavior=deps["normalize_run_policy_behavior"](
            deps["require_optional_string"](payload, "artifactBehavior", location=location),
            location=location,
            key="artifactBehavior",
            default=deps["default_artifact_behavior"],
        ),
    )


def load_agents(path: Path, *, deps: dict[str, Any]) -> dict[str, Any]:
    payload = deps["read_json"](path)
    if not isinstance(payload, dict):
        raise deps["error_factory"](f"{path}: expected JSON object")
    if payload.get("version") != 1:
        raise deps["error_factory"](f"{path}: expected version=1")
    raw_agents = payload.get("agents")
    if not isinstance(raw_agents, dict) or not raw_agents:
        raise deps["error_factory"](f"{path}: expected non-empty 'agents' object")

    agents: dict[str, Any] = {}
    for name, definition in raw_agents.items():
        location = f"{path}:{name}"
        if not isinstance(name, str) or not name.strip():
            raise deps["error_factory"](f"{location}: invalid agent name")
        if not isinstance(definition, dict):
            raise deps["error_factory"](f"{location}: expected object definition")
        workspace_mode = deps["ensure_workspace_mode"](
            deps["require_optional_string"](definition, "workspaceMode", location=location) or "copy",
            location=location,
        )
        worker_validation_mode = deps["require_optional_string"](
            definition,
            "workerValidationMode",
            location=location,
        )
        agent = deps["agent_definition_factory"](
            name=name.strip(),
            description=deps["require_string"](definition, "description", location=location),
            prompt=deps["require_string"](definition, "prompt", location=location),
            skills=deps["require_string_list"](definition, "skills", location=location),
            model=deps["require_optional_string"](definition, "model", location=location),
            model_profile=deps["ensure_model_profile"](
                deps["require_optional_string"](definition, "modelProfile", location=location),
                location=location,
            ),
            effort=deps["require_optional_string"](definition, "effort", location=location),
            permission_mode=deps["require_optional_string"](definition, "permissionMode", location=location),
            workspace_mode=workspace_mode or "copy",
            context_mode=deps["ensure_context_mode"](
                deps["require_optional_string"](definition, "contextMode", location=location) or deps["default_context_mode"],
                location=location,
            )
            or deps["default_context_mode"],
            worker_validation_mode=deps["normalize_worker_validation_mode"](
                worker_validation_mode,
                location=f"{location}:workerValidationMode",
            )
            if worker_validation_mode is not None
            else None,
            timeout_sec=deps["require_optional_int"](definition, "timeoutSec", location=location) or deps["default_task_timeout_sec"],
            max_budget_usd=deps["require_optional_float"](definition, "maxBudgetUsd", location=location),
            max_prompt_chars=deps["require_optional_int"](definition, "maxPromptChars", location=location),
            max_prompt_estimated_tokens=deps["require_optional_int"](
                definition, "maxPromptEstimatedTokens", location=location
            ),
            allowed_tools=deps["require_string_list"](definition, "allowedTools", location=location),
            disallowed_tools=deps["require_string_list"](definition, "disallowedTools", location=location),
        )
        agents[agent.name] = agent

    if deps["planner_task_id"] not in agents:
        raise deps["error_factory"](f"{path}: required agent '{deps['planner_task_id']}' is missing")
    return agents


def load_task_plan(path: Path, agents: dict[str, Any], *, deps: dict[str, Any]) -> Any:
    payload = deps["read_json"](path)
    if not isinstance(payload, dict):
        raise deps["error_factory"](f"{path}: expected JSON object")
    if payload.get("version") != 1:
        raise deps["error_factory"](f"{path}: expected version=1")
    shared_context_payload = payload.get("sharedContext", {})
    if not isinstance(shared_context_payload, dict):
        raise deps["error_factory"](f"{path}: sharedContext must be an object")
    if "files" in shared_context_payload:
        raise deps["error_factory"](f"{path}:sharedContext: legacy 'files' was replaced by 'readPaths'")
    shared_context = deps["shared_context_factory"](
        summary=deps["require_string"](shared_context_payload, "summary", location=f"{path}:sharedContext"),
        constraints=deps["require_string_list"](shared_context_payload, "constraints", location=f"{path}:sharedContext"),
        read_paths=deps["require_scope_path_list"](
            shared_context_payload,
            "readPaths",
            location=f"{path}:sharedContext",
            allow_repo_root=True,
        ),
        validation=deps["require_string_list"](shared_context_payload, "validation", location=f"{path}:sharedContext"),
    )
    run_policy = deps["load_run_policy"](payload.get("runPolicy"), location=str(path))
    raw_tasks = payload.get("tasks")
    if not isinstance(raw_tasks, list) or not raw_tasks:
        raise deps["error_factory"](f"{path}: expected non-empty tasks list")

    tasks: list[Any] = []
    seen_ids: set[str] = set()
    for index, task_payload in enumerate(raw_tasks):
        location = f"{path}:tasks[{index}]"
        if not isinstance(task_payload, dict):
            raise deps["error_factory"](f"{location}: expected task object")
        task_id = deps["require_string"](task_payload, "id", location=location)
        if not deps["task_id_re"].match(task_id):
            raise deps["error_factory"](f"{location}: task id '{task_id}' must match {deps['task_id_re'].pattern}")
        if task_id in seen_ids:
            raise deps["error_factory"](f"{location}: duplicate task id '{task_id}'")
        seen_ids.add(task_id)
        agent_name = deps["require_string"](task_payload, "agent", location=location)
        if agent_name not in agents:
            raise deps["error_factory"](f"{location}: unknown agent '{agent_name}'")
        if "files" in task_payload:
            raise deps["error_factory"](f"{location}: legacy 'files' was replaced by 'readPaths' and 'writePaths'")
        worker_validation_mode = deps["require_optional_string"](
            task_payload,
            "workerValidationMode",
            location=location,
        )
        dependency_materialization = deps["require_optional_string"](
            task_payload,
            "dependencyMaterialization",
            location=location,
        )
        task = deps["task_definition_factory"](
            id=task_id,
            title=deps["require_string"](task_payload, "title", location=location),
            agent=agent_name,
            prompt=deps["require_string"](task_payload, "prompt", location=location),
            depends_on=deps["require_string_list"](task_payload, "dependsOn", location=location),
            read_paths=deps["require_scope_path_list"](
                task_payload,
                "readPaths",
                location=location,
                allow_repo_root=True,
            ),
            write_paths=deps["require_scope_path_list"](
                task_payload,
                "writePaths",
                location=location,
                allow_repo_root=True,
            ),
            constraints=deps["require_string_list"](task_payload, "constraints", location=location),
            validation=deps["require_string_list"](task_payload, "validation", location=location),
            workspace_mode=deps["ensure_workspace_mode"](
                deps["require_optional_string"](task_payload, "workspaceMode", location=location),
                location=location,
            ),
            model=deps["require_optional_string"](task_payload, "model", location=location),
            model_profile=deps["ensure_model_profile"](
                deps["require_optional_string"](task_payload, "modelProfile", location=location),
                location=location,
            ),
            effort=deps["require_optional_string"](task_payload, "effort", location=location),
            permission_mode=deps["require_optional_string"](task_payload, "permissionMode", location=location),
            context_mode=deps["ensure_context_mode"](
                deps["require_optional_string"](task_payload, "contextMode", location=location),
                location=location,
            ),
            dependency_materialization=deps["normalize_dependency_materialization_mode"](
                dependency_materialization,
                location=f"{location}:dependencyMaterialization",
            ),
            worker_validation_mode=deps["normalize_worker_validation_mode"](
                worker_validation_mode,
                location=f"{location}:workerValidationMode",
            )
            if worker_validation_mode is not None
            else None,
            timeout_sec=deps["require_optional_int"](task_payload, "timeoutSec", location=location),
            max_budget_usd=deps["require_optional_float"](task_payload, "maxBudgetUsd", location=location),
            max_prompt_chars=deps["require_optional_int"](task_payload, "maxPromptChars", location=location),
            max_prompt_estimated_tokens=deps["require_optional_int"](
                task_payload, "maxPromptEstimatedTokens", location=location
            ),
            allowed_tools=deps["require_string_list"](task_payload, "allowedTools", location=location),
            disallowed_tools=deps["require_string_list"](task_payload, "disallowedTools", location=location),
        )
        tasks.append(task)

    task_ids = {task.id for task in tasks}
    for task in tasks:
        missing_dependencies = [dependency for dependency in task.depends_on if dependency not in task_ids]
        if missing_dependencies:
            raise deps["error_factory"](f"{path}:{task.id}: unknown dependencies {missing_dependencies}")
        if task.id in task.depends_on:
            raise deps["error_factory"](f"{path}:{task.id}: task cannot depend on itself")
    deps["topological_batches"](tasks)

    return deps["task_plan_factory"](
        version=1,
        name=deps["require_string"](payload, "name", location=str(path)),
        goal=deps["require_string"](payload, "goal", location=str(path)),
        shared_context=shared_context,
        tasks=tasks,
        run_policy=run_policy,
    )


def effective_task_read_paths(plan: Any, task: Any, *, dedupe_strings) -> list[str]:
    return dedupe_strings(plan.shared_context.read_paths + task.read_paths)


def prompt_task_read_paths(plan: Any, task: Any, *, context_mode: str, dedupe_strings) -> list[str]:
    if context_mode == "minimal":
        return dedupe_strings(task.read_paths)
    return effective_task_read_paths(plan, task, dedupe_strings=dedupe_strings)


def effective_task_write_scope(task: Any, *, dedupe_strings) -> list[str]:
    return dedupe_strings(task.write_paths)


def validate_task_scope_contract(
    plan: Any,
    task: Any,
    agent: Any,
    *,
    tasks_by_id: dict[str, Any],
    agents: dict[str, Any],
    deps: dict[str, Any],
) -> list[str]:
    issues: list[str] = []
    if deps["task_may_write"](plan, task, agent) and not deps["effective_task_write_scope"](task):
        issues.append("write-capable tasks must declare non-empty writePaths")
    workspace_mode = deps["effective_workspace_mode"](task, agent)
    dependency_materialization = deps["effective_dependency_materialization_mode"](task)
    if dependency_materialization == "apply-reviewed":
        if not task.depends_on:
            issues.append("dependencyMaterialization='apply-reviewed' requires non-empty dependsOn")
        if workspace_mode == "repo":
            issues.append("dependencyMaterialization='apply-reviewed' is not allowed for workspaceMode='repo'")
        for dependency_id in task.depends_on:
            dependency = tasks_by_id[dependency_id]
            dependency_workspace_mode = deps["effective_workspace_mode"](
                dependency,
                agents[dependency.agent],
            )
            if dependency_workspace_mode not in {"copy", "worktree"}:
                issues.append(
                    "dependencyMaterialization='apply-reviewed' requires reviewable dependency workspaces; "
                    f"dependency '{dependency_id}' resolves to workspaceMode='{dependency_workspace_mode}'"
                )
    if workspace_mode == "copy":
        hydration = deps["analyze_copy_hydration_inputs"](plan, task)
        if hydration["missingReadPaths"]:
            issues.append(
                "copy readPaths are missing from the repo: "
                f"{deps['summarize_paths'](hydration['missingReadPaths'])}"
            )
        if hydration["directoryReadPaths"]:
            issues.append(
                "copy readPaths must be concrete files, not directories: "
                f"{deps['summarize_paths'](hydration['directoryReadPaths'])}"
            )
        if hydration["oversizedPaths"]:
            issues.append(
                "copy workspace inputs exceed the hydration size limit: "
                f"{deps['summarize_paths'](hydration['oversizedPaths'])}"
            )
    return issues


def validate_scope_contract(plan: Any, agents: dict[str, Any], *, deps: dict[str, Any]) -> None:
    issues: list[str] = []
    tasks_by_id = {task.id: task for task in plan.tasks}
    for task in plan.tasks:
        task_issues = validate_task_scope_contract(
            plan,
            task,
            agents[task.agent],
            tasks_by_id=tasks_by_id,
            agents=agents,
            deps=deps,
        )
        for issue in task_issues:
            issues.append(f"{task.id}: {issue}")
    if issues:
        raise deps["error_factory"](deps["format_issue_block"]("Task scope validation failed", issues))
