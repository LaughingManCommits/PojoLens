#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import difflib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import textwrap
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass, field
from datetime import datetime, timedelta, timezone
from json import JSONDecodeError
from pathlib import Path
from typing import Any
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from pojo_lens_agents import governance as governance_layer
from pojo_lens_agents import path_safety as path_safety_layer
from pojo_lens_agents import provider as provider_layer
from pojo_lens_agents import run_summary as run_summary_layer
from pojo_lens_agents import runtime as runtime_layer
from pojo_lens_agents import run_store as run_store_layer
from pojo_lens_agents import workspace_review as workspace_review_layer

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
DEPENDENCY_MATERIALIZATION_MODES = {"summary-only", "apply-reviewed"}
DEFAULT_DEPENDENCY_MATERIALIZATION_MODE = "summary-only"
WORKER_VALIDATION_MODES = {"intents-only"}
LEGACY_WORKER_VALIDATION_MODES = {"compat", *WORKER_VALIDATION_MODES}
DEFAULT_WORKER_VALIDATION_MODE = "intents-only"
WORKER_VALIDATION_MODE_SOURCES = {"override", "task", "agent", "default"}
ANALYST_AGENT_NAME = "analyst"
IMPLEMENTER_AGENT_NAME = "implementer"
REVIEWER_AGENT_NAME = "reviewer"
RUN_POLICY_BEHAVIORS = {"warn", "stop"}
DEFAULT_RUN_BUDGET_BEHAVIOR = "stop"
DEFAULT_ARTIFACT_BEHAVIOR = "warn"
MODEL_PROFILE_TO_MODEL = {
    "simple": "claude-haiku-4-5-20251001",
    "balanced": "claude-sonnet-4-6",
    "complex": "claude-opus-4-7",
}
MODEL_TO_PROFILE = {value: key for key, value in MODEL_PROFILE_TO_MODEL.items()}
MAX_HYDRATED_FILE_BYTES = 512 * 1024
DEFAULT_PROMPT_SECTION_ITEM_LIMIT = 8
DEFAULT_PROMPT_ITEM_CHAR_LIMIT = 180
DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT = 220
DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT = 2
DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT = 120
DEFAULT_REVIEW_DEPENDENCY_CONTEXT_LINES = 1
DEFAULT_REVIEW_DEPENDENCY_PATCH_CHAR_LIMIT = 900
WORKER_STATUSES = {"completed", "blocked", "failed"}
DEFAULT_VALIDATE_RUN_STATUSES = ("completed",)
VALIDATE_RUN_EXECUTION_SCOPES = {"repo", "task-workspace"}
DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE = "repo"
MAX_WORKER_SUMMARY_CHARS = 360
MAX_WORKER_NOTES = 5
MAX_WORKER_NOTE_CHARS = 220
MAX_WORKER_FOLLOW_UPS = 3
MAX_WORKER_FOLLOW_UP_CHARS = 180
MAX_WORKER_VALIDATION_COMMANDS = 2
MAX_WORKER_VALIDATION_COMMAND_CHARS = 320
MAX_WORKER_VALIDATION_INTENTS = 2
MAX_WORKER_VALIDATION_INTENT_ARG_CHARS = 180
MAX_RUN_SUMMARY_TOP_TASKS = 3
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
SPARSE_COPY_BASE_FILES: tuple[str, ...] = ()
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

# Exit codes
EXIT_SUCCESS = 0
EXIT_ERROR = 1           # general orchestrator error (config, args, missing files)
EXIT_BOOTSTRAP = 2       # reserved for cli.py bootstrap failure
EXIT_VALIDATION = 3      # plan schema invalid or agent config invalid
EXIT_WORKER_FAILURE = 4  # one or more tasks returned "failed"
EXIT_BLOCKED = 5         # tasks blocked, no failures
EXIT_UNSAFE_PROMOTION = 6  # promotion refused (scope violations, protected paths)
EXIT_CRASH = 7           # unexpected Python exception
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
        "runPolicy": {
            "type": "object",
            "properties": {
                "runBudgetUsd": {"type": "number", "exclusiveMinimum": 0},
                "budgetBehavior": {
                    "type": "string",
                    "enum": sorted(RUN_POLICY_BEHAVIORS),
                },
                "maxTaskStdoutBytes": {"type": "integer", "minimum": 1},
                "maxTaskStderrBytes": {"type": "integer", "minimum": 1},
                "maxTaskResultBytes": {"type": "integer", "minimum": 1},
                "artifactBehavior": {
                    "type": "string",
                    "enum": sorted(RUN_POLICY_BEHAVIORS),
                },
            },
            "additionalProperties": False,
        },
        "sharedContext": {
            "type": "object",
            "properties": {
                "summary": {"type": "string"},
                "constraints": {"type": "array", "items": {"type": "string"}},
                "readPaths": {"type": "array", "items": {"type": "string"}},
                "validation": {"type": "array", "items": {"type": "string"}},
            },
            "required": ["summary", "constraints", "readPaths", "validation"],
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
                    "readPaths": {"type": "array", "items": {"type": "string"}},
                    "writePaths": {"type": "array", "items": {"type": "string"}},
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
                    "dependencyMaterialization": {
                        "type": "string",
                        "enum": sorted(DEPENDENCY_MATERIALIZATION_MODES),
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


class ValidationError(OrchestratorError):
    """Raised when a plan schema or agent config is structurally invalid."""


class PromotionBlockedError(OrchestratorError):
    """Raised when promotion is refused due to scope violations or protected paths."""


@dataclass(frozen=True)
class AgentDefinition:
    name: str
    description: str
    prompt: str
    skills: list[str] = field(default_factory=list)
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
    read_paths: list[str]
    validation: list[str]

    @property
    def files(self) -> list[str]:
        return self.read_paths


@dataclass(frozen=True)
class TaskDefinition:
    id: str
    title: str
    agent: str
    prompt: str
    depends_on: list[str] = field(default_factory=list)
    read_paths: list[str] = field(default_factory=list)
    write_paths: list[str] = field(default_factory=list)
    constraints: list[str] = field(default_factory=list)
    validation: list[str] = field(default_factory=list)
    workspace_mode: str | None = None
    model: str | None = None
    model_profile: str | None = None
    effort: str | None = None
    permission_mode: str | None = None
    context_mode: str | None = None
    dependency_materialization: str | None = None
    worker_validation_mode: str | None = None
    timeout_sec: int | None = None
    max_budget_usd: float | None = None
    max_prompt_chars: int | None = None
    max_prompt_estimated_tokens: int | None = None
    allowed_tools: list[str] = field(default_factory=list)
    disallowed_tools: list[str] = field(default_factory=list)

    @property
    def files(self) -> list[str]:
        return self.read_paths


@dataclass(frozen=True)
class RunPolicy:
    run_budget_usd: float | None = None
    budget_behavior: str = DEFAULT_RUN_BUDGET_BEHAVIOR
    max_task_stdout_bytes: int | None = None
    max_task_stderr_bytes: int | None = None
    max_task_result_bytes: int | None = None
    artifact_behavior: str = DEFAULT_ARTIFACT_BEHAVIOR


@dataclass(frozen=True)
class TaskPlan:
    version: int
    name: str
    goal: str
    shared_context: SharedContext
    tasks: list[TaskDefinition]
    run_policy: RunPolicy = field(default_factory=RunPolicy)


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
class DependencyLayerOperation:
    path: str
    action: str
    is_binary: bool = False


@dataclass(frozen=True)
class DependencyLayerRecord:
    task_id: str
    workspace_mode: str
    workspace_path: str
    operations: list[DependencyLayerOperation] = field(default_factory=list)


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


@dataclass(frozen=True)
class WorkspacePreparationResult:
    workspace_path: Path
    dependency_layers_applied: list[DependencyLayerRecord] = field(default_factory=list)


@dataclass
class TaskRunRecord:
    id: str
    title: str
    agent: str
    branch_context_id: str
    branch_parent_context_ids: list[str]
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
    stdout_bytes: int = 0
    stderr_bytes: int = 0
    result_bytes: int = 0
    validation_intents: list[ValidationIntent] = field(default_factory=list)
    unknown_fields: list[str] = field(default_factory=list)
    dependency_materialization_mode: str = DEFAULT_DEPENDENCY_MATERIALIZATION_MODE
    dependency_layers_applied: list[DependencyLayerRecord] = field(default_factory=list)
    write_scope_violations: list[str] = field(default_factory=list)
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE
    worker_validation_mode_source: str | None = None
    effort: str | None = None
    effort_source: str | None = None


def _add_provider_bin_arg(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--provider-bin",
        "--claude-bin",
        default=DEFAULT_CLAUDE_BIN,
        dest="claude_bin",
        metavar="BIN",
        help="AI provider CLI executable (default: claude). --claude-bin is a legacy alias.",
    )


def _add_verbose_arg(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Emit extra diagnostic details to stderr.",
    )


def _add_max_parallel_arg(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--max-parallel",
        type=int,
        default=2,
        help=(
            "Maximum number of independent ready tasks to run concurrently. "
            "Parallel execution is the default; only tasks with declared dependencies "
            "or overlapping write scopes are serialized. (Primary CLI feature.)"
        ),
    )


def _add_continue_on_error_arg(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Continue running independent tasks after a worker fails or reports blocked.",
    )


def _add_effort_arg(parser: argparse.ArgumentParser, *, help_text: str) -> None:
    parser.add_argument(
        "--effort",
        default="",
        help=help_text,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Coordinate local Claude Code workers from tracked repo task specs. "
            "Independent tasks run in parallel by default; use --max-parallel on run, "
            "resume, and retry to control concurrency. Only tasks with declared "
            "dependencies or overlapping write scopes are serialized automatically."
        ),
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
        "--dry-run",
        action="store_true",
        help="Validate without writing any output files.",
    )
    validate_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the validation summary as JSON.",
    )
    _add_verbose_arg(validate_parser)
    _add_provider_bin_arg(validate_parser)

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
        help="Repo-relative read-context hint to include in planner context. Repeatable.",
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
    _add_provider_bin_arg(plan_parser)
    _add_effort_arg(
        plan_parser,
        help_text="Override planner reasoning effort for this request. Defaults to the planner agent definition.",
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
    _add_verbose_arg(plan_parser)

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
    _add_provider_bin_arg(run_parser)
    run_parser.add_argument(
        "--runtime-root",
        default=str(DEFAULT_RUNTIME_ROOT),
        help="Runtime root for generated manifests, logs, and isolated workspaces.",
    )
    _add_max_parallel_arg(run_parser)
    _add_effort_arg(
        run_parser,
        help_text="Override worker reasoning effort for this run. Defaults to task definitions, then agent definitions.",
    )
    run_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict execution to a task id and its prerequisites. Repeatable.",
    )
    _add_continue_on_error_arg(run_parser)
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
    _add_verbose_arg(run_parser)

    resume_parser = subparsers.add_parser(
        "resume",
        help="Continue a partially completed run in place from an existing manifest.",
    )
    resume_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    resume_parser.add_argument(
        "--agents",
        default="",
        help="Override the agents JSON path. Defaults to the original run manifest agentsPath.",
    )
    _add_provider_bin_arg(resume_parser)
    _add_max_parallel_arg(resume_parser)
    _add_effort_arg(
        resume_parser,
        help_text="Override worker reasoning effort for the resumed run. Defaults to task definitions, then agent definitions.",
    )
    resume_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict resume to one or more task ids. Defaults to all non-completed or missing tasks in the run snapshot.",
    )
    _add_continue_on_error_arg(resume_parser)
    resume_parser.add_argument(
        "--worker-validation-mode",
        choices=sorted(WORKER_VALIDATION_MODES),
        default="",
        help="Override worker validation suggestion policy for the resumed run. Defaults to the source run mode or 'intents-only'.",
    )
    resume_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Refresh the in-place run manifest and task requests without invoking Claude.",
    )
    resume_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the resume summary as JSON.",
    )
    _add_verbose_arg(resume_parser)

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
    _add_provider_bin_arg(retry_parser)
    retry_parser.add_argument(
        "--runtime-root",
        default="",
        help="Override runtime root for the retry run. Defaults to the original run manifest runtimeRoot.",
    )
    _add_max_parallel_arg(retry_parser)
    _add_effort_arg(
        retry_parser,
        help_text="Override worker reasoning effort for the retry run. Defaults to task definitions, then agent definitions.",
    )
    retry_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict retry to one or more task ids. Defaults to failed or blocked tasks.",
    )
    _add_continue_on_error_arg(retry_parser)
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
    _add_verbose_arg(retry_parser)

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
        "--dry-run",
        action="store_true",
        help="Summarize diffs without writing review output.",
    )
    review_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the review summary as JSON.",
    )
    _add_verbose_arg(review_parser)
    _add_provider_bin_arg(review_parser)

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
        "--dry-run",
        action="store_true",
        help="Summarize patch without writing it to disk.",
    )
    export_patch_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the export summary as JSON.",
    )
    _add_verbose_arg(export_patch_parser)
    _add_provider_bin_arg(export_patch_parser)

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
    _add_verbose_arg(promote_parser)
    _add_provider_bin_arg(promote_parser)

    cleanup_parser = subparsers.add_parser(
        "cleanup",
        help="Remove runtime artifacts for a completed or abandoned run.",
    )
    cleanup_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    cleanup_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report what would be removed without deleting anything.",
    )
    cleanup_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the cleanup summary as JSON.",
    )
    _add_verbose_arg(cleanup_parser)
    _add_provider_bin_arg(cleanup_parser)

    inventory_parser = subparsers.add_parser(
        "inventory",
        help="List retained runs under the runtime root with compact status summaries.",
    )
    inventory_parser.add_argument(
        "--runtime-root",
        default=str(DEFAULT_RUNTIME_ROOT),
        help="Runtime root whose run manifests should be inventoried.",
    )
    inventory_parser.add_argument(
        "--limit",
        type=int,
        default=20,
        help="Maximum number of runs to show. Use 0 to show all discovered runs.",
    )
    inventory_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="List inventory candidates without any side effects.",
    )
    inventory_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the inventory summary as JSON.",
    )
    _add_verbose_arg(inventory_parser)
    _add_provider_bin_arg(inventory_parser)

    status_parser = subparsers.add_parser(
        "status",
        help="Show a compact operator summary for one retained run.",
    )
    status_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    status_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict status details to one or more task ids. Repeatable.",
    )
    status_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the run status summary as JSON.",
    )
    _add_verbose_arg(status_parser)
    _add_provider_bin_arg(status_parser)

    evaluate_run_parser = subparsers.add_parser(
        "evaluate-run",
        help="Evaluate retained-run orchestration quality and contract signals.",
    )
    evaluate_run_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    evaluate_run_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict evaluation details to one or more task ids. Repeatable.",
    )
    evaluate_run_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the run evaluation summary as JSON.",
    )
    _add_verbose_arg(evaluate_run_parser)
    _add_provider_bin_arg(evaluate_run_parser)

    evaluate_corpus_parser = subparsers.add_parser(
        "evaluate-corpus",
        help="Evaluate retained-run quality across the runtime root and report aggregate benchmark signals.",
    )
    evaluate_corpus_parser.add_argument(
        "--runtime-root",
        default=str(DEFAULT_RUNTIME_ROOT),
        help="Runtime root whose retained runs should be evaluated.",
    )
    evaluate_corpus_parser.add_argument(
        "--limit",
        type=int,
        default=20,
        help="Maximum number of retained runs to include. Use 0 to include all discovered runs.",
    )
    evaluate_corpus_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the retained-run corpus evaluation summary as JSON.",
    )
    _add_verbose_arg(evaluate_corpus_parser)
    _add_provider_bin_arg(evaluate_corpus_parser)

    prune_parser = subparsers.add_parser(
        "prune",
        help="Prune retained run directories and workspaces by age under the runtime root.",
    )
    prune_parser.add_argument(
        "--runtime-root",
        default=str(DEFAULT_RUNTIME_ROOT),
        help="Runtime root whose retained runs should be considered for pruning.",
    )
    prune_parser.add_argument(
        "--older-than-days",
        type=float,
        default=7.0,
        help="Prune runs older than this many days.",
    )
    prune_parser.add_argument(
        "--keep",
        type=int,
        default=0,
        help="Always keep this many newest runs even if they are older than the cutoff.",
    )
    prune_parser.add_argument(
        "--include-incomplete",
        action="store_true",
        help="Allow pruning runs that still have non-completed tasks.",
    )
    prune_parser.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Continue pruning later runs after a cleanup failure.",
    )
    prune_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report prune candidates without deleting them.",
    )
    prune_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the prune summary as JSON.",
    )
    _add_verbose_arg(prune_parser)
    _add_provider_bin_arg(prune_parser)

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
        "--execution-scope",
        choices=sorted(VALIDATE_RUN_EXECUTION_SCOPES),
        default=DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
        help="Run validation from repo root (default) or from each suggesting task workspace when available.",
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
    _add_verbose_arg(validate_run_parser)
    _add_provider_bin_arg(validate_run_parser)

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
    except OSError as exc:
        raise OrchestratorError(f"Cannot read {path}: {exc}") from exc


