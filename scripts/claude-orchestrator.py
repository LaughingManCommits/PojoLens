#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import difflib
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import textwrap
import threading
import time
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
WORKER_VALIDATION_MODES = {"intents-only"}
LEGACY_WORKER_VALIDATION_MODES = {"compat", *WORKER_VALIDATION_MODES}
DEFAULT_WORKER_VALIDATION_MODE = "intents-only"
WORKER_VALIDATION_MODE_SOURCES = {"override", "task", "agent", "default"}
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
DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT = 2
DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT = 120
WORKER_STATUSES = {"completed", "blocked", "failed"}
DEFAULT_VALIDATE_RUN_STATUSES = ("completed",)
MAX_WORKER_SUMMARY_CHARS = 360
MAX_WORKER_NOTES = 5
MAX_WORKER_NOTE_CHARS = 220
MAX_WORKER_FOLLOW_UPS = 3
MAX_WORKER_FOLLOW_UP_CHARS = 180
MAX_WORKER_VALIDATION_COMMANDS = 2
MAX_WORKER_VALIDATION_COMMAND_CHARS = 320
MAX_WORKER_VALIDATION_INTENTS = 2
MAX_WORKER_VALIDATION_INTENT_ARG_CHARS = 180
VALIDATION_INTENT_KINDS = {"repo-script", "tool"}
VALIDATION_ALLOWED_EXECUTABLES = {
    "git",
    "java",
    "mvn",
    "mvnw",
    "mvnw.cmd",
    "node",
    "npm",
    "pnpm",
    "pwsh",
    "py",
    "pytest",
    "python",
    "python3",
    "yarn",
}
VALIDATION_ALLOWED_RELATIVE_PREFIXES = ("scripts/",)
VALIDATION_ALLOWED_SCRIPT_SUFFIXES = {".bat", ".cmd", ".ps1", ".py", ".sh"}
VALIDATION_SHELL_OPERATOR_TOKENS = {"&", "&&", "|", "||", ";", "<", ">", ">>"}
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
WORKER_UNKNOWNABLE_FIELDS = (
    "filesTouched",
    "followUps",
    "notes",
)
SLOP_PROGRESS_DOTS = (".", "..", "...")
SLOP_PROGRESS_INTERVAL_SEC = 1.0
SLOP_LOG_LOCK = threading.Lock()
WORKER_RESULT_SCHEMA = {
    "type": "object",
    "properties": {
        "status": {
            "type": "string",
            "enum": ["completed", "blocked", "failed"],
        },
        "summary": {"type": "string"},
        "filesTouched": {"type": ["array", "null"], "items": {"type": "string"}},
        "validationIntents": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "kind": {"type": "string", "enum": sorted(VALIDATION_INTENT_KINDS)},
                    "entrypoint": {"type": "string"},
                    "args": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["kind", "entrypoint"],
                "additionalProperties": False,
            },
        },
        "followUps": {"type": ["array", "null"], "items": {"type": "string"}},
        "notes": {"type": ["array", "null"], "items": {"type": "string"}},
    },
    "required": [
        "status",
        "summary",
        "filesTouched",
        "validationIntents",
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
                    "workerValidationMode": {
                        "type": "string",
                        "enum": sorted(WORKER_VALIDATION_MODES),
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
    worker_validation_mode: str | None = None
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
    worker_validation_mode: str | None = None
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


@dataclass(frozen=True)
class ValidationIntent:
    kind: str
    entrypoint: str
    args: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class WorkerValidationModeResolution:
    mode: str
    source: str


@dataclass(frozen=True)
class SlopLogAction:
    actor: str
    phase: str
    phrase: str
    reason: str


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
    validation_intents: list[ValidationIntent] = field(default_factory=list)
    unknown_fields: list[str] = field(default_factory=list)
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE
    worker_validation_mode_source: str | None = None


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
        "--worker-validation-mode",
        choices=sorted(WORKER_VALIDATION_MODES),
        default="",
        help="Override worker validation suggestion policy. Defaults to task or agent definitions, then 'intents-only'.",
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

    retry_parser = subparsers.add_parser(
        "retry",
        help="Retry failed or blocked tasks from a previous run manifest.",
    )
    retry_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    retry_parser.add_argument(
        "--agents",
        default="",
        help="Override the agents JSON path. Defaults to the original run manifest agentsPath.",
    )
    retry_parser.add_argument(
        "--claude-bin",
        default=DEFAULT_CLAUDE_BIN,
        help="Claude CLI executable to invoke.",
    )
    retry_parser.add_argument(
        "--runtime-root",
        default="",
        help="Override runtime root for the retry run. Defaults to the original run manifest runtimeRoot.",
    )
    retry_parser.add_argument(
        "--max-parallel",
        type=int,
        default=2,
        help="Maximum number of ready tasks to run concurrently.",
    )
    retry_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict retry to one or more task ids. Defaults to failed or blocked tasks.",
    )
    retry_parser.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Continue running independent tasks after a worker fails or reports blocked.",
    )
    retry_parser.add_argument(
        "--worker-validation-mode",
        choices=sorted(WORKER_VALIDATION_MODES),
        default="",
        help="Override worker validation suggestion policy for the retry run. Defaults to the source run mode or 'intents-only'.",
    )
    retry_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Create the retry run manifest and task requests without invoking Claude.",
    )
    retry_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the retry run summary as JSON.",
    )

    review_parser = subparsers.add_parser(
        "review",
        help="Summarize worker workspace diffs for a completed run.",
    )
    review_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    review_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict review to one or more task ids. Repeatable.",
    )
    review_parser.add_argument(
        "--context-lines",
        type=int,
        default=3,
        help="Context lines to use when generating per-file patch previews.",
    )
    review_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the review summary as JSON.",
    )

    export_patch_parser = subparsers.add_parser(
        "export-patch",
        help="Export a unified diff patch from worker workspace changes.",
    )
    export_patch_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    export_patch_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict patch export to one or more task ids. Repeatable.",
    )
    export_patch_parser.add_argument(
        "--context-lines",
        type=int,
        default=3,
        help="Context lines to use in the exported unified diff.",
    )
    export_patch_parser.add_argument(
        "--out",
        default="",
        help="Destination patch path. Defaults under the run directory review/ subfolder.",
    )
    export_patch_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the export summary as JSON.",
    )

    promote_parser = subparsers.add_parser(
        "promote",
        help="Apply reviewed worker workspace changes back into the live repo.",
    )
    promote_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    promote_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict promotion to one or more task ids. Repeatable.",
    )
    promote_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Summarize promotable changes without applying them.",
    )
    promote_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the promotion summary as JSON.",
    )

    cleanup_parser = subparsers.add_parser(
        "cleanup",
        help="Remove runtime artifacts for a completed or abandoned run.",
    )
    cleanup_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    cleanup_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the cleanup summary as JSON.",
    )

    validate_run_parser = subparsers.add_parser(
        "validate-run",
        help="Consolidate or execute worker-suggested validation commands for a run.",
    )
    validate_run_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    validate_run_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict validation aggregation to one or more task ids. Repeatable.",
    )
    validate_run_parser.add_argument(
        "--include-status",
        dest="include_statuses",
        action="append",
        choices=sorted(WORKER_STATUSES | {"planned"}),
        default=[],
        help="Include validation commands from tasks with this status. Defaults to completed only. Repeatable.",
    )
    validate_run_parser.add_argument(
        "--allow-unsafe-commands",
        action="store_true",
        help="Execute worker-suggested validation commands even when they fail the coordinator quality policy.",
    )
    validate_run_parser.add_argument(
        "--intents-only",
        action="store_true",
        help="Reject raw legacy validationCommands and accept only worker-emitted structured validationIntents.",
    )
    validate_run_parser.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Continue running remaining validation commands after a failure.",
    )
    validate_run_parser.add_argument(
        "--timeout-sec",
        type=int,
        default=DEFAULT_TASK_TIMEOUT_SEC,
        help="Timeout in seconds for each validation command.",
    )
    validate_run_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Summarize validation commands without executing them.",
    )
    validate_run_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the validation summary as JSON.",
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


def read_bytes(path: Path) -> bytes:
    return path.read_bytes()


def file_sha256(path: Path) -> str:
    return hashlib.sha256(read_bytes(path)).hexdigest()


