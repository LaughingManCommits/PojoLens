#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
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
COPY_IGNORE_NAMES = {
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
    allowed_tools: list[str] = field(default_factory=list)
    disallowed_tools: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class TaskPlan:
    version: int
    name: str
    goal: str
    shared_context: SharedContext
    tasks: list[TaskDefinition]


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
    validation_commands: list[str]
    follow_ups: list[str]
    notes: list[str]
    model: str | None
    model_profile: str | None
    prompt_chars: int
    prompt_estimated_tokens: int
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


def repo_copy_ignore(directory: str, names: list[str], runtime_root: Path) -> set[str]:
    current_dir = Path(directory).resolve()
    resolved_runtime_root = runtime_root.resolve()
    indexes_dir = (ROOT / "ai" / "indexes").resolve()
    ignored = set()
    for name in names:
        candidate = (current_dir / name).resolve()
        if name in COPY_IGNORE_NAMES:
            ignored.add(name)
        elif current_dir == indexes_dir and (
            name.endswith(".db") or name.endswith(".sqlite") or name.endswith(".sqlite3")
        ):
            ignored.add(name)
        elif path_is_relative_to(resolved_runtime_root, candidate):
            ignored.add(name)
    return ignored


def hydrate_copy_workspace(workspace_path: Path, file_hints: list[str]) -> None:
    copied: set[Path] = set()
    for hint in file_hints:
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
        shutil.copytree(
            ROOT,
            workspace_path,
            ignore=lambda directory, names: repo_copy_ignore(directory, names, runtime_root),
        )
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
) -> str:
    agent_catalog = "\n".join(
        f"- `{agent.name}`: {agent.description}"
        for agent in agents.values()
        if agent.name != PLANNER_TASK_ID
    )
    file_lines = "\n".join(f"- `{item}`" for item in files) or "- none supplied"
    constraint_lines = "\n".join(f"- {item}" for item in constraints) or "- keep tasks bounded and concrete"
    validation_lines = "\n".join(f"- `{item}`" for item in validation) or "- none supplied"
    return textwrap.dedent(
        f"""\
        Plan a bounded Claude worker DAG for this repository.

        Goal:
        {goal}

        Plan name:
        {name}

        Available worker agents:
        {agent_catalog}

        File hints:
        {file_lines}

        Constraints:
        {constraint_lines}

        Validation hints:
        {validation_lines}

        Requirements:
        - Return JSON only, matching the provided schema.
        - Create 1 to 6 tasks.
        - Use `workspaceMode="copy"` for most tasks.
        - Use `workspaceMode="worktree"` only when a task clearly benefits from isolated git metadata.
        - Avoid `workspaceMode="repo"` unless direct in-place execution is essential.
        - Use `contextMode="minimal"` unless a task truly needs the full shared file and validation context.
        - Pick `modelProfile` deliberately to control cost:
          - `simple` -> `claude-haiku-4-5` for quick summaries, classification, or narrow lookups
          - `balanced` -> `claude-sonnet-4-6` for most coding, analysis, and implementation tasks
          - `complex` -> `claude-opus-4-6` for architecture or deeply nuanced multi-step reasoning
        - Keep file lists repo-relative and concrete.
        - Minimize per-task context; only include the files and validations each worker actually needs.
        - Do not assign `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*` edits to workers.
        - Put cross-task setup in `sharedContext`.
        - Use `dependsOn` only when one task genuinely needs another task's output.
        """
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
        lines.append(f"- `{dependency_id}` ({record.status}): {record.summary}")
    return "\n".join(lines)


def worker_prompt(
    plan: TaskPlan,
    task: TaskDefinition,
    agent: AgentDefinition,
    workspace_mode: str,
    workspace_path: Path,
    dependency_text: str,
) -> str:
    task_root = ROOT if workspace_mode == "repo" else workspace_path
    context_mode = effective_context_mode(task, agent)
    relevant_files = dedupe_strings(task.files or plan.shared_context.files)
    constraints = dedupe_strings(plan.shared_context.constraints + task.constraints)
    validation_hints = (
        dedupe_strings(task.validation)
        if context_mode == "minimal"
        else dedupe_strings(plan.shared_context.validation + task.validation)
    )
    file_lines = "\n".join(f"- `{item}`" for item in relevant_files) or "- none"
    constraint_lines = "\n".join(f"- {item}" for item in constraints) or "- none"
    validation_lines = "\n".join(f"- `{item}`" for item in validation_hints) or "- none"
    workspace_rule = {
        "copy": (
            "You are running in an isolated filesystem copy of the repo that includes the current working tree state. "
            "Edits are allowed inside this copy only."
        ),
        "repo": (
            "You are running directly in the live repo root. Treat this as high-risk and avoid incidental edits."
        ),
        "worktree": (
            "You are running in an isolated detached git worktree rooted at HEAD. Uncommitted root-repo changes are not present."
        ),
    }[workspace_mode]
    return textwrap.dedent(
        f"""\
        Task repo root: {task_root}
        Source repo root: {ROOT}
        Task workspace: {workspace_path}
        Workspace mode: {workspace_mode}
        Context mode: {context_mode}
        {workspace_rule}
        Use the task repo root for file and shell access. In copy/worktree mode, do not access files through the source repo root.

        Coordinator goal:
        {plan.goal}

        Shared context summary:
        {plan.shared_context.summary}

        Task id/title:
        {task.id} / {task.title}

        Task objective:
        {task.prompt}

        Relevant file hints:
        {file_lines}

        Constraints:
        {constraint_lines}

        Dependency outputs:
        {dependency_text}

        Validation hints:
        {validation_lines}

        Worker rules:
        - Follow repo instructions from AGENTS.md and ai/AGENTS.md when relevant.
        - Do not edit TODO.md, ai/state/*, ai/log/*, or ai/indexes/*.
        - Keep changes tightly scoped to the task.
        - Return JSON only, matching the provided schema.
        - If blocked, explain the blocker concretely and list the next coordinator action.
        """
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
        command.extend(["--allowed-tools", *allowed_tools])
    if disallowed_tools:
        command.extend(["--disallowed-tools", *disallowed_tools])
    if max_budget_usd is not None:
        command.extend(["--max-budget-usd", f"{max_budget_usd:.2f}"])
    command.append(prompt)
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
    prompt = planner_prompt(
        args.goal,
        args.name,
        args.files,
        args.constraints,
        args.validation,
        agents,
    )
    agent = agents[args.planner_agent]
    planner_model = agent.model or (MODEL_PROFILE_TO_MODEL[agent.model_profile] if agent.model_profile else None)
    agents_json = agent_payload_for_claude(agents)
    output_path = planner_output_path(args.name, args.out)
    command = claude_command(
        args.claude_bin,
        agents_json,
        args.planner_agent,
        prompt,
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
        "prompt": prompt,
        "model": planner_model,
        "modelProfile": agent.model_profile,
        "promptChars": len(prompt),
        "promptEstimatedTokens": estimate_tokens(prompt),
        "dryRun": args.dry_run,
    }
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
        validation_commands=[],
        follow_ups=[reason],
        notes=[],
        model=resolved_model(task, agent),
        model_profile=resolved_model_profile(task, agent),
        prompt_chars=0,
        prompt_estimated_tokens=0,
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
    prompt = worker_prompt(
        plan,
        task,
        agent,
        workspace_mode,
        prepared_workspace,
        dependency_summary(dependency_records, task),
    )
    prompt_chars = len(prompt)
    prompt_estimated_tokens = estimate_tokens(prompt)
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
            validation_commands=[],
            follow_ups=[],
            notes=[],
            model=model_name,
            model_profile=model_profile,
            prompt_chars=prompt_chars,
            prompt_estimated_tokens=prompt_estimated_tokens,
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
    try:
        completed = run_subprocess(
            command,
            cwd=prepared_workspace,
            timeout_sec=task.timeout_sec or agent.timeout_sec,
        )
        return_code = completed.returncode
        write_text(stdout_path, completed.stdout)
        write_text(stderr_path, completed.stderr)
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
                validation_commands=[],
                follow_ups=["Inspect stderr/stdout artifacts for details."],
                notes=[],
                model=model_name,
                model_profile=model_profile,
                prompt_chars=prompt_chars,
                prompt_estimated_tokens=prompt_estimated_tokens,
                usage=usage,
                return_code=return_code,
                prompt_path=str(prompt_path),
                command_path=str(command_path),
                stdout_path=str(stdout_path),
                stderr_path=str(stderr_path),
                result_path=None,
            )
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
            validation_commands=[str(item) for item in payload["validationCommands"]],
            follow_ups=[str(item) for item in payload["followUps"]],
            notes=[str(item) for item in payload["notes"]],
            model=model_name,
            model_profile=model_profile,
            prompt_chars=prompt_chars,
            prompt_estimated_tokens=prompt_estimated_tokens,
            usage=usage,
            return_code=return_code,
            prompt_path=str(prompt_path),
            command_path=str(command_path),
            stdout_path=str(stdout_path),
            stderr_path=str(stderr_path),
            result_path=str(worker_result_path),
        )
        write_json(result_path, asdict(record))
        return record
    except OrchestratorError as exc:
        write_text(stderr_path, str(exc))
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
            validation_commands=[],
            follow_ups=["Retry after fixing the worker failure."],
            notes=[],
            model=model_name,
            model_profile=model_profile,
            prompt_chars=prompt_chars,
            prompt_estimated_tokens=prompt_estimated_tokens,
            usage=usage,
            return_code=return_code,
            prompt_path=str(prompt_path),
            command_path=str(command_path),
            stdout_path=str(stdout_path) if stdout_path.exists() else None,
            stderr_path=str(stderr_path),
            result_path=None,
        )
        write_json(result_path, asdict(record))
        return record


def manifest_payload(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    records: dict[str, TaskRunRecord],
    *,
    dry_run: bool,
) -> dict[str, Any]:
    usage_totals = aggregate_usage(records)
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
        },
        "usageTotals": usage_totals,
        "tasks": {task_id: asdict(record) for task_id, record in sorted(records.items())},
    }


def write_manifest(
    run_id: str,
    plan_path: Path,
    agents_path: Path,
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

        batch = sorted(ready, key=lambda task: task.id)[: max(args.max_parallel, 1)]
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