def write_text(path: Path, text: str | None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text or "", encoding="utf-8")


def write_json(path: Path, payload: object) -> None:
    write_text(path, json.dumps(payload, indent=2) + "\n")


def read_bytes(path: Path) -> bytes:
    return path.read_bytes()


def file_sha256(path: Path) -> str:
    return workspace_review_layer.file_sha256(path)


def normalize_relative_path(path_value: str, *, location: str) -> str:
    return path_safety_layer.normalize_relative_path(
        path_value,
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
    if payload is None:
        return RunPolicy()
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: runPolicy must be an object")
    return RunPolicy(
        run_budget_usd=require_optional_float(payload, "runBudgetUsd", location=location),
        budget_behavior=normalize_run_policy_behavior(
            require_optional_string(payload, "budgetBehavior", location=location),
            location=location,
            key="budgetBehavior",
            default=DEFAULT_RUN_BUDGET_BEHAVIOR,
        ),
        max_task_stdout_bytes=require_optional_int(payload, "maxTaskStdoutBytes", location=location),
        max_task_stderr_bytes=require_optional_int(payload, "maxTaskStderrBytes", location=location),
        max_task_result_bytes=require_optional_int(payload, "maxTaskResultBytes", location=location),
        artifact_behavior=normalize_run_policy_behavior(
            require_optional_string(payload, "artifactBehavior", location=location),
            location=location,
            key="artifactBehavior",
            default=DEFAULT_ARTIFACT_BEHAVIOR,
        ),
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
            skills=require_string_list(definition, "skills", location=location),
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
    if "files" in shared_context_payload:
        raise OrchestratorError(
            f"{path}:sharedContext: legacy 'files' was replaced by 'readPaths'"
        )
    shared_context = SharedContext(
        summary=require_string(shared_context_payload, "summary", location=f"{path}:sharedContext"),
        constraints=require_string_list(shared_context_payload, "constraints", location=f"{path}:sharedContext"),
        read_paths=require_scope_path_list(
            shared_context_payload,
            "readPaths",
            location=f"{path}:sharedContext",
            allow_repo_root=True,
        ),
        validation=require_string_list(shared_context_payload, "validation", location=f"{path}:sharedContext"),
    )
    run_policy = load_run_policy(payload.get("runPolicy"), location=str(path))
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
        if "files" in task_payload:
            raise OrchestratorError(
                f"{location}: legacy 'files' was replaced by 'readPaths' and 'writePaths'"
            )
        worker_validation_mode = require_optional_string(
            task_payload,
            "workerValidationMode",
            location=location,
        )
        dependency_materialization = require_optional_string(
            task_payload,
            "dependencyMaterialization",
            location=location,
        )
        task = TaskDefinition(
            id=task_id,
            title=require_string(task_payload, "title", location=location),
            agent=agent_name,
            prompt=require_string(task_payload, "prompt", location=location),
            depends_on=require_string_list(task_payload, "dependsOn", location=location),
            read_paths=require_scope_path_list(
                task_payload,
                "readPaths",
                location=location,
                allow_repo_root=True,
            ),
            write_paths=require_scope_path_list(
                task_payload,
                "writePaths",
                location=location,
                allow_repo_root=True,
            ),
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
            dependency_materialization=normalize_dependency_materialization_mode(
                dependency_materialization,
                location=f"{location}:dependencyMaterialization",
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
        run_policy=run_policy,
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
    return dedupe_strings(plan.shared_context.read_paths + task.read_paths)


def prompt_task_read_paths(plan: TaskPlan, task: TaskDefinition, *, context_mode: str) -> list[str]:
    if context_mode == "minimal":
        return dedupe_strings(task.read_paths)
    return effective_task_read_paths(plan, task)


def effective_task_write_scope(task: TaskDefinition) -> list[str]:
    return dedupe_strings(task.write_paths)


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
        _, source = resolve_relative_path(ROOT, read_path, location=f"{task.id}:readPaths")
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
        _, source = resolve_relative_path(ROOT, write_path, location=f"{task.id}:writePaths")
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
    issues: list[str] = []
    if task_may_write(plan, task, agent) and not effective_task_write_scope(task):
        issues.append("write-capable tasks must declare non-empty writePaths")
    workspace_mode = effective_workspace_mode(task, agent)
    dependency_materialization = effective_dependency_materialization_mode(task)
    if dependency_materialization == "apply-reviewed":
        if not task.depends_on:
            issues.append("dependencyMaterialization='apply-reviewed' requires non-empty dependsOn")
        if workspace_mode == "repo":
            issues.append("dependencyMaterialization='apply-reviewed' is not allowed for workspaceMode='repo'")
        for dependency_id in task.depends_on:
            dependency = tasks_by_id[dependency_id]
            dependency_workspace_mode = effective_workspace_mode(
                dependency,
                agents[dependency.agent],
            )
            if dependency_workspace_mode not in {"copy", "worktree"}:
                issues.append(
                    "dependencyMaterialization='apply-reviewed' requires reviewable dependency workspaces; "
                    f"dependency '{dependency_id}' resolves to workspaceMode='{dependency_workspace_mode}'"
                )
    if workspace_mode == "copy":
        hydration = analyze_copy_hydration_inputs(plan, task)
        if hydration["missingReadPaths"]:
            issues.append(
                "copy readPaths are missing from the repo: "
                f"{summarize_paths(hydration['missingReadPaths'])}"
            )
        if hydration["directoryReadPaths"]:
            issues.append(
                "copy readPaths must be concrete files, not directories: "
                f"{summarize_paths(hydration['directoryReadPaths'])}"
            )
        if hydration["oversizedPaths"]:
            issues.append(
                "copy workspace inputs exceed the hydration size limit: "
                f"{summarize_paths(hydration['oversizedPaths'])}"
            )
    return issues


def validate_scope_contract(plan: TaskPlan, agents: dict[str, AgentDefinition]) -> None:
    issues: list[str] = []
    tasks_by_id = {task.id: task for task in plan.tasks}
    for task in plan.tasks:
        task_issues = validate_task_scope_contract(
            plan,
            task,
            agents[task.agent],
            tasks_by_id=tasks_by_id,
            agents=agents,
        )
        for issue in task_issues:
            issues.append(f"{task.id}: {issue}")
    if issues:
        raise OrchestratorError(format_issue_block("Task scope validation failed", issues))


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
) -> str:
    selected = set(selected_names or agents.keys())
    payload = {
        name: {
            "description": agent.description,
            "prompt": agent.prompt,
            **({"skills": agent.skills} if agent.skills else {}),
        }
        for name, agent in agents.items()
        if name in selected
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
    return path_safety_layer.path_is_relative_to(path, parent)


def resolve_relative_path(root: Path, relative_path: str, *, location: str) -> tuple[str, Path]:
    return path_safety_layer.resolve_relative_path(
        root,
        relative_path,
        location=location,
        error_factory=OrchestratorError,
    )


def hydrate_copy_workspace(workspace_path: Path, file_paths: list[str]) -> None:
    workspace_review_layer.hydrate_copy_workspace(
        source_root=ROOT,
        workspace_path=workspace_path,
        file_paths=file_paths,
        base_files=SPARSE_COPY_BASE_FILES,
        max_file_bytes=MAX_HYDRATED_FILE_BYTES,
        path_is_relative_to=path_is_relative_to,
    )


def prepare_workspace(
    plan: TaskPlan,
    task: TaskDefinition,
    workspace_mode: str,
    workspace_path: Path,
    runtime_root: Path,
    dependency_records: dict[str, TaskRunRecord],
) -> WorkspacePreparationResult:
    if workspace_mode == "repo":
        return WorkspacePreparationResult(ROOT, [])
    if workspace_path.exists():
        shutil.rmtree(workspace_path)
    workspace_path.parent.mkdir(parents=True, exist_ok=True)
    action = workspace_prep_action(task, workspace_mode)
    if action is not None:
        emit_slop_log(action)
    if workspace_mode == "copy":
        hydration = analyze_copy_hydration_inputs(plan, task)
        hydration_issues: list[str] = []
        if hydration["missingReadPaths"]:
            hydration_issues.append(
                "copy readPaths are missing from the repo: "
                f"{summarize_paths(hydration['missingReadPaths'])}"
            )
        if hydration["directoryReadPaths"]:
            hydration_issues.append(
                "copy readPaths must be concrete files, not directories: "
                f"{summarize_paths(hydration['directoryReadPaths'])}"
            )
        if hydration["oversizedPaths"]:
            hydration_issues.append(
                "copy workspace inputs exceed the hydration size limit: "
                f"{summarize_paths(hydration['oversizedPaths'])}"
            )
        if hydration_issues:
            raise OrchestratorError(
                format_issue_block(f"{task.id}: invalid copy workspace inputs", hydration_issues)
            )
        hydrate_copy_workspace(workspace_path, hydration["filesToCopy"])
        return WorkspacePreparationResult(
            workspace_path,
            materialize_dependency_layers(
                task,
                dependency_records,
                workspace_root=workspace_path,
            ),
        )
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
        return WorkspacePreparationResult(
            workspace_path,
            materialize_dependency_layers(
                task,
                dependency_records,
                workspace_root=workspace_path,
            ),
        )
    raise OrchestratorError(f"{task.id}: unsupported workspace mode '{workspace_mode}'")


def snapshot_workspace_files(workspace_root: Path) -> dict[str, str]:
    return workspace_review_layer.snapshot_workspace_files(
        workspace_root,
        ignore_dir_names=WORKSPACE_AUDIT_IGNORE_DIR_NAMES,
    )


def diff_workspace_snapshots(before: dict[str, str], after: dict[str, str]) -> list[str]:
    return workspace_review_layer.diff_workspace_snapshots(before, after)


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
    return path_safety_layer.protected_path_violations(
        paths,
        exact_paths=PROTECTED_PATH_EXACT,
        path_prefixes=PROTECTED_PATH_PREFIXES,
        error_factory=OrchestratorError,
    )


def write_scope_violations(paths: list[str], declared_scope: list[str]) -> list[str]:
    return paths_outside_scope(paths, declared_scope)


def apply_workspace_audit(
    record: TaskRunRecord,
    *,
    reported_files: list[str],
    actual_files: list[str],
    declared_write_scope: list[str],
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
    protected_violations = protected_path_violations(record.files_touched)
    record.protected_path_violations = protected_violations
    scope_violations = write_scope_violations(actual_files, declared_write_scope)
    record.write_scope_violations = scope_violations
    failure_reasons: list[str] = []
    if protected_violations:
        failure_reasons.append(
            "Protected-path violation: "
            f"{summarize_paths(protected_violations)}"
        )
    if scope_violations:
        failure_reasons.append(
            "Write-scope violation: "
            f"{summarize_paths(scope_violations)}"
        )
    if failure_reasons:
        record.status = "failed"
        record.summary = "; ".join(failure_reasons) + f". Original outcome: {record.summary}"
        follow_ups = [
            "Inspect and discard or manually review forbidden workspace edits before promotion.",
            "Inspect and discard or manually review out-of-scope workspace edits before promotion.",
        ]
        for follow_up in reversed(follow_ups):
            if follow_up not in record.follow_ups and (
                ("forbidden" in follow_up and protected_violations)
                or ("out-of-scope" in follow_up and scope_violations)
            ):
                record.follow_ups.insert(0, follow_up)
    return record


def apply_repository_isolation_audit(
    record: TaskRunRecord,
    *,
    workspace_mode: str,
    changed_repo_files: list[str],
) -> TaskRunRecord:
    if workspace_mode == "repo" or not changed_repo_files:
        return record
    summary = (
        "Repository isolation violation: worker modified live repo while running in "
        f"workspaceMode='{workspace_mode}': {summarize_paths(changed_repo_files)}"
    )
    record.status = "failed"
    record.summary = summary + f". Original outcome: {record.summary}"
    follow_up = (
        "Discard or restore live repo mutations, then rerun the task and promote only "
        "workspace-reviewed changes."
    )
    if follow_up not in record.follow_ups:
        record.follow_ups.insert(0, follow_up)
    return record


def resolve_manifest_path(run_ref: str) -> Path:
    return run_store_layer.resolve_manifest_path(
        run_ref,
        error_factory=OrchestratorError,
    )


def load_run_manifest(run_ref: str) -> tuple[Path, dict[str, Any]]:
    manifest_path = resolve_manifest_path(run_ref)
    payload = read_json(manifest_path)
    return manifest_path, run_store_layer.validate_manifest_payload(
        payload,
        manifest_path=manifest_path,
        error_factory=OrchestratorError,
    )


def manifest_required_path(manifest: dict[str, Any], key: str, *, location: str) -> Path:
    return run_store_layer.manifest_required_path(
        manifest,
        key,
        location=location,
        error_factory=OrchestratorError,
    )


def manifest_run_dir(manifest_path: Path, manifest: dict[str, Any]) -> Path:
    return run_store_layer.manifest_run_dir(manifest_path, manifest)


def manifest_workspaces_dir(manifest: dict[str, Any], *, run_dir: Path) -> Path:
    return run_store_layer.manifest_workspaces_dir(
        manifest,
        run_dir=run_dir,
        error_factory=OrchestratorError,
    )


def manifest_selected_plan_path(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    location: str,
) -> Path:
    return run_store_layer.manifest_selected_plan_path(
        manifest_path,
        manifest,
        location=location,
        error_factory=OrchestratorError,
    )


def manifest_worker_validation_override(
    manifest: dict[str, Any],
    *,
    location: str,
) -> str | None:
    override = require_optional_string(manifest, "workerValidationModeOverride", location=location)
    if override:
        return override
    legacy_mode = require_optional_string(manifest, "workerValidationMode", location=location)
    if legacy_mode in WORKER_VALIDATION_MODES:
        return legacy_mode
    return None


def count_statuses(values: list[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    return counts


def task_branch_parent_context_ids(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord] | None = None,
) -> list[str]:
    dependency_records = dependency_records or {}
    parent_context_ids: list[str] = []
    for dependency_id in task.depends_on:
        dependency_record = dependency_records.get(dependency_id)
        context_id = dependency_record.branch_context_id if dependency_record is not None else dependency_id
        if context_id and context_id not in parent_context_ids:
            parent_context_ids.append(context_id)
    return parent_context_ids


def task_branch_context_id(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord] | None = None,
) -> str:
    parent_context_ids = task_branch_parent_context_ids(task, dependency_records)
    if not parent_context_ids:
        return task.id
    return f"{task.id}<-{','.join(parent_context_ids)}"


def summarize_branch_contexts(records: list[TaskRunRecord]) -> dict[str, Any]:
    return run_summary_layer.summarize_branch_contexts(records)


def task_cost_usd(record: TaskRunRecord) -> float:
    return governance_layer.task_cost_usd(record)


def coerce_task_run_record(payload: Any, *, location: str) -> TaskRunRecord:
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: expected task record object")
    _budget = payload.get("prompt_budget")
    budget_payload: dict[str, Any] = _budget if isinstance(_budget, dict) else {}
    return TaskRunRecord(
        id=str(payload.get("id", "")),
        title=str(payload.get("title", "")),
        agent=str(payload.get("agent", "")),
        branch_context_id=str(payload.get("branch_context_id", payload.get("id", ""))),
        branch_parent_context_ids=[
            str(item) for item in payload.get("branch_parent_context_ids", []) or []
        ],
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
        write_scope_violations=[
            str(item) for item in payload.get("write_scope_violations", []) or []
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
                int(budget_payload["max_chars"])
                if budget_payload.get("max_chars") is not None
                else None
            ),
            max_estimated_tokens=(
                int(budget_payload["max_estimated_tokens"])
                if budget_payload.get("max_estimated_tokens") is not None
                else None
            ),
            exceeded=bool(budget_payload.get("exceeded", False)),
            violations=[str(item) for item in budget_payload.get("violations", [])],
        ),
        usage=payload.get("usage") if isinstance(payload.get("usage"), dict) else None,
        return_code=int(payload["return_code"]) if payload.get("return_code") is not None else None,
        prompt_path=str(payload.get("prompt_path", "")),
        command_path=str(payload.get("command_path", "")),
        stdout_path=str(payload["stdout_path"]) if payload.get("stdout_path") is not None else None,
        stderr_path=str(payload["stderr_path"]) if payload.get("stderr_path") is not None else None,
        result_path=str(payload["result_path"]) if payload.get("result_path") is not None else None,
        stdout_bytes=int(payload.get("stdout_bytes", 0) or 0),
        stderr_bytes=int(payload.get("stderr_bytes", 0) or 0),
        result_bytes=int(payload.get("result_bytes", 0) or 0),
        validation_intents=[
            coerce_validation_intent_payload(item, location=f"{location}:validation_intents")
            for item in payload.get("validation_intents", []) or []
        ],
        unknown_fields=normalized_worker_unknown_fields(payload.get("unknown_fields")),
        dependency_materialization_mode=(
            normalize_dependency_materialization_mode(
                payload.get("dependency_materialization_mode"),
                location=f"{location}:dependency_materialization_mode",
            )
            or DEFAULT_DEPENDENCY_MATERIALIZATION_MODE
        ),
        dependency_layers_applied=[
            coerce_dependency_layer_record_payload(
                item,
                location=f"{location}:dependency_layers_applied[{index}]",
            )
            for index, item in enumerate(payload.get("dependency_layers_applied", []) or [], start=1)
        ],
        worker_validation_mode=normalize_worker_validation_mode(
            payload.get("worker_validation_mode"),
            location=f"{location}:worker_validation_mode",
        ),
        worker_validation_mode_source=normalize_worker_validation_mode_source(
            payload.get("worker_validation_mode_source"),
            location=f"{location}:worker_validation_mode_source",
        ),
        effort=normalize_effort_override(
            payload.get("effort"),
            location=f"{location}:effort",
        ),
        effort_source=require_optional_string(payload, "effort_source", location=location),
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
            "dependencyMaterializationMode": record.dependency_materialization_mode,
            "dependencyLayersApplied": [asdict(layer) for layer in record.dependency_layers_applied],
            "protectedPathViolations": record.protected_path_violations,
            "writeScopeViolations": record.write_scope_violations,
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


def dependency_review_context(record: TaskRunRecord) -> list[str]:
    changed_paths = dedupe_strings(record.actual_files_touched or record.files_touched)
    if not changed_paths:
        if worker_field_unknown(record, "filesTouched"):
            return ["  changed files: unknown"]
        return []
    try:
        review_payload, patch_chunks = task_review_summary(
            record,
            context_lines=DEFAULT_REVIEW_DEPENDENCY_CONTEXT_LINES,
        )
    except OrchestratorError as exc:
        reason, _ = truncate_text(str(exc), DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT)
        return [f"  changed files: unavailable ({reason})"]
    changed_files = [
        file_payload
        for file_payload in review_payload["files"]
        if str(file_payload.get("status", "")) in {"added", "modified", "deleted"}
    ]
    if not changed_files:
        return []
    visible_files: list[str] = []
    for file_payload in changed_files[:DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT]:
        path = str(file_payload.get("path", ""))
        status = str(file_payload.get("status", ""))
        if file_payload.get("isBinary"):
            visible_files.append(f"`{path}` ({status}, binary)")
            continue
        added = int(file_payload.get("addedLines", 0) or 0)
        removed = int(file_payload.get("removedLines", 0) or 0)
        visible_files.append(f"`{path}` ({status}, +{added}/-{removed})")
    hidden_files = len(changed_files) - len(visible_files)
    if hidden_files > 0:
        visible_files.append(f"... ({hidden_files} more changed files omitted)")
    lines = ["  changed files: " + " ; ".join(visible_files)]
    preview_chunks: list[str] = []
    preview_chars = 0
    for patch_chunk in patch_chunks[:DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT]:
        chunk = patch_chunk.strip()
        if not chunk:
            continue
        remaining_chars = DEFAULT_REVIEW_DEPENDENCY_PATCH_CHAR_LIMIT - preview_chars
        if remaining_chars <= 0:
            break
        preview_text, truncated = truncate_multiline_text(chunk, remaining_chars)
        if not preview_text:
            continue
        preview_chunks.append(preview_text)
        preview_chars += len(preview_text) + 1
        if truncated:
            break
    if preview_chunks:
        lines.append("  diff preview:")
        for preview_line in "\n\n".join(preview_chunks).splitlines():
            lines.append(f"    {preview_line}")
    return lines


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
    changed_task_count = 0
    changed_files = 0
    protected_violations = 0
    write_scope_violations = 0
    validation_suggestion_count = 0
    dependency_materialization_modes: dict[str, int] = {}
    for record in records:
        review_payload, _ = task_review_summary(record, context_lines=args.context_lines)
        task_payloads.append(review_payload)
        if int(review_payload["diffStats"]["filesChanged"]) > 0:
            changed_task_count += 1
        changed_files += int(review_payload["diffStats"]["filesChanged"])
        protected_violations += len(review_payload["protectedPathViolations"])
        write_scope_violations += len(review_payload["writeScopeViolations"])
        validation_suggestion_count += len(record.validation_intents) + len(record.validation_commands)
        mode = str(review_payload["dependencyMaterializationMode"] or DEFAULT_DEPENDENCY_MATERIALIZATION_MODE)
        dependency_materialization_modes[mode] = dependency_materialization_modes.get(mode, 0) + 1
    payload = {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "runDir": str(manifest_path.parent),
        "taskCount": len(task_payloads),
        "summary": {
            "changedTaskCount": changed_task_count,
            "changedFileCount": changed_files,
            "protectedPathViolationCount": protected_violations,
            "writeScopeViolationCount": write_scope_violations,
            "validationSuggestionCount": validation_suggestion_count,
            "dependencyMaterializationModes": dependency_materialization_modes,
        },
        "tasks": task_payloads,
    }
    write_run_checkpoint(
        manifest_path,
        manifest,
        checkpoint_name="coordinatorReview",
        directory_name="review",
        payload=payload,
    )
    return payload


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
    patch_text = "".join(patch_chunks)
    if patch_text:
        write_text(output_path, patch_text)
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
            "dependencyMaterializationMode": record.dependency_materialization_mode,
            "dependencyLayersApplied": [asdict(layer) for layer in record.dependency_layers_applied],
            "protectedPathViolations": record.protected_path_violations,
            "writeScopeViolations": record.write_scope_violations,
            "filesPromotable": len(operations),
            "unsupportedFiles": unsupported_paths,
            "operations": operations,
        },
        operations,
    )


def coerce_dependency_layer_operation_payload(
    payload: Any,
    *,
    location: str,
) -> DependencyLayerOperation:
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: expected dependency layer operation object")
    action = str(payload.get("action", "")).strip()
    if action not in {"added", "modified", "deleted"}:
        raise OrchestratorError(
            f"{location}: dependency layer action must be one of ['added', 'deleted', 'modified']"
        )
    return DependencyLayerOperation(
        path=normalize_relative_path(str(payload.get("path", "")), location=f"{location}:path"),
        action=action,
        is_binary=bool(payload.get("is_binary", payload.get("isBinary", False))),
    )


def coerce_dependency_layer_record_payload(
    payload: Any,
    *,
    location: str,
) -> DependencyLayerRecord:
    if not isinstance(payload, dict):
        raise OrchestratorError(f"{location}: expected dependency layer record object")
    task_id = str(payload.get("task_id", payload.get("taskId", ""))).strip()
    if not task_id:
        raise OrchestratorError(f"{location}: dependency layer task id is required")
    workspace_mode = str(payload.get("workspace_mode", payload.get("workspaceMode", ""))).strip()
    if workspace_mode not in {"copy", "worktree"}:
        raise OrchestratorError(
            f"{location}: dependency layer workspace mode must be 'copy' or 'worktree'"
        )
    workspace_path = str(payload.get("workspace_path", payload.get("workspacePath", ""))).strip()
    if not workspace_path:
        raise OrchestratorError(f"{location}: dependency layer workspace path is required")
    operations_payload = payload.get("operations", [])
    if operations_payload is None:
        operations_payload = []
    if not isinstance(operations_payload, list):
        raise OrchestratorError(f"{location}: dependency layer operations must be a list")
    return DependencyLayerRecord(
        task_id=task_id,
        workspace_mode=workspace_mode,
        workspace_path=workspace_path,
        operations=[
            coerce_dependency_layer_operation_payload(
                item,
                location=f"{location}:operations[{index}]",
            )
            for index, item in enumerate(operations_payload, start=1)
        ],
    )


def own_dependency_layer(record: TaskRunRecord) -> DependencyLayerRecord | None:
    _, operations = task_promotion_operations(record)
    if not operations:
        return None
    if record.workspace_mode not in {"copy", "worktree"}:
        raise OrchestratorError(
            f"{record.id}: workspaceMode='{record.workspace_mode}' is not materializable"
        )
    if not record.workspace_path:
        raise OrchestratorError(f"{record.id}: workspace path missing for dependency materialization")
    return DependencyLayerRecord(
        task_id=record.id,
        workspace_mode=record.workspace_mode,
        workspace_path=record.workspace_path,
        operations=[
            DependencyLayerOperation(
                path=str(operation["path"]),
                action=str(operation["action"]),
                is_binary=bool(operation.get("isBinary", False)),
            )
            for operation in operations
        ],
    )


def dependency_layers_for_record(record: TaskRunRecord) -> list[DependencyLayerRecord]:
    layers = list(record.dependency_layers_applied)
    own_layer = own_dependency_layer(record)
    if own_layer is not None:
        layers.append(own_layer)
    return layers


def dependency_layer_conflicts(task: TaskDefinition, records: dict[str, TaskRunRecord]) -> list[str]:
    owner_by_path: dict[str, str] = {}
    issues: list[str] = []
    for dependency_id in task.depends_on:
        record = records[dependency_id]
        touched_paths = dedupe_strings(
            [
                operation.path
                for layer in dependency_layers_for_record(record)
                for operation in layer.operations
            ]
        )
        for path in touched_paths:
            owner = owner_by_path.get(path)
            if owner is not None and owner != dependency_id:
                issues.append(
                    f"dependency materialization is ambiguous for '{path}' from '{owner}' and '{dependency_id}'"
                )
            else:
                owner_by_path[path] = dependency_id
    return dedupe_strings(issues)


def apply_workspace_operation(
    source_workspace_path: str,
    operation: DependencyLayerOperation,
    *,
    target_root: Path,
    location: str,
) -> None:
    normalized, target_file = resolve_relative_path(
        target_root,
        operation.path,
        location=f"{location}:target",
    )
    if operation.action == "deleted":
        if target_file.exists():
            target_file.unlink()
        return
    source_root = Path(source_workspace_path).resolve()
    if not source_root.exists():
        raise OrchestratorError(f"{location}: source workspace '{source_root}' does not exist")
    _, source_file = resolve_relative_path(
        source_root,
        normalized,
        location=f"{location}:source",
    )
    if not source_file.exists() or not source_file.is_file():
        raise OrchestratorError(
            f"{location}: expected materialized source file '{normalized}' in '{source_root}'"
        )
    target_file.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, target_file)


def materialize_dependency_layers(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord],
    *,
    workspace_root: Path,
) -> list[DependencyLayerRecord]:
    if effective_dependency_materialization_mode(task) != "apply-reviewed":
        return []
    conflict_issues = dependency_layer_conflicts(task, dependency_records)
    if conflict_issues:
        raise OrchestratorError(format_issue_block(f"{task.id}: dependency materialization blocked", conflict_issues))
    applied_layers: list[DependencyLayerRecord] = []
    for dependency_id in task.depends_on:
        record = dependency_records[dependency_id]
        if record.status != "completed":
            raise OrchestratorError(
                f"{task.id}: dependency '{dependency_id}' must be completed before materialization"
            )
        for layer in dependency_layers_for_record(record):
            for index, operation in enumerate(layer.operations, start=1):
                apply_workspace_operation(
                    layer.workspace_path,
                    operation,
                    target_root=workspace_root,
                    location=f"{task.id}:dependency:{layer.task_id}:operation[{index}]",
                )
            applied_layers.append(layer)
    return applied_layers


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
        if record.write_scope_violations:
            issues.append(
                f"{record.id}: write-scope violations must be reviewed manually: "
                f"{summarize_paths(record.write_scope_violations)}"
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
        raise PromotionBlockedError(format_issue_block("Promotion blocked", issues))
    return task_payloads, all_operations, counts


def summarize_promotion_readiness(records: list[TaskRunRecord]) -> dict[str, Any]:
    try:
        task_payloads, operations, counts = plan_promotion(records)
        return {
            "allowed": True,
            "blockedReasons": [],
            "promotableTaskIds": [payload["id"] for payload in task_payloads if payload["filesPromotable"]],
            "filesPromotable": len(operations),
            "operationCounts": counts,
        }
    except PromotionBlockedError as exc:
        return {
            "allowed": False,
            "blockedReasons": dedupe_strings(
                [line[2:] if line.startswith("- ") else line for line in str(exc).splitlines() if line.strip() and not line.endswith(":")]
            ),
            "promotableTaskIds": [],
            "filesPromotable": 0,
            "operationCounts": {"added": 0, "modified": 0, "deleted": 0},
        }


def apply_promotion_operation(record: TaskRunRecord, operation: dict[str, Any]) -> None:
    apply_workspace_operation(
        str(record.workspace_path),
        DependencyLayerOperation(
            path=str(operation["path"]),
            action=str(operation["action"]),
            is_binary=bool(operation.get("isBinary", False)),
        ),
        target_root=ROOT,
        location=f"{record.id}: promote",
    )


def promote_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    readiness = summarize_promotion_readiness(records)
    if not readiness["allowed"] and not args.dry_run:
        raise PromotionBlockedError(format_issue_block("Promotion blocked", list(readiness["blockedReasons"])))
    task_payloads, operations, counts = (
        plan_promotion(records)
        if readiness["allowed"]
        else ([task_promotion_operations(record)[0] for record in records], [], {"added": 0, "modified": 0, "deleted": 0})
    )
    promotable_task_ids = (
        [payload["id"] for payload in task_payloads if payload["filesPromotable"]]
        if readiness["allowed"]
        else []
    )
    if not args.dry_run:
        records_by_id = {record.id: record for record in records}
        for operation in operations:
            apply_promotion_operation(records_by_id[str(operation["taskId"])], operation)
    payload = {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "repoRoot": str(ROOT),
        "dryRun": args.dry_run,
        "taskCount": len(task_payloads),
        "taskIds": [record.id for record in records],
        "promotionAllowed": bool(readiness["allowed"]),
        "blockedReasons": list(readiness["blockedReasons"]),
        "promotableTaskIds": promotable_task_ids,
        "promotedTaskIds": [] if args.dry_run else promotable_task_ids,
        "filesPromotable": len(operations),
        "filesPromoted": 0 if args.dry_run else len(operations),
        "operationCounts": counts,
        "tasks": task_payloads,
    }
    write_run_checkpoint(
        manifest_path,
        manifest,
        checkpoint_name="coordinatorPromotion",
        directory_name="promotion",
        payload=payload,
    )
    return payload


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
            "Prefer the smallest actor set that can finish the work; do not add analyst or reviewer hops by default.",
            "For narrow code changes, prefer a single implementer or implementer->reviewer path. Use a separate analyst only when implementation uncertainty is high, and use a reviewer only when independent review materially lowers risk or the operator asked for it.",
            'Use `workspaceMode="copy"` for most tasks.',
            'Use `workspaceMode="worktree"` only when isolated git metadata matters.',
            'Avoid `workspaceMode="repo"` unless direct in-place execution is essential.',
            'Use `contextMode="minimal"` unless a task really needs the full shared context.',
            '`modelProfile="simple"` fits narrow lookups, doc summaries, and other cheap read-only tasks.',
            '`modelProfile="balanced"` fits planning, coding, analysis, and most review tasks.',
            '`modelProfile="complex"` is the exceptional path; use it only when `simple` or `balanced` are likely insufficient for architecture or unusually deep multi-step reasoning.',
            "Live worker validation suggestions are structured-intent-only; do not plan around raw worker `validationCommands`.",
            "Use `sharedContext.readPaths` for cross-task context, task `readPaths` for task-local context, and task `writePaths` for allowed edits.",
            "Keep `readPaths` repo-relative and concrete. In copy mode they must point at existing files, not directories.",
            "Keep `writePaths` conservative and only as wide as the task needs. Use directory scopes only when multiple sibling edits are intentional.",
            "Use `dependencyMaterialization=\"apply-reviewed\"` only for downstream tasks that truly need reviewed upstream code state inside their workspace; otherwise leave the default summary-only path.",
            "Minimize per-task context and validation hints.",
            "Do not assign `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*` edits to workers.",
            "Put cross-task setup in `sharedContext`.",
            "Add `runPolicy` only when run-level cost or artifact governance is important for the plan.",
            "Use `dependsOn` only when one task genuinely needs another task's output.",
            "Omit speculative tasks when evidence is insufficient.",
        ],
        empty_line="- none",
        max_items=22,
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
                heading="Read hints",
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
    return list(dict.fromkeys(values))


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


def truncate_multiline_text(text: str, max_chars: int | None) -> tuple[str, bool]:
    stripped = text.strip()
    if max_chars is None or len(stripped) <= max_chars:
        return stripped, False
    if max_chars <= 3:
        return stripped[:max_chars], True
    return stripped[: max_chars - 3].rstrip() + "...", True


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


def effective_dependency_materialization_mode(task: TaskDefinition) -> str:
    return task.dependency_materialization or DEFAULT_DEPENDENCY_MATERIALIZATION_MODE


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


def normalize_effort_override(value: str | None, *, location: str) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    if not normalized:
        return None
    if any(char.isspace() for char in normalized):
        raise OrchestratorError(f"{location}: effort override must not contain whitespace")
    return normalized


def resolve_effort(
    task: TaskDefinition | None,
    agent: AgentDefinition,
    *,
    run_override: str | None = None,
) -> tuple[str | None, str]:
    if run_override:
        return run_override, "override"
    if task is not None and task.effort:
        return task.effort, "task"
    if agent.effort:
        return agent.effort, "agent"
    return None, "default"


def effective_plan_model_profiles(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str | None]:
    return {
        task.id: resolved_model_profile(task, agents[task.agent])
        for task in plan.tasks
    }


def effective_plan_models(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str | None]:
    return {
        task.id: resolved_model(task, agents[task.agent])
        for task in plan.tasks
    }


def effective_plan_efforts(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str | None]:
    return {
        task.id: resolve_effort(task, agents[task.agent], run_override=run_override)[0]
        for task in plan.tasks
    }


def effective_plan_effort_sources(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str]:
    return {
        task.id: resolve_effort(task, agents[task.agent], run_override=run_override)[1]
        for task in plan.tasks
    }


def complex_model_task_ids(task_model_profiles: dict[str, str | None]) -> list[str]:
    return [task_id for task_id, profile in task_model_profiles.items() if profile == "complex"]


def dependency_handoff(record: TaskRunRecord) -> str:
    summary, _ = truncate_text(record.summary, DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT)
    parts = [f"`{record.id}` ({record.status}): {summary}"]
    parts.append(f"context: `{record.branch_context_id}`")
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
    if follow_ups:
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
    include_review_context = task.agent == REVIEWER_AGENT_NAME
    blocks: list[str] = []
    for dependency_id in task.depends_on:
        record = records[dependency_id]
        lines = [f"- {dependency_handoff(record)}"]
        if include_review_context:
            lines.extend(dependency_review_context(record))
        blocks.append("\n".join(lines))
    return "\n".join(blocks)


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
        resolved_entrypoint = shutil.which(intent.entrypoint) or intent.entrypoint
        return [resolved_entrypoint, *intent.args]
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
    *,
    dependency_materialization_mode: str = DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    dependency_layers_applied: list[DependencyLayerRecord] | None = None,
    dry_run: bool = False,
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE,
) -> PromptRenderResult:
    task_root = ROOT if workspace_mode == "repo" else workspace_path
    context_mode = effective_context_mode(task, agent)
    dependency_materialization_mode = normalize_dependency_materialization_mode(
        dependency_materialization_mode,
        location=f"task '{task.id}' dependency materialization mode",
    ) or DEFAULT_DEPENDENCY_MATERIALIZATION_MODE
    dependency_layers_applied = list(dependency_layers_applied or [])
    worker_validation_mode = normalize_worker_validation_mode(
        worker_validation_mode,
        location=f"task '{task.id}' worker validation mode",
    )
    read_paths = prompt_task_read_paths(plan, task, context_mode=context_mode)
    write_paths = effective_task_write_scope(task)
    constraints = dedupe_strings(plan.shared_context.constraints + task.constraints)
    validation_hints = (
        dedupe_strings(task.validation)
        if context_mode == "minimal"
        else dedupe_strings(plan.shared_context.validation + task.validation)
    )
    workspace_rule = {
        "copy": "Isolated sparse filesystem copy seeded from declared read context and existing write-scope files. Undeclared repo files are unavailable. Edit only inside this copy.",
        "repo": "Live repo root. Treat this as high-risk and avoid incidental edits.",
        "worktree": "Detached git worktree rooted at HEAD. Root-repo uncommitted changes are not present.",
    }[workspace_mode]
    read_path_lines, read_path_count, read_paths_truncated = format_bullet_list(
        read_paths,
        empty_line="- none",
        code_format=True,
    )
    write_path_lines, write_path_count, write_paths_truncated = format_bullet_list(
        write_paths,
        empty_line="- read-only task; do not make edits",
        code_format=True,
    )
    constraint_lines, constraint_count, constraints_truncated = format_bullet_list(
        constraints,
        empty_line="- none",
    )
    rendered_validation_hints: list[str] = []
    for hint in validation_hints:
        policy = validation_command_policy(hint)
        intent = policy.get("intent") if isinstance(policy, dict) else None
        if policy.get("accepted") and isinstance(intent, dict):
            kind = str(intent.get("kind", "")).strip()
            entrypoint = str(intent.get("entrypoint", "")).strip()
            if kind and entrypoint:
                rendered_validation_hints.append(
                    f"`{hint}` -> `{kind}` `{entrypoint}` (mirror exactly; keep args unchanged)"
                )
                continue
        rendered_validation_hints.append(f"`{hint}`")
    validation_lines, validation_count, validation_truncated = format_bullet_list(
        rendered_validation_hints,
        empty_line="- none",
        code_format=False,
    )
    materialization_items = [
        f"{layer.task_id} ({len(layer.operations)} ops from {layer.workspace_mode})"
        for layer in dependency_layers_applied
    ]
    if dependency_materialization_mode == "summary-only":
        materialization_lines = "Mode: `summary-only`\nApplied layers: none"
        materialization_count = 0
        materialization_truncated = False
    else:
        layer_lines, layer_count, layers_truncated = format_bullet_list(
            materialization_items,
            empty_line=(
                "- none applied yet in this dry-run; live execution will materialize reviewed "
                "dependency state before the worker runs"
                if dry_run
                else "- none"
            ),
        )
        materialization_lines = "Mode: `apply-reviewed`\nApplied layers:\n" + layer_lines
        materialization_count = layer_count
        materialization_truncated = layers_truncated
    worker_rules, rule_count, rules_truncated = format_bullet_list(
        [
            "Treat this prompt plus the declared workspace as the full contract. Do not assume hidden coordinator memory or undeclared repo files.",
            "Use only this workspace plus the declared `readPaths` and `writePaths`.",
            "In copy/worktree mode, do not inspect the source repo root, other task workspaces, or prior run artifacts.",
            "Treat `writePaths` as the edit contract. Dependency outputs are the coordinator handoff.",
            "If dependency layers are materialized, workspace state overrides prompt summaries for those upstream files.",
            "If validation hints already show an approved command, mirror that exact entrypoint and args in `validationIntents`. Do not swap `mvn` and `mvnw` or invent alternate wrappers.",
            "Suggest only coordinator-accepted validation shapes: `repo-script` for `scripts/...` or `mvnw(.cmd)`, `tool` for approved executables like `git`, `java`, `mvn`, `py`, or `pytest`.",
            "If no approved validation shape fits, emit `[]`. Do not invent scripts or use `grep`, `findstr`, or shell fragments.",
            "State blockers explicitly, and do not claim edits or validation you did not actually perform.",
            "Do not edit `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*`. Keep changes tightly scoped to the task.",
        ],
        empty_line="- none",
        max_items=10,
    )
    return render_prompt(
        [
            PromptSection(name="coordinator_goal", heading="Coordinator goal", body=plan.goal),
            PromptSection(
                name="shared_summary",
                heading="Shared context summary",
                body=plan.shared_context.summary,
            ),
            PromptSection(
                name="worker_rules",
                heading="Worker rules",
                body=worker_rules,
                item_count=rule_count,
                truncated=rules_truncated,
            ),
            PromptSection(name="task_identity", heading="Task id/title", body=f"{task.id} / {task.title}"),
            PromptSection(name="task_objective", heading="Task objective", body=task.prompt),
            PromptSection(
                name="read_paths",
                heading="Read context",
                body=read_path_lines,
                item_count=read_path_count,
                truncated=read_paths_truncated,
            ),
            PromptSection(
                name="write_paths",
                heading="Write scope",
                body=write_path_lines,
                item_count=write_path_count,
                truncated=write_paths_truncated,
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
                name="dependency_materialization",
                heading="Dependency materialization",
                body=materialization_lines,
                item_count=materialization_count,
                truncated=materialization_truncated,
            ),
            PromptSection(
                name="validation_hints",
                heading="Validation hints",
                body=validation_lines,
                item_count=validation_count,
                truncated=validation_truncated,
            ),
            PromptSection(
                name="execution_context",
                heading="Execution context",
                body=textwrap.dedent(
                    f"""\
                    Current working directory: {(
                        "live repo root"
                        if workspace_mode == "repo"
                        else "isolated task workspace"
                        if workspace_mode == "copy"
                        else "detached worktree"
                    )}
                    Workspace mode: {workspace_mode}
                    Context mode: {context_mode}
                    Contract: {workspace_rule}
                    """
                ).strip(),
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
    return provider_layer.extract_json_payload(
        text,
        error_factory=OrchestratorError,
    )


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
    return provider_layer.extract_usage(payload)


def aggregate_usage(records: dict[str, TaskRunRecord]) -> dict[str, Any]:
    return governance_layer.aggregate_usage(records)


def aggregate_artifacts(records: dict[str, TaskRunRecord]) -> dict[str, Any]:
    return governance_layer.aggregate_artifacts(
        records,
        top_task_limit=MAX_RUN_SUMMARY_TOP_TASKS,
    )


def evaluate_run_governance(
    records: dict[str, TaskRunRecord],
    run_policy: RunPolicy,
) -> dict[str, Any]:
    return governance_layer.evaluate_run_governance(
        records,
        run_policy,
        serialize_run_policy=serialize_run_policy,
        top_task_limit=MAX_RUN_SUMMARY_TOP_TASKS,
    )


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
    return provider_layer.run_process(
        command,
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=shell,
        timeout_error=timeout_error,
        on_progress_frame=(
            (lambda frame: emit_slop_log(progress_action, frame=frame))
            if progress_action is not None
            else None
        ),
        progress_interval_sec=SLOP_PROGRESS_INTERVAL_SEC,
        error_factory=OrchestratorError,
    )


def run_subprocess(
    command: list[str],
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return provider_layer.run_subprocess(
        command,
        cwd=cwd,
        timeout_sec=timeout_sec,
        timeout_error=f"Claude timed out after {timeout_sec} seconds",
        on_progress_frame=(
            (lambda frame: emit_slop_log(progress_action, frame=frame))
            if progress_action is not None
            else None
        ),
        progress_interval_sec=SLOP_PROGRESS_INTERVAL_SEC,
        error_factory=OrchestratorError,
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
    planner_effort = normalize_effort_override(
        getattr(args, "effort", None),
        location="planner effort override",
    ) or agent.effort
    prompt_budget = evaluate_prompt_budget(
        prompt_chars=prompt_render.chars,
        prompt_estimated_tokens=prompt_render.estimated_tokens,
        max_chars=agent.max_prompt_chars,
        max_estimated_tokens=agent.max_prompt_estimated_tokens,
    )
    planner_model = agent.model or (MODEL_PROFILE_TO_MODEL[agent.model_profile] if agent.model_profile else None)
    agents_json = agent_payload_for_claude(agents, selected_names=[args.planner_agent])
    output_path = planner_output_path(args.name, args.out)
    command = claude_command(
        args.claude_bin,
        agents_json,
        args.planner_agent,
        prompt_render.text,
        plan_output_schema_json(),
        model=planner_model,
        effort=planner_effort,
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
        "effort": planner_effort,
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
    dependency_records: dict[str, TaskRunRecord] | None = None,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
) -> TaskRunRecord:
    now = iso_now()
    validation_resolution = resolve_worker_validation_mode(
        task,
        agent,
        run_override=worker_validation_mode,
    )
    resolved_effort, effort_source = resolve_effort(task, agent, run_override=effort_override)
    dependency_materialization_mode = effective_dependency_materialization_mode(task)
    return TaskRunRecord(
        id=task.id,
        title=task.title,
        agent=agent_name,
        branch_context_id=task_branch_context_id(task, dependency_records),
        branch_parent_context_ids=task_branch_parent_context_ids(task, dependency_records),
        status="blocked",
        summary=reason,
        workspace_mode=workspace_mode,
        workspace_path="",
        started_at=now,
        finished_at=now,
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
        write_scope_violations=[],
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
        dependency_materialization_mode=dependency_materialization_mode,
        worker_validation_mode=validation_resolution.mode,
        worker_validation_mode_source=validation_resolution.source,
        effort=resolved_effort,
        effort_source=effort_source,
    )


def planned_record(
    task: TaskDefinition,
    agent_name: str,
    agent: AgentDefinition,
    workspace_mode: str,
    workspace_path: str,
    *,
    summary: str,
    dependency_records: dict[str, TaskRunRecord] | None = None,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
) -> TaskRunRecord:
    now = iso_now()
    validation_resolution = resolve_worker_validation_mode(
        task,
        agent,
        run_override=worker_validation_mode,
    )
    resolved_effort, effort_source = resolve_effort(task, agent, run_override=effort_override)
    dependency_materialization_mode = effective_dependency_materialization_mode(task)
    return TaskRunRecord(
        id=task.id,
        title=task.title,
        agent=agent_name,
        branch_context_id=task_branch_context_id(task, dependency_records),
        branch_parent_context_ids=task_branch_parent_context_ids(task, dependency_records),
        status="planned",
        summary=summary,
        workspace_mode=workspace_mode,
        workspace_path=workspace_path,
        started_at=now,
        finished_at=now,
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
        write_scope_violations=[],
        validation_commands=[],
        follow_ups=[],
        notes=[],
        model=resolved_model(task, agent),
        model_profile=resolved_model_profile(task, agent),
        prompt_chars=0,
        prompt_estimated_tokens=0,
        prompt_sections=[],
        prompt_budget=PromptBudgetResult(
            max_chars=None,
            max_estimated_tokens=None,
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
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=[],
        worker_validation_mode=validation_resolution.mode,
        worker_validation_mode_source=validation_resolution.source,
        effort=resolved_effort,
        effort_source=effort_source,
    )


def artifact_file_size(path: Path | None) -> int:
    return workspace_review_layer.artifact_file_size(path)


def _make_execute_record(
    task: TaskDefinition,
    workspace_mode: str,
    prepared_workspace: Path,
    started_at: str,
    model_name: str | None,
    model_profile: str | None,
    prompt_chars: int,
    prompt_estimated_tokens: int,
    prompt_render: PromptRenderResult,
    prompt_budget: PromptBudgetResult,
    prompt_path: Path,
    command_path: Path,
    dependency_materialization_mode: str,
    prepared_dependency_layers: list[DependencyLayerRecord],
    dependency_records: dict[str, TaskRunRecord],
    effort: str | None,
    effort_source: str,
    effective_validation_mode: str,
    validation_resolution: WorkerValidationModeResolution,
    *,
    status: str,
    summary: str,
    files_touched: list[str] = (),
    validation_intents: list[ValidationIntent] = (),
    validation_commands: list[str] = (),
    follow_ups: list[str] = (),
    notes: list[str] = (),
    usage: dict[str, Any] | None = None,
    return_code: int | None = None,
    stdout_path: str | None = None,
    stderr_path: str | None = None,
    result_path: str | None = None,
    stdout_bytes: int = 0,
    stderr_bytes: int = 0,
    result_bytes: int = 0,
    unknown_fields: list[str] = (),
) -> TaskRunRecord:
    return TaskRunRecord(
        id=task.id,
        title=task.title,
        agent=task.agent,
        branch_context_id=task_branch_context_id(task, dependency_records),
        branch_parent_context_ids=task_branch_parent_context_ids(task, dependency_records),
        status=status,
        summary=summary,
        workspace_mode=workspace_mode,
        workspace_path=str(prepared_workspace),
        started_at=started_at,
        finished_at=iso_now(),
        files_touched=list(files_touched),
        actual_files_touched=[],
        protected_path_violations=[],
        write_scope_violations=[],
        validation_intents=list(validation_intents),
        validation_commands=list(validation_commands),
        follow_ups=list(follow_ups),
        notes=list(notes),
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
        stdout_path=stdout_path,
        stderr_path=stderr_path,
        result_path=result_path,
        stdout_bytes=stdout_bytes,
        stderr_bytes=stderr_bytes,
        result_bytes=result_bytes,
        unknown_fields=list(unknown_fields),
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=prepared_dependency_layers,
        worker_validation_mode=effective_validation_mode,
        worker_validation_mode_source=validation_resolution.source,
        effort=effort,
        effort_source=effort_source,
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
    effort_override: str | None = None,
) -> TaskRunRecord:
    agent = agents[task.agent]
    validation_resolution = resolve_worker_validation_mode(
        task,
        agent,
        run_override=worker_validation_mode,
    )
    effective_validation_mode = validation_resolution.mode
    resolved_effort, effort_source = resolve_effort(
        task,
        agent,
        run_override=effort_override,
    )
    dependency_materialization_mode = effective_dependency_materialization_mode(task)
    model_name = resolved_model(task, agent)
    model_profile = resolved_model_profile(task, agent)
    workspace_mode = effective_workspace_mode(task, agent)
    declared_write_scope = effective_task_write_scope(task)
    workspace_path = workspaces_dir / task.id
    task_dir = run_dir / "tasks" / task.id
    task_dir.mkdir(parents=True, exist_ok=True)
    prepared_workspace = ROOT if workspace_mode == "repo" else workspace_path
    prepared_dependency_layers: list[DependencyLayerRecord] = []
    if not dry_run:
        preparation = prepare_workspace(
            plan,
            task,
            workspace_mode,
            workspace_path,
            runtime_root,
            dependency_records,
        )
        prepared_workspace = preparation.workspace_path
        prepared_dependency_layers = preparation.dependency_layers_applied
    prompt_render = worker_prompt(
        plan,
        task,
        agent,
        workspace_mode,
        prepared_workspace,
        dependency_summary(dependency_records, task),
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=prepared_dependency_layers,
        dry_run=dry_run,
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
    _task_allowed, _task_disallowed = _effective_tool_lists(task, agent)
    command = claude_command(
        claude_bin,
        agents_json,
        task.agent,
        prompt,
        task_output_schema_json(effective_validation_mode),
        model=model_name,
        effort=resolved_effort,
        permission_mode=task.permission_mode or agent.permission_mode,
        allowed_tools=_task_allowed,
        disallowed_tools=_task_disallowed,
        max_budget_usd=task.max_budget_usd if task.max_budget_usd is not None else agent.max_budget_usd,
    )
    prompt_path = task_dir / "prompt.txt"
    command_path = task_dir / "command.json"
    write_text(prompt_path, prompt)
    write_json(command_path, {"cwd": str(prepared_workspace), "command": command})

    started_at = iso_now()
    result_path = task_dir / "result.json"
    if prompt_budget.exceeded:
        record = _make_execute_record(
            task, workspace_mode, prepared_workspace, started_at,
            model_name, model_profile, prompt_chars, prompt_estimated_tokens,
            prompt_render, prompt_budget, prompt_path, command_path,
            dependency_materialization_mode, prepared_dependency_layers, dependency_records,
            resolved_effort, effort_source,
            effective_validation_mode, validation_resolution,
            status="failed",
            summary=prompt_budget_failure_summary(prompt_budget),
            follow_ups=["Reduce the task prompt scope, dependency summary, or validation hints."],
        )
        write_json(result_path, asdict(record))
        return record
    if dry_run:
        record = _make_execute_record(
            task, workspace_mode, prepared_workspace, started_at,
            model_name, model_profile, prompt_chars, prompt_estimated_tokens,
            prompt_render, prompt_budget, prompt_path, command_path,
            dependency_materialization_mode, prepared_dependency_layers, dependency_records,
            resolved_effort, effort_source,
            effective_validation_mode, validation_resolution,
            status="planned",
            summary="Dry run only; Claude was not invoked.",
        )
        write_json(result_path, asdict(record))
        return record

    stdout_path = task_dir / "stdout.json"
    stderr_path = task_dir / "stderr.txt"
    worker_result_path = task_dir / "worker-result.json"
    return_code: int | None = None
    usage: dict[str, Any] | None = None
    workspace_snapshot_before = snapshot_workspace_files(prepared_workspace)
    repo_snapshot_before = (
        snapshot_workspace_files(ROOT)
        if workspace_mode != "repo"
        else {}
    )
    actual_changed_files: list[str] = []
    changed_repo_files: list[str] = []
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
        if workspace_mode != "repo":
            changed_repo_files = diff_workspace_snapshots(
                repo_snapshot_before,
                snapshot_workspace_files(ROOT),
            )
        raw_payload: Any | None = None
        try:
            raw_payload = extract_json_payload(completed.stdout)
            usage = extract_usage(raw_payload)
        except OrchestratorError:
            raw_payload = None
        if completed.returncode != 0:
            record = _make_execute_record(
                task, workspace_mode, prepared_workspace, started_at,
                model_name, model_profile, prompt_chars, prompt_estimated_tokens,
                prompt_render, prompt_budget, prompt_path, command_path,
                dependency_materialization_mode, prepared_dependency_layers, dependency_records,
                resolved_effort, effort_source,
                effective_validation_mode, validation_resolution,
                status="failed",
                summary=completed.stderr.strip() or completed.stdout.strip() or "Claude failed",
                follow_ups=["Inspect stderr/stdout artifacts for details."],
                usage=usage,
                return_code=return_code,
                stdout_path=str(stdout_path),
                stderr_path=str(stderr_path),
                stdout_bytes=artifact_file_size(stdout_path),
                stderr_bytes=artifact_file_size(stderr_path),
            )
            apply_workspace_audit(
                record,
                reported_files=[],
                actual_files=actual_changed_files,
                declared_write_scope=declared_write_scope,
            )
            apply_repository_isolation_audit(
                record,
                workspace_mode=workspace_mode,
                changed_repo_files=changed_repo_files,
            )
            write_json(result_path, asdict(record))
            return record

        if raw_payload is None:
            raise OrchestratorError("Claude output did not contain a standalone JSON payload")
        payload = coerce_worker_result(
            raw_payload,
            worker_validation_mode=effective_validation_mode,
        )
        write_json(worker_result_path, payload)
        record = _make_execute_record(
            task, workspace_mode, prepared_workspace, started_at,
            model_name, model_profile, prompt_chars, prompt_estimated_tokens,
            prompt_render, prompt_budget, prompt_path, command_path,
            dependency_materialization_mode, prepared_dependency_layers, dependency_records,
            resolved_effort, effort_source,
            effective_validation_mode, validation_resolution,
            status=str(payload["status"]),
            summary=str(payload["summary"]),
            files_touched=[str(item) for item in payload["filesTouched"]],
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
            usage=usage,
            return_code=return_code,
            stdout_path=str(stdout_path),
            stderr_path=str(stderr_path),
            result_path=str(worker_result_path),
            stdout_bytes=artifact_file_size(stdout_path),
            stderr_bytes=artifact_file_size(stderr_path),
            result_bytes=artifact_file_size(worker_result_path),
            unknown_fields=[str(item) for item in payload.get("unknownFields", [])],
        )
        apply_workspace_audit(
            record,
            reported_files=[str(item) for item in payload["filesTouched"]],
            actual_files=actual_changed_files,
            declared_write_scope=declared_write_scope,
        )
        apply_repository_isolation_audit(
            record,
            workspace_mode=workspace_mode,
            changed_repo_files=changed_repo_files,
        )
        write_json(result_path, asdict(record))
        return record
    except OrchestratorError as exc:
        write_text(stderr_path, str(exc))
        actual_changed_files = diff_workspace_snapshots(
            workspace_snapshot_before,
            snapshot_workspace_files(prepared_workspace),
        )
        if workspace_mode != "repo":
            changed_repo_files = diff_workspace_snapshots(
                repo_snapshot_before,
                snapshot_workspace_files(ROOT),
            )
        record = _make_execute_record(
            task, workspace_mode, prepared_workspace, started_at,
            model_name, model_profile, prompt_chars, prompt_estimated_tokens,
            prompt_render, prompt_budget, prompt_path, command_path,
            dependency_materialization_mode, prepared_dependency_layers, dependency_records,
            resolved_effort, effort_source,
            effective_validation_mode, validation_resolution,
            status="failed",
            summary=str(exc),
            follow_ups=["Retry after fixing the worker failure."],
            usage=usage,
            return_code=return_code,
            stdout_path=str(stdout_path) if stdout_path.exists() else None,
            stderr_path=str(stderr_path),
            stdout_bytes=artifact_file_size(stdout_path) if stdout_path.exists() else 0,
            stderr_bytes=artifact_file_size(stderr_path),
        )
        apply_workspace_audit(
            record,
            reported_files=[],
            actual_files=actual_changed_files,
            declared_write_scope=declared_write_scope,
        )
        apply_repository_isolation_audit(
            record,
            workspace_mode=workspace_mode,
            changed_repo_files=changed_repo_files,
        )
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
    effort_override: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
    run_events: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    worker_validation_override = (
        normalize_worker_validation_mode(
            worker_validation_mode,
            location="manifest worker validation mode override",
        )
        if worker_validation_mode
        else None
    )
    normalized_effort_override = normalize_effort_override(
        effort_override,
        location="manifest effort override",
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
    task_efforts = effective_plan_efforts(
        plan,
        agents,
        run_override=normalized_effort_override,
    )
    task_effort_sources = effective_plan_effort_sources(
        plan,
        agents,
        run_override=normalized_effort_override,
    )
    task_model_profiles = effective_plan_model_profiles(plan, agents)
    task_models = effective_plan_models(plan, agents)
    complex_model_tasks = complex_model_task_ids(task_model_profiles)
    topology = analyze_plan_topology(plan, agents)
    usage_totals = aggregate_usage(records)
    run_governance = evaluate_run_governance(records, plan.run_policy)
    parallel_conflicts = detect_parallel_scope_conflicts(plan, agents)
    payload = {
        "runId": run_id,
        "generatedAt": iso_now(),
        "dryRun": dry_run,
        "workerValidationMode": summarized_worker_validation_mode(
            list(task_worker_validation_modes.values())
        ),
        "workerValidationModeOverride": worker_validation_override,
        "effortOverride": normalized_effort_override,
        "taskWorkerValidationModes": task_worker_validation_modes,
        "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
        "taskEfforts": task_efforts,
        "taskEffortSources": task_effort_sources,
        "taskModels": task_models,
        "taskModelProfiles": task_model_profiles,
        "complexModelTaskIds": complex_model_tasks,
        "complexModelTaskCount": len(complex_model_tasks),
        "topology": topology,
        "repoRoot": str(ROOT),
        "planPath": str(plan_path),
        "agentsPath": str(agents_path),
        "runtimeRoot": str(runtime_root),
        "runDir": str(run_dir),
        "workspacesDir": str(workspaces_dir),
        "runPolicy": serialize_run_policy(plan.run_policy),
        "runGovernance": run_governance,
        "plan": {
            "name": plan.name,
            "goal": plan.goal,
            "taskIds": [task.id for task in plan.tasks],
            "parallelConflicts": parallel_conflicts,
        },
        "events": list(run_events or []),
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
    effort_override: str | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    seeded_task_ids: list[str] | None = None,
    run_events: list[dict[str, Any]] | None = None,
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
            effort_override=effort_override,
            retry_of_run_id=retry_of_run_id,
            requested_task_ids=requested_task_ids,
            retried_task_ids=retried_task_ids,
            seeded_task_ids=seeded_task_ids,
            run_events=run_events,
        ),
    )


def write_selected_plan_snapshot(run_dir: Path, plan: TaskPlan) -> None:
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
                    "dependsOn": task.depends_on,
                    "readPaths": task.read_paths,
                    "writePaths": task.write_paths,
                    "constraints": task.constraints,
                    "validation": task.validation,
                    "workspaceMode": task.workspace_mode,
                    "model": task.model,
                    "modelProfile": task.model_profile,
                    "contextMode": task.context_mode,
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
    effort_override: str | None = None,
    initial_records: dict[str, TaskRunRecord] | None = None,
    retry_of_run_id: str | None = None,
    requested_task_ids: list[str] | None = None,
    retried_task_ids: list[str] | None = None,
    existing_run_id: str | None = None,
    existing_run_dir: Path | None = None,
    existing_workspaces_dir: Path | None = None,
    write_plan_snapshot: bool = True,
) -> dict[str, Any]:
    worker_validation_override = (
        normalize_worker_validation_mode(
            worker_validation_mode,
            location="run worker validation mode override",
        )
        if worker_validation_mode
        else None
    )
    normalized_effort_override = normalize_effort_override(
        effort_override,
        location="run effort override",
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
    task_efforts = effective_plan_efforts(
        plan,
        agents,
        run_override=normalized_effort_override,
    )
    task_effort_sources = effective_plan_effort_sources(
        plan,
        agents,
        run_override=normalized_effort_override,
    )
    topological_batches(plan.tasks)
    validate_scope_contract(plan, agents)
    if not dry_run:
        ensure_claude_available(claude_bin)

    runtime_root = runtime_root.resolve()
    run_id = existing_run_id or (
        f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"
        f"-{slugify(plan.name)}-{uuid4().hex[:8]}"
    )
    run_dir = existing_run_dir.resolve() if existing_run_dir is not None else runtime_root / "runs" / run_id
    workspaces_dir = (
        existing_workspaces_dir.resolve()
        if existing_workspaces_dir is not None
        else runtime_root / "workspaces" / run_id
    )
    run_dir.mkdir(parents=True, exist_ok=True)
    workspaces_dir.mkdir(parents=True, exist_ok=True)
    if write_plan_snapshot:
        write_selected_plan_snapshot(run_dir, plan)

    seeded_task_ids = sorted(initial_records or {})
    agents_json_by_name = {
        name: agent_payload_for_claude(agents, selected_names=[name])
        for name in agents
    }
    records: dict[str, TaskRunRecord] = dict(initial_records or {})
    run_events: list[dict[str, Any]] = []
    append_run_event(
        run_events,
        phase="run-start",
        task_ids=[task.id for task in plan.tasks],
        branch_context_ids=[task_branch_context_id(task, records) for task in plan.tasks],
        details={
            "dryRun": dry_run,
            "maxParallel": max(max_parallel, 1),
            "resume": existing_run_id is not None,
            "retryOfRunId": retry_of_run_id,
            "seededTaskIds": seeded_task_ids,
        },
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
                effort_override=normalized_effort_override,
                retry_of_run_id=retry_of_run_id,
                requested_task_ids=requested_task_ids,
                retried_task_ids=retried_task_ids,
                seeded_task_ids=seeded_task_ids,
                run_events=run_events,
            )
            continue

        if fail_fast_triggered or stop_scheduling_reason is not None:
            for task_id, task in list(pending.items()):
                records[task_id] = blocked_record(
                    task,
                    task.agent,
                    agents[task.agent],
                    effective_workspace_mode(task, agents[task.agent]),
                    reason=stop_scheduling_reason
                    or "Coordinator stopped scheduling new tasks after a worker failure.",
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
                    message=stop_scheduling_reason
                    or "Coordinator stopped scheduling new tasks after a worker failure.",
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
        append_run_event(
            run_events,
            phase="batch-ready",
            task_ids=[task.id for task in batch],
            branch_context_ids=[task_branch_context_id(task, records) for task in batch],
            details={"pendingTaskIds": sorted(pending)},
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
                    agents_json=agents_json_by_name[task.agent],
                    dry_run=dry_run,
                    worker_validation_mode=worker_validation_override,
                    effort_override=normalized_effort_override,
                ): task
                for task in batch
            }
            for future in as_completed(future_map):
                task = future_map[future]
                records[task.id] = future.result()
                append_run_event(
                    run_events,
                    phase="task-finished",
                    task_id=task.id,
                    parent_task_ids=task.depends_on,
                    branch_context_id=records[task.id].branch_context_id,
                    status=records[task.id].status,
                    message=records[task.id].summary,
                )
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
                    effort_override=normalized_effort_override,
                    retry_of_run_id=retry_of_run_id,
                    requested_task_ids=requested_task_ids,
                    retried_task_ids=retried_task_ids,
                    seeded_task_ids=seeded_task_ids,
                    run_events=run_events,
                )
                if not continue_on_error and records[task.id].status not in {"completed", "planned"}:
                    fail_fast_triggered = True
                    stop_scheduling_reason = "Coordinator stopped scheduling new tasks after a worker failure."

    append_run_event(
        run_events,
        phase="run-finished",
        details={"remainingTaskIds": sorted(pending)},
    )
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
        effort_override=normalized_effort_override,
        retry_of_run_id=retry_of_run_id,
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
        seeded_task_ids=seeded_task_ids,
        run_events=run_events,
    )
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
        "workerValidationMode": summarized_worker_validation_mode(
            list(task_worker_validation_modes.values())
        ),
        "workerValidationModeOverride": worker_validation_override,
        "effortOverride": normalized_effort_override,
        "taskWorkerValidationModes": task_worker_validation_modes,
        "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
        "taskEfforts": task_efforts,
        "taskEffortSources": task_effort_sources,
        "taskModels": task_models,
        "taskModelProfiles": task_model_profiles,
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
        effort_override=getattr(args, "effort", None),
    )


def resume_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    previous_records = {record.id: record for record in selected_run_records(manifest, [])}
    if not previous_records:
        raise OrchestratorError(f"{manifest_path}: run manifest contains no task records to resume")

    run_dir = manifest_run_dir(manifest_path, manifest)
    workspaces_dir = manifest_workspaces_dir(manifest, run_dir=run_dir)
    runtime_root = manifest_required_path(manifest, "runtimeRoot", location=str(manifest_path))
    agents_path = (
        Path(args.agents).resolve()
        if args.agents
        else manifest_required_path(manifest, "agentsPath", location=str(manifest_path))
    )
    if not agents_path.exists():
        raise OrchestratorError(f"Resume source agents file '{agents_path}' does not exist")
    agents = load_agents(agents_path)

    source_plan_path = manifest_selected_plan_path(
        manifest_path,
        manifest,
        location=str(manifest_path),
    )
    if not source_plan_path.exists():
        raise OrchestratorError(f"Resume source plan '{source_plan_path}' does not exist")
    base_plan = load_task_plan(source_plan_path, agents)

    source_worker_validation_override = manifest_worker_validation_override(
        manifest,
        location=str(manifest_path),
    )
    resume_worker_validation_mode = (
        normalize_worker_validation_mode(
            getattr(args, "worker_validation_mode", "") or source_worker_validation_override,
            location=f"resume source '{manifest_path}' worker validation mode",
        )
        if (getattr(args, "worker_validation_mode", "") or source_worker_validation_override)
        else None
    )

    requested_task_ids = list(args.selected_tasks)
    if not requested_task_ids:
        requested_task_ids = [
            task.id
            for task in base_plan.tasks
            if previous_records.get(task.id) is None or previous_records[task.id].status != "completed"
        ]
    if not requested_task_ids:
        raise OrchestratorError(
            "Run manifest contains no resumable tasks; use --task to pick tasks explicitly"
        )

    resume_scope = selected_plan(base_plan, requested_task_ids)
    resume_scope_ids = {task.id for task in resume_scope.tasks}
    requested_set = set(requested_task_ids)
    initial_records: dict[str, TaskRunRecord] = {}
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
            str((workspaces_dir / task.id).resolve())
            if effective_workspace_mode(task, agents[task.agent]) != "repo"
            else str(ROOT),
            summary="Pending from the existing run; not selected for this resume.",
            dependency_records=initial_records,
            worker_validation_mode=resume_worker_validation_mode,
            effort_override=normalize_effort_override(
                getattr(args, "effort", None),
                location="resume effort override",
            ),
        )
    if not resumed_task_ids:
        raise OrchestratorError("Nothing to resume; all selected tasks are already completed")

    payload = run_loaded_plan(
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
    )
    payload["sourceManifestPath"] = str(manifest_path)
    payload["requestedTaskIds"] = requested_task_ids
    payload["resumedTaskIds"] = resumed_task_ids
    payload["preservedTaskIds"] = sorted(initial_records)
    payload["resumedInPlace"] = True
    return payload


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

    plan_path = manifest_selected_plan_path(
        manifest_path,
        manifest,
        location=str(manifest_path),
    )
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

    source_worker_validation_override = manifest_worker_validation_override(
        manifest,
        location=str(manifest_path),
    )
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
                getattr(args, "worker_validation_mode", "") or source_worker_validation_override,
                location=f"retry source '{manifest_path}' worker validation mode",
            )
            if (getattr(args, "worker_validation_mode", "") or source_worker_validation_override)
            else None
        ),
        effort_override=getattr(args, "effort", None),
        initial_records=initial_records,
        retry_of_run_id=str(manifest.get("runId", "")),
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
    )
    payload["sourceManifestPath"] = str(manifest_path)
    return payload


