#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import textwrap
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from json import JSONDecodeError
from pathlib import Path
from typing import Any
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
AI_ORCHESTRATOR_DIR = ROOT / "ai" / "orchestrator"
DEFAULT_AGENTS_PATH = AI_ORCHESTRATOR_DIR / "agents.json"
DEFAULT_TASKS_DIR = AI_ORCHESTRATOR_DIR / "tasks"
DEFAULT_RUNTIME_ROOT = ROOT / ".claude-orchestrator"
DEFAULT_CLAUDE_BIN = "claude"
DEFAULT_TASK_TIMEOUT_SEC = 30 * 60
PLANNER_TASK_ID = "planner"
WORKSPACE_MODES = {"copy", "repo", "worktree"}
TASK_ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
CONTEXT_MODES = {"minimal", "full"}
DEFAULT_CONTEXT_MODE = "minimal"
MODEL_PROFILE_TO_MODEL = {
    "simple": "claude-haiku-4-5",
    "balanced": "claude-sonnet-4-6",
    "complex": "claude-opus-4-6",
}
MODEL_TO_PROFILE = {value: key for key, value in MODEL_PROFILE_TO_MODEL.items()}
MAX_HYDRATED_FILE_BYTES = 512 * 1024
DEFAULT_PROMPT_SECTION_ITEM_LIMIT = 8
DEFAULT_PROMPT_ITEM_CHAR_LIMIT = 180
DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT = 220
WRITE_CAPABLE_TOOLS = {"Bash", "Edit", "Write", "MultiEdit"}
SPARSE_COPY_BASE_FILES = ("AGENTS.md", "ai/AGENTS.md")
WORKSPACE_AUDIT_IGNORE_DIR_NAMES = {
    ".git",
    ".claude",
    ".claude-orchestrator",
    ".idea",
    ".mvn",
    ".vs",
    "__pycache__",
    "node_modules",
    "target",
}
PROTECTED_PATH_EXACT = {"TODO.md"}
PROTECTED_PATH_PREFIXES = ("ai/state/", "ai/log/", "ai/indexes/")
WORKER_RESULT_SCHEMA = {
    "type": "object",
    "properties": {
        "status": {
            "type": "string",
            "enum": ["completed", "blocked", "failed"],
        },
        "summary": {"type": "string"},
        "filesTouched": {"type": "array", "items": {"type": "string"}},
        "validationCommands": {"type": "array", "items": {"type": "string"}},
        "followUps": {"type": "array", "items": {"type": "string"}},
        "notes": {"type": "array", "items": {"type": "string"}},
    },
    "required": [
        "status",
        "summary",
        "filesTouched",
        "validationCommands",
        "followUps",
        "notes",
    ],
    "additionalProperties": False,
}

PLAN_RESULT_SCHEMA = {
    "type": "object",
    "properties": {
        "version": {"type": "integer", "enum": [1]},
        "name": {"type": "string"},
        "goal": {"type": "string"},
        "sharedContext": {
            "type": "object",
            "properties": {
                "summary": {"type": "string"},
                "constraints": {"type": "array", "items": {"type": "string"}},
                "files": {"type": "array", "items": {"type": "string"}},
                "validation": {"type": "array", "items": {"type": "string"}},
            },
            "required": ["summary", "constraints", "files", "validation"],
            "additionalProperties": False,
        },
        "tasks": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "title": {"type": "string"},
                    "agent": {"type": "string"},
                    "prompt": {"type": "string"},
                    "dependsOn": {"type": "array", "items": {"type": "string"}},
                    "files": {"type": "array", "items": {"type": "string"}},
                    "constraints": {"type": "array", "items": {"type": "string"}},
                    "validation": {"type": "array", "items": {"type": "string"}},
                    "workspaceMode": {
                        "type": "string",
                        "enum": sorted(WORKSPACE_MODES),
                    },
                    "model": {"type": "string"},
                    "modelProfile": {
                        "type": "string",
                        "enum": sorted(MODEL_PROFILE_TO_MODEL),
                    },
                    "contextMode": {
                        "type": "string",
                        "enum": sorted(CONTEXT_MODES),
                    },
                    "effort": {"type": "string"},
                    "permissionMode": {"type": "string"},
                    "timeoutSec": {"type": "integer", "minimum": 1},
                    "maxBudgetUsd": {"type": "number", "exclusiveMinimum": 0},
                    "maxPromptChars": {"type": "integer", "minimum": 1},
                    "maxPromptEstimatedTokens": {"type": "integer", "minimum": 1},
                    "allowedTools": {"type": "array", "items": {"type": "string"}},
                    "disallowedTools": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["id", "title", "agent", "prompt"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["version", "name", "goal", "sharedContext", "tasks"],
    "additionalProperties": False,
}


class OrchestratorError(RuntimeError):
    pass


@dataclass(frozen=True)
class AgentDefinition:
    name: str
    description: str
    prompt: str
    model: str | None = None
    model_profile: str | None = None
    effort: str | None = None
    permission_mode: str | None = None
    workspace_mode: str = "copy"
    context_mode: str = DEFAULT_CONTEXT_MODE
    timeout_sec: int = DEFAULT_TASK_TIMEOUT_SEC
    max_budget_usd: float | None = None
    max_prompt_chars: int | None = None
    max_prompt_estimated_tokens: int | None = None
    allowed_tools: list[str] = field(default_factory=list)
    disallowed_tools: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class SharedContext:
    summary: str
    constraints: list[str]
    files: list[str]
    validation: list[str]


@dataclass(frozen=True)
class TaskDefinition:
    id: str
    title: str
    agent: str
    prompt: str
    depends_on: list[str] = field(default_factory=list)
    files: list[str] = field(default_factory=list)
    constraints: list[str] = field(default_factory=list)
    validation: list[str] = field(default_factory=list)
    workspace_mode: str | None = None
    model: str | None = None
    model_profile: str | None = None
    effort: str | None = None
    permission_mode: str | None = None
    context_mode: str | None = None
    timeout_sec: int | None = None
    max_budget_usd: float | None = None
    max_prompt_chars: int | None = None
    max_prompt_estimated_tokens: int | None = None
    allowed_tools: list[str] = field(default_factory=list)
    disallowed_tools: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class TaskPlan:
    version: int
    name: str
    goal: str
    shared_context: SharedContext
    tasks: list[TaskDefinition]


@dataclass(frozen=True)
class PromptSectionMetric:
    name: str
    heading: str
    chars: int
    estimated_tokens: int
    item_count: int
    truncated: bool = False


@dataclass(frozen=True)
class PromptBudgetResult:
    max_chars: int | None
    max_estimated_tokens: int | None
    exceeded: bool
    violations: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class PromptSection:
    name: str
    heading: str
    body: str
    item_count: int = 0
    truncated: bool = False


@dataclass(frozen=True)
class PromptRenderResult:
    text: str
    sections: list[PromptSectionMetric]
    chars: int
    estimated_tokens: int


@dataclass
class TaskRunRecord:
    id: str
    title: str
    agent: str
    status: str
    summary: str
    workspace_mode: str
    workspace_path: str
    started_at: str
    finished_at: str
    files_touched: list[str]
    actual_files_touched: list[str]
    protected_path_violations: list[str]
    validation_commands: list[str]
    follow_ups: list[str]
    notes: list[str]
    model: str | None
    model_profile: str | None
    prompt_chars: int
    prompt_estimated_tokens: int
    prompt_sections: list[PromptSectionMetric]
    prompt_budget: PromptBudgetResult
    usage: dict[str, Any] | None
    return_code: int | None
    prompt_path: str
    command_path: str
    stdout_path: str | None
    stderr_path: str | None
    result_path: str | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Coordinate local Claude Code workers from tracked repo task specs."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser(
        "validate",
        help="Validate orchestrator agent and task-plan files without invoking Claude.",
    )
    validate_parser.add_argument(
        "task_plan",
        nargs="?",
        help="Path to a task-plan JSON file. If omitted, only agent definitions are validated.",
    )
    validate_parser.add_argument(
        "--agents",
        default=str(DEFAULT_AGENTS_PATH),
        help="Path to the tracked agents JSON file.",
    )
    validate_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the validation summary as JSON.",
    )

    plan_parser = subparsers.add_parser(
        "plan",
        help="Ask a Claude planner worker to generate a task-plan JSON file.",
    )
    plan_parser.add_argument("goal", help="High-level coordinator goal to decompose.")
    plan_parser.add_argument(
        "--name",
        default="generated-plan",
        help="Logical plan name used for the output filename and run metadata.",
    )
    plan_parser.add_argument(
        "--out",
        default="",
        help="Destination task-plan JSON path. Defaults to ai/orchestrator/tasks/generated-<slug>.json",
    )
    plan_parser.add_argument(
        "--agents",
        default=str(DEFAULT_AGENTS_PATH),
        help="Path to the tracked agents JSON file.",
    )
    plan_parser.add_argument(
        "--planner-agent",
        default=PLANNER_TASK_ID,
        help="Agent name from the agents file to use for planning.",
    )
    plan_parser.add_argument(
        "--file",
        dest="files",
        action="append",
        default=[],
        help="Repo-relative file or directory hint to include in planner context. Repeatable.",
    )
    plan_parser.add_argument(
        "--constraint",
        dest="constraints",
        action="append",
        default=[],
        help="Extra planning constraint. Repeatable.",
    )
    plan_parser.add_argument(
        "--validation",
        dest="validation",
        action="append",
        default=[],
        help="Validation command hint. Repeatable.",
    )
    plan_parser.add_argument(
        "--claude-bin",
        default=DEFAULT_CLAUDE_BIN,
        help="Claude CLI executable to invoke.",
    )
    plan_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Write no files and invoke no workers; print the planner request instead.",
    )
    plan_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the planner request/result as JSON.",
    )

    run_parser = subparsers.add_parser(
        "run",
        help="Execute a tracked task-plan through local Claude workers.",
    )
    run_parser.add_argument("task_plan", help="Path to the task-plan JSON file to execute.")
    run_parser.add_argument(
        "--agents",
        default=str(DEFAULT_AGENTS_PATH),
        help="Path to the tracked agents JSON file.",
    )
    run_parser.add_argument(
        "--claude-bin",
        default=DEFAULT_CLAUDE_BIN,
        help="Claude CLI executable to invoke.",
    )
    run_parser.add_argument(
        "--runtime-root",
        default=str(DEFAULT_RUNTIME_ROOT),
        help="Runtime root for generated manifests, logs, and isolated workspaces.",
    )
    run_parser.add_argument(
        "--max-parallel",
        type=int,
        default=2,
        help="Maximum number of ready tasks to run concurrently.",
    )
    run_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict execution to a task id and its prerequisites. Repeatable.",
    )
    run_parser.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Continue running independent tasks after a worker fails or reports blocked.",
    )
    run_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Create the run manifest and task requests without invoking Claude.",
    )
    run_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the run summary as JSON.",
    )

    return parser.parse_args()


