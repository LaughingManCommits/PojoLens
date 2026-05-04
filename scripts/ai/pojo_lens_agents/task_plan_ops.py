from __future__ import annotations

from pathlib import Path
from typing import Any

from pydantic import ValidationError

from pojo_lens_agents.orchestrator_models import (
    AgentDefinitionModel,
    ExtraToolDefModel,
    RunPolicyModel,
    TaskPlanModel,
    dump_contract,
    validation_error_summary,
)


def _contract_error(location: str, exc: ValidationError, *, error_factory) -> Exception:
    return error_factory(f"{location}: typed contract validation failed: {validation_error_summary(exc)}")


def _load_extra_tools(
    raw_list: Any,
    *,
    location: str,
    error_factory: Any,
    extra_tool_def_factory: Any,
) -> list:
    if raw_list is None:
        return []
    if not isinstance(raw_list, list):
        raise error_factory(f"{location}: extraTools must be an array")
    tools = []
    for i, raw in enumerate(raw_list):
        tool_location = f"{location}:extraTools[{i}]"
        try:
            model = ExtraToolDefModel.model_validate(raw)
        except ValidationError as exc:
            raise error_factory(f"{tool_location}: {validation_error_summary(exc)}") from exc
        tools.append(extra_tool_def_factory(
            name=model.name,
            description=model.description,
            kind=model.kind,
            template=model.template,
            timeout_sec=model.timeout_sec,
        ))
    return tools


def load_run_policy(payload: Any, *, location: str, deps: dict[str, Any]) -> Any:
    if payload is None:
        model = RunPolicyModel()
    elif not isinstance(payload, dict):
        raise deps["error_factory"](f"{location}: runPolicy must be an object")
    else:
        try:
            model = RunPolicyModel.model_validate(payload)
        except ValidationError as exc:
            raise _contract_error(f"{location}:runPolicy", exc, error_factory=deps["error_factory"]) from exc
    return deps["run_policy_factory"](
        run_budget_usd=model.run_budget_usd,
        budget_behavior=model.budget_behavior,
        max_task_stdout_bytes=model.max_task_stdout_bytes,
        max_task_stderr_bytes=model.max_task_stderr_bytes,
        max_task_result_bytes=model.max_task_result_bytes,
        artifact_behavior=model.artifact_behavior,
        follow_up_behavior=model.follow_up_behavior,
        hitl=model.hitl,
        hitl_mode=model.hitl_mode,
    )