def parse_iso_datetime(value: Any) -> datetime | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone()


def datetime_to_iso(value: datetime) -> str:
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone().isoformat()


def cleanup_loaded_run(manifest_path: Path, manifest: dict[str, Any]) -> dict[str, Any]:
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


def cleanup_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    return cleanup_loaded_run(manifest_path, manifest)


def runtime_manifest_entries(runtime_root: Path) -> list[tuple[Path, dict[str, Any]]]:
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


def summarize_run_events(events_payload: Any) -> dict[str, Any]:
    return run_summary_layer.summarize_run_events(events_payload)


def summarize_approval_checkpoints(manifest: dict[str, Any]) -> dict[str, Any]:
    return run_summary_layer.summarize_approval_checkpoints(manifest)


def derive_run_lifecycle_state(
    *,
    manifest: dict[str, Any],
    records: list[TaskRunRecord],
    summary_base: dict[str, Any],
    promotion_readiness: dict[str, Any],
    approval_summary: dict[str, Any],
) -> tuple[str, str]:
    _ = manifest
    return run_summary_layer.derive_run_lifecycle_state(
        records=records,
        summary_base=summary_base,
        promotion_readiness=promotion_readiness,
        approval_summary=approval_summary,
    )


def summarize_run_manifest(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    now: datetime | None = None,
) -> tuple[dict[str, Any], datetime]:
    return run_summary_layer.summarize_run_manifest(
        manifest_path,
        manifest,
        now=now,
        manifest_run_dir=manifest_run_dir,
        manifest_workspaces_dir=manifest_workspaces_dir,
        selected_run_records=selected_run_records,
        count_statuses=count_statuses,
        summarize_promotion_readiness=summarize_promotion_readiness,
        parse_iso_datetime=parse_iso_datetime,
        datetime_to_iso=datetime_to_iso,
    )


