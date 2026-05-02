#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

from pojo_lens_agents import path_safety as path_safety_layer
from pojo_lens_agents import prompt_contracts as prompt_contracts_layer
from pojo_lens_agents import runtime as runtime_layer
from pojo_lens_agents import skill_router as skill_router_layer
from pojo_lens_agents import task_execution as task_execution_layer
from pojo_lens_agents import task_plan_ops as task_plan_ops_layer
from pojo_lens_agents import workspace_review as workspace_review_layer
from pojo_lens_agents.orchestrator_contracts import (
    AGENT_PROMPT_FAIL_BYTES,
    AGENT_PROMPT_WARN_BYTES,
    ANALYST_AGENT_NAME,
    CONTEXT_MODES,
    DEFAULT_OUTPUT_PROFILE,
    DEFAULT_AGENTS_PATH,
    DEPENDENCY_MATERIALIZATION_MODES,
    DEFAULT_ARTIFACT_BEHAVIOR,
    DEFAULT_CONTEXT_MODE,
    DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    DEFAULT_RUN_BUDGET_BEHAVIOR,
    DEFAULT_SKILL_REGISTRY_PATH,
    DEFAULT_TASK_TIMEOUT_SEC,
    DEFAULT_WORKER_VALIDATION_MODE,
    IMPLEMENTER_AGENT_NAME,
    LEGACY_WORKER_VALIDATION_MODES,
    MAX_HYDRATED_FILE_BYTES,
    MODEL_PROFILE_TO_MODEL,
    OUTPUT_PROFILES,
    OUTPUT_PROFILE_SOURCES,
    OrchestratorError,
    PLANNER_TASK_ID,
    RESOLVED_SKILLS_FAIL_COUNT,
    RESOLVED_SKILLS_WARN_COUNT,
    REVIEWER_AGENT_NAME,
    ROOT,
    RUN_POLICY_BEHAVIORS,
    RunPolicy,
    SKILL_PROMPT_WARN_BYTES,
    SharedContext,
    SkillDefinition,
    TASK_ID_RE,
    TaskDefinition,
    TaskPlan,
    WORKER_VALIDATION_MODES,
    WORKER_VALIDATION_MODE_SOURCES,
    WORKSPACE_MODES,
    WRITE_CAPABLE_TOOLS,
    AgentDefinition,
    WorkerValidationModeResolution,
)
from pojo_lens_agents.orchestrator_utils import dedupe_strings, format_issue_block, read_json, summarize_paths
from pojo_lens_agents.orchestrator_utils import read_text


def current_root() -> Path:
    app = sys.modules.get("pojo_lens_agents.orchestrator_app")
    return Path(getattr(app, "ROOT", ROOT))
def normalize_relative_path(path_value: str, *, location: str) -> str:
    return path_safety_layer.normalize_relative_path(
        path_value,
        location=location,
        error_factory=OrchestratorError,
    )


def effective_workspace_mode(task: TaskDefinition, agent: AgentDefinition) -> str:
    return task_execution_layer.effective_workspace_mode(task, agent)


def effective_dependency_materialization_mode(task: TaskDefinition) -> str:
    return prompt_contracts_layer.effective_dependency_materialization_mode(
        task,
        default_mode=DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    )


def resolve_relative_path(root: Path, relative_path: str, *, location: str) -> tuple[str, Path]:
    return path_safety_layer.resolve_relative_path(
        root,
        relative_path,
        location=location,
        error_factory=OrchestratorError,
    )