def load_task_definition(
    payload: Any,
    agents: dict[str, Any],
    *,
    location: str,
    deps: dict[str, Any],
) -> Any:
    if not isinstance(payload, dict):
        raise deps["error_factory"](f"{location}: expected task object")
    task_id = deps["require_string"](payload, "id", location=location)
    if not deps["task_id_re"].match(task_id):
        raise deps["error_factory"](f"{location}: task id '{task_id}' must match {deps['task_id_re'].pattern}")
    agent_name = deps["require_string"](payload, "agent", location=location)
    if agent_name not in agents:
        raise deps["error_factory"](f"{location}: unknown agent '{agent_name}'")
    if "files" in payload:
        raise deps["error_factory"](f"{location}: legacy 'files' was replaced by 'readPaths' and 'writePaths'")
    worker_validation_mode = deps["require_optional_string"](
        payload,
        "workerValidationMode",
        location=location,
    )
    dependency_materialization = deps["require_optional_string"](
        payload,
        "dependencyMaterialization",
        location=location,
    )
    explicit_skills = deps["validate_known_skills"](
        deps["require_string_list"](payload, "skills", location=location),
        deps["skill_registry"],
        registry_path=deps["skill_registry_path"],
        location=f"{location}:skills",
        deps={
            "dedupe_strings": deps["dedupe_strings"],
            "error_factory": deps["error_factory"],
        },
    )
    return deps["task_definition_factory"](
        id=task_id,
        title=deps["require_string"](payload, "title", location=location),
        agent=agent_name,
        prompt=deps["require_string"](payload, "prompt", location=location),
        skills=explicit_skills,
        depends_on=deps["require_string_list"](payload, "dependsOn", location=location),
        read_paths=deps["require_scope_path_list"](
            payload,
            "readPaths",
            location=location,
            allow_repo_root=True,
        ),
        write_paths=deps["require_scope_path_list"](
            payload,
            "writePaths",
            location=location,
            allow_repo_root=True,
        ),
        constraints=deps["require_string_list"](payload, "constraints", location=location),
        validation=deps["require_string_list"](payload, "validation", location=location),
        workspace_mode=deps["ensure_workspace_mode"](
            deps["require_optional_string"](payload, "workspaceMode", location=location),
            location=location,
        ),
        model=deps["require_optional_string"](payload, "model", location=location),
        model_profile=deps["ensure_model_profile"](
            deps["require_optional_string"](payload, "modelProfile", location=location),
            location=location,
        ),
        effort=deps["require_optional_string"](payload, "effort", location=location),
        permission_mode=deps["require_optional_string"](payload, "permissionMode", location=location),
        context_mode=deps["ensure_context_mode"](
            deps["require_optional_string"](payload, "contextMode", location=location),
            location=location,
        ),
        output_profile=deps["normalize_output_profile"](
            deps["require_optional_string"](payload, "outputProfile", location=location),
            location=f"{location}:outputProfile",
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
        timeout_sec=deps["require_optional_int"](payload, "timeoutSec", location=location),
        max_budget_usd=deps["require_optional_float"](payload, "maxBudgetUsd", location=location),
        max_prompt_chars=deps["require_optional_int"](payload, "maxPromptChars", location=location),
        max_prompt_estimated_tokens=deps["require_optional_int"](
            payload, "maxPromptEstimatedTokens", location=location
        ),
        allowed_tools=deps["require_string_list"](payload, "allowedTools", location=location),
        disallowed_tools=deps["require_string_list"](payload, "disallowedTools", location=location),
        max_retries=deps["require_optional_int"](payload, "maxRetries", location=location),
        injected_from=deps["require_optional_string"](payload, "injectedFrom", location=location),
        condition_field=deps["require_optional_string"](payload, "conditionField", location=location),
        condition_value=deps["require_optional_string"](payload, "conditionValue", location=location),
        extra_tools=_load_extra_tools(
            payload.get("extraTools"),
            location=location,
            error_factory=deps["error_factory"],
            extra_tool_def_factory=deps["extra_tool_def_factory"],
        ),
        shared_context_tags=deps["require_string_list"](payload, "sharedContextTags", location=location),
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
        inline_prompt = deps["require_optional_string"](definition, "prompt", location=location)
        prompt_file = deps["require_optional_string"](definition, "promptFile", location=location)
        if inline_prompt and prompt_file:
            raise deps["error_factory"](f"{location}: define only one of 'prompt' or 'promptFile'")
        if prompt_file:
            prompt_path = (path.parent / Path(prompt_file)).resolve()
            if Path(prompt_file).is_absolute():
                raise deps["error_factory"](f"{location}: 'promptFile' must be a relative path")
            if not prompt_path.exists():
                raise deps["error_factory"](f"{location}: prompt file '{prompt_file}' does not exist")
            if not prompt_path.is_file():
                raise deps["error_factory"](f"{location}: prompt file '{prompt_file}' must be a file")
            try:
                prompt_value = deps["read_text"](prompt_path).strip()
            except OSError as exc:
                raise deps["error_factory"](f"{location}: cannot read prompt file '{prompt_file}': {exc}") from exc
            if not prompt_value:
                raise deps["error_factory"](f"{location}: prompt file '{prompt_file}' is empty")
            prompt_bytes = len(prompt_value.encode("utf-8"))
            if prompt_bytes > deps["agent_prompt_fail_bytes"]:
                raise deps["error_factory"](
                    f"{location}: prompt file '{prompt_file}' is {prompt_bytes} bytes and exceeds "
                    f"the hard cap of {deps['agent_prompt_fail_bytes']} bytes"
                )
        elif inline_prompt:
            prompt_value = inline_prompt
        else:
            raise deps["error_factory"](f"{location}: expected one of 'prompt' or 'promptFile'")
        explicit_skills = deps["validate_known_skills"](
            deps["require_string_list"](definition, "skills", location=location),
            deps["skill_registry"],
            registry_path=deps["skill_registry_path"],
            location=f"{location}:skills",
            deps={
                "dedupe_strings": deps["dedupe_strings"],
                "error_factory": deps["error_factory"],
            },
        )
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
            prompt=prompt_value,
            prompt_path=str(prompt_path) if prompt_file else None,
            skills=explicit_skills,
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
            output_profile=deps["normalize_output_profile"](
                deps["require_optional_string"](definition, "outputProfile", location=location),
                location=f"{location}:outputProfile",
            )
            or deps["default_output_profile"],
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
            max_retries=deps["require_optional_int"](definition, "maxRetries", location=location),
            extra_tools=_load_extra_tools(
                definition.get("extraTools"),
                location=location,
                error_factory=deps["error_factory"],
                extra_tool_def_factory=deps["extra_tool_def_factory"],
            ),
        )
        try:
            AgentDefinitionModel.model_validate(dump_contract(agent))
        except ValidationError as exc:
            raise _contract_error(location, exc, error_factory=deps["error_factory"]) from exc
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
        task = load_task_definition(task_payload, agents, location=location, deps=deps)
        task_id = task.id
        if task_id in seen_ids:
            raise deps["error_factory"](f"{location}: duplicate task id '{task_id}'")
        seen_ids.add(task_id)
        tasks.append(task)

    task_ids = {task.id for task in tasks}
    for task in tasks:
        missing_dependencies = [dependency for dependency in task.depends_on if dependency not in task_ids]
        if missing_dependencies:
            raise deps["error_factory"](f"{path}:{task.id}: unknown dependencies {missing_dependencies}")
        if task.id in task.depends_on:
            raise deps["error_factory"](f"{path}:{task.id}: task cannot depend on itself")
    deps["topological_batches"](tasks)

    raw_codebase_path = payload.get("codebasePath")
    raw_workspace_strategy = str(payload.get("workspaceStrategy") or "repo").strip() or "repo"
    codebase_path: str | None = str(raw_codebase_path).strip() or None if raw_codebase_path is not None else None

    plan = deps["task_plan_factory"](
        version=1,
        name=deps["require_string"](payload, "name", location=str(path)),
        goal=deps["require_string"](payload, "goal", location=str(path)),
        shared_context=shared_context,
        tasks=tasks,
        run_policy=run_policy,
        codebase_path=codebase_path,
        workspace_strategy=raw_workspace_strategy,
    )
    try:
        TaskPlanModel.model_validate(dump_contract(plan))
    except ValidationError as exc:
        raise _contract_error(str(path), exc, error_factory=deps["error_factory"]) from exc
    return plan


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