def append_run_event(
    events: list[dict[str, Any]],
    *,
    phase: str,
    task_id: str | None = None,
    task_ids: list[str] | None = None,
    parent_task_ids: list[str] | None = None,
    branch_context_id: str | None = None,
    branch_context_ids: list[str] | None = None,
    status: str | None = None,
    message: str | None = None,
    details: dict[str, Any] | None = None,
) -> None:
    payload: dict[str, Any] = {
        "ts": iso_now(),
        "phase": phase,
    }
    if task_id:
        payload["taskId"] = task_id
    if task_ids:
        payload["taskIds"] = list(task_ids)
    if parent_task_ids is not None:
        payload["parentTaskIds"] = list(parent_task_ids)
    if branch_context_id:
        payload["branchContextId"] = branch_context_id
    if branch_context_ids:
        payload["branchContextIds"] = list(branch_context_ids)
    if status:
        payload["status"] = status
    if message:
        payload["message"] = message
    if details:
        payload["details"] = details
    events.append(payload)


def status_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    summary, _ = summarize_run_manifest(
        manifest_path,
        manifest,
        now=datetime.now(timezone.utc).astimezone(),
    )
    task_efforts = summary.get("taskEfforts", {})
    task_effort_sources = summary.get("taskEffortSources", {})
    records = selected_run_records(manifest, args.selected_tasks)
    task_payloads: list[dict[str, Any]] = []
    review_summary = {
        "changedTaskCount": 0,
        "changedFileCount": 0,
        "protectedPathViolationCount": 0,
        "writeScopeViolationCount": 0,
        "validationSuggestionCount": 0,
    }
    for record in records:
        review_payload, _ = task_review_summary(record, context_lines=0)
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
                "filesPromotable": int(task_promotion_operations(record)[0]["filesPromotable"]),
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