def require_string(payload: dict[str, Any], key: str, *, location: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise OrchestratorError(f"{location}: expected non-empty string for '{key}'")
    return value.strip()


def require_optional_string(payload: dict[str, Any], key: str, *, location: str) -> str | None:
    value = payload.get(key)
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise OrchestratorError(f"{location}: expected string for '{key}'")
    return value.strip()


def require_optional_int(payload: dict[str, Any], key: str, *, location: str) -> int | None:
    value = payload.get(key)
    if value is None:
        return None
    if not isinstance(value, int) or value <= 0:
        raise OrchestratorError(f"{location}: expected positive integer for '{key}'")
    return value


def require_optional_float(payload: dict[str, Any], key: str, *, location: str) -> float | None:
    value = payload.get(key)
    if value is None:
        return None
    if not isinstance(value, (int, float)) or value <= 0:
        raise OrchestratorError(f"{location}: expected positive number for '{key}'")
    return float(value)


def require_string_list(payload: dict[str, Any], key: str, *, location: str) -> list[str]:
    value = payload.get(key, [])
    if value is None:
        return []
    if not isinstance(value, list) or not all(isinstance(item, str) and item.strip() for item in value):
        raise OrchestratorError(f"{location}: expected list of strings for '{key}'")
    return [item.strip() for item in value]


def require_scope_path_list(
    payload: dict[str, Any],
    key: str,
    *,
    location: str,
    allow_repo_root: bool = False,
) -> list[str]:
    raw_values = require_string_list(payload, key, location=location)
    normalized: list[str] = []
    for raw_value in raw_values:
        if allow_repo_root and raw_value.strip() == ".":
            normalized.append(".")
            continue
        normalized.append(normalize_relative_path(raw_value, location=f"{location}:{key}"))
    return dedupe_strings(normalized)


def ensure_workspace_mode(value: str | None, *, location: str) -> str | None:
    if value is None:
        return None
    if value not in WORKSPACE_MODES:
        raise OrchestratorError(
            f"{location}: invalid workspaceMode '{value}', expected one of {sorted(WORKSPACE_MODES)}"
        )
    return value


def ensure_context_mode(value: str | None, *, location: str) -> str | None:
    if value is None:
        return None
    if value not in CONTEXT_MODES:
        raise OrchestratorError(
            f"{location}: invalid contextMode '{value}', expected one of {sorted(CONTEXT_MODES)}"
        )
    return value


def normalize_output_profile(value: str | None, *, location: str) -> str | None:
    if value is None:
        return None
    normalized = value.strip().lower()
    if normalized not in OUTPUT_PROFILES:
        raise OrchestratorError(
            f"{location}: invalid outputProfile '{value}', expected one of {sorted(OUTPUT_PROFILES)}"
        )
    return normalized


def normalize_run_policy_behavior(value: str | None, *, location: str, key: str, default: str) -> str:
    normalized = (value or "").strip()
    if not normalized:
        return default
    if normalized not in RUN_POLICY_BEHAVIORS:
        raise OrchestratorError(
            f"{location}:{key}: expected one of {sorted(RUN_POLICY_BEHAVIORS)}"
        )
    return normalized


def normalize_dependency_materialization_mode(
    value: str | None,
    *,
    location: str,
) -> str | None:
    if value is None:
        return None
    if value not in DEPENDENCY_MATERIALIZATION_MODES:
        raise OrchestratorError(
            f"{location}: invalid dependencyMaterialization '{value}', expected one of "
            f"{sorted(DEPENDENCY_MATERIALIZATION_MODES)}"
        )
    return value


def ensure_model_profile(value: str | None, *, location: str) -> str | None:
    if value is None:
        return None
    if value not in MODEL_PROFILE_TO_MODEL:
        raise OrchestratorError(
            f"{location}: invalid modelProfile '{value}', expected one of {sorted(MODEL_PROFILE_TO_MODEL)}"
        )
    return value


def load_run_policy(payload: Any, *, location: str) -> RunPolicy:
    return task_plan_ops_layer.load_run_policy(
        payload,
        location=location,
        deps={
            "run_policy_factory": RunPolicy,
            "error_factory": OrchestratorError,
            "require_optional_float": require_optional_float,
            "require_optional_string": require_optional_string,
            "require_optional_int": require_optional_int,
            "normalize_run_policy_behavior": normalize_run_policy_behavior,
            "default_run_budget_behavior": DEFAULT_RUN_BUDGET_BEHAVIOR,
            "default_artifact_behavior": DEFAULT_ARTIFACT_BEHAVIOR,
        },
    )


def serialize_run_policy(run_policy: RunPolicy) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    if run_policy.run_budget_usd is not None:
        payload["runBudgetUsd"] = run_policy.run_budget_usd
        payload["budgetBehavior"] = run_policy.budget_behavior
    if run_policy.max_task_stdout_bytes is not None:
        payload["maxTaskStdoutBytes"] = run_policy.max_task_stdout_bytes
    if run_policy.max_task_stderr_bytes is not None:
        payload["maxTaskStderrBytes"] = run_policy.max_task_stderr_bytes
    if run_policy.max_task_result_bytes is not None:
        payload["maxTaskResultBytes"] = run_policy.max_task_result_bytes
    if any(
        limit is not None
        for limit in (
            run_policy.max_task_stdout_bytes,
            run_policy.max_task_stderr_bytes,
            run_policy.max_task_result_bytes,
        )
    ):
        payload["artifactBehavior"] = run_policy.artifact_behavior
    return payload


def load_agents(path: Path) -> dict[str, AgentDefinition]:
    registry_path = skill_router_layer.discover_skill_registry(path)
    skill_registry = skill_router_layer.load_skill_registry(
        path,
        deps={
            "discover_skill_registry": skill_router_layer.discover_skill_registry,
            "read_json": read_json,
            "read_text": read_text,
            "error_factory": OrchestratorError,
            "require_string": require_string,
            "skill_definition_factory": SkillDefinition,
        },
    )
    return task_plan_ops_layer.load_agents(
        path,
        deps={
            "read_json": read_json,
            "read_text": read_text,
            "error_factory": OrchestratorError,
            "agent_definition_factory": AgentDefinition,
            "require_optional_string": require_optional_string,
            "require_optional_int": require_optional_int,
            "require_optional_float": require_optional_float,
            "require_string": require_string,
            "require_string_list": require_string_list,
            "ensure_workspace_mode": ensure_workspace_mode,
            "ensure_context_mode": ensure_context_mode,
            "ensure_model_profile": ensure_model_profile,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "normalize_output_profile": normalize_output_profile,
            "default_context_mode": DEFAULT_CONTEXT_MODE,
            "default_output_profile": DEFAULT_OUTPUT_PROFILE,
            "default_task_timeout_sec": DEFAULT_TASK_TIMEOUT_SEC,
            "agent_prompt_fail_bytes": AGENT_PROMPT_FAIL_BYTES,
            "planner_task_id": PLANNER_TASK_ID,
            "skill_registry_path": registry_path,
            "skill_registry": skill_registry,
            "validate_known_skills": skill_router_layer.validate_known_skills,
            "dedupe_strings": dedupe_strings,
        },
    )


def load_task_plan(path: Path, agents: dict[str, AgentDefinition]) -> TaskPlan:
    registry_path = skill_router_layer.discover_skill_registry(path)
    skill_registry = skill_router_layer.load_skill_registry(
        path,
        deps={
            "discover_skill_registry": skill_router_layer.discover_skill_registry,
            "read_json": read_json,
            "read_text": read_text,
            "error_factory": OrchestratorError,
            "require_string": require_string,
            "skill_definition_factory": SkillDefinition,
        },
    )
    return task_plan_ops_layer.load_task_plan(
        path,
        agents,
        deps={
            "read_json": read_json,
            "error_factory": OrchestratorError,
            "shared_context_factory": SharedContext,
            "task_definition_factory": TaskDefinition,
            "task_plan_factory": TaskPlan,
            "require_string": require_string,
            "require_string_list": require_string_list,
            "require_optional_string": require_optional_string,
            "require_optional_int": require_optional_int,
            "require_optional_float": require_optional_float,
            "require_scope_path_list": require_scope_path_list,
            "ensure_workspace_mode": ensure_workspace_mode,
            "ensure_context_mode": ensure_context_mode,
            "ensure_model_profile": ensure_model_profile,
            "normalize_dependency_materialization_mode": normalize_dependency_materialization_mode,
            "normalize_output_profile": normalize_output_profile,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "load_run_policy": load_run_policy,
            "skill_registry_path": registry_path,
            "skill_registry": skill_registry,
            "validate_known_skills": skill_router_layer.validate_known_skills,
            "dedupe_strings": dedupe_strings,
            "topological_batches": topological_batches,
            "task_id_re": TASK_ID_RE,
        },
    )


def topological_batches(tasks: list[TaskDefinition]) -> list[list[TaskDefinition]]:
    return runtime_layer.topological_batches(tasks, error_factory=OrchestratorError)


def task_dependency_hops(tasks: list[TaskDefinition]) -> dict[str, int]:
    return runtime_layer.task_dependency_hops(tasks)


def upstream_task_ids(tasks: list[TaskDefinition], task_id: str) -> set[str]:
    return runtime_layer.upstream_task_ids(tasks, task_id)


def analyze_plan_topology(plan: TaskPlan, agents: dict[str, AgentDefinition]) -> dict[str, Any]:
    batches = topological_batches(plan.tasks)
    batch_sizes = [len(batch) for batch in batches]
    dependency_hops = task_dependency_hops(plan.tasks)
    agent_counts: dict[str, int] = {}
    write_task_ids: list[str] = []
    read_only_task_ids: list[str] = []
    analyst_task_ids: list[str] = []
    implementer_task_ids: list[str] = []
    reviewer_task_ids: list[str] = []
    analyst_task_id_set: set[str] = set()
    for task in plan.tasks:
        agent_counts[task.agent] = agent_counts.get(task.agent, 0) + 1
        if task.agent == ANALYST_AGENT_NAME:
            analyst_task_ids.append(task.id)
            analyst_task_id_set.add(task.id)
        elif task.agent == IMPLEMENTER_AGENT_NAME:
            implementer_task_ids.append(task.id)
        elif task.agent == REVIEWER_AGENT_NAME:
            reviewer_task_ids.append(task.id)
        if task_may_write(plan, task, agents[task.agent]):
            write_task_ids.append(task.id)
        else:
            read_only_task_ids.append(task.id)
    warnings: list[dict[str, Any]] = []
    skill_registry = skill_router_layer.load_skill_registry(
        DEFAULT_AGENTS_PATH,
        deps={
            "discover_skill_registry": skill_router_layer.discover_skill_registry,
            "read_json": read_json,
            "read_text": read_text,
            "error_factory": OrchestratorError,
            "require_string": require_string,
            "skill_definition_factory": SkillDefinition,
        },
    )
    oversized_agent_prompt_task_ids: list[str] = []
    agent_prompt_warning_details: list[str] = []
    seen_agent_prompt_keys: set[tuple[str, str | None]] = set()
    for task in plan.tasks:
        agent = agents[task.agent]
        prompt_key = (agent.name, agent.prompt_path)
        if prompt_key in seen_agent_prompt_keys:
            continue
        seen_agent_prompt_keys.add(prompt_key)
        prompt_bytes = len(agent.prompt.encode("utf-8"))
        if prompt_bytes > AGENT_PROMPT_WARN_BYTES:
            matching_task_ids = sorted(candidate.id for candidate in plan.tasks if candidate.agent == agent.name)
            oversized_agent_prompt_task_ids.extend(matching_task_ids)
            prompt_label = agent.prompt_path or f"inline prompt for agent '{agent.name}'"
            agent_prompt_warning_details.append(
                f"{agent.name} ({prompt_label}, {prompt_bytes} bytes)"
            )
    if agent_prompt_warning_details:
        warnings.append(
            {
                "kind": "agent-prompt-size-warning",
                "taskIds": dedupe_strings(oversized_agent_prompt_task_ids),
                "message": (
                    f"Agent prompt text is above the {AGENT_PROMPT_WARN_BYTES}-byte warning threshold for: "
                    + ", ".join(agent_prompt_warning_details)
                    + ". Keep always-loaded role prompts compact."
                ),
            }
        )
    oversized_skill_task_ids: list[str] = []
    skill_warning_details: list[str] = []
    seen_skill_names: set[str] = set()
    for task in plan.tasks:
        resolved_skills = effective_task_skills(task, agents[task.agent])
        for skill_name in resolved_skills:
            if skill_name in seen_skill_names:
                continue
            seen_skill_names.add(skill_name)
            skill = skill_registry.get(skill_name)
            if skill is None:
                continue
            skill_text = read_text(Path(skill.prompt_path))
            skill_bytes = len(skill_text.encode("utf-8"))
            if skill_bytes > SKILL_PROMPT_WARN_BYTES:
                matching_task_ids = sorted(
                    candidate.id
                    for candidate in plan.tasks
                    if skill_name in effective_task_skills(candidate, agents[candidate.agent])
                )
                oversized_skill_task_ids.extend(matching_task_ids)
                skill_warning_details.append(f"{skill_name} ({skill_bytes} bytes)")
    if skill_warning_details:
        warnings.append(
            {
                "kind": "skill-prompt-size-warning",
                "taskIds": dedupe_strings(oversized_skill_task_ids),
                "message": (
                    f"Skill text is above the {SKILL_PROMPT_WARN_BYTES}-byte warning threshold for: "
                    + ", ".join(skill_warning_details)
                    + ". Keep skills narrow and move detail to referenced files."
                ),
            }
        )
    resolved_skill_warning_task_ids: list[str] = []
    for task in plan.tasks:
        resolved_skills = effective_task_skills(task, agents[task.agent])
        if len(resolved_skills) > RESOLVED_SKILLS_WARN_COUNT:
            resolved_skill_warning_task_ids.append(task.id)
    if resolved_skill_warning_task_ids:
        warnings.append(
            {
                "kind": "resolved-skills-count-warning",
                "taskIds": sorted(resolved_skill_warning_task_ids),
                "message": (
                    f"Resolved skill count is above the warning threshold of {RESOLVED_SKILLS_WARN_COUNT} for one or more tasks. "
                    f"Keep resolved skills at or below {RESOLVED_SKILLS_FAIL_COUNT} and prefer fewer, sharper skills."
                ),
            }
        )
    if reviewer_task_ids and not write_task_ids and len(read_only_task_ids) > len(reviewer_task_ids):
        warnings.append(
            {
                "kind": "read-only-review-optional",
                "taskIds": sorted(reviewer_task_ids),
                "message": (
                    f"Plan is read-only but includes {REVIEWER_AGENT_NAME} tasks; prefer "
                    f"{ANALYST_AGENT_NAME}-only execution unless independent review is required."
                ),
            }
        )
    if len(write_task_ids) == 1 and len(implementer_task_ids) == 1 and analyst_task_ids:
        sole_write_task_id = write_task_ids[0]
        upstream_analyst_task_ids = sorted(
            task_id
            for task_id in upstream_task_ids(plan.tasks, sole_write_task_id)
            if task_id in analyst_task_id_set
        )
        if upstream_analyst_task_ids:
            warnings.append(
                {
                    "kind": "single-write-task-upstream-analyst",
                    "taskIds": upstream_analyst_task_ids + [sole_write_task_id],
                    "message": (
                        f"Plan has one write-capable task ('{sole_write_task_id}') plus upstream analyst work; "
                        "consider folding analysis into the implementer unless implementation uncertainty is high."
                    ),
                }
            )
    doc_like_suffixes = {".md", ".txt", ".adoc", ".rst"}

    def _doc_like_path(path_value: str) -> bool:
        path = Path(path_value)
        return path.suffix.lower() in doc_like_suffixes or path.name.lower() in {
            "readme",
            "changelog",
            "contributing",
            "license",
        }

    docs_only_write_task_ids = [
        task.id
        for task in plan.tasks
        if task.id in write_task_ids
        and effective_task_write_scope(task)
        and all(_doc_like_path(path_value) for path_value in effective_task_write_scope(task))
    ]
    if docs_only_write_task_ids and len(docs_only_write_task_ids) == len(write_task_ids):
        validation_hints = dedupe_strings(
            list(plan.shared_context.validation)
            + [hint for task in plan.tasks for hint in task.validation]
        )
        if not any("check-doc-consistency" in hint for hint in validation_hints):
            warnings.append(
                {
                    "kind": "docs-validation-missing",
                    "taskIds": sorted(docs_only_write_task_ids),
                    "message": (
                        "Plan writes only documentation-like files but does not declare a docs consistency validation "
                        "step such as `scripts/docs/check-doc-consistency.ps1`."
                    ),
                }
            )
    tasks_by_id = {task.id: task for task in plan.tasks}
    for reviewer_task_id in reviewer_task_ids:
        reviewer_task = tasks_by_id[reviewer_task_id]
        if effective_dependency_materialization_mode(reviewer_task) != "apply-reviewed":
            continue
        direct_write_dependencies = [
            dependency_id
            for dependency_id in reviewer_task.depends_on
            if task_may_write(plan, tasks_by_id[dependency_id], agents[tasks_by_id[dependency_id].agent])
        ]
        if len(direct_write_dependencies) < 2:
            continue
        reviewer_budget = prompt_contracts_layer.resolved_max_prompt_estimated_tokens(
            reviewer_task,
            agents[reviewer_task.agent],
        )
        if reviewer_task.max_prompt_estimated_tokens is not None:
            continue
        warnings.append(
            {
                "kind": "reviewer-prompt-budget-risk",
                "taskIds": [*sorted(direct_write_dependencies), reviewer_task_id],
                "message": (
                    f"Reviewer task '{reviewer_task_id}' materializes {len(direct_write_dependencies)} write-capable "
                    "dependencies with apply-reviewed but does not override maxPromptEstimatedTokens; "
                    f"it currently inherits {reviewer_budget if reviewer_budget is not None else 'no'} prompt-token budget. "
                    "Set an explicit reviewer budget or reduce dependency payload size."
                ),
            }
        )
    return {
        "taskCount": len(plan.tasks),
        "dependencyEdgeCount": sum(len(task.depends_on) for task in plan.tasks),
        "batchCount": len(batches),
        "batchSizes": batch_sizes,
        "maxParallelWidth": max(batch_sizes, default=0),
        "maxDependencyHops": max(dependency_hops.values(), default=0),
        "taskDependencyHops": dependency_hops,
        "agentCounts": {name: agent_counts[name] for name in sorted(agent_counts)},
        "agentKinds": sorted(agent_counts),
        "readOnlyTaskCount": len(read_only_task_ids),
        "writeTaskCount": len(write_task_ids),
        "readOnlyTaskIds": sorted(read_only_task_ids),
        "writeTaskIds": sorted(write_task_ids),
        "analystTaskCount": len(analyst_task_ids),
        "implementerTaskCount": len(implementer_task_ids),
        "reviewerTaskCount": len(reviewer_task_ids),
        "warnings": warnings,
        "warningCount": len(warnings),
    }


def selected_plan(plan: TaskPlan, selected_ids: list[str]) -> TaskPlan:
    if not selected_ids:
        return plan
    wanted: set[str] = set()
    by_id = {task.id: task for task in plan.tasks}
    stack = list(selected_ids)
    while stack:
        task_id = stack.pop()
        task = by_id.get(task_id)
        if task is None:
            raise OrchestratorError(f"Unknown selected task '{task_id}'")
        if task_id in wanted:
            continue
        wanted.add(task_id)
        stack.extend(task.depends_on)
    return TaskPlan(
        version=plan.version,
        name=plan.name,
        goal=plan.goal,
        shared_context=plan.shared_context,
        tasks=[task for task in plan.tasks if task.id in wanted],
        run_policy=plan.run_policy,
    )


def _effective_tool_lists(task: TaskDefinition, agent: AgentDefinition) -> tuple[list[str], list[str]]:
    if task.allowed_tools:
        return list(task.allowed_tools), list(task.disallowed_tools)
    return list(agent.allowed_tools), list(agent.disallowed_tools)


def effective_allowed_tools(task: TaskDefinition, agent: AgentDefinition) -> list[str]:
    allowed, denied = _effective_tool_lists(task, agent)
    return [tool for tool in allowed if tool not in set(denied)]


def task_may_write(plan: TaskPlan, task: TaskDefinition, agent: AgentDefinition) -> bool:
    if effective_workspace_mode(task, agent) == "repo":
        return True
    return any(tool in WRITE_CAPABLE_TOOLS for tool in effective_allowed_tools(task, agent))


def effective_task_read_paths(plan: TaskPlan, task: TaskDefinition) -> list[str]:
    return task_plan_ops_layer.effective_task_read_paths(
        plan,
        task,
        dedupe_strings=dedupe_strings,
    )


def prompt_task_read_paths(plan: TaskPlan, task: TaskDefinition, *, context_mode: str) -> list[str]:
    return task_plan_ops_layer.prompt_task_read_paths(
        plan,
        task,
        context_mode=context_mode,
        dedupe_strings=dedupe_strings,
    )


def effective_task_write_scope(task: TaskDefinition) -> list[str]:
    return task_plan_ops_layer.effective_task_write_scope(task, dedupe_strings=dedupe_strings)


def path_within_scope(path: str, scope: str) -> bool:
    return path_safety_layer.path_within_scope(path, scope)


def paths_outside_scope(paths: list[str], declared_scope: list[str]) -> list[str]:
    return path_safety_layer.paths_outside_scope(
        paths,
        declared_scope,
        error_factory=OrchestratorError,
    )


def analyze_copy_hydration_inputs(
    plan: TaskPlan,
    task: TaskDefinition,
) -> dict[str, list[str]]:
    files_to_copy: list[str] = []
    missing_read_paths: list[str] = []
    directory_read_paths: list[str] = []
    oversized_files: list[str] = []
    for read_path in effective_task_read_paths(plan, task):
        _, source = resolve_relative_path(current_root(), read_path, location=f"{task.id}:readPaths")
        if not source.exists():
            missing_read_paths.append(read_path)
            continue
        if source.is_dir():
            directory_read_paths.append(read_path)
            continue
        if source.stat().st_size > MAX_HYDRATED_FILE_BYTES:
            oversized_files.append(read_path)
            continue
        files_to_copy.append(read_path)
    for write_path in effective_task_write_scope(task):
        _, source = resolve_relative_path(current_root(), write_path, location=f"{task.id}:writePaths")
        if not source.exists() or source.is_dir():
            continue
        if source.stat().st_size > MAX_HYDRATED_FILE_BYTES:
            oversized_files.append(write_path)
            continue
        files_to_copy.append(write_path)
    return {
        "filesToCopy": dedupe_strings(files_to_copy),
        "missingReadPaths": dedupe_strings(missing_read_paths),
        "directoryReadPaths": dedupe_strings(directory_read_paths),
        "oversizedPaths": dedupe_strings(oversized_files),
    }


def validate_task_scope_contract(
    plan: TaskPlan,
    task: TaskDefinition,
    agent: AgentDefinition,
    *,
    tasks_by_id: dict[str, TaskDefinition],
    agents: dict[str, AgentDefinition],
) -> list[str]:
    return task_plan_ops_layer.validate_task_scope_contract(
        plan,
        task,
        agent,
        tasks_by_id=tasks_by_id,
        agents=agents,
        deps={
            "task_may_write": task_may_write,
            "effective_task_write_scope": effective_task_write_scope,
            "effective_workspace_mode": effective_workspace_mode,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "analyze_copy_hydration_inputs": analyze_copy_hydration_inputs,
            "summarize_paths": summarize_paths,
        },
    )


def validate_scope_contract(plan: TaskPlan, agents: dict[str, AgentDefinition]) -> None:
    task_plan_ops_layer.validate_scope_contract(
        plan,
        agents,
        deps={
            "error_factory": OrchestratorError,
            "format_issue_block": format_issue_block,
            "validate_task_scope_contract": validate_task_scope_contract,
            "task_may_write": task_may_write,
            "effective_task_write_scope": effective_task_write_scope,
            "effective_workspace_mode": effective_workspace_mode,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "analyze_copy_hydration_inputs": analyze_copy_hydration_inputs,
            "summarize_paths": summarize_paths,
        },
    )


def overlapping_scope_entries(left: list[str], right: list[str]) -> list[str]:
    return runtime_layer.overlapping_scope_entries(left, right)


def detect_parallel_scope_conflicts(plan: TaskPlan, agents: dict[str, AgentDefinition]) -> list[dict[str, Any]]:
    return runtime_layer.detect_parallel_scope_conflicts(
        plan.tasks,
        task_may_write=lambda task: task_may_write(plan, task, agents[task.agent]),
        task_write_scope=effective_task_write_scope,
        error_factory=OrchestratorError,
    )


def select_parallel_ready_batch(
    plan: TaskPlan,
    ready: list[TaskDefinition],
    agents: dict[str, AgentDefinition],
    *,
    max_parallel: int,
) -> list[TaskDefinition]:
    return runtime_layer.select_parallel_ready_batch(
        ready,
        max_parallel=max_parallel,
        task_may_write=lambda task: task_may_write(plan, task, agents[task.agent]),
        task_write_scope=effective_task_write_scope,
    )


def agent_payload_for_claude(
    agents: dict[str, AgentDefinition],
    *,
    selected_names: list[str] | None = None,
    resolved_skills_by_name: dict[str, list[str]] | None = None,
) -> str:
    selected = set(selected_names or agents.keys())
    payload = {
        name: {
            "description": agent.description,
            "prompt": agent.prompt,
            **(
                {
                    "skills": (
                        resolved_skills_by_name.get(name, agent.skills)
                        if resolved_skills_by_name is not None
                        else agent.skills
                    )
                }
                if (
                    (resolved_skills_by_name is not None and resolved_skills_by_name.get(name, agent.skills))
                    or agent.skills
                )
                else {}
            ),
        }
        for name, agent in agents.items()
        if name in selected
    }
    return json.dumps(payload, separators=(",", ":"))


def effective_task_skills(task: TaskDefinition, agent: AgentDefinition) -> list[str]:
    skill_registry = skill_router_layer.load_skill_registry(
        DEFAULT_AGENTS_PATH,
        deps={
            "discover_skill_registry": skill_router_layer.discover_skill_registry,
            "read_json": read_json,
            "read_text": read_text,
            "error_factory": OrchestratorError,
            "require_string": require_string,
            "skill_definition_factory": SkillDefinition,
        },
    )
    return skill_router_layer.resolve_task_skills(task, agent, skill_registry, dedupe_strings=dedupe_strings)


def normalize_worker_validation_mode(
    mode: str | None,
    *,
    location: str,
    allow_legacy_compat: bool = False,
) -> str:
    normalized = (mode or DEFAULT_WORKER_VALIDATION_MODE).strip().lower()
    allowed_modes = LEGACY_WORKER_VALIDATION_MODES if allow_legacy_compat else WORKER_VALIDATION_MODES
    if normalized in allowed_modes:
        return normalized
    if normalized == "compat" and not allow_legacy_compat:
        raise OrchestratorError(
            f"{location}: workerValidationMode='compat' was removed from the live worker contract; "
            "workers must emit structured validationIntents instead"
        )
    raise OrchestratorError(
        f"{location}: worker validation mode must be one of {sorted(allowed_modes)}"
    )


def normalize_worker_validation_mode_source(source: str | None, *, location: str) -> str | None:
    if source is None:
        return None
    normalized = source.strip().lower()
    if normalized not in WORKER_VALIDATION_MODE_SOURCES:
        raise OrchestratorError(
            f"{location}: worker validation mode source must be one of {sorted(WORKER_VALIDATION_MODE_SOURCES)}"
        )
    return normalized


def normalize_output_profile_source(source: str | None, *, location: str) -> str | None:
    if source is None:
        return None
    normalized = source.strip().lower()
    if normalized not in OUTPUT_PROFILE_SOURCES:
        raise OrchestratorError(
            f"{location}: output profile source must be one of {sorted(OUTPUT_PROFILE_SOURCES)}"
        )
    return normalized


def resolve_output_profile(
    task: TaskDefinition,
    agent: AgentDefinition,
) -> tuple[str, str]:
    if task.output_profile is not None:
        return (
            normalize_output_profile(
                task.output_profile,
                location=f"task '{task.id}' outputProfile",
            )
            or DEFAULT_OUTPUT_PROFILE,
            "task",
        )
    return (
        normalize_output_profile(
            agent.output_profile,
            location=f"agent '{agent.name}' outputProfile",
        )
        or DEFAULT_OUTPUT_PROFILE,
        "agent" if agent.output_profile else "default",
    )


def effective_plan_output_profiles(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str]:
    return {
        task.id: resolve_output_profile(task, agents[task.agent])[0]
        for task in plan.tasks
    }


def effective_plan_output_profile_sources(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str]:
    return {
        task.id: resolve_output_profile(task, agents[task.agent])[1]
        for task in plan.tasks
    }


def resolve_worker_validation_mode(
    task: TaskDefinition,
    agent: AgentDefinition,
    *,
    run_override: str | None = None,
) -> WorkerValidationModeResolution:
    if run_override:
        return WorkerValidationModeResolution(
            mode=normalize_worker_validation_mode(
                run_override,
                location=f"task '{task.id}' worker validation mode override",
            ),
            source="override",
        )
    if task.worker_validation_mode is not None:
        return WorkerValidationModeResolution(
            mode=normalize_worker_validation_mode(
                task.worker_validation_mode,
                location=f"task '{task.id}' workerValidationMode",
            ),
            source="task",
        )
    if agent.worker_validation_mode is not None:
        return WorkerValidationModeResolution(
            mode=normalize_worker_validation_mode(
                agent.worker_validation_mode,
                location=f"agent '{agent.name}' workerValidationMode",
            ),
            source="agent",
        )
    return WorkerValidationModeResolution(
        mode=DEFAULT_WORKER_VALIDATION_MODE,
        source="default",
    )


def resolved_worker_validation_mode(
    task: TaskDefinition,
    agent: AgentDefinition,
    *,
    run_override: str | None = None,
) -> str:
    return resolve_worker_validation_mode(task, agent, run_override=run_override).mode


def resolved_worker_validation_mode_source(
    task: TaskDefinition,
    agent: AgentDefinition,
    *,
    run_override: str | None = None,
) -> str:
    return resolve_worker_validation_mode(task, agent, run_override=run_override).source


def effective_plan_worker_validation_modes(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str]:
    return {
        task.id: resolve_worker_validation_mode(task, agents[task.agent], run_override=run_override).mode
        for task in plan.tasks
    }


def effective_plan_worker_validation_mode_sources(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str]:
    return {
        task.id: resolve_worker_validation_mode(task, agents[task.agent], run_override=run_override).source
        for task in plan.tasks
    }


def summarized_worker_validation_mode(modes: list[str]) -> str:
    unique = sorted(set(modes))
    if not unique:
        return DEFAULT_WORKER_VALIDATION_MODE
    if len(unique) == 1:
        return unique[0]
    return "mixed"