def normalize_relative_path(path_value: str, *, location: str) -> str:
    candidate = Path(path_value)
    if candidate.is_absolute():
        raise OrchestratorError(f"{location}: absolute paths are not allowed: {path_value}")
    parts: list[str] = []
    for part in candidate.parts:
        if part in {"", "."}:
            continue
        if part == "..":
            raise OrchestratorError(
                f"{location}: parent-directory traversal is not allowed: {path_value}"
            )
        parts.append(part)
    if not parts:
        raise OrchestratorError(f"{location}: expected non-empty relative path")
    return Path(*parts).as_posix()


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
        worker_validation_mode = require_optional_string(
            definition,
            "workerValidationMode",
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
            worker_validation_mode=normalize_worker_validation_mode(
                worker_validation_mode,
                location=f"{location}:workerValidationMode",
            )
            if worker_validation_mode is not None
            else None,
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
        worker_validation_mode = require_optional_string(
            task_payload,
            "workerValidationMode",
            location=location,
        )
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
            worker_validation_mode=normalize_worker_validation_mode(
                worker_validation_mode,
                location=f"{location}:workerValidationMode",
            )
            if worker_validation_mode is not None
            else None,
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


def resolve_relative_path(root: Path, relative_path: str, *, location: str) -> tuple[str, Path]:
    normalized = normalize_relative_path(relative_path, location=location)
    root_resolved = root.resolve()
    candidate = (root / normalized).resolve()
    if not path_is_relative_to(candidate, root_resolved):
        raise OrchestratorError(f"{location}: path escapes root '{root_resolved}'")
    return normalized, candidate


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
    action = workspace_prep_action(task, workspace_mode)
    if action is not None:
        emit_slop_log(action)
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
        normalized = normalize_relative_path(path, location=f"changed path '{path}'")
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
        if worker_field_unknown(record, "filesTouched"):
            record.notes.append(
                "Workspace diff resolved worker-unknown `filesTouched`: "
                f"{summarize_paths(actual_only)}"
            )
        else:
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


def resolve_manifest_path(run_ref: str) -> Path:
    candidate = Path(run_ref).resolve()
    if candidate.is_dir():
        candidate = candidate / "manifest.json"
    if not candidate.exists():
        raise OrchestratorError(f"Run manifest '{candidate}' does not exist")
    if candidate.name != "manifest.json":
        raise OrchestratorError("Review/export/promote expects a run directory or manifest.json path")
    return candidate


def load_run_manifest(run_ref: str) -> tuple[Path, dict[str, Any]]:
    manifest_path = resolve_manifest_path(run_ref)
    payload = read_json(manifest_path)
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{manifest_path}: expected JSON object")
    tasks = payload.get("tasks")
    if not isinstance(tasks, dict):
        raise OrchestratorError(f"{manifest_path}: expected object 'tasks'")
    return manifest_path, payload


def manifest_required_path(manifest: dict[str, Any], key: str, *, location: str) -> Path:
    value = str(manifest.get(key, "")).strip()
    if not value:
        raise OrchestratorError(f"{location}: run manifest is missing '{key}'")
    return Path(value).resolve()


def manifest_run_dir(manifest_path: Path, manifest: dict[str, Any]) -> Path:
    value = str(manifest.get("runDir", "")).strip()
    return Path(value).resolve() if value else manifest_path.parent.resolve()


def manifest_workspaces_dir(manifest: dict[str, Any], *, run_dir: Path) -> Path:
    value = str(manifest.get("workspacesDir", "")).strip()
    if value:
        return Path(value).resolve()
    run_id = str(manifest.get("runId", "")).strip()
    if not run_id:
        raise OrchestratorError("Run manifest is missing 'workspacesDir' and 'runId'")
    return (run_dir.parent.parent / "workspaces" / run_id).resolve()


def count_statuses(values: list[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    return counts


def coerce_task_run_record(payload: Any, *, location: str) -> TaskRunRecord:
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: expected task record object")
    return TaskRunRecord(
        id=str(payload.get("id", "")),
        title=str(payload.get("title", "")),
        agent=str(payload.get("agent", "")),
        status=str(payload.get("status", "")),
        summary=str(payload.get("summary", "")),
        workspace_mode=str(payload.get("workspace_mode", "")),
        workspace_path=str(payload.get("workspace_path", "")),
        started_at=str(payload.get("started_at", "")),
        finished_at=str(payload.get("finished_at", "")),
        files_touched=[str(item) for item in payload.get("files_touched", []) or []],
        actual_files_touched=[str(item) for item in payload.get("actual_files_touched", []) or []],
        protected_path_violations=[
            str(item) for item in payload.get("protected_path_violations", []) or []
        ],
        validation_commands=[str(item) for item in payload.get("validation_commands", []) or []],
        follow_ups=[str(item) for item in payload.get("follow_ups", []) or []],
        notes=[str(item) for item in payload.get("notes", []) or []],
        model=str(payload["model"]) if payload.get("model") is not None else None,
        model_profile=str(payload["model_profile"]) if payload.get("model_profile") is not None else None,
        prompt_chars=int(payload.get("prompt_chars", 0) or 0),
        prompt_estimated_tokens=int(payload.get("prompt_estimated_tokens", 0) or 0),
        prompt_sections=[
            PromptSectionMetric(
                name=str(section.get("name", "")),
                heading=str(section.get("heading", "")),
                chars=int(section.get("chars", 0) or 0),
                estimated_tokens=int(section.get("estimated_tokens", 0) or 0),
                item_count=int(section.get("item_count", 0) or 0),
                truncated=bool(section.get("truncated", False)),
            )
            for section in payload.get("prompt_sections", []) or []
            if isinstance(section, dict)
        ],
        prompt_budget=PromptBudgetResult(
            max_chars=(
                int(payload.get("prompt_budget", {}).get("max_chars"))
                if isinstance(payload.get("prompt_budget"), dict)
                and payload.get("prompt_budget", {}).get("max_chars") is not None
                else None
            ),
            max_estimated_tokens=(
                int(payload.get("prompt_budget", {}).get("max_estimated_tokens"))
                if isinstance(payload.get("prompt_budget"), dict)
                and payload.get("prompt_budget", {}).get("max_estimated_tokens") is not None
                else None
            ),
            exceeded=bool(
                payload.get("prompt_budget", {}).get("exceeded", False)
                if isinstance(payload.get("prompt_budget"), dict)
                else False
            ),
            violations=[
                str(item)
                for item in (
                    payload.get("prompt_budget", {}).get("violations", [])
                    if isinstance(payload.get("prompt_budget"), dict)
                    else []
                )
            ],
        ),
        usage=payload.get("usage") if isinstance(payload.get("usage"), dict) else None,
        return_code=int(payload["return_code"]) if payload.get("return_code") is not None else None,
        prompt_path=str(payload.get("prompt_path", "")),
        command_path=str(payload.get("command_path", "")),
        stdout_path=str(payload["stdout_path"]) if payload.get("stdout_path") is not None else None,
        stderr_path=str(payload["stderr_path"]) if payload.get("stderr_path") is not None else None,
        result_path=str(payload["result_path"]) if payload.get("result_path") is not None else None,
        validation_intents=[
            coerce_validation_intent_payload(item, location=f"{location}:validation_intents")
            for item in payload.get("validation_intents", []) or []
        ],
        unknown_fields=normalized_worker_unknown_fields(payload.get("unknown_fields")),
        worker_validation_mode=normalize_worker_validation_mode(
            payload.get("worker_validation_mode"),
            location=f"{location}:worker_validation_mode",
            allow_legacy_compat=True,
        ),
        worker_validation_mode_source=normalize_worker_validation_mode_source(
            payload.get("worker_validation_mode_source"),
            location=f"{location}:worker_validation_mode_source",
        ),
    )


def selected_run_records(
    manifest: dict[str, Any],
    selected_ids: list[str],
) -> list[TaskRunRecord]:
    raw_tasks = manifest.get("tasks", {})
    if not isinstance(raw_tasks, dict):
        raise OrchestratorError("Manifest does not contain task records")
    selected = set(selected_ids)
    records = []
    for task_id in sorted(raw_tasks):
        if selected and task_id not in selected:
            continue
        records.append(coerce_task_run_record(raw_tasks[task_id], location=f"manifest:tasks[{task_id}]"))
    missing = sorted(selected.difference(record.id for record in records))
    if missing:
        raise OrchestratorError(f"Unknown task ids in run manifest: {missing}")
    return records


def decode_text_or_none(content: bytes) -> str | None:
    try:
        return content.decode("utf-8")
    except UnicodeDecodeError:
        return None


def diff_file_against_workspace(
    record: TaskRunRecord,
    relative_path: str,
    *,
    context_lines: int,
) -> tuple[dict[str, Any], str | None]:
    normalized, repo_file = resolve_relative_path(
        ROOT,
        relative_path,
        location=f"{record.id}: review path",
    )
    summary: dict[str, Any] = {
        "path": normalized,
        "status": "unchanged",
        "isBinary": False,
        "addedLines": 0,
        "removedLines": 0,
        "patchable": False,
    }
    if record.workspace_mode == "repo":
        summary["status"] = "unsupported"
        summary["reason"] = "repo-mode runs do not preserve an isolated review baseline"
        return summary, None
    if not record.workspace_path:
        raise OrchestratorError(f"{record.id}: workspace path missing for reviewable task output")
    workspace_root = Path(record.workspace_path)
    if not workspace_root.exists():
        raise OrchestratorError(
            f"{record.id}: workspace path '{workspace_root}' does not exist for review/export"
        )
    _, workspace_file = resolve_relative_path(
        workspace_root,
        normalized,
        location=f"{record.id}: workspace review path",
    )
    repo_exists = repo_file.exists()
    workspace_exists = workspace_file.exists()
    if not repo_exists and not workspace_exists:
        summary["status"] = "missing"
        return summary, None
    repo_bytes = read_bytes(repo_file) if repo_exists else b""
    workspace_bytes = read_bytes(workspace_file) if workspace_exists else b""
    if repo_exists and workspace_exists and repo_bytes == workspace_bytes:
        return summary, None
    if not repo_exists and workspace_exists:
        summary["status"] = "added"
    elif repo_exists and not workspace_exists:
        summary["status"] = "deleted"
    else:
        summary["status"] = "modified"
    repo_text = decode_text_or_none(repo_bytes) if repo_exists else ""
    workspace_text = decode_text_or_none(workspace_bytes) if workspace_exists else ""
    if (repo_exists and repo_text is None) or (workspace_exists and workspace_text is None):
        summary["isBinary"] = True
        return summary, None
    diff_lines = list(
        difflib.unified_diff(
            repo_text.splitlines(),
            workspace_text.splitlines(),
            fromfile=f"a/{normalized}",
            tofile=f"b/{normalized}",
            n=max(context_lines, 0),
            lineterm="",
        )
    )
    added_lines = 0
    removed_lines = 0
    for line in diff_lines:
        if line.startswith(("+++", "---", "@@")):
            continue
        if line.startswith("+"):
            added_lines += 1
        elif line.startswith("-"):
            removed_lines += 1
    summary["addedLines"] = added_lines
    summary["removedLines"] = removed_lines
    summary["patchable"] = True
    return summary, ("\n".join(diff_lines) + "\n") if diff_lines else None


def task_review_summary(record: TaskRunRecord, *, context_lines: int) -> tuple[dict[str, Any], list[str]]:
    reviewed_paths = dedupe_strings(record.actual_files_touched or record.files_touched)
    file_summaries: list[dict[str, Any]] = []
    patch_chunks: list[str] = []
    changed_files = 0
    binary_files = 0
    added_lines = 0
    removed_lines = 0
    for relative_path in reviewed_paths:
        summary, patch_text = diff_file_against_workspace(
            record,
            relative_path,
            context_lines=context_lines,
        )
        if summary["status"] not in {"unchanged", "missing"}:
            changed_files += 1
        if summary.get("isBinary"):
            binary_files += 1
        added_lines += int(summary.get("addedLines", 0) or 0)
        removed_lines += int(summary.get("removedLines", 0) or 0)
        file_summaries.append(summary)
        if patch_text:
            patch_chunks.append(patch_text)
    return (
        {
            "id": record.id,
            "title": record.title,
            "agent": record.agent,
            "status": record.status,
            "summary": record.summary,
            "workspaceMode": record.workspace_mode,
            "workspacePath": record.workspace_path,
            "protectedPathViolations": record.protected_path_violations,
            "unknownFields": record.unknown_fields,
            "filesReported": record.files_touched,
            "filesObserved": record.actual_files_touched,
            "diffStats": {
                "filesReviewed": len(reviewed_paths),
                "filesChanged": changed_files,
                "binaryFiles": binary_files,
                "addedLines": added_lines,
                "removedLines": removed_lines,
            },
            "files": file_summaries,
        },
        patch_chunks,
    )


def default_patch_output_path(manifest_path: Path, task_ids: list[str]) -> Path:
    review_dir = manifest_path.parent / "review"
    if not task_ids:
        filename = "combined.patch"
    elif len(task_ids) == 1:
        filename = f"{slugify(task_ids[0])}.patch"
    else:
        filename = f"selected-{slugify('-'.join(task_ids))}.patch"
    return review_dir / filename


def review_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    task_payloads = []
    for record in records:
        review_payload, _ = task_review_summary(record, context_lines=args.context_lines)
        task_payloads.append(review_payload)
    return {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "runDir": str(manifest_path.parent),
        "taskCount": len(task_payloads),
        "tasks": task_payloads,
    }


def export_patch(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    patch_chunks: list[str] = []
    exported_task_ids: list[str] = []
    binary_files: list[str] = []
    changed_files = 0
    for record in records:
        review_payload, task_patches = task_review_summary(record, context_lines=args.context_lines)
        if any(file_payload.get("isBinary") for file_payload in review_payload["files"]):
            binary_files.extend(
                file_payload["path"]
                for file_payload in review_payload["files"]
                if file_payload.get("isBinary")
            )
        if review_payload["diffStats"]["filesChanged"]:
            exported_task_ids.append(record.id)
        changed_files += int(review_payload["diffStats"]["filesChanged"])
        patch_chunks.extend(task_patches)
    output_path = Path(args.out).resolve() if args.out else default_patch_output_path(
        manifest_path,
        exported_task_ids or [record.id for record in records],
    )
    write_text(output_path, "".join(patch_chunks))
    return {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "patchPath": str(output_path),
        "taskIds": exported_task_ids or [record.id for record in records],
        "filesChanged": changed_files,
        "binaryFilesSkipped": dedupe_strings(binary_files),
        "patchBytes": output_path.stat().st_size if output_path.exists() else 0,
    }


def format_issue_block(header: str, issues: list[str]) -> str:
    visible = dedupe_strings(issues)
    return header if not visible else f"{header}:\n- " + "\n- ".join(visible)


def task_promotion_operations(record: TaskRunRecord) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    review_payload, _ = task_review_summary(record, context_lines=0)
    unsupported_paths: list[str] = []
    operations: list[dict[str, Any]] = []
    for file_payload in review_payload["files"]:
        status = str(file_payload.get("status", ""))
        if status == "unsupported":
            unsupported_paths.append(str(file_payload.get("path", "")))
            continue
        if status not in {"added", "modified", "deleted"}:
            continue
        operations.append(
            {
                "taskId": record.id,
                "path": str(file_payload["path"]),
                "action": status,
                "isBinary": bool(file_payload.get("isBinary", False)),
            }
        )
    return (
        {
            "id": record.id,
            "title": record.title,
            "agent": record.agent,
            "status": record.status,
            "summary": record.summary,
            "workspaceMode": record.workspace_mode,
            "workspacePath": record.workspace_path,
            "protectedPathViolations": record.protected_path_violations,
            "filesPromotable": len(operations),
            "unsupportedFiles": unsupported_paths,
            "operations": operations,
        },
        operations,
    )


def plan_promotion(records: list[TaskRunRecord]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, int]]:
    issues: list[str] = []
    task_payloads: list[dict[str, Any]] = []
    all_operations: list[dict[str, Any]] = []
    owners_by_path: dict[str, str] = {}
    counts = {"added": 0, "modified": 0, "deleted": 0}
    for record in records:
        task_payload, operations = task_promotion_operations(record)
        task_payloads.append(task_payload)
        if record.protected_path_violations:
            issues.append(
                f"{record.id}: protected-path violations must be reviewed manually: "
                f"{summarize_paths(record.protected_path_violations)}"
            )
        if task_payload["unsupportedFiles"]:
            issues.append(
                f"{record.id}: workspaceMode='{record.workspace_mode}' cannot be promoted for "
                f"{summarize_paths(task_payload['unsupportedFiles'])}"
            )
        if operations and record.status != "completed":
            issues.append(f"{record.id}: only completed tasks can be promoted, found status '{record.status}'")
        if operations and record.workspace_mode not in {"copy", "worktree"}:
            issues.append(
                f"{record.id}: workspaceMode='{record.workspace_mode}' is not promotable; use copy or worktree"
            )
        for operation in operations:
            owner = owners_by_path.get(operation["path"])
            if owner is not None and owner != record.id:
                issues.append(
                    f"{record.id}: '{operation['path']}' is also changed by task '{owner}'"
                )
            else:
                owners_by_path[operation["path"]] = record.id
            counts[str(operation["action"])] += 1
            all_operations.append(operation)
    if issues:
        raise OrchestratorError(format_issue_block("Promotion blocked", issues))
    return task_payloads, all_operations, counts


def apply_promotion_operation(record: TaskRunRecord, operation: dict[str, Any]) -> None:
    normalized, repo_file = resolve_relative_path(
        ROOT,
        str(operation["path"]),
        location=f"{record.id}: promote path",
    )
    action = str(operation["action"])
    if action == "deleted":
        if repo_file.exists():
            repo_file.unlink()
        return
    if not record.workspace_path:
        raise OrchestratorError(f"{record.id}: workspace path missing for promotion")
    workspace_root = Path(record.workspace_path)
    if not workspace_root.exists():
        raise OrchestratorError(
            f"{record.id}: workspace path '{workspace_root}' does not exist for promotion"
        )
    _, workspace_file = resolve_relative_path(
        workspace_root,
        normalized,
        location=f"{record.id}: promote workspace path",
    )
    if not workspace_file.exists() or not workspace_file.is_file():
        raise OrchestratorError(
            f"{record.id}: expected workspace file '{normalized}' for promotion"
        )
    repo_file.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(workspace_file, repo_file)


def promote_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    task_payloads, operations, counts = plan_promotion(records)
    promotable_task_ids = [payload["id"] for payload in task_payloads if payload["filesPromotable"]]
    if not args.dry_run:
        records_by_id = {record.id: record for record in records}
        for operation in operations:
            apply_promotion_operation(records_by_id[str(operation["taskId"])], operation)
    return {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "repoRoot": str(ROOT),
        "dryRun": args.dry_run,
        "taskCount": len(task_payloads),
        "taskIds": [record.id for record in records],
        "promotableTaskIds": promotable_task_ids,
        "promotedTaskIds": [] if args.dry_run else promotable_task_ids,
        "filesPromotable": len(operations),
        "filesPromoted": 0 if args.dry_run else len(operations),
        "operationCounts": counts,
        "tasks": task_payloads,
    }


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
            "Live worker validation suggestions are structured-intent-only; do not plan around raw worker `validationCommands`.",
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


def dependency_handoff(record: TaskRunRecord) -> str:
    summary, _ = truncate_text(record.summary, DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT)
    parts = [f"`{record.id}` ({record.status}): {summary}"]
    notes = dedupe_strings(record.notes)
    if notes:
        visible_notes: list[str] = []
        for note in notes[:DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT]:
            note_text, _ = truncate_text(note, DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT)
            visible_notes.append(note_text)
        hidden_note_count = len(notes) - len(visible_notes)
        if hidden_note_count > 0:
            visible_notes.append(f"... ({hidden_note_count} more notes omitted)")
        parts.append("key notes: " + " ; ".join(visible_notes))
    elif worker_field_unknown(record, "notes"):
        parts.append("key notes: unknown")
    follow_ups = dedupe_strings(record.follow_ups)
    if follow_ups and not notes:
        visible_follow_ups: list[str] = []
        for follow_up in follow_ups[:1]:
            follow_up_text, _ = truncate_text(follow_up, DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT)
            visible_follow_ups.append(follow_up_text)
        hidden_follow_up_count = len(follow_ups) - len(visible_follow_ups)
        if hidden_follow_up_count > 0:
            visible_follow_ups.append(f"... ({hidden_follow_up_count} more follow-ups omitted)")
        parts.append("next: " + " ; ".join(visible_follow_ups))
    elif worker_field_unknown(record, "followUps"):
        parts.append("next: unknown")
    return " | ".join(parts)


def dependency_summary(records: dict[str, TaskRunRecord], task: TaskDefinition) -> str:
    if not task.depends_on:
        return "- none"
    lines = []
    for dependency_id in task.depends_on:
        lines.append(dependency_handoff(records[dependency_id]))
    rendered, _, _ = format_bullet_list(lines, empty_line="- none", max_chars=None)
    return rendered


def truncate_command_text(text: str, max_chars: int) -> tuple[str, bool]:
    stripped = text.strip()
    if len(stripped) <= max_chars:
        return stripped, False
    if max_chars <= 3:
        return stripped[:max_chars], True
    return stripped[: max_chars - 3].rstrip() + "...", True


def strip_optional_quotes(token: str) -> str:
    if len(token) >= 2 and token[0] == token[-1] and token[0] in {"'", '"'}:
        return token[1:-1]
    return token


def contains_unquoted_shell_operator(text: str) -> bool:
    in_single = False
    in_double = False
    index = 0
    while index < len(text):
        char = text[index]
        if char == "'" and not in_double:
            in_single = not in_single
            index += 1
            continue
        if char == '"' and not in_single:
            in_double = not in_double
            index += 1
            continue
        if not in_single and not in_double:
            if text.startswith(("&&", "||", ">>"), index):
                return True
            if char in {"|", "&", ";", "<", ">"}:
                return True
        index += 1
    return False


def normalize_worker_text_list(
    payload: Any,
    *,
    key: str,
    max_items: int,
    max_chars: int,
    compact: bool,
) -> tuple[list[str], bool]:
    if payload is None:
        return [], False
    if not isinstance(payload, list):
        raise OrchestratorError(f"Claude JSON output field '{key}' must be an array or null")
    normalized: list[str] = []
    for item in payload:
        if not isinstance(item, str):
            raise OrchestratorError(f"Claude JSON output field '{key}' must contain only strings")
        text = item.strip()
        if not text:
            continue
        if compact:
            text, _ = truncate_text(text, max_chars)
        else:
            text, _ = truncate_command_text(text, max_chars)
        normalized.append(text)
        if len(normalized) >= max_items:
            break
    return dedupe_strings(normalized), True


def normalize_worker_files_touched(payload: Any) -> tuple[list[str], bool]:
    if payload is None:
        return [], False
    if not isinstance(payload, list):
        raise OrchestratorError("Claude JSON output field 'filesTouched' must be an array or null")
    normalized: list[str] = []
    for item in payload:
        if not isinstance(item, str):
            raise OrchestratorError("Claude JSON output field 'filesTouched' must contain only strings")
        text = item.strip()
        if not text:
            continue
        normalized.append(normalize_relative_path(text, location="worker result filesTouched"))
    return dedupe_strings(normalized), True


def normalized_worker_unknown_fields(payload: Any) -> list[str]:
    if not isinstance(payload, list):
        return []
    return [field_name for field_name in WORKER_UNKNOWNABLE_FIELDS if field_name in payload]


def worker_field_unknown(record: TaskRunRecord, field_name: str) -> bool:
    return field_name in record.unknown_fields


def analyze_validation_command(command_text: str) -> dict[str, Any]:
    stripped = command_text.strip()
    if not stripped:
        return {
            "accepted": False,
            "reason": "empty command",
            "entrypoint": None,
            "intent": None,
        }
    if "\n" in stripped or "\r" in stripped:
        return {
            "accepted": False,
            "reason": "multi-line commands are not allowed",
            "entrypoint": None,
            "intent": None,
        }
    if contains_unquoted_shell_operator(stripped):
        return {
            "accepted": False,
            "reason": "shell composition is not allowed; use a direct tool or script invocation",
            "entrypoint": None,
            "intent": None,
        }
    try:
        tokens = shlex.split(stripped, posix=False)
    except ValueError as exc:
        return {
            "accepted": False,
            "reason": f"unable to parse command text: {exc}",
            "entrypoint": None,
            "intent": None,
        }
    if not tokens:
        return {
            "accepted": False,
            "reason": "empty command",
            "entrypoint": None,
            "intent": None,
        }
    cleaned_tokens = [strip_optional_quotes(token) for token in tokens]
    entrypoint = cleaned_tokens[0]
    args = [token for token in cleaned_tokens[1:] if token]
    entry_path = Path(entrypoint)
    if entry_path.is_absolute():
        suffix = entry_path.suffix.lower()
        if suffix == ".exe" or entry_path.name.lower() in VALIDATION_ALLOWED_EXECUTABLES:
            return {
                "accepted": True,
                "reason": None,
                "entrypoint": entrypoint,
                "intent": asdict(ValidationIntent(kind="tool", entrypoint=entrypoint, args=args)),
            }
        return {
            "accepted": False,
            "reason": "absolute entrypoint is not an approved executable",
            "entrypoint": entrypoint,
            "intent": None,
        }
    lowered_entrypoint = entrypoint.lower()
    if lowered_entrypoint in VALIDATION_ALLOWED_EXECUTABLES:
        return {
            "accepted": True,
            "reason": None,
            "entrypoint": entrypoint,
            "intent": asdict(ValidationIntent(kind="tool", entrypoint=entrypoint, args=args)),
        }
    try:
        normalized = normalize_relative_path(entrypoint, location="validation command entrypoint")
    except OrchestratorError as exc:
        return {
            "accepted": False,
            "reason": str(exc),
            "entrypoint": entrypoint,
            "intent": None,
        }
    suffix = Path(normalized).suffix.lower()
    if suffix in VALIDATION_ALLOWED_SCRIPT_SUFFIXES and (
        normalized in {"mvnw", "mvnw.cmd"} or normalized.startswith(VALIDATION_ALLOWED_RELATIVE_PREFIXES)
    ):
        return {
            "accepted": True,
            "reason": None,
            "entrypoint": normalized,
            "intent": asdict(ValidationIntent(kind="repo-script", entrypoint=normalized, args=args)),
        }
    return {
        "accepted": False,
        "reason": "entrypoint is not an approved repo script or executable",
        "entrypoint": entrypoint,
        "intent": None,
    }


def coerce_validation_intent_payload(payload: Any, *, location: str) -> ValidationIntent:
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: expected validation intent object")
    extra_keys = sorted(set(payload) - {"kind", "entrypoint", "args"})
    if extra_keys:
        raise OrchestratorError(f"{location}: unsupported validation intent fields {extra_keys}")
    kind = payload.get("kind")
    if not isinstance(kind, str) or kind not in VALIDATION_INTENT_KINDS:
        raise OrchestratorError(
            f"{location}: field 'kind' must be one of {sorted(VALIDATION_INTENT_KINDS)}"
        )
    entrypoint_value = payload.get("entrypoint")
    if not isinstance(entrypoint_value, str) or not entrypoint_value.strip():
        raise OrchestratorError(f"{location}: field 'entrypoint' must be a non-empty string")
    cleaned_entrypoint = strip_optional_quotes(entrypoint_value.strip())
    if kind == "repo-script":
        cleaned_entrypoint = normalize_relative_path(
            cleaned_entrypoint,
            location=f"{location}:entrypoint",
        )
    args_payload = payload.get("args", [])
    if args_payload is None:
        args_payload = []
    if not isinstance(args_payload, list):
        raise OrchestratorError(f"{location}: field 'args' must be an array when supplied")
    args: list[str] = []
    for index, item in enumerate(args_payload, start=1):
        if not isinstance(item, str):
            raise OrchestratorError(f"{location}: field 'args[{index}]' must be a string")
        text = strip_optional_quotes(item.strip())
        if not text:
            continue
        text, _ = truncate_text(text, MAX_WORKER_VALIDATION_INTENT_ARG_CHARS)
        args.append(text)
    return ValidationIntent(kind=kind, entrypoint=cleaned_entrypoint, args=args)


def normalize_worker_validation_intents(payload: Any) -> list[ValidationIntent]:
    if payload is None:
        return []
    if not isinstance(payload, list):
        raise OrchestratorError("Claude JSON output field 'validationIntents' must be an array")
    intents: list[ValidationIntent] = []
    seen: set[tuple[str, str, tuple[str, ...]]] = set()
    for index, item in enumerate(payload, start=1):
        intent = coerce_validation_intent_payload(
            item,
            location=f"Claude JSON output field 'validationIntents[{index}]'",
        )
        key = (intent.kind, intent.entrypoint, tuple(intent.args))
        if key in seen:
            continue
        seen.add(key)
        intents.append(intent)
        if len(intents) >= MAX_WORKER_VALIDATION_INTENTS:
            break
    return intents


def validation_intent_policy(intent: ValidationIntent) -> dict[str, Any]:
    if intent.kind == "repo-script":
        suffix = Path(intent.entrypoint).suffix.lower()
        if suffix in VALIDATION_ALLOWED_SCRIPT_SUFFIXES and (
            intent.entrypoint in {"mvnw", "mvnw.cmd"} or intent.entrypoint.startswith(VALIDATION_ALLOWED_RELATIVE_PREFIXES)
        ):
            return {
                "accepted": True,
                "reason": None,
                "entrypoint": intent.entrypoint,
            }
        return {
            "accepted": False,
            "reason": "repo-script intents must point at an approved repo-local script or wrapper",
            "entrypoint": intent.entrypoint,
        }
    entrypoint = intent.entrypoint
    entry_path = Path(entrypoint)
    if entry_path.is_absolute():
        suffix = entry_path.suffix.lower()
        if suffix == ".exe" or entry_path.name.lower() in VALIDATION_ALLOWED_EXECUTABLES:
            return {
                "accepted": True,
                "reason": None,
                "entrypoint": entrypoint,
            }
        return {
            "accepted": False,
            "reason": "absolute tool entrypoint is not an approved executable",
            "entrypoint": entrypoint,
        }
    lowered_entrypoint = entrypoint.lower()
    if lowered_entrypoint in VALIDATION_ALLOWED_EXECUTABLES:
        return {
            "accepted": True,
            "reason": None,
            "entrypoint": entrypoint,
        }
    return {
        "accepted": False,
        "reason": "tool intent entrypoint is not an approved executable",
        "entrypoint": entrypoint,
    }


def render_command_tokens(tokens: list[str]) -> str:
    if os.name == "nt":
        return subprocess.list2cmdline(tokens)
    return shlex.join(tokens)


def validation_intent_command_text(intent: ValidationIntent) -> str:
    return render_command_tokens([intent.entrypoint, *intent.args])


def validation_intent_execution_tokens(intent: ValidationIntent) -> list[str]:
    if intent.kind == "tool":
        return [intent.entrypoint, *intent.args]
    absolute_path = str((ROOT / intent.entrypoint).resolve())
    suffix = Path(intent.entrypoint).suffix.lower()
    if suffix == ".ps1":
        powershell_bin = shutil.which("pwsh") or shutil.which("powershell") or "powershell"
        return [powershell_bin, "-File", absolute_path, *intent.args]
    if suffix == ".py":
        return [sys.executable, absolute_path, *intent.args]
    if suffix in {".cmd", ".bat"}:
        return ["cmd.exe", "/c", absolute_path, *intent.args]
    if suffix == ".sh":
        return ["bash", absolute_path, *intent.args]
    return [absolute_path, *intent.args]


def validation_command_policy(command_text: str) -> dict[str, Any]:
    analysis = analyze_validation_command(command_text)
    return {
        "accepted": bool(analysis.get("accepted", False)),
        "reason": analysis.get("reason"),
        "entrypoint": analysis.get("entrypoint"),
        "intent": analysis.get("intent"),
    }


def worker_prompt(
    plan: TaskPlan,
    task: TaskDefinition,
    agent: AgentDefinition,
    workspace_mode: str,
    workspace_path: Path,
    dependency_text: str,
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE,
) -> PromptRenderResult:
    task_root = ROOT if workspace_mode == "repo" else workspace_path
    context_mode = effective_context_mode(task, agent)
    worker_validation_mode = normalize_worker_validation_mode(
        worker_validation_mode,
        location=f"task '{task.id}' worker validation mode",
    )
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
            "Use only the task repo root and explicit file hints. In copy/worktree mode, do not access the source repo root, other task workspaces, or prior run artifacts.",
            "Treat dependency outputs in this prompt as the coordinator handoff from prior tasks.",
            "Return schema-valid JSON only. Keep `summary` to 1-2 sentences, `notes` to <=5 items, `followUps` to <=3, and `validationIntents` to <=2 high-signal suggestions.",
            "Use `[]` when `filesTouched`, `validationIntents`, `followUps`, or `notes` are known-empty. Use `null` only for `filesTouched`, `followUps`, or `notes` when the value is genuinely unknown or unverified.",
            "Emit structured `validationIntents` only for validation suggestions in this run. Do not return raw `validationCommands` items.",
            "Validation commands must be direct repo-script or tool invocations only. Do not use pipes, redirection, chaining, or shell wrappers.",
            "State uncertainty or blockers explicitly instead of guessing.",
            "Do not claim edits or validation you did not actually perform.",
            "Do not edit TODO.md, ai/state/*, ai/log/*, or ai/indexes/*.",
            "Keep changes and coordinator asks tightly scoped to the task.",
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


def worker_result_schema(worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE) -> dict[str, Any]:
    normalize_worker_validation_mode(
        worker_validation_mode,
        location="worker result schema",
    )
    return copy.deepcopy(WORKER_RESULT_SCHEMA)


def task_output_schema_json(worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE) -> str:
    return json.dumps(worker_result_schema(worker_validation_mode), separators=(",", ":"))


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


def coerce_worker_result(
    payload: Any,
    *,
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE,
) -> dict[str, Any]:
    worker_validation_mode = normalize_worker_validation_mode(
        worker_validation_mode,
        location="Claude JSON output",
    )
    required = {
        "status",
        "summary",
        "filesTouched",
        "validationIntents",
        "followUps",
        "notes",
    }
    if isinstance(payload, dict) and not required.issubset(payload):
        for key in ("result", "data", "response", "structured_output"):
            nested = payload.get(key)
            if isinstance(nested, dict) and required.issubset(nested):
                payload = nested
                break
        else:
            raise OrchestratorError("Claude JSON output did not match the expected worker schema")
    if not isinstance(payload, dict):
        raise OrchestratorError("Claude JSON output did not match the expected worker schema")
    status = payload.get("status")
    if not isinstance(status, str) or status not in WORKER_STATUSES:
        raise OrchestratorError(
            f"Claude JSON output field 'status' must be one of {sorted(WORKER_STATUSES)}"
        )
    summary_value = payload.get("summary")
    if not isinstance(summary_value, str) or not summary_value.strip():
        raise OrchestratorError("Claude JSON output field 'summary' must be a non-empty string")
    summary, _ = truncate_text(summary_value, MAX_WORKER_SUMMARY_CHARS)
    unknown_fields: list[str] = []
    normalized_files_touched, files_touched_known = normalize_worker_files_touched(payload.get("filesTouched"))
    if not files_touched_known:
        unknown_fields.append("filesTouched")
    validation_commands, _ = normalize_worker_text_list(
        payload.get("validationCommands", []),
        key="validationCommands",
        max_items=MAX_WORKER_VALIDATION_COMMANDS,
        max_chars=MAX_WORKER_VALIDATION_COMMAND_CHARS,
        compact=False,
    )
    if validation_commands:
        raise OrchestratorError(
            "Claude JSON output must not include raw validationCommands items; use structured validationIntents instead"
        )
    follow_ups, follow_ups_known = normalize_worker_text_list(
        payload.get("followUps"),
        key="followUps",
        max_items=MAX_WORKER_FOLLOW_UPS,
        max_chars=MAX_WORKER_FOLLOW_UP_CHARS,
        compact=True,
    )
    if not follow_ups_known:
        unknown_fields.append("followUps")
    notes, notes_known = normalize_worker_text_list(
        payload.get("notes"),
        key="notes",
        max_items=MAX_WORKER_NOTES,
        max_chars=MAX_WORKER_NOTE_CHARS,
        compact=True,
    )
    if not notes_known:
        unknown_fields.append("notes")
    validation_intents = normalize_worker_validation_intents(payload.get("validationIntents"))
    return {
        "status": status,
        "summary": summary,
        "filesTouched": normalized_files_touched,
        "validationIntents": [asdict(intent) for intent in validation_intents],
        "validationCommands": validation_commands,
        "followUps": follow_ups,
        "notes": notes,
        "unknownFields": unknown_fields,
    }


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


def slop_log_action(actor: str, phase: str, phrase: str, reason: str) -> SlopLogAction:
    normalized_phase = phase.strip().upper()
    if not normalized_phase:
        raise OrchestratorError("Slop log phase must not be empty")
    return SlopLogAction(
        actor=actor.strip().upper() or "AGENT",
        phase=normalized_phase,
        phrase=phrase.strip(),
        reason=reason.strip(),
    )


def emit_slop_log(action: SlopLogAction, *, frame: int | None = None) -> None:
    if not getattr(sys.stderr, "isatty", lambda: False)():
        return
    dots = ""
    if frame is not None:
        dots = f" {SLOP_PROGRESS_DOTS[frame % len(SLOP_PROGRESS_DOTS)]}"
    reason = f" ({action.reason})" if action.reason else ""
    with SLOP_LOG_LOCK:
        print(
            f"[{action.actor}][{action.phase}] {action.phrase}{dots}{reason}",
            file=sys.stderr,
            flush=True,
        )


def planner_wait_action(agent_name: str) -> SlopLogAction:
    return slop_log_action("planner", "PROCESS", "Slopchurning", f"waiting for {agent_name}")


def task_wait_action(task: TaskDefinition) -> SlopLogAction:
    return slop_log_action(task.id, "FLOW", "Slopsloshing", f"waiting for {task.agent}")


def workspace_prep_action(task: TaskDefinition, workspace_mode: str) -> SlopLogAction | None:
    if workspace_mode == "copy":
        return slop_log_action(task.id, "INGEST", "Slurping up slop", "hydrating copy workspace")
    if workspace_mode == "worktree":
        return slop_log_action(task.id, "INGEST", "Slophoovering", "creating detached worktree")
    return None


def validation_wait_action(command_text: str, source_kind: str) -> SlopLogAction:
    summarized = textwrap.shorten(" ".join(command_text.split()), width=88, placeholder="...")
    if source_kind == "intent":
        reason = f"running validation intent {summarized}"
        phrase = "Slopcessing"
    else:
        reason = f"running validation command {summarized}"
        phrase = "Slopcrunching"
    return slop_log_action("validate-run", "PROCESS", phrase, reason)


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


def run_process(
    command: list[str] | str,
    *,
    cwd: Path,
    timeout_sec: int,
    shell: bool,
    progress_action: SlopLogAction | None = None,
    timeout_error: str,
) -> subprocess.CompletedProcess[str]:
    process = subprocess.Popen(
        command,
        cwd=cwd,
        shell=shell,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    frame = 0
    if progress_action is not None:
        emit_slop_log(progress_action, frame=frame)
    deadline = time.monotonic() + timeout_sec
    stdout = ""
    stderr = ""
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise subprocess.TimeoutExpired(process.args, timeout_sec)
            try:
                stdout, stderr = process.communicate(
                    timeout=min(SLOP_PROGRESS_INTERVAL_SEC, remaining)
                )
                break
            except subprocess.TimeoutExpired:
                if progress_action is None:
                    continue
                frame += 1
                emit_slop_log(progress_action, frame=frame)
        return subprocess.CompletedProcess(process.args, process.returncode, stdout, stderr)
    except subprocess.TimeoutExpired as exc:
        process.kill()
        stdout, stderr = process.communicate()
        raise OrchestratorError(timeout_error) from exc


def run_subprocess(
    command: list[str],
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return run_process(
        command,
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=False,
        progress_action=progress_action,
        timeout_error=f"Claude timed out after {timeout_sec} seconds",
    )


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

    completed = run_subprocess(
        command,
        cwd=ROOT,
        timeout_sec=agent.timeout_sec,
        progress_action=planner_wait_action(args.planner_agent),
    )
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
    worker_validation_mode: str | None = None,
) -> TaskRunRecord:
    now = iso_now()
    validation_resolution = resolve_worker_validation_mode(
        task,
        agent,
        run_override=worker_validation_mode,
    )
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
        worker_validation_mode=validation_resolution.mode,
        worker_validation_mode_source=validation_resolution.source,
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
    worker_validation_mode: str | None = None,
) -> TaskRunRecord:
    agent = agents[task.agent]
    validation_resolution = resolve_worker_validation_mode(
        task,
        agent,
        run_override=worker_validation_mode,
    )
    effective_validation_mode = validation_resolution.mode
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
        worker_validation_mode=effective_validation_mode,
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
        task_output_schema_json(effective_validation_mode),
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
            worker_validation_mode=effective_validation_mode,
            worker_validation_mode_source=validation_resolution.source,
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
            worker_validation_mode=effective_validation_mode,
            worker_validation_mode_source=validation_resolution.source,
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
            progress_action=task_wait_action(task),
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
                worker_validation_mode=effective_validation_mode,
                worker_validation_mode_source=validation_resolution.source,
            )
            apply_workspace_audit(record, reported_files=[], actual_files=actual_changed_files)
            write_json(result_path, asdict(record))
            return record

        if raw_payload is None:
            raise OrchestratorError("Claude output did not contain a standalone JSON payload")
        payload = coerce_worker_result(
            raw_payload,
            worker_validation_mode=effective_validation_mode,
        )
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
            validation_intents=[
                coerce_validation_intent_payload(
                    item,
                    location=f"worker result {task.id}:validationIntents",
                )
                for item in payload.get("validationIntents", [])
            ],
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
            unknown_fields=[str(item) for item in payload.get("unknownFields", [])],
            worker_validation_mode=effective_validation_mode,
            worker_validation_mode_source=validation_resolution.source,
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
            worker_validation_mode=effective_validation_mode,
            worker_validation_mode_source=validation_resolution.source,
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
    worker_validation_mode: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
) -> dict[str, Any]:
    worker_validation_override = (
        normalize_worker_validation_mode(
            worker_validation_mode,
            location="manifest worker validation mode override",
        )
        if worker_validation_mode
        else None
    )
    task_worker_validation_modes = effective_plan_worker_validation_modes(
        plan,
        agents,
        run_override=worker_validation_override,
    )
    task_worker_validation_mode_sources = effective_plan_worker_validation_mode_sources(
        plan,
        agents,
        run_override=worker_validation_override,
    )
    usage_totals = aggregate_usage(records)
    parallel_conflicts = detect_parallel_scope_conflicts(plan, agents)
    payload = {
        "runId": run_id,
        "generatedAt": iso_now(),
        "dryRun": dry_run,
        "workerValidationMode": summarized_worker_validation_mode(
            list(task_worker_validation_modes.values())
        ),
        "workerValidationModeOverride": worker_validation_override,
        "taskWorkerValidationModes": task_worker_validation_modes,
        "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
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
    agents: dict[str, AgentDefinition],
    runtime_root: Path,
    run_dir: Path,
    workspaces_dir: Path,
    plan: TaskPlan,
    records: dict[str, TaskRunRecord],
    *,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
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
            worker_validation_mode=worker_validation_mode,
            retry_of_run_id=retry_of_run_id,
            requested_task_ids=requested_task_ids,
            retried_task_ids=retried_task_ids,
            seeded_task_ids=seeded_task_ids,
        ),
    )