def _evaluation_check(
    name: str,
    status: str,
    summary: str,
    *,
    evidence: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return {
        "name": name,
        "status": status,
        "summary": summary,
        "evidence": evidence or {},
    }


def summarize_evaluation_score(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    status_counts = count_statuses([str(check.get("status", "pass") or "pass") for check in checks])
    weights = {"pass": 1.0, "warn": 0.5, "fail": 0.0}
    earned_points = sum(weights.get(str(check.get("status", "pass") or "pass"), 0.0) for check in checks)
    max_points = float(len(checks))
    score_percent = round((earned_points / max_points) * 100.0, 1) if max_points else 100.0
    status = "pass"
    if int(status_counts.get("fail", 0) or 0) > 0:
        status = "fail"
    elif int(status_counts.get("warn", 0) or 0) > 0:
        status = "warn"
    return {
        "status": status,
        "statusCounts": status_counts,
        "totalChecks": len(checks),
        "earnedPoints": round(earned_points, 3),
        "maxPoints": round(max_points, 3),
        "scorePercent": score_percent,
        "promotionReady": bool(run_summary.get("promotionReady", False)),
        "resumable": bool(run_summary.get("isResumable", False)),
        "taskCount": int(run_summary.get("taskCount", 0) or 0),
        "batchCount": int((run_summary.get("topologyBatchCount", 0) or 0)),
        "parallelWidth": int((run_summary.get("topologyMaxParallelWidth", 0) or 0)),
    }


def benchmark_dimensions_for_evaluation(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    by_name = {str(check.get("name", "")): check for check in checks}

    def worst_status(names: list[str]) -> str:
        rank = {"pass": 0, "warn": 1, "fail": 2}
        return max(
            (str(by_name.get(name, {}).get("status", "pass") or "pass") for name in names),
            key=lambda item: rank[item],
            default="pass",
        )

    decomposition_status = worst_status(["over-delegation", "reviewer-hops", "effort-fit"])
    retry_status = str(by_name.get("retry-resume-contract", {}).get("status", "pass") or "pass")
    promotion_status = str(by_name.get("promotion-readiness", {}).get("status", "pass") or "pass")
    task_count = int(run_summary.get("taskCount", 0) or 0)
    batch_count = int(run_summary.get("topologyBatchCount", 0) or 0)
    parallel_width = int(run_summary.get("topologyMaxParallelWidth", 0) or 0)
    if task_count <= 1:
        parallel_status = "pass"
        parallel_summary = "Single-task run had no parallel opportunity requirement."
    elif parallel_width > 1:
        parallel_status = "pass"
        parallel_summary = "Run exposed concurrent-ready width greater than one."
    else:
        parallel_status = "warn"
        parallel_summary = "Run stayed fully serial; compare dependency shape and task split for missed parallel opportunity."
    return {
        "decompositionQuality": {
            "status": decomposition_status,
            "summary": "Composition quality across delegation, reviewer hops, and effort fit.",
            "checkNames": ["over-delegation", "reviewer-hops", "effort-fit"],
        },
        "retryCorrectness": {
            "status": retry_status,
            "summary": "Retry/resume metadata consistency.",
            "checkNames": ["retry-resume-contract"],
        },
        "reviewPromotionAccuracy": {
            "status": promotion_status,
            "summary": "Promotion-readiness accuracy against retained run state.",
            "checkNames": ["promotion-readiness"],
        },
        "parallelEfficiency": {
            "status": parallel_status,
            "summary": parallel_summary,
            "evidence": {
                "taskCount": task_count,
                "batchCount": batch_count,
                "parallelWidth": parallel_width,
            },
        },
    }


def evaluate_loaded_run_quality(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    selected_tasks: list[str],
) -> dict[str, Any]:
    summary, _ = summarize_run_manifest(
        manifest_path,
        manifest,
        now=datetime.now(timezone.utc).astimezone(),
    )
    records = selected_run_records(manifest, selected_tasks)
    topology = manifest.get("topology") if isinstance(manifest.get("topology"), dict) else {}
    run_start_event = next(
        (
            event
            for event in (manifest.get("events") or [])
            if isinstance(event, dict) and str(event.get("phase", "")) == "run-start"
        ),
        None,
    )
    run_start_details = (
        run_start_event.get("details")
        if isinstance(run_start_event, dict) and isinstance(run_start_event.get("details"), dict)
        else {}
    )
    checks: list[dict[str, Any]] = []

    reviewer_task_count = int(topology.get("reviewerTaskCount", 0) or 0)
    write_task_count = int(topology.get("writeTaskCount", 0) or 0)
    if reviewer_task_count > 0 and write_task_count == 0 and len(records) > reviewer_task_count:
        checks.append(
            _evaluation_check(
                "over-delegation",
                "warn",
                "Read-only run still allocated reviewer-only work.",
                evidence={
                    "reviewerTaskCount": reviewer_task_count,
                    "writeTaskCount": write_task_count,
                    "taskCount": len(records),
                },
            )
        )
    else:
        checks.append(
            _evaluation_check(
                "over-delegation",
                "pass",
                "Task split is proportionate to the run shape.",
                evidence={
                    "reviewerTaskCount": reviewer_task_count,
                    "writeTaskCount": write_task_count,
                    "taskCount": len(records),
                },
            )
        )

    topology_warnings = [
        warning
        for warning in (topology.get("warnings") or [])
        if isinstance(warning, dict)
    ]
    optional_reviewer_warnings = [
        warning
        for warning in topology_warnings
        if str(warning.get("kind", "")) == "read-only-review-optional"
    ]
    if optional_reviewer_warnings:
        checks.append(
            _evaluation_check(
                "reviewer-hops",
                "warn",
                "Topology marks at least one reviewer hop as optional.",
                evidence={"warnings": optional_reviewer_warnings},
            )
        )
    else:
        checks.append(
            _evaluation_check(
                "reviewer-hops",
                "pass",
                "No optional reviewer hop warning was detected.",
                evidence={"warningCount": len(topology_warnings)},
            )
        )

    legacy_validation_tasks = sorted(record.id for record in records if record.validation_commands)
    if legacy_validation_tasks:
        checks.append(
            _evaluation_check(
                "validation-suggestions",
                "warn",
                "Legacy raw validation commands are still present in the run record.",
                evidence={"taskIds": legacy_validation_tasks},
            )
        )
    else:
        checks.append(
            _evaluation_check(
                "validation-suggestions",
                "pass",
                "Validation suggestions stayed on structured intents or were absent.",
                evidence={
                    "intentTaskCount": sum(1 for record in records if record.validation_intents),
                },
            )
        )

    task_efforts = (
        manifest.get("taskEfforts")
        if isinstance(manifest.get("taskEfforts"), dict)
        else summary.get("taskEfforts", {})
    )
    task_model_profiles = (
        manifest.get("taskModelProfiles")
        if isinstance(manifest.get("taskModelProfiles"), dict)
        else {}
    )
    read_only_task_ids = [str(task_id) for task_id in topology.get("readOnlyTaskIds", []) or []]
    overspecified_effort_task_ids = sorted(
        task_id
        for task_id in read_only_task_ids
        if str(task_efforts.get(task_id, "") or "") in {"high", "xhigh"}
        and str(task_model_profiles.get(task_id, "") or "") in {"simple", "balanced"}
    )
    if overspecified_effort_task_ids:
        checks.append(
            _evaluation_check(
                "effort-fit",
                "warn",
                "Read-only tasks are using high effort on non-complex model profiles.",
                evidence={
                    "taskIds": overspecified_effort_task_ids,
                    "taskEfforts": {task_id: task_efforts.get(task_id) for task_id in overspecified_effort_task_ids},
                    "taskModelProfiles": {
                        task_id: task_model_profiles.get(task_id)
                        for task_id in overspecified_effort_task_ids
                    },
                },
            )
        )
    else:
        checks.append(
            _evaluation_check(
                "effort-fit",
                "pass",
                "Resolved effort looks proportionate to the retained task shape.",
                evidence={
                    "taskEffortCounts": summary.get("effortCounts", {}),
                },
            )
        )

    retry_of_run_id = str(manifest.get("retryOfRunId", "")).strip()
    requested_task_ids = [str(task_id) for task_id in manifest.get("requestedTaskIds", []) or []]
    retried_task_ids = [str(task_id) for task_id in manifest.get("retriedTaskIds", []) or []]
    seeded_task_ids = [str(task_id) for task_id in manifest.get("seededTaskIds", []) or []]
    is_resume = bool(run_start_details.get("resume", False))
    contract_status = "pass"
    contract_summary = "Resume/retry metadata is internally consistent."
    contract_evidence: dict[str, Any] = {
        "retryOfRunId": retry_of_run_id or None,
        "requestedTaskIds": requested_task_ids,
        "retriedTaskIds": retried_task_ids,
        "seededTaskIds": seeded_task_ids,
        "resume": is_resume,
    }
    if retry_of_run_id and not retried_task_ids:
        contract_status = "fail"
        contract_summary = "Retry run is missing retried task ids."
    elif retry_of_run_id and is_resume:
        contract_status = "fail"
        contract_summary = "Run metadata claims both retry and in-place resume."
    elif requested_task_ids and retry_of_run_id and not set(retried_task_ids).issubset(set(requested_task_ids)):
        contract_status = "fail"
        contract_summary = "Retried task ids are not a subset of the requested retry scope."
    checks.append(
        _evaluation_check(
            "retry-resume-contract",
            contract_status,
            contract_summary,
            evidence=contract_evidence,
        )
    )

    promotion_summary = summary["promotionSummary"]
    changed_completed_tasks = sorted(
        record.id
        for record in records
        if record.status == "completed" and (record.actual_files_touched or record.files_touched)
    )
    promotion_status = "pass"
    promotion_check_summary = "Promotion readiness is consistent with task state and promotable files."
    if summary["promotionReady"] and (summary["hasFailures"] or summary["hasBlocked"]):
        promotion_status = "fail"
        promotion_check_summary = "Promotion is marked ready even though the run still has failed or blocked tasks."
    elif not summary["promotionReady"] and promotion_summary["allowed"] and promotion_summary["filesPromotable"] > 0:
        promotion_status = "fail"
        promotion_check_summary = "Promotion summary reports promotable files but the run summary is not marked ready."
    elif (
        not summary["promotionReady"]
        and changed_completed_tasks
        and not summary["hasFailures"]
        and not summary["hasBlocked"]
        and promotion_summary["filesPromotable"] == 0
    ):
        promotion_status = "warn"
        promotion_check_summary = "Completed tasks changed files, but nothing is promotable; review ownership and scope evidence."
    checks.append(
        _evaluation_check(
            "promotion-readiness",
            promotion_status,
            promotion_check_summary,
            evidence={
                "promotionReady": summary["promotionReady"],
                "promotionSummary": promotion_summary,
                "changedCompletedTaskIds": changed_completed_tasks,
            },
        )
    )

    status_rank = {"pass": 0, "warn": 1, "fail": 2}
    overall_status = max((check["status"] for check in checks), key=lambda item: status_rank[item], default="pass")
    score_summary = summarize_evaluation_score(checks, run_summary=summary)
    benchmark_dimensions = benchmark_dimensions_for_evaluation(checks, run_summary=summary)
    return {
        "run": summary,
        "traceSummary": summary["traceSummary"],
        "branchSummary": summary["branchSummary"],
        "status": overall_status,
        "scoreSummary": score_summary,
        "benchmarkDimensions": benchmark_dimensions,
        "checkCount": len(checks),
        "warningCheckCount": sum(1 for check in checks if check["status"] == "warn"),
        "failingCheckCount": sum(1 for check in checks if check["status"] == "fail"),
        "checks": checks,
    }


def evaluate_run_quality(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    return evaluate_loaded_run_quality(
        manifest_path,
        manifest,
        selected_tasks=list(args.selected_tasks),
    )


def inventory_runs(args: argparse.Namespace) -> dict[str, Any]:
    runtime_root = Path(args.runtime_root).resolve()
    now = datetime.now(timezone.utc).astimezone()
    entries = [
        (*summarize_run_manifest(manifest_path, manifest, now=now), manifest_path, manifest)
        for manifest_path, manifest in runtime_manifest_entries(runtime_root)
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


def evaluate_run_corpus(args: argparse.Namespace) -> dict[str, Any]:
    runtime_root = Path(args.runtime_root).resolve()
    now = datetime.now(timezone.utc).astimezone()
    entries = [
        (*summarize_run_manifest(manifest_path, manifest, now=now), manifest_path, manifest)
        for manifest_path, manifest in runtime_manifest_entries(runtime_root)
    ]
    entries.sort(key=lambda item: item[1], reverse=True)
    limit = max(int(args.limit), 0)
    visible = entries[:limit] if limit else entries
    run_evaluations = [
        evaluate_loaded_run_quality(manifest_path, manifest, selected_tasks=[])
        for _, _, manifest_path, manifest in visible
    ]
    corpus_status_counts = count_statuses([str(item["status"]) for item in run_evaluations])
    score_status_counts = count_statuses([str(item["scoreSummary"]["status"]) for item in run_evaluations])
    dimension_status_counts: dict[str, dict[str, int]] = {}
    for item in run_evaluations:
        for dimension_name, dimension in item["benchmarkDimensions"].items():
            if not isinstance(dimension, dict):
                continue
            dimension_status_counts.setdefault(dimension_name, {})
            status = str(dimension.get("status", "pass") or "pass")
            dimension_status_counts[dimension_name][status] = (
                dimension_status_counts[dimension_name].get(status, 0) + 1
            )
    average_score_percent = round(
        (
            sum(float(item["scoreSummary"]["scorePercent"]) for item in run_evaluations)
            / len(run_evaluations)
        ),
        1,
    ) if run_evaluations else 100.0
    return {
        "runtimeRoot": str(runtime_root),
        "runCount": len(entries),
        "shownRunCount": len(run_evaluations),
        "statusCounts": corpus_status_counts,
        "scoreStatusCounts": score_status_counts,
        "averageScorePercent": average_score_percent,
        "dimensionStatusCounts": dimension_status_counts,
        "runs": [
            {
                "runId": item["run"]["runId"],
                "plan": item["run"]["plan"],
                "generatedAt": item["run"]["generatedAt"],
                "status": item["status"],
                "scoreSummary": item["scoreSummary"],
                "benchmarkDimensions": item["benchmarkDimensions"],
                "traceSummary": item["traceSummary"],
                "branchSummary": item["branchSummary"],
                "flags": item["run"]["flags"],
            }
            for item in run_evaluations
        ],
    }


def prune_runs(args: argparse.Namespace) -> dict[str, Any]:
    runtime_root = Path(args.runtime_root).resolve()
    older_than_days = float(args.older_than_days)
    if older_than_days < 0:
        raise OrchestratorError("--older-than-days must be >= 0")
    keep = max(int(args.keep), 0)
    now = datetime.now(timezone.utc).astimezone()
    entries = [
        (*summarize_run_manifest(manifest_path, manifest, now=now), manifest_path, manifest)
        for manifest_path, manifest in runtime_manifest_entries(runtime_root)
    ]
    entries.sort(key=lambda item: item[1], reverse=True)
    keep_run_ids = {
        summary["runId"]
        for summary, _, _, _ in entries[:keep]
    }
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
            removed.append(cleanup_loaded_run(manifest_path, manifest))
        except OrchestratorError as exc:
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


def collect_validation_commands(
    records: list[TaskRunRecord],
    *,
    included_statuses: set[str],
    execution_scope: str = DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    task_payloads: list[dict[str, Any]] = []
    suggestions_by_command: dict[tuple[str, str | None], dict[str, Any]] = {}
    suggestion_order: list[tuple[str, str | None]] = []
    for record in records:
        execution_target = validation_execution_target(record, execution_scope=execution_scope)
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
                "workspaceMode": record.workspace_mode,
                "workspacePath": record.workspace_path,
                "executionScope": execution_scope,
                "executionCwd": execution_target.get("cwd"),
                "executionTargetAccepted": bool(execution_target.get("accepted", False)),
                "executionTargetReason": execution_target.get("reason"),
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
            aggregate_key = (
                command_text,
                str(execution_target.get("cwd") or "")
                if execution_scope == "task-workspace"
                else DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
            )
            payload = suggestions_by_command.get(aggregate_key)
            if payload is None:
                payload = {
                    "sourceKind": "intent",
                    "command": command_text,
                    "taskIds": [],
                    "policy": validation_intent_policy(intent),
                    "intent": asdict(intent),
                    "compatibilityOnly": False,
                    "executionScope": execution_scope,
                    "cwd": execution_target.get("cwd"),
                    "workspaceMode": execution_target.get("workspaceMode"),
                    "workspacePath": execution_target.get("workspacePath"),
                    "executionTargetAccepted": bool(execution_target.get("accepted", False)),
                    "executionTargetReason": execution_target.get("reason"),
                }
                suggestions_by_command[aggregate_key] = payload
                suggestion_order.append(aggregate_key)
            payload["taskIds"].append(record.id)
        for command in commands:
            policy = validation_command_policy(command)
            aggregate_key = (
                command,
                str(execution_target.get("cwd") or "")
                if execution_scope == "task-workspace"
                else DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
            )
            payload = suggestions_by_command.get(aggregate_key)
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
                    "executionScope": execution_scope,
                    "cwd": execution_target.get("cwd"),
                    "workspaceMode": execution_target.get("workspaceMode"),
                    "workspacePath": execution_target.get("workspacePath"),
                    "executionTargetAccepted": bool(execution_target.get("accepted", False)),
                    "executionTargetReason": execution_target.get("reason"),
                }
                suggestions_by_command[aggregate_key] = payload
                suggestion_order.append(aggregate_key)
            payload["taskIds"].append(record.id)
    command_payloads = []
    for aggregate_key in suggestion_order:
        payload = suggestions_by_command[aggregate_key]
        payload["taskIds"] = dedupe_strings([str(task_id) for task_id in payload["taskIds"]])
        command_payloads.append(payload)
    return task_payloads, command_payloads


def validation_execution_target(
    record: TaskRunRecord,
    *,
    execution_scope: str,
) -> dict[str, Any]:
    if execution_scope not in VALIDATE_RUN_EXECUTION_SCOPES:
        raise OrchestratorError(
            f"validate-run: unsupported execution scope '{execution_scope}'"
        )
    if execution_scope == "repo":
        return {
            "cwd": str(ROOT),
            "workspaceMode": "repo",
            "workspacePath": str(ROOT),
            "accepted": True,
            "reason": None,
        }
    if record.workspace_mode == "repo":
        return {
            "cwd": str(ROOT),
            "workspaceMode": "repo",
            "workspacePath": str(ROOT),
            "accepted": True,
            "reason": None,
        }
    if not record.workspace_path:
        return {
            "cwd": None,
            "workspaceMode": record.workspace_mode,
            "workspacePath": None,
            "accepted": False,
            "reason": f"{record.id}: task workspace path is missing",
        }
    workspace_root = Path(record.workspace_path).resolve()
    if not workspace_root.exists():
        return {
            "cwd": str(workspace_root),
            "workspaceMode": record.workspace_mode,
            "workspacePath": str(workspace_root),
            "accepted": False,
            "reason": f"{record.id}: task workspace '{workspace_root}' does not exist",
        }
    return {
        "cwd": str(workspace_root),
        "workspaceMode": record.workspace_mode,
        "workspacePath": str(workspace_root),
        "accepted": True,
        "reason": None,
    }


def run_shell_command_text(
    command_text: str,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    try:
        tokens = shlex.split(command_text, posix=(os.name != "nt"))
    except ValueError:
        tokens = None
    timeout_error = f"Validation command timed out after {timeout_sec} seconds: {command_text}"
    if tokens:
        return run_process(
            tokens,
            cwd=cwd,
            timeout_sec=timeout_sec,
            shell=False,
            progress_action=progress_action,
            timeout_error=timeout_error,
        )
    return run_process(
        command_text,
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=True,
        progress_action=progress_action,
        timeout_error=timeout_error,
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


def write_run_checkpoint(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    checkpoint_name: str,
    directory_name: str,
    payload: dict[str, Any],
) -> dict[str, Any]:
    checkpoint_dir = manifest_path.parent / directory_name
    summary_path = checkpoint_dir / "summary.json"
    write_json(summary_path, payload)
    updated_manifest = dict(manifest)
    updated_manifest[checkpoint_name] = {
        **payload,
        "summaryPath": str(summary_path),
    }
    write_json(manifest_path, updated_manifest)
    return updated_manifest


def write_coordinator_validation_summary(
    manifest_path: Path,
    manifest: dict[str, Any],
    summary: dict[str, Any],
) -> dict[str, Any]:
    updated_manifest = dict(manifest)
    return write_run_checkpoint(
        manifest_path,
        updated_manifest,
        checkpoint_name="coordinatorValidation",
        directory_name="validation",
        payload=summary,
    )


def validate_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    run_dir = manifest_run_dir(manifest_path, manifest)
    records = selected_run_records(manifest, args.selected_tasks)
    intents_only = bool(getattr(args, "intents_only", False))
    execution_scope = str(
        getattr(args, "execution_scope", DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE)
        or DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE
    )
    included_statuses = {
        str(status)
        for status in (getattr(args, "include_statuses", None) or list(DEFAULT_VALIDATE_RUN_STATUSES))
    }
    task_payloads, commands = collect_validation_commands(
        records,
        included_statuses=included_statuses,
        execution_scope=execution_scope,
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
        execution_cwd = command_payload.get("cwd")
        execution_target_accepted = bool(command_payload.get("executionTargetAccepted", False))
        execution_target_reason = command_payload.get("executionTargetReason")
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
            "executionScope": execution_scope,
            "cwd": execution_cwd,
            "workspaceMode": command_payload.get("workspaceMode"),
            "workspacePath": command_payload.get("workspacePath"),
            "executionTargetAccepted": execution_target_accepted,
            "executionTargetReason": execution_target_reason,
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
        if not execution_target_accepted:
            result_payload["status"] = "rejected"
            command_results.append(result_payload)
            continue
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
        command_cwd = Path(str(execution_cwd)).resolve()
        try:
            if execution_intent_payload is not None:
                completed = run_validation_intent(
                    coerce_validation_intent_payload(
                        execution_intent_payload,
                        location=f"validate-run:{index}:intent",
                    ),
                    cwd=command_cwd,
                    timeout_sec=max(args.timeout_sec, 1),
                    progress_action=validation_wait_action(command_text, "intent"),
                )
            else:
                completed = run_shell_command_text(
                    command_text,
                    cwd=command_cwd,
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
        "executionScope": execution_scope,
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
        "acceptedCommandCount": sum(
            1
            for payload in command_results
            if payload.get("policyAccepted") and payload.get("executionTargetAccepted")
        ),
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
        validate_scope_contract(plan, agents)
        task_worker_validation_modes = effective_plan_worker_validation_modes(plan, agents)
        task_worker_validation_mode_sources = effective_plan_worker_validation_mode_sources(plan, agents)
        task_model_profiles = effective_plan_model_profiles(plan, agents)
        task_models = effective_plan_models(plan, agents)
        complex_model_tasks = complex_model_task_ids(task_model_profiles)
        topology = analyze_plan_topology(plan, agents)
        plan_batches = topological_batches(plan.tasks)
        payload.update(
            {
                "taskPlanPath": str(plan_path),
                "planName": plan.name,
                "runPolicy": serialize_run_policy(plan.run_policy),
                "taskCount": len(plan.tasks),
                "taskIds": [task.id for task in plan.tasks],
                "taskWorkerValidationModes": task_worker_validation_modes,
                "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
                "taskEfforts": effective_plan_efforts(plan, agents),
                "taskEffortSources": effective_plan_effort_sources(plan, agents),
                "taskModels": task_models,
                "taskModelProfiles": task_model_profiles,
                "complexModelTaskIds": complex_model_tasks,
                "complexModelTaskCount": len(complex_model_tasks),
                "topology": topology,
                "tasks": [
                    {
                        "id": task.id,
                        "agent": task.agent,
                        "model": task_models[task.id],
                        "modelProfile": task_model_profiles[task.id],
                        "effort": effective_plan_efforts(plan, agents)[task.id],
                        "effortSource": effective_plan_effort_sources(plan, agents)[task.id],
                        "readPaths": effective_task_read_paths(plan, task),
                        "writePaths": effective_task_write_scope(task),
                        "dependencyMaterialization": effective_dependency_materialization_mode(task),
                        "workerValidationMode": task_worker_validation_modes[task.id],
                        "workerValidationModeSource": task_worker_validation_mode_sources[task.id],
                    }
                    for task in plan.tasks
                ],
                "batches": [[task.id for task in batch] for batch in plan_batches],
                "parallelConflicts": detect_parallel_scope_conflicts(plan, agents),
            }
        )
    return payload


def print_payload(payload: dict[str, Any], *, as_json: bool) -> None:
    if as_json:
        print(json.dumps(payload))
    else:
        print(json.dumps(payload, indent=2))


def _worker_run_exit_code(status_counts: dict[str, int]) -> int:
    """Derive exit code from a run/resume/retry statusCounts payload."""
    if status_counts.get("failed", 0) > 0:
        return EXIT_WORKER_FAILURE
    if status_counts.get("blocked", 0) > 0:
        return EXIT_BLOCKED
    return EXIT_SUCCESS


def main() -> int:
    args = parse_args()
    try:
        if args.command == "validate":
            try:
                payload = validate_command(args)
            except OrchestratorError as exc:
                print(f"[claude-orchestrator] {exc}", file=sys.stderr)
                return EXIT_VALIDATION
            print_payload(payload, as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "plan":
            print_payload(plan_with_claude(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "run":
            payload = run_plan(args)
            print_payload(payload, as_json=args.json)
            return _worker_run_exit_code(payload.get("statusCounts", {}))
        if args.command == "resume":
            payload = resume_run(args)
            print_payload(payload, as_json=args.json)
            return _worker_run_exit_code(payload.get("statusCounts", {}))
        if args.command == "retry":
            payload = retry_run(args)
            print_payload(payload, as_json=args.json)
            return _worker_run_exit_code(payload.get("statusCounts", {}))
        if args.command == "review":
            print_payload(review_run(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "export-patch":
            print_payload(export_patch(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "promote":
            print_payload(promote_run(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "cleanup":
            print_payload(cleanup_run(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "inventory":
            print_payload(inventory_runs(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "status":
            print_payload(status_run(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "evaluate-run":
            print_payload(evaluate_run_quality(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "evaluate-corpus":
            print_payload(evaluate_run_corpus(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "prune":
            print_payload(prune_runs(args), as_json=args.json)
            return EXIT_SUCCESS
        if args.command == "validate-run":
            print_payload(validate_run(args), as_json=args.json)
            return EXIT_SUCCESS
        raise OrchestratorError(f"Unknown command '{args.command}'")
    except PromotionBlockedError as exc:
        print(f"[claude-orchestrator] {exc}", file=sys.stderr)
        return EXIT_UNSAFE_PROMOTION
    except OrchestratorError as exc:
        print(f"[claude-orchestrator] {exc}", file=sys.stderr)
        return EXIT_ERROR
    except Exception as exc:
        print(f"[claude-orchestrator] unexpected error: {exc}", file=sys.stderr)
        return EXIT_CRASH


if __name__ == "__main__":
    sys.exit(main())