def iso_now() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat()


def slugify(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return normalized or "task"


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def read_json(path: Path) -> Any:
    try:
        return json.loads(read_text(path))
    except JSONDecodeError as exc:
        raise OrchestratorError(f"Invalid JSON in {path}: {exc}") from exc


def write_text(path: Path, text: str | None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text or "", encoding="utf-8")


def write_json(path: Path, payload: object) -> None:
    write_text(path, json.dumps(payload, indent=2) + "\n")


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


def ensure_model_profile(value: str | None, *, location: str) -> str | None:
    if value is None:
        return None
    if value not in MODEL_PROFILE_TO_MODEL:
        raise OrchestratorError(
            f"{location}: invalid modelProfile '{value}', expected one of {sorted(MODEL_PROFILE_TO_MODEL)}"
        )
    return value


def load_agents(path: Path) -> dict[str, AgentDefinition]:
    payload = read_json(path)
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{path}: expected JSON object")
    if payload.get("version") != 1:
        raise OrchestratorError(f"{path}: expected version=1")
    raw_agents = payload.get("agents")
    if not isinstance(raw_agents, dict) or not raw_agents:
        raise OrchestratorError(f"{path}: expected non-empty 'agents' object")

    agents: dict[str, AgentDefinition] = {}
    for name, definition in raw_agents.items():
        location = f"{path}:{name}"
        if not isinstance(name, str) or not name.strip():
            raise OrchestratorError(f"{location}: invalid agent name")
        if not isinstance(definition, dict):
            raise OrchestratorError(f"{location}: expected object definition")
        workspace_mode = ensure_workspace_mode(
            require_optional_string(definition, "workspaceMode", location=location) or "copy",
            location=location,
        )
        agent = AgentDefinition(
            name=name.strip(),
            description=require_string(definition, "description", location=location),
            prompt=require_string(definition, "prompt", location=location),
            model=require_optional_string(definition, "model", location=location),
            model_profile=ensure_model_profile(
                require_optional_string(definition, "modelProfile", location=location),
                location=location,
            ),
            effort=require_optional_string(definition, "effort", location=location),
            permission_mode=require_optional_string(definition, "permissionMode", location=location),
            workspace_mode=workspace_mode or "copy",
            context_mode=ensure_context_mode(
                require_optional_string(definition, "contextMode", location=location) or DEFAULT_CONTEXT_MODE,
                location=location,
            )
            or DEFAULT_CONTEXT_MODE,
            timeout_sec=require_optional_int(definition, "timeoutSec", location=location) or DEFAULT_TASK_TIMEOUT_SEC,
            max_budget_usd=require_optional_float(definition, "maxBudgetUsd", location=location),
            max_prompt_chars=require_optional_int(definition, "maxPromptChars", location=location),
            max_prompt_estimated_tokens=require_optional_int(
                definition, "maxPromptEstimatedTokens", location=location
            ),
            allowed_tools=require_string_list(definition, "allowedTools", location=location),
            disallowed_tools=require_string_list(definition, "disallowedTools", location=location),
        )
        agents[agent.name] = agent

    if PLANNER_TASK_ID not in agents:
        raise OrchestratorError(f"{path}: required agent '{PLANNER_TASK_ID}' is missing")
    return agents


def load_task_plan(path: Path, agents: dict[str, AgentDefinition]) -> TaskPlan:
    payload = read_json(path)
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{path}: expected JSON object")
    if payload.get("version") != 1:
        raise OrchestratorError(f"{path}: expected version=1")
    shared_context_payload = payload.get("sharedContext", {})
    if not isinstance(shared_context_payload, dict):
        raise OrchestratorError(f"{path}: sharedContext must be an object")
    shared_context = SharedContext(
        summary=require_string(shared_context_payload, "summary", location=f"{path}:sharedContext"),
        constraints=require_string_list(shared_context_payload, "constraints", location=f"{path}:sharedContext"),
        files=require_string_list(shared_context_payload, "files", location=f"{path}:sharedContext"),
        validation=require_string_list(shared_context_payload, "validation", location=f"{path}:sharedContext"),
    )
    raw_tasks = payload.get("tasks")
    if not isinstance(raw_tasks, list) or not raw_tasks:
        raise OrchestratorError(f"{path}: expected non-empty tasks list")

    tasks: list[TaskDefinition] = []
    seen_ids: set[str] = set()
    for index, task_payload in enumerate(raw_tasks):
        location = f"{path}:tasks[{index}]"
        if not isinstance(task_payload, dict):
            raise OrchestratorError(f"{location}: expected task object")
        task_id = require_string(task_payload, "id", location=location)
        if not TASK_ID_RE.match(task_id):
            raise OrchestratorError(
                f"{location}: task id '{task_id}' must match {TASK_ID_RE.pattern}"
            )
        if task_id in seen_ids:
            raise OrchestratorError(f"{location}: duplicate task id '{task_id}'")
        seen_ids.add(task_id)
        agent_name = require_string(task_payload, "agent", location=location)
        if agent_name not in agents:
            raise OrchestratorError(f"{location}: unknown agent '{agent_name}'")
        task = TaskDefinition(
            id=task_id,
            title=require_string(task_payload, "title", location=location),
            agent=agent_name,
            prompt=require_string(task_payload, "prompt", location=location),
            depends_on=require_string_list(task_payload, "dependsOn", location=location),
            files=require_string_list(task_payload, "files", location=location),
            constraints=require_string_list(task_payload, "constraints", location=location),
            validation=require_string_list(task_payload, "validation", location=location),
            workspace_mode=ensure_workspace_mode(
                require_optional_string(task_payload, "workspaceMode", location=location),
                location=location,
            ),
            model=require_optional_string(task_payload, "model", location=location),
            model_profile=ensure_model_profile(
                require_optional_string(task_payload, "modelProfile", location=location),
                location=location,
            ),
            effort=require_optional_string(task_payload, "effort", location=location),
            permission_mode=require_optional_string(task_payload, "permissionMode", location=location),
            context_mode=ensure_context_mode(
                require_optional_string(task_payload, "contextMode", location=location),
                location=location,
            ),
            timeout_sec=require_optional_int(task_payload, "timeoutSec", location=location),
            max_budget_usd=require_optional_float(task_payload, "maxBudgetUsd", location=location),
            max_prompt_chars=require_optional_int(task_payload, "maxPromptChars", location=location),
            max_prompt_estimated_tokens=require_optional_int(
                task_payload, "maxPromptEstimatedTokens", location=location
            ),
            allowed_tools=require_string_list(task_payload, "allowedTools", location=location),
            disallowed_tools=require_string_list(task_payload, "disallowedTools", location=location),
        )
        tasks.append(task)

    task_ids = {task.id for task in tasks}
    for task in tasks:
        missing_dependencies = [dependency for dependency in task.depends_on if dependency not in task_ids]
        if missing_dependencies:
            raise OrchestratorError(f"{path}:{task.id}: unknown dependencies {missing_dependencies}")
        if task.id in task.depends_on:
            raise OrchestratorError(f"{path}:{task.id}: task cannot depend on itself")
    topological_batches(tasks)

    return TaskPlan(
        version=1,
        name=require_string(payload, "name", location=str(path)),
        goal=require_string(payload, "goal", location=str(path)),
        shared_context=shared_context,
        tasks=tasks,
    )


def topological_batches(tasks: list[TaskDefinition]) -> list[list[TaskDefinition]]:
    by_id = {task.id: task for task in tasks}
    pending = {task.id: set(task.depends_on) for task in tasks}
    batches: list[list[TaskDefinition]] = []
    while pending:
        ready_ids = sorted(task_id for task_id, dependencies in pending.items() if not dependencies)
        if not ready_ids:
            raise OrchestratorError("Task plan contains a dependency cycle")
        batches.append([by_id[task_id] for task_id in ready_ids])
        for task_id in ready_ids:
            pending.pop(task_id)
        for dependencies in pending.values():
            dependencies.difference_update(ready_ids)
    return batches


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
    )