def write_selected_plan_snapshot(run_dir: Path, plan: TaskPlan) -> None:
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


def run_loaded_plan(
    plan_path: Path,
    agents_path: Path,
    agents: dict[str, AgentDefinition],
    plan: TaskPlan,
    *,
    claude_bin: str,
    runtime_root: Path,
    max_parallel: int,
    continue_on_error: bool,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    initial_records: dict[str, TaskRunRecord] | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
) -> dict[str, Any]:
    worker_validation_override = (
        normalize_worker_validation_mode(
            worker_validation_mode,
            location="run worker validation mode override",
        )
        if worker_validation_mode
        else None
    )
    task_worker_validation_modes = effective_plan_worker_validation_modes(
        plan,
        agents,
        run_override=worker_validation_override,
    )
    task_worker_validation_mode_sources = effective_plan_worker_validation_mode_sources(
        plan,
        agents,
        run_override=worker_validation_override,
    )
    topological_batches(plan.tasks)
    if not dry_run:
        ensure_claude_available(claude_bin)

    run_id = (
        f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"
        f"-{slugify(plan.name)}-{uuid4().hex[:8]}"
    )
    runtime_root = runtime_root.resolve()
    run_dir = runtime_root / "runs" / run_id
    workspaces_dir = runtime_root / "workspaces" / run_id
    run_dir.mkdir(parents=True, exist_ok=True)
    workspaces_dir.mkdir(parents=True, exist_ok=True)
    write_selected_plan_snapshot(run_dir, plan)

    seeded_task_ids = sorted(initial_records or {})
    agents_json = agent_payload_for_claude(agents)
    records: dict[str, TaskRunRecord] = dict(initial_records or {})
    pending = {task.id: task for task in plan.tasks if task.id not in records}
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
                    worker_validation_mode=worker_validation_override,
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
                dry_run=dry_run,
                worker_validation_mode=worker_validation_override,
                retry_of_run_id=retry_of_run_id,
                requested_task_ids=requested_task_ids,
                retried_task_ids=retried_task_ids,
                seeded_task_ids=seeded_task_ids,
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
                    worker_validation_mode=worker_validation_override,
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
            max_parallel=max(max_parallel, 1),
        )
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
                    agents_json=agents_json,
                    dry_run=dry_run,
                    worker_validation_mode=worker_validation_override,
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
                    dry_run=dry_run,
                    worker_validation_mode=worker_validation_override,
                    retry_of_run_id=retry_of_run_id,
                    requested_task_ids=requested_task_ids,
                    retried_task_ids=retried_task_ids,
                    seeded_task_ids=seeded_task_ids,
                )
                if not continue_on_error and records[task.id].status not in {"completed", "planned"}:
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
        dry_run=dry_run,
        worker_validation_mode=worker_validation_override,
        retry_of_run_id=retry_of_run_id,
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
        seeded_task_ids=seeded_task_ids,
    )
    status_counts: dict[str, int] = {}
    for record in records.values():
        status_counts[record.status] = status_counts.get(record.status, 0) + 1
    usage_totals = aggregate_usage(records)
    payload = {
        "runId": run_id,
        "plan": plan.name,
        "goal": plan.goal,
        "dryRun": dry_run,
        "workerValidationMode": summarized_worker_validation_mode(
            list(task_worker_validation_modes.values())
        ),
        "workerValidationModeOverride": worker_validation_override,
        "taskWorkerValidationModes": task_worker_validation_modes,
        "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
        "runtimeRoot": str(runtime_root),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "statusCounts": status_counts,
        "usageTotals": usage_totals,
        "tasks": [asdict(records[task.id]) for task in plan.tasks],
    }
    if retry_of_run_id:
        payload["retryOfRunId"] = retry_of_run_id
        payload["requestedTaskIds"] = list(requested_task_ids or [])
        payload["retriedTaskIds"] = list(retried_task_ids or [])
        payload["seededTaskIds"] = seeded_task_ids
    return payload