def effective_allowed_tools(task: TaskDefinition, agent: AgentDefinition) -> list[str]:
    allowed = list(task.allowed_tools or agent.allowed_tools)
    if not allowed:
        return []
    denied = set(task.disallowed_tools or agent.disallowed_tools)
    return [tool for tool in allowed if tool not in denied]


def task_may_write(plan: TaskPlan, task: TaskDefinition, agent: AgentDefinition) -> bool:
    if effective_workspace_mode(task, agent) == "repo":
        return True
    return any(tool in WRITE_CAPABLE_TOOLS for tool in effective_allowed_tools(task, agent))


def effective_task_scopes(plan: TaskPlan, task: TaskDefinition, agent: AgentDefinition) -> list[str]:
    if effective_workspace_mode(task, agent) == "repo":
        return ["."]
    scopes = []
    for item in dedupe_strings(task.files or plan.shared_context.files):
        relative = Path(item)
        if relative.is_absolute():
            continue
        normalized = relative.as_posix().strip("/")
        if not normalized or normalized == ".":
            return ["."]
        scopes.append(normalized)
    return scopes or ["."]


def overlapping_scope_entries(left: list[str], right: list[str]) -> list[str]:
    overlaps: list[str] = []
    for left_item in left:
        for right_item in right:
            if (
                left_item == "."
                or right_item == "."
                or left_item == right_item
                or left_item.startswith(f"{right_item}/")
                or right_item.startswith(f"{left_item}/")
            ):
                overlaps.extend([left_item, right_item])
    return dedupe_strings(overlaps)


def detect_parallel_scope_conflicts(plan: TaskPlan, agents: dict[str, AgentDefinition]) -> list[dict[str, Any]]:
    conflicts: list[dict[str, Any]] = []
    for batch_index, batch in enumerate(topological_batches(plan.tasks), start=1):
        for left_index, left_task in enumerate(batch):
            left_agent = agents[left_task.agent]
            if not task_may_write(plan, left_task, left_agent):
                continue
            left_scopes = effective_task_scopes(plan, left_task, left_agent)
            for right_task in batch[left_index + 1 :]:
                right_agent = agents[right_task.agent]
                if not task_may_write(plan, right_task, right_agent):
                    continue
                overlaps = overlapping_scope_entries(
                    left_scopes,
                    effective_task_scopes(plan, right_task, right_agent),
                )
                if not overlaps:
                    continue
                conflicts.append(
                    {
                        "batch": batch_index,
                        "taskIds": [left_task.id, right_task.id],
                        "overlappingScopes": overlaps,
                    }
                )
    return conflicts


def select_parallel_ready_batch(
    plan: TaskPlan,
    ready: list[TaskDefinition],
    agents: dict[str, AgentDefinition],
    *,
    max_parallel: int,
) -> list[TaskDefinition]:
    ordered = sorted(ready, key=lambda task: task.id)
    selected: list[TaskDefinition] = []
    for candidate in ordered:
        if len(selected) >= max_parallel:
            break
        candidate_agent = agents[candidate.agent]
        if not task_may_write(plan, candidate, candidate_agent):
            selected.append(candidate)
            continue
        candidate_scopes = effective_task_scopes(plan, candidate, candidate_agent)
        if any(
            task_may_write(plan, chosen, agents[chosen.agent])
            and overlapping_scope_entries(
                candidate_scopes,
                effective_task_scopes(plan, chosen, agents[chosen.agent]),
            )
            for chosen in selected
        ):
            continue
        selected.append(candidate)
    if selected:
        return selected
    return ordered[:1]


def agent_payload_for_claude(agents: dict[str, AgentDefinition]) -> str:
    payload = {
        name: {
            "description": agent.description,
            "prompt": agent.prompt,
        }
        for name, agent in agents.items()
    }
    return json.dumps(payload, separators=(",", ":"))


def ensure_claude_available(claude_bin: str) -> None:
    if shutil.which(claude_bin) is None:
        raise OrchestratorError(f"Claude CLI '{claude_bin}' is not available on PATH")


def ensure_clean_for_worktrees() -> None:
    completed = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        raise OrchestratorError("Failed to inspect git status for worktree mode")
    if completed.stdout.strip():
        raise OrchestratorError(
            "worktree mode requires a clean repo because detached worktrees start from HEAD only; "
            "use workspaceMode='copy' to isolate current uncommitted changes"
        )


def path_is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def hydrate_copy_workspace(workspace_path: Path, file_hints: list[str]) -> None:
    copied: set[Path] = set()
    workspace_path.mkdir(parents=True, exist_ok=True)
    for hint in dedupe_strings(list(SPARSE_COPY_BASE_FILES) + file_hints):
        relative = Path(hint)
        if relative.is_absolute():
            continue
        source = (ROOT / relative).resolve()
        if not source.exists() or not path_is_relative_to(source, ROOT.resolve()):
            continue
        if source.is_dir():
            continue
        if source.stat().st_size > MAX_HYDRATED_FILE_BYTES:
            continue
        destination = workspace_path / relative
        if destination.exists() or source in copied:
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied.add(source)