def run_plan(args: argparse.Namespace) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    plan_path = Path(args.task_plan).resolve()
    agents = load_agents(agents_path)
    plan = selected_plan(load_task_plan(plan_path, agents), args.selected_tasks)
    return run_loaded_plan(
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
    )


def retry_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    previous_records = {record.id: record for record in selected_run_records(manifest, [])}
    requested_task_ids = list(args.selected_tasks)
    if not requested_task_ids:
        requested_task_ids = [
            task_id
            for task_id, record in sorted(previous_records.items())
            if record.status in {"failed", "blocked"}
        ]
    if not requested_task_ids:
        raise OrchestratorError(
            "Run manifest contains no failed or blocked tasks to retry; use --task to pick tasks explicitly"
        )

    plan_path = manifest_required_path(manifest, "planPath", location=str(manifest_path))
    agents_path = (
        Path(args.agents).resolve()
        if args.agents
        else manifest_required_path(manifest, "agentsPath", location=str(manifest_path))
    )
    runtime_root = (
        Path(args.runtime_root).resolve()
        if args.runtime_root
        else manifest_required_path(manifest, "runtimeRoot", location=str(manifest_path))
    )
    if not plan_path.exists():
        raise OrchestratorError(f"Retry source plan '{plan_path}' does not exist")
    if not agents_path.exists():
        raise OrchestratorError(f"Retry source agents file '{agents_path}' does not exist")

    agents = load_agents(agents_path)
    retry_plan = selected_plan(load_task_plan(plan_path, agents), requested_task_ids)
    requested_set = set(requested_task_ids)
    initial_records: dict[str, TaskRunRecord] = {}
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
        raise OrchestratorError("Nothing to retry; all selected tasks are already completed")

    manifest_worker_validation_override = str(manifest.get("workerValidationModeOverride", "")).strip()
    if not manifest_worker_validation_override:
        legacy_manifest_mode = str(manifest.get("workerValidationMode", "")).strip()
        if legacy_manifest_mode in WORKER_VALIDATION_MODES:
            manifest_worker_validation_override = legacy_manifest_mode
    payload = run_loaded_plan(
        plan_path,
        agents_path,
        agents,
        retry_plan,
        claude_bin=args.claude_bin,
        runtime_root=runtime_root,
        max_parallel=args.max_parallel,
        continue_on_error=args.continue_on_error,
        dry_run=args.dry_run,
        worker_validation_mode=(
            normalize_worker_validation_mode(
                getattr(args, "worker_validation_mode", "") or manifest_worker_validation_override,
                location=f"retry source '{manifest_path}' worker validation mode",
            )
            if (getattr(args, "worker_validation_mode", "") or manifest_worker_validation_override)
            else None
        ),
        initial_records=initial_records,
        retry_of_run_id=str(manifest.get("runId", "")),
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
    )
    payload["sourceManifestPath"] = str(manifest_path)
    return payload


def cleanup_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
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
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        if completed.returncode != 0:
            raise OrchestratorError(
                f"Failed to remove detached worktree '{workspace}': "
                f"{completed.stderr.strip() or completed.stdout.strip()}"
            )
        removed_worktrees.append(str(workspace))
    if removed_worktrees:
        completed = subprocess.run(
            ["git", "worktree", "prune"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        if completed.returncode != 0:
            raise OrchestratorError(
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


def collect_validation_commands(
    records: list[TaskRunRecord],
    *,
    included_statuses: set[str],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    task_payloads: list[dict[str, Any]] = []
    suggestions_by_command: dict[str, dict[str, Any]] = {}
    suggestion_order: list[str] = []
    for record in records:
        commands = dedupe_strings(record.validation_commands)
        rendered_intents = dedupe_strings(
            [validation_intent_command_text(intent) for intent in record.validation_intents]
        )
        included = record.status in included_statuses
        task_payloads.append(
            {
                "id": record.id,
                "status": record.status,
                "summary": record.summary,
                "unknownFields": record.unknown_fields,
                "validationIntents": [asdict(intent) for intent in record.validation_intents],
                "renderedValidationIntents": rendered_intents,
                "validationCommands": commands,
                "validationCommandsKnown": not worker_field_unknown(record, "validationCommands"),
                "legacyValidationCommandCount": len(commands),
                "legacyValidationCommandsPresent": bool(commands),
                "includedForValidation": included,
                "excludedReason": (
                    None
                    if included
                    else f"status '{record.status}' is excluded by the current validation policy"
                ),
            }
        )
        if not included:
            continue
        for intent in record.validation_intents:
            command_text = validation_intent_command_text(intent)
            payload = suggestions_by_command.get(command_text)
            if payload is None:
                payload = {
                    "sourceKind": "intent",
                    "command": command_text,
                    "taskIds": [],
                    "policy": validation_intent_policy(intent),
                    "intent": asdict(intent),
                    "compatibilityOnly": False,
                }
                suggestions_by_command[command_text] = payload
                suggestion_order.append(command_text)
            payload["taskIds"].append(record.id)
        for command in commands:
            policy = validation_command_policy(command)
            payload = suggestions_by_command.get(command)
            if payload is None:
                payload = {
                    "sourceKind": "command",
                    "command": command,
                    "taskIds": [],
                    "policy": policy,
                    "intent": None,
                    "compatibilityOnly": True,
                    "normalizedIntent": (
                        policy.get("intent") if isinstance(policy.get("intent"), dict) else None
                    ),
                }
                suggestions_by_command[command] = payload
                suggestion_order.append(command)
            payload["taskIds"].append(record.id)
    command_payloads = []
    for command_text in suggestion_order:
        payload = suggestions_by_command[command_text]
        payload["taskIds"] = dedupe_strings([str(task_id) for task_id in payload["taskIds"]])
        command_payloads.append(payload)
    return task_payloads, command_payloads


def run_shell_command_text(
    command_text: str,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return run_process(
        command_text,
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=True,
        progress_action=progress_action,
        timeout_error=f"Validation command timed out after {timeout_sec} seconds: {command_text}",
    )


def run_validation_intent(
    intent: ValidationIntent,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return run_process(
        validation_intent_execution_tokens(intent),
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=False,
        progress_action=progress_action,
        timeout_error=(
            "Validation intent timed out after "
            f"{timeout_sec} seconds: {validation_intent_command_text(intent)}"
        ),
    )


def write_coordinator_validation_summary(
    manifest_path: Path,
    manifest: dict[str, Any],
    summary: dict[str, Any],
) -> dict[str, Any]:
    validation_dir = manifest_path.parent / "validation"
    summary_path = validation_dir / "summary.json"
    write_json(summary_path, summary)
    updated_manifest = dict(manifest)
    updated_manifest["coordinatorValidation"] = {
        **summary,
        "summaryPath": str(summary_path),
    }
    write_json(manifest_path, updated_manifest)
    return updated_manifest


def validate_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    run_dir = manifest_run_dir(manifest_path, manifest)
    records = selected_run_records(manifest, args.selected_tasks)
    intents_only = bool(getattr(args, "intents_only", False))
    included_statuses = {
        str(status)
        for status in (getattr(args, "include_statuses", None) or list(DEFAULT_VALIDATE_RUN_STATUSES))
    }
    task_payloads, commands = collect_validation_commands(
        records,
        included_statuses=included_statuses,
    )
    validation_dir = run_dir / "validation"
    command_results: list[dict[str, Any]] = []
    stop_after_failure = False
    for index, command_payload in enumerate(commands, start=1):
        command_text = str(command_payload["command"])
        source_kind = str(command_payload.get("sourceKind", "command"))
        intent_payload = command_payload.get("intent")
        normalized_intent_payload = command_payload.get("normalizedIntent")
        execution_intent_payload = (
            intent_payload
            if isinstance(intent_payload, dict)
            else normalized_intent_payload if isinstance(normalized_intent_payload, dict) else None
        )
        quality_policy = command_payload["policy"] if isinstance(command_payload.get("policy"), dict) else {
            "accepted": True,
            "reason": None,
            "entrypoint": None,
        }
        policy = dict(quality_policy)
        intents_only_rejected = source_kind == "command" and intents_only
        if intents_only_rejected:
            policy = {
                "accepted": False,
                "reason": (
                    "raw validationCommands are compatibility-only under --intents-only; "
                    "emit structured validationIntents instead"
                ),
                "entrypoint": quality_policy.get("entrypoint"),
            }
        policy_accepted = bool(policy.get("accepted", False))
        policy_override = bool(args.allow_unsafe_commands and not policy_accepted and not intents_only_rejected)
        result_payload: dict[str, Any] = {
            "index": index,
            "sourceKind": source_kind,
            "command": command_text,
            "taskIds": list(command_payload["taskIds"]),
            "intent": intent_payload if isinstance(intent_payload, dict) else None,
            "normalizedIntent": (
                normalized_intent_payload if isinstance(normalized_intent_payload, dict) else None
            ),
            "compatibilityOnly": bool(command_payload.get("compatibilityOnly", source_kind == "command")),
            "intentsOnlyRejected": intents_only_rejected,
            "executionKind": "argv" if execution_intent_payload is not None else "shell",
            "qualityPolicyAccepted": bool(quality_policy.get("accepted", False)),
            "qualityPolicyReason": quality_policy.get("reason"),
            "policyAccepted": policy_accepted,
            "policyReason": policy.get("reason"),
            "policyOverride": policy_override,
            "entrypoint": policy.get("entrypoint"),
            "status": "planned" if args.dry_run else "completed",
            "returnCode": None,
            "stdoutPath": None,
            "stderrPath": None,
        }
        if not policy_accepted and not policy_override:
            result_payload["status"] = "rejected"
            command_results.append(result_payload)
            continue
        if stop_after_failure:
            result_payload["status"] = "skipped"
            command_results.append(result_payload)
            continue
        if args.dry_run:
            command_results.append(result_payload)
            continue
        command_dir = validation_dir / f"{index:02d}-{slugify(command_text[:48])}"
        stdout_path = command_dir / "stdout.txt"
        stderr_path = command_dir / "stderr.txt"
        command_path = command_dir / "command.txt"
        write_text(command_path, command_text + "\n")
        if execution_intent_payload is not None:
            write_json(command_dir / "intent.json", execution_intent_payload)
        try:
            if execution_intent_payload is not None:
                completed = run_validation_intent(
                    coerce_validation_intent_payload(
                        execution_intent_payload,
                        location=f"validate-run:{index}:intent",
                    ),
                    cwd=ROOT,
                    timeout_sec=max(args.timeout_sec, 1),
                    progress_action=validation_wait_action(command_text, "intent"),
                )
            else:
                completed = run_shell_command_text(
                    command_text,
                    cwd=ROOT,
                    timeout_sec=max(args.timeout_sec, 1),
                    progress_action=validation_wait_action(command_text, "command"),
                )
            write_text(stdout_path, completed.stdout)
            write_text(stderr_path, completed.stderr)
            result_payload["returnCode"] = completed.returncode
            result_payload["stdoutPath"] = str(stdout_path)
            result_payload["stderrPath"] = str(stderr_path)
            if completed.returncode != 0:
                result_payload["status"] = "failed"
                if not args.continue_on_error:
                    stop_after_failure = True
            command_results.append(result_payload)
        except OrchestratorError as exc:
            write_text(stderr_path, str(exc) + "\n")
            result_payload["status"] = "failed"
            result_payload["returnCode"] = None
            result_payload["stdoutPath"] = str(stdout_path) if stdout_path.exists() else None
            result_payload["stderrPath"] = str(stderr_path)
            command_results.append(result_payload)
            if not args.continue_on_error:
                stop_after_failure = True
    summary = {
        "generatedAt": iso_now(),
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "repoRoot": str(ROOT),
        "dryRun": args.dry_run,
        "intentsOnly": intents_only,
        "selectedTaskIds": [record.id for record in records],
        "includedStatuses": sorted(included_statuses),
        "taskCount": len(task_payloads),
        "tasks": task_payloads,
        "includedTaskIds": [payload["id"] for payload in task_payloads if payload["includedForValidation"]],
        "excludedTaskIds": [payload["id"] for payload in task_payloads if not payload["includedForValidation"]],
        "legacyValidationCommandCount": sum(
            int(payload.get("legacyValidationCommandCount", 0) or 0) for payload in task_payloads
        ),
        "includedLegacyValidationCommandCount": sum(
            int(payload.get("legacyValidationCommandCount", 0) or 0)
            for payload in task_payloads
            if payload["includedForValidation"]
        ),
        "legacyValidationCommandTaskIds": [
            payload["id"] for payload in task_payloads if payload.get("legacyValidationCommandsPresent")
        ],
        "includedLegacyValidationCommandTaskIds": [
            payload["id"]
            for payload in task_payloads
            if payload["includedForValidation"] and payload.get("legacyValidationCommandsPresent")
        ],
        "commandCount": len(command_results),
        "acceptedCommandCount": sum(1 for payload in command_results if payload.get("policyAccepted")),
        "rejectedCommandCount": sum(1 for payload in command_results if payload["status"] == "rejected"),
        "intentsOnlyRejectedCommandCount": sum(
            1 for payload in command_results if payload.get("intentsOnlyRejected")
        ),
        "allowUnsafeCommands": bool(args.allow_unsafe_commands),
        "suggestedCommands": [payload["command"] for payload in commands],
        "suggestedValidationIntents": [
            payload["intent"] for payload in commands if isinstance(payload.get("intent"), dict)
        ],
        "commands": command_results,
        "statusCounts": count_statuses([str(payload["status"]) for payload in command_results]),
        "allPassed": all(payload["status"] in {"planned", "completed"} for payload in command_results),
    }
    write_coordinator_validation_summary(manifest_path, manifest, summary)
    return summary


def validate_command(args: argparse.Namespace) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    agents = load_agents(agents_path)
    payload: dict[str, Any] = {
        "agentsPath": str(agents_path),
        "agentCount": len(agents),
        "agents": sorted(agents),
        "agentWorkerValidationModes": {
            name: agent.worker_validation_mode for name, agent in sorted(agents.items())
        },
    }
    if args.task_plan:
        plan_path = Path(args.task_plan).resolve()
        plan = load_task_plan(plan_path, agents)
        task_worker_validation_modes = effective_plan_worker_validation_modes(plan, agents)
        task_worker_validation_mode_sources = effective_plan_worker_validation_mode_sources(plan, agents)
        payload.update(
            {
                "taskPlanPath": str(plan_path),
                "planName": plan.name,
                "taskCount": len(plan.tasks),
                "taskIds": [task.id for task in plan.tasks],
                "taskWorkerValidationModes": task_worker_validation_modes,
                "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
                "tasks": [
                    {
                        "id": task.id,
                        "agent": task.agent,
                        "workerValidationMode": task_worker_validation_modes[task.id],
                        "workerValidationModeSource": task_worker_validation_mode_sources[task.id],
                    }
                    for task in plan.tasks
                ],
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
        if args.command == "retry":
            print_payload(retry_run(args), as_json=args.json)
            return 0
        if args.command == "review":
            print_payload(review_run(args), as_json=args.json)
            return 0
        if args.command == "export-patch":
            print_payload(export_patch(args), as_json=args.json)
            return 0
        if args.command == "promote":
            print_payload(promote_run(args), as_json=args.json)
            return 0
        if args.command == "cleanup":
            print_payload(cleanup_run(args), as_json=args.json)
            return 0
        if args.command == "validate-run":
            print_payload(validate_run(args), as_json=args.json)
            return 0
        raise OrchestratorError(f"Unknown command '{args.command}'")
    except OrchestratorError as exc:
        print(f"[claude-orchestrator] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