def prepare_workspace(
    plan: TaskPlan,
    task: TaskDefinition,
    workspace_mode: str,
    workspace_path: Path,
    runtime_root: Path,
) -> Path:
    if workspace_mode == "repo":
        return ROOT
    if workspace_path.exists():
        shutil.rmtree(workspace_path)
    workspace_path.parent.mkdir(parents=True, exist_ok=True)
    if workspace_mode == "copy":
        hydrate_copy_workspace(workspace_path, plan.shared_context.files + task.files)
        return workspace_path
    if workspace_mode == "worktree":
        ensure_clean_for_worktrees()
        completed = subprocess.run(
            ["git", "worktree", "add", "--detach", str(workspace_path), "HEAD"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        if completed.returncode != 0:
            raise OrchestratorError(
                f"{task.id}: failed to create worktree: {completed.stderr.strip() or completed.stdout.strip()}"
            )
        return workspace_path
    raise OrchestratorError(f"{task.id}: unsupported workspace mode '{workspace_mode}'")


def snapshot_workspace_files(workspace_root: Path) -> dict[str, str]:
    if not workspace_root.exists():
        return {}
    snapshots: dict[str, str] = {}
    for current_root, dir_names, file_names in os.walk(workspace_root, topdown=True):
        dir_names[:] = sorted(
            name for name in dir_names if name not in WORKSPACE_AUDIT_IGNORE_DIR_NAMES
        )
        current_path = Path(current_root)
        for file_name in sorted(file_names):
            file_path = current_path / file_name
            snapshots[file_path.relative_to(workspace_root).as_posix()] = file_sha256(file_path)
    return snapshots


def diff_workspace_snapshots(before: dict[str, str], after: dict[str, str]) -> list[str]:
    changed = []
    for relative_path in sorted(set(before) | set(after)):
        if before.get(relative_path) != after.get(relative_path):
            changed.append(relative_path)
    return changed


def summarize_paths(paths: list[str], *, limit: int = 4) -> str:
    visible = paths[:limit]
    if not visible:
        return "none"
    summary = ", ".join(visible)
    hidden = len(paths) - len(visible)
    if hidden > 0:
        summary += f", ... ({hidden} more)"
    return summary


def protected_path_violations(paths: list[str]) -> list[str]:
    violations = []
    for path in paths:
        normalized = Path(path).as_posix().strip("/")
        if normalized in PROTECTED_PATH_EXACT or normalized.startswith(PROTECTED_PATH_PREFIXES):
            violations.append(normalized)
    return dedupe_strings(violations)


def apply_workspace_audit(
    record: TaskRunRecord,
    *,
    reported_files: list[str],
    actual_files: list[str],
) -> TaskRunRecord:
    record.actual_files_touched = list(actual_files)
    record.files_touched = dedupe_strings(actual_files + reported_files)
    actual_only = [path for path in actual_files if path not in reported_files]
    if actual_only:
        record.notes.append(
            "Workspace diff found files not reported by the worker: "
            f"{summarize_paths(actual_only)}"
        )
    violations = protected_path_violations(record.files_touched)
    record.protected_path_violations = violations
    if violations:
        record.status = "failed"
        record.summary = f"Protected-path violation: {summarize_paths(violations)}. Original outcome: {record.summary}"
        follow_up = "Inspect and discard or manually review forbidden workspace edits before promotion."
        if follow_up not in record.follow_ups:
            record.follow_ups.insert(0, follow_up)
    return record


def planner_output_path(name: str, explicit_path: str) -> Path:
    if explicit_path:
        return Path(explicit_path).resolve()
    filename = f"generated-{slugify(name)}.json"
    return (DEFAULT_TASKS_DIR / filename).resolve()


def planner_prompt(
    goal: str,
    name: str,
    files: list[str],
    constraints: list[str],
    validation: list[str],
    agents: dict[str, AgentDefinition],
) -> PromptRenderResult:
    agent_catalog, agent_count, agent_catalog_truncated = format_bullet_list(
        [
            f"`{agent.name}`: {agent.description}"
            for agent in agents.values()
            if agent.name != PLANNER_TASK_ID
        ],
        empty_line="- none supplied",
        max_chars=DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
    )
    file_lines, file_count, files_truncated = format_bullet_list(
        files,
        empty_line="- none supplied",
        code_format=True,
    )
    constraint_lines, constraint_count, constraints_truncated = format_bullet_list(
        constraints,
        empty_line="- keep tasks bounded and concrete",
    )
    validation_lines, validation_count, validation_truncated = format_bullet_list(
        validation,
        empty_line="- none supplied",
        code_format=True,
    )
    requirement_lines, requirement_count, requirements_truncated = format_bullet_list(
        [
            "Return schema-valid JSON only. No narration or markdown fences.",
            "Create 1 to 6 tasks.",
            'Use `workspaceMode="copy"` for most tasks.',
            'Use `workspaceMode="worktree"` only when isolated git metadata matters.',
            'Avoid `workspaceMode="repo"` unless direct in-place execution is essential.',
            'Use `contextMode="minimal"` unless a task really needs the full shared context.',
            '`modelProfile="simple"` fits quick summaries, classification, or narrow lookups.',
            '`modelProfile="balanced"` fits most coding, analysis, and implementation tasks.',
            '`modelProfile="complex"` fits architecture or deeply nuanced multi-step reasoning.',
            "Keep file lists repo-relative, concrete, and file-based for copy workspaces.",
            "Minimize per-task context and validation hints.",
            "Do not assign `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*` edits to workers.",
            "Put cross-task setup in `sharedContext`.",
            "Use `dependsOn` only when one task genuinely needs another task's output.",
            "Omit speculative tasks when evidence is insufficient.",
        ],
        empty_line="- none",
        max_items=16,
    )
    return render_prompt(
        [
            PromptSection(
                name="planner_role",
                heading="Planner role",
                body="Plan a bounded Claude worker DAG for this repository.",
            ),
            PromptSection(name="goal", heading="Goal", body=goal),
            PromptSection(name="plan_name", heading="Plan name", body=name),
            PromptSection(
                name="available_agents",
                heading="Available worker agents",
                body=agent_catalog,
                item_count=agent_count,
                truncated=agent_catalog_truncated,
            ),
            PromptSection(
                name="file_hints",
                heading="File hints",
                body=file_lines,
                item_count=file_count,
                truncated=files_truncated,
            ),
            PromptSection(
                name="constraints",
                heading="Constraints",
                body=constraint_lines,
                item_count=constraint_count,
                truncated=constraints_truncated,
            ),
            PromptSection(
                name="validation_hints",
                heading="Validation hints",
                body=validation_lines,
                item_count=validation_count,
                truncated=validation_truncated,
            ),
            PromptSection(
                name="requirements",
                heading="Requirements",
                body=requirement_lines,
                item_count=requirement_count,
                truncated=requirements_truncated,
            ),
        ]
    )


def dedupe_strings(values: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        ordered.append(value)
    return ordered


def estimate_tokens(text: str) -> int:
    return max(1, (len(text) + 3) // 4)


def compact_text(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def truncate_text(text: str, max_chars: int | None) -> tuple[str, bool]:
    compacted = compact_text(text)
    if max_chars is None or len(compacted) <= max_chars:
        return compacted, False
    if max_chars <= 3:
        return compacted[:max_chars], True
    return compacted[: max_chars - 3].rstrip() + "...", True


def format_bullet_list(
    values: list[str],
    *,
    empty_line: str,
    code_format: bool = False,
    max_items: int = DEFAULT_PROMPT_SECTION_ITEM_LIMIT,
    max_chars: int | None = DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
) -> tuple[str, int, bool]:
    if not values:
        return empty_line, 0, False
    truncated = False
    visible_values = values[:max_items] if max_items > 0 else values
    lines: list[str] = []
    for value in visible_values:
        item_text, item_truncated = truncate_text(value, max_chars)
        if code_format:
            item_text = f"`{item_text}`"
        lines.append(f"- {item_text}")
        truncated = truncated or item_truncated
    hidden_count = len(values) - len(visible_values)
    if hidden_count > 0:
        lines.append(f"- ... ({hidden_count} more omitted)")
        truncated = True
    return "\n".join(lines), len(values), truncated


def render_prompt(sections: list[PromptSection]) -> PromptRenderResult:
    rendered_sections: list[str] = []
    metrics: list[PromptSectionMetric] = []
    for section in sections:
        rendered = f"{section.heading}:\n{section.body}"
        rendered_sections.append(rendered)
        metrics.append(
            PromptSectionMetric(
                name=section.name,
                heading=section.heading,
                chars=len(rendered),
                estimated_tokens=estimate_tokens(rendered),
                item_count=section.item_count,
                truncated=section.truncated,
            )
        )
    prompt = "\n\n".join(rendered_sections)
    return PromptRenderResult(
        text=prompt,
        sections=metrics,
        chars=len(prompt),
        estimated_tokens=estimate_tokens(prompt),
    )


def evaluate_prompt_budget(
    *,
    prompt_chars: int,
    prompt_estimated_tokens: int,
    max_chars: int | None,
    max_estimated_tokens: int | None,
) -> PromptBudgetResult:
    violations: list[str] = []
    if max_chars is not None and prompt_chars > max_chars:
        violations.append(f"prompt_chars={prompt_chars} exceeds maxPromptChars={max_chars}")
    if max_estimated_tokens is not None and prompt_estimated_tokens > max_estimated_tokens:
        violations.append(
            "prompt_estimated_tokens="
            f"{prompt_estimated_tokens} exceeds maxPromptEstimatedTokens={max_estimated_tokens}"
        )
    return PromptBudgetResult(
        max_chars=max_chars,
        max_estimated_tokens=max_estimated_tokens,
        exceeded=bool(violations),
        violations=violations,
    )


def prompt_budget_failure_summary(result: PromptBudgetResult) -> str:
    details = "; ".join(result.violations) if result.violations else "configured budget violation"
    return f"Prompt budget exceeded: {details}"


def resolved_max_prompt_chars(task: TaskDefinition | None, agent: AgentDefinition) -> int | None:
    if task is not None and task.max_prompt_chars is not None:
        return task.max_prompt_chars
    return agent.max_prompt_chars


def resolved_max_prompt_estimated_tokens(task: TaskDefinition | None, agent: AgentDefinition) -> int | None:
    if task is not None and task.max_prompt_estimated_tokens is not None:
        return task.max_prompt_estimated_tokens
    return agent.max_prompt_estimated_tokens


def effective_context_mode(task: TaskDefinition, agent: AgentDefinition) -> str:
    return task.context_mode or agent.context_mode or DEFAULT_CONTEXT_MODE


def resolved_model_profile(task: TaskDefinition, agent: AgentDefinition) -> str | None:
    explicit_model = task.model or agent.model
    if explicit_model:
        return MODEL_TO_PROFILE.get(explicit_model)
    return task.model_profile or agent.model_profile


def resolved_model(task: TaskDefinition, agent: AgentDefinition) -> str | None:
    explicit_model = task.model or agent.model
    if explicit_model:
        return explicit_model
    profile = resolved_model_profile(task, agent)
    if profile:
        return MODEL_PROFILE_TO_MODEL[profile]
    return None


def dependency_summary(records: dict[str, TaskRunRecord], task: TaskDefinition) -> str:
    if not task.depends_on:
        return "- none"
    lines = []
    for dependency_id in task.depends_on:
        record = records[dependency_id]
        summary, _ = truncate_text(record.summary, DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT)
        lines.append(f"`{dependency_id}` ({record.status}): {summary}")
    rendered, _, _ = format_bullet_list(lines, empty_line="- none", max_chars=None)
    return rendered


def worker_prompt(
    plan: TaskPlan,
    task: TaskDefinition,
    agent: AgentDefinition,
    workspace_mode: str,
    workspace_path: Path,
    dependency_text: str,
) -> PromptRenderResult:
    task_root = ROOT if workspace_mode == "repo" else workspace_path
    context_mode = effective_context_mode(task, agent)
    relevant_files = dedupe_strings(task.files or plan.shared_context.files)
    constraints = dedupe_strings(plan.shared_context.constraints + task.constraints)
    validation_hints = (
        dedupe_strings(task.validation)
        if context_mode == "minimal"
        else dedupe_strings(plan.shared_context.validation + task.validation)
    )
    workspace_rule = {
        "copy": "Isolated sparse filesystem copy seeded from AGENTS files plus explicit file hints from the current working tree. Edit only inside this copy.",
        "repo": "Live repo root. Treat this as high-risk and avoid incidental edits.",
        "worktree": "Detached git worktree rooted at HEAD. Root-repo uncommitted changes are not present.",
    }[workspace_mode]
    file_lines, file_count, files_truncated = format_bullet_list(
        relevant_files,
        empty_line="- none",
        code_format=True,
    )
    constraint_lines, constraint_count, constraints_truncated = format_bullet_list(
        constraints,
        empty_line="- none",
    )
    validation_lines, validation_count, validation_truncated = format_bullet_list(
        validation_hints,
        empty_line="- none",
        code_format=True,
    )
    worker_rules, rule_count, rules_truncated = format_bullet_list(
        [
            "Follow repo instructions from AGENTS.md and ai/AGENTS.md when relevant.",
            "Use the task repo root for file and shell access. In copy/worktree mode, do not access files through the source repo root.",
            "Return schema-valid JSON only. No narration or markdown fences.",
            "Keep summaries and notes terse. Leave arrays empty when there is nothing to add.",
            "State uncertainty explicitly instead of guessing.",
            "Do not claim edits or validation you did not actually perform.",
            "Do not edit TODO.md, ai/state/*, ai/log/*, or ai/indexes/*.",
            "Keep changes tightly scoped to the task.",
            "If blocked, explain the blocker concretely and list the next coordinator action.",
        ],
        empty_line="- none",
        max_items=12,
    )
    return render_prompt(
        [
            PromptSection(
                name="execution_context",
                heading="Execution context",
                body=textwrap.dedent(
                    f"""\
                    Task repo root: {task_root}
                    Source repo root: {ROOT}
                    Task workspace: {workspace_path}
                    Workspace mode: {workspace_mode}
                    Context mode: {context_mode}
                    Workspace contract: {workspace_rule}
                    """
                ).strip(),
            ),
            PromptSection(name="coordinator_goal", heading="Coordinator goal", body=plan.goal),
            PromptSection(
                name="shared_summary",
                heading="Shared context summary",
                body=plan.shared_context.summary,
            ),
            PromptSection(name="task_identity", heading="Task id/title", body=f"{task.id} / {task.title}"),
            PromptSection(name="task_objective", heading="Task objective", body=task.prompt),
            PromptSection(
                name="file_hints",
                heading="Relevant file hints",
                body=file_lines,
                item_count=file_count,
                truncated=files_truncated,
            ),
            PromptSection(
                name="constraints",
                heading="Constraints",
                body=constraint_lines,
                item_count=constraint_count,
                truncated=constraints_truncated,
            ),
            PromptSection(
                name="dependency_outputs",
                heading="Dependency outputs",
                body=dependency_text,
                item_count=len(task.depends_on),
            ),
            PromptSection(
                name="validation_hints",
                heading="Validation hints",
                body=validation_lines,
                item_count=validation_count,
                truncated=validation_truncated,
            ),
            PromptSection(
                name="worker_rules",
                heading="Worker rules",
                body=worker_rules,
                item_count=rule_count,
                truncated=rules_truncated,
            ),
        ]
    )


def task_output_schema_json() -> str:
    return json.dumps(WORKER_RESULT_SCHEMA, separators=(",", ":"))


def plan_output_schema_json() -> str:
    return json.dumps(PLAN_RESULT_SCHEMA, separators=(",", ":"))


def claude_command(
    claude_bin: str,
    agents_json: str,
    agent_name: str,
    prompt: str,
    schema_json: str,
    *,
    model: str | None,
    effort: str | None,
    permission_mode: str | None,
    allowed_tools: list[str],
    disallowed_tools: list[str],
    max_budget_usd: float | None,
) -> list[str]:
    command = [
        claude_bin,
        "-p",
        "--no-session-persistence",
        "--output-format",
        "json",
        "--json-schema",
        schema_json,
        "--agents",
        agents_json,
        "--agent",
        agent_name,
    ]
    if model:
        command.extend(["--model", model])
    if effort:
        command.extend(["--effort", effort])
    if permission_mode:
        command.extend(["--permission-mode", permission_mode])
    if allowed_tools:
        command.extend(["--allowed-tools", ",".join(allowed_tools)])
    if disallowed_tools:
        command.extend(["--disallowed-tools", ",".join(disallowed_tools)])
    if max_budget_usd is not None:
        command.extend(["--max-budget-usd", f"{max_budget_usd:.2f}"])
    command.extend(["--", prompt])
    return command


def extract_json_payload(text: str) -> Any:
    stripped = text.strip()
    if not stripped:
        raise OrchestratorError("Claude returned empty output")
    decoder = json.JSONDecoder()
    for index, char in enumerate(stripped):
        if char not in "[{":
            continue
        try:
            payload, end = decoder.raw_decode(stripped[index:])
        except JSONDecodeError:
            continue
        trailing = stripped[index + end :].strip()
        if trailing:
            continue
        return payload
    raise OrchestratorError("Claude output did not contain a standalone JSON payload")


def coerce_worker_result(payload: Any) -> dict[str, Any]:
    required = {
        "status",
        "summary",
        "filesTouched",
        "validationCommands",
        "followUps",
        "notes",
    }
    if isinstance(payload, dict) and required.issubset(payload):
        return payload
    if isinstance(payload, dict):
        for key in ("result", "data", "response", "structured_output"):
            nested = payload.get(key)
            if isinstance(nested, dict) and required.issubset(nested):
                return nested
    raise OrchestratorError("Claude JSON output did not match the expected worker schema")


def extract_usage(payload: Any) -> dict[str, Any] | None:
    if not isinstance(payload, dict):
        return None
    usage = payload.get("usage")
    model_usage = payload.get("modelUsage")
    total_cost = payload.get("total_cost_usd")
    if usage is None and model_usage is None and total_cost is None:
        return None
    summary: dict[str, Any] = {
        "inputTokens": int(usage.get("input_tokens", 0)) if isinstance(usage, dict) else 0,
        "outputTokens": int(usage.get("output_tokens", 0)) if isinstance(usage, dict) else 0,
        "cacheReadInputTokens": int(usage.get("cache_read_input_tokens", 0)) if isinstance(usage, dict) else 0,
        "cacheCreationInputTokens": int(usage.get("cache_creation_input_tokens", 0))
        if isinstance(usage, dict)
        else 0,
        "serviceTier": usage.get("service_tier") if isinstance(usage, dict) else None,
        "durationMs": payload.get("duration_ms"),
        "durationApiMs": payload.get("duration_api_ms"),
        "numTurns": payload.get("num_turns"),
        "stopReason": payload.get("stop_reason"),
        "isError": payload.get("is_error"),
        "totalCostUsd": float(total_cost) if isinstance(total_cost, (int, float)) else None,
        "modelUsage": model_usage if isinstance(model_usage, dict) else {},
    }
    return summary


def aggregate_usage(records: dict[str, TaskRunRecord]) -> dict[str, Any]:
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
    for model_name, model_totals in totals["perModel"].items():
        model_totals["costUsd"] = round(float(model_totals["costUsd"]), 6)
    return totals


def coerce_plan_result(payload: Any) -> dict[str, Any]:
    required = {"version", "name", "goal", "sharedContext", "tasks"}
    if isinstance(payload, dict) and required.issubset(payload):
        return payload
    if isinstance(payload, dict):
        for key in ("result", "data", "response"):
            nested = payload.get(key)
            if isinstance(nested, dict) and required.issubset(nested):
                return nested
    raise OrchestratorError("Claude JSON output did not match the expected plan schema")


def run_subprocess(
    command: list[str],
    *,
    cwd: Path,
    timeout_sec: int,
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            cwd=cwd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_sec,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise OrchestratorError(
            f"Claude timed out after {timeout_sec} seconds"
        ) from exc


def plan_with_claude(args: argparse.Namespace) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    agents = load_agents(agents_path)
    if args.planner_agent not in agents:
        raise OrchestratorError(f"Unknown planner agent '{args.planner_agent}'")
    if not args.dry_run:
        ensure_claude_available(args.claude_bin)
    prompt_render = planner_prompt(
        args.goal,
        args.name,
        args.files,
        args.constraints,
        args.validation,
        agents,
    )
    agent = agents[args.planner_agent]
    prompt_budget = evaluate_prompt_budget(
        prompt_chars=prompt_render.chars,
        prompt_estimated_tokens=prompt_render.estimated_tokens,
        max_chars=agent.max_prompt_chars,
        max_estimated_tokens=agent.max_prompt_estimated_tokens,
    )
    planner_model = agent.model or (MODEL_PROFILE_TO_MODEL[agent.model_profile] if agent.model_profile else None)
    agents_json = agent_payload_for_claude(agents)
    output_path = planner_output_path(args.name, args.out)
    command = claude_command(
        args.claude_bin,
        agents_json,
        args.planner_agent,
        prompt_render.text,
        plan_output_schema_json(),
        model=planner_model,
        effort=agent.effort,
        permission_mode=agent.permission_mode,
        allowed_tools=agent.allowed_tools,
        disallowed_tools=agent.disallowed_tools,
        max_budget_usd=agent.max_budget_usd,
    )
    request_payload = {
        "name": args.name,
        "goal": args.goal,
        "outputPath": str(output_path),
        "command": command,
        "prompt": prompt_render.text,
        "model": planner_model,
        "modelProfile": agent.model_profile,
        "promptChars": prompt_render.chars,
        "promptEstimatedTokens": prompt_render.estimated_tokens,
        "promptSections": [asdict(section) for section in prompt_render.sections],
        "promptBudget": asdict(prompt_budget),
        "dryRun": args.dry_run,
    }
    if prompt_budget.exceeded:
        request_payload["status"] = "prompt-budget-exceeded"
        if args.dry_run:
            return request_payload
        raise OrchestratorError(prompt_budget_failure_summary(prompt_budget))
    if args.dry_run:
        return request_payload

    completed = run_subprocess(command, cwd=ROOT, timeout_sec=agent.timeout_sec)
    if completed.returncode != 0:
        raise OrchestratorError(
            f"Planner failed with exit code {completed.returncode}: "
            f"{completed.stderr.strip() or completed.stdout.strip()}"
        )
    raw_payload = extract_json_payload(completed.stdout)
    payload = coerce_plan_result(raw_payload)
    write_json(output_path, payload)
    return {
        **request_payload,
        "status": "written",
        "usage": extract_usage(raw_payload),
    }


def effective_workspace_mode(task: TaskDefinition, agent: AgentDefinition) -> str:
    return task.workspace_mode or agent.workspace_mode


def blocked_record(
    task: TaskDefinition,
    agent_name: str,
    agent: AgentDefinition,
    workspace_mode: str,
    *,
    reason: str,
) -> TaskRunRecord:
    now = iso_now()
    return TaskRunRecord(
        id=task.id,
        title=task.title,
        agent=agent_name,
        status="blocked",
        summary=reason,
        workspace_mode=workspace_mode,
        workspace_path="",
        started_at=now,
        finished_at=now,
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
        validation_commands=[],
        follow_ups=[reason],
        notes=[],
        model=resolved_model(task, agent),
        model_profile=resolved_model_profile(task, agent),
        prompt_chars=0,
        prompt_estimated_tokens=0,
        prompt_sections=[],
        prompt_budget=PromptBudgetResult(
            max_chars=resolved_max_prompt_chars(task, agent),
            max_estimated_tokens=resolved_max_prompt_estimated_tokens(task, agent),
            exceeded=False,
            violations=[],
        ),
        usage=None,
        return_code=None,
        prompt_path="",
        command_path="",
        stdout_path=None,
        stderr_path=None,
        result_path=None,
    )


def execute_task(
    run_dir: Path,
    runtime_root: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord],
    *,
    claude_bin: str,
    agents_json: str,
    dry_run: bool,
) -> TaskRunRecord:
    agent = agents[task.agent]
    model_name = resolved_model(task, agent)
    model_profile = resolved_model_profile(task, agent)
    workspace_mode = effective_workspace_mode(task, agent)
    workspace_path = workspaces_dir / task.id
    task_dir = run_dir / "tasks" / task.id
    task_dir.mkdir(parents=True, exist_ok=True)
    prepared_workspace = ROOT if workspace_mode == "repo" else workspace_path
    if not dry_run:
        prepared_workspace = prepare_workspace(plan, task, workspace_mode, workspace_path, runtime_root)
    prompt_render = worker_prompt(
        plan,
        task,
        agent,
        workspace_mode,
        prepared_workspace,
        dependency_summary(dependency_records, task),
    )
    prompt = prompt_render.text
    prompt_chars = prompt_render.chars
    prompt_estimated_tokens = prompt_render.estimated_tokens
    prompt_budget = evaluate_prompt_budget(
        prompt_chars=prompt_chars,
        prompt_estimated_tokens=prompt_estimated_tokens,
        max_chars=resolved_max_prompt_chars(task, agent),
        max_estimated_tokens=resolved_max_prompt_estimated_tokens(task, agent),
    )
    command = claude_command(
        claude_bin,
        agents_json,
        task.agent,
        prompt,
        task_output_schema_json(),
        model=model_name,
        effort=task.effort or agent.effort,
        permission_mode=task.permission_mode or agent.permission_mode,
        allowed_tools=task.allowed_tools or agent.allowed_tools,
        disallowed_tools=task.disallowed_tools or agent.disallowed_tools,
        max_budget_usd=task.max_budget_usd if task.max_budget_usd is not None else agent.max_budget_usd,
    )
    prompt_path = task_dir / "prompt.txt"
    command_path = task_dir / "command.json"
    write_text(prompt_path, prompt)
    write_json(command_path, {"cwd": str(prepared_workspace), "command": command})

    started_at = iso_now()
    result_path = task_dir / "result.json"
    if prompt_budget.exceeded:
        record = TaskRunRecord(
            id=task.id,
            title=task.title,
            agent=task.agent,
            status="failed",
            summary=prompt_budget_failure_summary(prompt_budget),
            workspace_mode=workspace_mode,
            workspace_path=str(prepared_workspace),
            started_at=started_at,
            finished_at=iso_now(),
            files_touched=[],
            actual_files_touched=[],
            protected_path_violations=[],
            validation_commands=[],
            follow_ups=["Reduce the task prompt scope, dependency summary, or validation hints."],
            notes=[],
            model=model_name,
            model_profile=model_profile,
            prompt_chars=prompt_chars,
            prompt_estimated_tokens=prompt_estimated_tokens,
            prompt_sections=prompt_render.sections,
            prompt_budget=prompt_budget,
            usage=None,
            return_code=None,
            prompt_path=str(prompt_path),
            command_path=str(command_path),
            stdout_path=None,
            stderr_path=None,
            result_path=None,
        )
        write_json(result_path, asdict(record))
        return record
    if dry_run:
        record = TaskRunRecord(
            id=task.id,
            title=task.title,
            agent=task.agent,
            status="planned",
            summary="Dry run only; Claude was not invoked.",
            workspace_mode=workspace_mode,
            workspace_path=str(prepared_workspace),
            started_at=started_at,
            finished_at=iso_now(),
            files_touched=[],
            actual_files_touched=[],
            protected_path_violations=[],
            validation_commands=[],
            follow_ups=[],
            notes=[],
            model=model_name,
            model_profile=model_profile,
            prompt_chars=prompt_chars,
            prompt_estimated_tokens=prompt_estimated_tokens,
            prompt_sections=prompt_render.sections,
            prompt_budget=prompt_budget,
            usage=None,
            return_code=None,
            prompt_path=str(prompt_path),
            command_path=str(command_path),
            stdout_path=None,
            stderr_path=None,
            result_path=None,
        )
        write_json(result_path, asdict(record))
        return record

    stdout_path = task_dir / "stdout.json"
    stderr_path = task_dir / "stderr.txt"
    worker_result_path = task_dir / "worker-result.json"
    return_code: int | None = None
    usage: dict[str, Any] | None = None
    workspace_snapshot_before = snapshot_workspace_files(prepared_workspace)
    actual_changed_files: list[str] = []
    try:
        completed = run_subprocess(
            command,
            cwd=prepared_workspace,
            timeout_sec=task.timeout_sec or agent.timeout_sec,
        )
        return_code = completed.returncode
        write_text(stdout_path, completed.stdout)
        write_text(stderr_path, completed.stderr)
        actual_changed_files = diff_workspace_snapshots(
            workspace_snapshot_before,
            snapshot_workspace_files(prepared_workspace),
        )
        raw_payload: Any | None = None
        try:
            raw_payload = extract_json_payload(completed.stdout)
            usage = extract_usage(raw_payload)
        except OrchestratorError:
            raw_payload = None
        if completed.returncode != 0:
            record = TaskRunRecord(
                id=task.id,
                title=task.title,
                agent=task.agent,
                status="failed",
                summary=completed.stderr.strip() or completed.stdout.strip() or "Claude failed",
                workspace_mode=workspace_mode,
                workspace_path=str(prepared_workspace),
                started_at=started_at,
                finished_at=iso_now(),
                files_touched=[],
                actual_files_touched=[],
                protected_path_violations=[],
                validation_commands=[],
                follow_ups=["Inspect stderr/stdout artifacts for details."],
                notes=[],
                model=model_name,
                model_profile=model_profile,
                prompt_chars=prompt_chars,
                prompt_estimated_tokens=prompt_estimated_tokens,
                prompt_sections=prompt_render.sections,
                prompt_budget=prompt_budget,
                usage=usage,
                return_code=return_code,
                prompt_path=str(prompt_path),
                command_path=str(command_path),
                stdout_path=str(stdout_path),
                stderr_path=str(stderr_path),
                result_path=None,
            )
            apply_workspace_audit(record, reported_files=[], actual_files=actual_changed_files)
            write_json(result_path, asdict(record))
            return record

        if raw_payload is None:
            raise OrchestratorError("Claude output did not contain a standalone JSON payload")
        payload = coerce_worker_result(raw_payload)
        write_json(worker_result_path, payload)
        record = TaskRunRecord(
            id=task.id,
            title=task.title,
            agent=task.agent,
            status=str(payload["status"]),
            summary=str(payload["summary"]),
            workspace_mode=workspace_mode,
            workspace_path=str(prepared_workspace),
            started_at=started_at,
            finished_at=iso_now(),
            files_touched=[str(item) for item in payload["filesTouched"]],
            actual_files_touched=[],
            protected_path_violations=[],
            validation_commands=[str(item) for item in payload["validationCommands"]],
            follow_ups=[str(item) for item in payload["followUps"]],
            notes=[str(item) for item in payload["notes"]],
            model=model_name,
            model_profile=model_profile,
            prompt_chars=prompt_chars,
            prompt_estimated_tokens=prompt_estimated_tokens,
            prompt_sections=prompt_render.sections,
            prompt_budget=prompt_budget,
            usage=usage,
            return_code=return_code,
            prompt_path=str(prompt_path),
            command_path=str(command_path),
            stdout_path=str(stdout_path),
            stderr_path=str(stderr_path),
            result_path=str(worker_result_path),
        )
        apply_workspace_audit(
            record,
            reported_files=[str(item) for item in payload["filesTouched"]],
            actual_files=actual_changed_files,
        )
        write_json(result_path, asdict(record))
        return record
    except OrchestratorError as exc:
        write_text(stderr_path, str(exc))
        actual_changed_files = diff_workspace_snapshots(
            workspace_snapshot_before,
            snapshot_workspace_files(prepared_workspace),
        )
        record = TaskRunRecord(
            id=task.id,
            title=task.title,
            agent=task.agent,
            status="failed",
            summary=str(exc),
            workspace_mode=workspace_mode,
            workspace_path=str(prepared_workspace),
            started_at=started_at,
            finished_at=iso_now(),
            files_touched=[],
            actual_files_touched=[],
            protected_path_violations=[],
            validation_commands=[],
            follow_ups=["Retry after fixing the worker failure."],
            notes=[],
            model=model_name,
            model_profile=model_profile,
            prompt_chars=prompt_chars,
            prompt_estimated_tokens=prompt_estimated_tokens,
            prompt_sections=prompt_render.sections,
            prompt_budget=prompt_budget,
            usage=usage,
            return_code=return_code,
            prompt_path=str(prompt_path),
            command_path=str(command_path),
            stdout_path=str(stdout_path) if stdout_path.exists() else None,
            stderr_path=str(stderr_path),
            result_path=None,
        )
        apply_workspace_audit(record, reported_files=[], actual_files=actual_changed_files)
        write_json(result_path, asdict(record))
        return record


def manifest_payload(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, AgentDefinition],
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    records: dict[str, TaskRunRecord],
    *,
    dry_run: bool,
) -> dict[str, Any]:
    usage_totals = aggregate_usage(records)
    parallel_conflicts = detect_parallel_scope_conflicts(plan, agents)
    return {
        "runId": run_id,
        "generatedAt": iso_now(),
        "dryRun": dry_run,
        "repoRoot": str(ROOT),
        "planPath": str(plan_path),
        "agentsPath": str(agents_path),
        "runtimeRoot": str(runtime_root),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "plan": {
            "name": plan.name,
            "goal": plan.goal,
            "taskIds": [task.id for task in plan.tasks],
            "parallelConflicts": parallel_conflicts,
        },
        "usageTotals": usage_totals,
        "tasks": {task_id: asdict(record) for task_id, record in sorted(records.items())},
    }


def write_manifest(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, AgentDefinition],
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    records: dict[str, TaskRunRecord],
    *,
    dry_run: bool,
) -> None:
    write_json(
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
        ),
    )


def run_plan(args: argparse.Namespace) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    plan_path = Path(args.task_plan).resolve()
    agents = load_agents(agents_path)
    plan = selected_plan(load_task_plan(plan_path, agents), args.selected_tasks)
    topological_batches(plan.tasks)
    if not args.dry_run:
        ensure_claude_available(args.claude_bin)

    run_id = (
        f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"
        f"-{slugify(plan.name)}-{uuid4().hex[:8]}"
    )
    runtime_root = Path(args.runtime_root).resolve()
    run_dir = runtime_root / "runs" / run_id
    workspaces_dir = runtime_root / "workspaces" / run_id
    run_dir.mkdir(parents=True, exist_ok=True)
    workspaces_dir.mkdir(parents=True, exist_ok=True)
    write_json(
        run_dir / "selected-plan.json",
        {
            "version": plan.version,
            "name": plan.name,
            "goal": plan.goal,
            "sharedContext": asdict(plan.shared_context),
            "tasks": [
                {
                    "id": task.id,
                    "title": task.title,
                    "agent": task.agent,
                    "prompt": task.prompt,
                    "dependsOn": task.depends_on,
                    "files": task.files,
                    "constraints": task.constraints,
                    "validation": task.validation,
                    "workspaceMode": task.workspace_mode,
                    "model": task.model,
                    "modelProfile": task.model_profile,
                    "contextMode": task.context_mode,
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

    agents_json = agent_payload_for_claude(agents)
    records: dict[str, TaskRunRecord] = {}
    pending = {task.id: task for task in plan.tasks}
    fail_fast_triggered = False

    while pending:
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
                )
                pending.pop(task_id)
                newly_blocked = True
        if newly_blocked:
            write_manifest(
                run_id,
                plan_path,
                agents_path,
                agents,
                runtime_root,
                run_dir,
                workspaces_dir,
                plan,
                records,
                dry_run=args.dry_run,
            )
            continue

        if fail_fast_triggered:
            for task_id, task in list(pending.items()):
                records[task_id] = blocked_record(
                    task,
                    task.agent,
                    agents[task.agent],
                    effective_workspace_mode(task, agents[task.agent]),
                    reason="Coordinator stopped scheduling new tasks after a worker failure.",
                )
                pending.pop(task_id)
            break

        ready = [
            task
            for task in pending.values()
            if all(records.get(dependency_id) is not None for dependency_id in task.depends_on)
        ]
        if not ready:
            unresolved = ", ".join(sorted(pending))
            raise OrchestratorError(f"No schedulable tasks remain; unresolved tasks: {unresolved}")

        batch = select_parallel_ready_batch(
            plan,
            ready,
            agents,
            max_parallel=max(args.max_parallel, 1),
        )
        with ThreadPoolExecutor(max_workers=max(1, min(args.max_parallel, len(batch)))) as executor:
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
                    claude_bin=args.claude_bin,
                    agents_json=agents_json,
                    dry_run=args.dry_run,
                ): task
                for task in batch
            }
            for future in as_completed(future_map):
                task = future_map[future]
                records[task.id] = future.result()
                pending.pop(task.id, None)
                write_manifest(
                    run_id,
                    plan_path,
                    agents_path,
                    agents,
                    runtime_root,
                    run_dir,
                    workspaces_dir,
                    plan,
                    records,
                    dry_run=args.dry_run,
                )
                if not args.continue_on_error and records[task.id].status not in {"completed", "planned"}:
                    fail_fast_triggered = True

    write_manifest(
        run_id,
        plan_path,
        agents_path,
        agents,
        runtime_root,
        run_dir,
        workspaces_dir,
        plan,
        records,
        dry_run=args.dry_run,
    )
    status_counts: dict[str, int] = {}
    for record in records.values():
        status_counts[record.status] = status_counts.get(record.status, 0) + 1
    usage_totals = aggregate_usage(records)
    return {
        "runId": run_id,
        "plan": plan.name,
        "goal": plan.goal,
        "dryRun": args.dry_run,
        "runtimeRoot": str(runtime_root),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "statusCounts": status_counts,
        "usageTotals": usage_totals,
        "tasks": [asdict(records[task.id]) for task in plan.tasks],
    }


def validate_command(args: argparse.Namespace) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    agents = load_agents(agents_path)
    payload: dict[str, Any] = {
        "agentsPath": str(agents_path),
        "agentCount": len(agents),
        "agents": sorted(agents),
    }
    if args.task_plan:
        plan_path = Path(args.task_plan).resolve()
        plan = load_task_plan(plan_path, agents)
        payload.update(
            {
                "taskPlanPath": str(plan_path),
                "planName": plan.name,
                "taskCount": len(plan.tasks),
                "taskIds": [task.id for task in plan.tasks],
                "batches": [[task.id for task in batch] for batch in topological_batches(plan.tasks)],
                "parallelConflicts": detect_parallel_scope_conflicts(plan, agents),
            }
        )
    return payload


def print_payload(payload: dict[str, Any], *, as_json: bool) -> None:
    if as_json:
        print(json.dumps(payload, indent=2))
        return
    print(json.dumps(payload, indent=2))


def main() -> int:
    args = parse_args()
    try:
        if args.command == "validate":
            print_payload(validate_command(args), as_json=args.json)
            return 0
        if args.command == "plan":
            print_payload(plan_with_claude(args), as_json=args.json)
            return 0
        if args.command == "run":
            print_payload(run_plan(args), as_json=args.json)
            return 0
        raise OrchestratorError(f"Unknown command '{args.command}'")
    except OrchestratorError as exc:
        print(f"[claude-orchestrator] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
