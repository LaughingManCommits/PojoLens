#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import difflib
import importlib
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


ROOT = Path(__file__).resolve().parents[3]
SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

class _LazyModuleProxy:
    def __init__(self, module_name: str) -> None:
        self._module_name = module_name
        self._module: Any | None = None

    def _load(self) -> Any:
        if self._module is None:
            self._module = importlib.import_module(self._module_name)
        return self._module

    def __getattr__(self, name: str) -> Any:
        return getattr(self._load(), name)


governance_layer = _LazyModuleProxy("pojo_lens_agents.governance")
evals_layer = _LazyModuleProxy("pojo_lens_agents.evals")
manifest_records_layer = _LazyModuleProxy("pojo_lens_agents.manifest_records")
path_safety_layer = _LazyModuleProxy("pojo_lens_agents.path_safety")
planner_ops_layer = _LazyModuleProxy("pojo_lens_agents.planner_ops")
prompt_contracts_layer = _LazyModuleProxy("pojo_lens_agents.prompt_contracts")
manifest_io_layer = _LazyModuleProxy("pojo_lens_agents.manifest_io")
provider_layer = _LazyModuleProxy("pojo_lens_agents.provider")
review_ops_layer = _LazyModuleProxy("pojo_lens_agents.review_ops")
run_ops_layer = _LazyModuleProxy("pojo_lens_agents.run_ops")
run_summary_layer = _LazyModuleProxy("pojo_lens_agents.run_summary")
runtime_layer = _LazyModuleProxy("pojo_lens_agents.runtime")
runtime_admin_layer = _LazyModuleProxy("pojo_lens_agents.runtime_admin")
run_store_layer = _LazyModuleProxy("pojo_lens_agents.run_store")
task_execution_layer = _LazyModuleProxy("pojo_lens_agents.task_execution")
validate_cli_layer = _LazyModuleProxy("pojo_lens_agents.validate_cli")
validation_ops_layer = _LazyModuleProxy("pojo_lens_agents.validation_ops")
worker_contracts_layer = _LazyModuleProxy("pojo_lens_agents.worker_contracts")
workspace_review_layer = _LazyModuleProxy("pojo_lens_agents.workspace_review")

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
    return task_execution_layer.prepare_workspace(
        plan,
        task,
        workspace_mode,
        workspace_path,
        runtime_root,
        dependency_records,
        deps={
            "root": ROOT,
            "shutil": shutil,
            "subprocess": subprocess,
            "workspace_preparation_result_factory": WorkspacePreparationResult,
            "workspace_prep_action": workspace_prep_action,
            "emit_slop_log": emit_slop_log,
            "analyze_copy_hydration_inputs": analyze_copy_hydration_inputs,
            "summarize_paths": summarize_paths,
            "format_issue_block": format_issue_block,
            "hydrate_copy_workspace": hydrate_copy_workspace,
            "materialize_dependency_layers": materialize_dependency_layers,
            "ensure_clean_for_worktrees": ensure_clean_for_worktrees,
            "error_factory": OrchestratorError,
        },
    )


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
    return manifest_records_layer.count_statuses(values)


def task_branch_parent_context_ids(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord] | None = None,
) -> list[str]:
    return manifest_records_layer.task_branch_parent_context_ids(
        task,
        dependency_records=dependency_records,
    )


def task_branch_context_id(
    task: TaskDefinition,
    dependency_records: dict[str, TaskRunRecord] | None = None,
) -> str:
    return manifest_records_layer.task_branch_context_id(
        task,
        dependency_records=dependency_records,
    )


def summarize_branch_contexts(records: list[TaskRunRecord]) -> dict[str, Any]:
    return run_summary_layer.summarize_branch_contexts(records)


def task_cost_usd(record: TaskRunRecord) -> float:
    return governance_layer.task_cost_usd(record)


def coerce_task_run_record(payload: Any, *, location: str) -> TaskRunRecord:
    return manifest_records_layer.coerce_task_run_record(
        payload,
        location=location,
        deps={
            "error_factory": OrchestratorError,
            "task_run_record_factory": TaskRunRecord,
            "prompt_section_metric_factory": PromptSectionMetric,
            "prompt_budget_result_factory": PromptBudgetResult,
            "coerce_validation_intent_payload": coerce_validation_intent_payload,
            "normalized_worker_unknown_fields": normalized_worker_unknown_fields,
            "normalize_dependency_materialization_mode": normalize_dependency_materialization_mode,
            "default_dependency_materialization_mode": DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
            "coerce_dependency_layer_record_payload": coerce_dependency_layer_record_payload,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "normalize_worker_validation_mode_source": normalize_worker_validation_mode_source,
            "normalize_effort_override": normalize_effort_override,
            "require_optional_string": require_optional_string,
        },
    )


def selected_run_records(
    manifest: dict[str, Any],
    selected_ids: list[str],
) -> list[TaskRunRecord]:
    return manifest_records_layer.selected_run_records(
        manifest,
        selected_ids,
        coerce_task_run_record=coerce_task_run_record,
        error_factory=OrchestratorError,
    )


def decode_text_or_none(content: bytes) -> str | None:
    return review_ops_layer.decode_text_or_none(content)


def diff_file_against_workspace(
    record: TaskRunRecord,
    relative_path: str,
    *,
    context_lines: int,
) -> tuple[dict[str, Any], str | None]:
    return review_ops_layer.diff_file_against_workspace(
        record,
        relative_path,
        context_lines=context_lines,
        root=ROOT,
        resolve_relative_path=resolve_relative_path,
        read_bytes=read_bytes,
        error_factory=OrchestratorError,
    )


def task_review_summary(record: TaskRunRecord, *, context_lines: int) -> tuple[dict[str, Any], list[str]]:
    return review_ops_layer.task_review_summary(
        record,
        context_lines=context_lines,
        dedupe_strings=dedupe_strings,
        diff_file_against_workspace_fn=diff_file_against_workspace,
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
    return review_ops_layer.default_patch_output_path(
        manifest_path,
        task_ids,
        slugify=slugify,
    )


def review_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    return review_ops_layer.review_run(
        manifest_path=manifest_path,
        manifest=manifest,
        records=records,
        context_lines=args.context_lines,
        default_dependency_materialization_mode=DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
        write_run_checkpoint=write_run_checkpoint,
        task_review_summary_fn=task_review_summary,
    )


def export_patch(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    return review_ops_layer.export_patch(
        manifest_path=manifest_path,
        manifest=manifest,
        records=records,
        context_lines=args.context_lines,
        out=args.out,
        default_patch_output_path_fn=default_patch_output_path,
        write_text=write_text,
        dedupe_strings=dedupe_strings,
        task_review_summary_fn=task_review_summary,
    )


def format_issue_block(header: str, issues: list[str]) -> str:
    visible = dedupe_strings(issues)
    return header if not visible else f"{header}:\n- " + "\n- ".join(visible)


def task_promotion_operations(record: TaskRunRecord) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    return review_ops_layer.task_promotion_operations(
        record,
        task_review_summary_fn=task_review_summary,
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
    return review_ops_layer.own_dependency_layer(
        record,
        task_promotion_operations_fn=task_promotion_operations,
        dependency_layer_record_factory=DependencyLayerRecord,
        dependency_layer_operation_factory=DependencyLayerOperation,
        error_factory=OrchestratorError,
    )


def dependency_layers_for_record(record: TaskRunRecord) -> list[DependencyLayerRecord]:
    return review_ops_layer.dependency_layers_for_record(
        record,
        own_dependency_layer_fn=own_dependency_layer,
    )


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
    return review_ops_layer.plan_promotion(
        records,
        summarize_paths=summarize_paths,
        format_issue_block=format_issue_block,
        task_promotion_operations_fn=task_promotion_operations,
        blocked_error_factory=PromotionBlockedError,
    )


def summarize_promotion_readiness(records: list[TaskRunRecord]) -> dict[str, Any]:
    return review_ops_layer.summarize_promotion_readiness(
        records,
        task_promotion_operations_fn=task_promotion_operations,
        plan_promotion_fn=plan_promotion,
        blocked_error_factory=PromotionBlockedError,
        dedupe_strings=dedupe_strings,
    )


def apply_promotion_operation(record: TaskRunRecord, operation: dict[str, Any]) -> None:
    review_ops_layer.apply_promotion_operation(
        record,
        operation,
        root=ROOT,
        dependency_layer_operation_factory=DependencyLayerOperation,
        apply_workspace_operation_fn=apply_workspace_operation,
    )


def promote_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    records = selected_run_records(manifest, args.selected_tasks)
    return review_ops_layer.promote_run(
        manifest_path=manifest_path,
        manifest=manifest,
        records=records,
        dry_run=args.dry_run,
        root=ROOT,
        format_issue_block=format_issue_block,
        task_promotion_operations_fn=task_promotion_operations,
        summarize_promotion_readiness_fn=summarize_promotion_readiness,
        plan_promotion_fn=plan_promotion,
        apply_promotion_operation_fn=apply_promotion_operation,
        blocked_error_factory=PromotionBlockedError,
        write_run_checkpoint=write_run_checkpoint,
    )


def planner_output_path(name: str, explicit_path: str) -> Path:
    return planner_ops_layer.planner_output_path(
        name,
        explicit_path,
        default_tasks_dir=DEFAULT_TASKS_DIR,
        slugify=slugify,
    )


def planner_prompt(
    goal: str,
    name: str,
    files: list[str],
    constraints: list[str],
    validation: list[str],
    agents: dict[str, AgentDefinition],
) -> PromptRenderResult:
    return planner_ops_layer.planner_prompt(
        goal,
        name,
        files,
        constraints,
        validation,
        agents,
        planner_task_id=PLANNER_TASK_ID,
        default_prompt_item_char_limit=DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
        format_bullet_list=format_bullet_list,
        prompt_section_factory=PromptSection,
        render_prompt=render_prompt,
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
    return prompt_contracts_layer.evaluate_prompt_budget(
        prompt_chars=prompt_chars,
        prompt_estimated_tokens=prompt_estimated_tokens,
        max_chars=max_chars,
        max_estimated_tokens=max_estimated_tokens,
        prompt_budget_result_factory=PromptBudgetResult,
    )


def prompt_budget_failure_summary(result: PromptBudgetResult) -> str:
    return prompt_contracts_layer.prompt_budget_failure_summary(result)


def resolved_max_prompt_chars(task: TaskDefinition | None, agent: AgentDefinition) -> int | None:
    return prompt_contracts_layer.resolved_max_prompt_chars(task, agent)


def resolved_max_prompt_estimated_tokens(task: TaskDefinition | None, agent: AgentDefinition) -> int | None:
    return prompt_contracts_layer.resolved_max_prompt_estimated_tokens(task, agent)


def effective_context_mode(task: TaskDefinition, agent: AgentDefinition) -> str:
    return prompt_contracts_layer.effective_context_mode(
        task,
        agent,
        default_context_mode=DEFAULT_CONTEXT_MODE,
    )


def effective_dependency_materialization_mode(task: TaskDefinition) -> str:
    return prompt_contracts_layer.effective_dependency_materialization_mode(
        task,
        default_mode=DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    )


def resolved_model_profile(task: TaskDefinition, agent: AgentDefinition) -> str | None:
    return prompt_contracts_layer.resolved_model_profile(
        task,
        agent,
        model_to_profile=MODEL_TO_PROFILE,
    )


def resolved_model(task: TaskDefinition, agent: AgentDefinition) -> str | None:
    return prompt_contracts_layer.resolved_model(
        task,
        agent,
        model_to_profile=MODEL_TO_PROFILE,
        model_profile_to_model=MODEL_PROFILE_TO_MODEL,
    )


def normalize_effort_override(value: str | None, *, location: str) -> str | None:
    return prompt_contracts_layer.normalize_effort_override(
        value,
        location=location,
        error_factory=OrchestratorError,
    )


def resolve_effort(
    task: TaskDefinition | None,
    agent: AgentDefinition,
    *,
    run_override: str | None = None,
) -> tuple[str | None, str]:
    return prompt_contracts_layer.resolve_effort(
        task,
        agent,
        run_override=run_override,
    )


def effective_plan_model_profiles(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str | None]:
    return prompt_contracts_layer.effective_plan_model_profiles(
        plan,
        agents,
        model_to_profile=MODEL_TO_PROFILE,
    )


def effective_plan_models(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
) -> dict[str, str | None]:
    return prompt_contracts_layer.effective_plan_models(
        plan,
        agents,
        model_to_profile=MODEL_TO_PROFILE,
        model_profile_to_model=MODEL_PROFILE_TO_MODEL,
    )


def effective_plan_efforts(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str | None]:
    return prompt_contracts_layer.effective_plan_efforts(
        plan,
        agents,
        run_override=run_override,
    )


def effective_plan_effort_sources(
    plan: TaskPlan,
    agents: dict[str, AgentDefinition],
    *,
    run_override: str | None = None,
) -> dict[str, str]:
    return prompt_contracts_layer.effective_plan_effort_sources(
        plan,
        agents,
        run_override=run_override,
    )


def complex_model_task_ids(task_model_profiles: dict[str, str | None]) -> list[str]:
    return prompt_contracts_layer.complex_model_task_ids(task_model_profiles)


def dependency_handoff(record: TaskRunRecord) -> str:
    return prompt_contracts_layer.dependency_handoff(
        record,
        deps={
            "truncate_text": truncate_text,
            "dedupe_strings": dedupe_strings,
            "worker_field_unknown": worker_field_unknown,
            "default_dependency_summary_char_limit": DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT,
            "default_dependency_detail_item_limit": DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT,
            "default_dependency_detail_char_limit": DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT,
        },
    )


def dependency_summary(records: dict[str, TaskRunRecord], task: TaskDefinition) -> str:
    return prompt_contracts_layer.dependency_summary(
        records,
        task,
        deps={
            "reviewer_agent_name": REVIEWER_AGENT_NAME,
            "dependency_review_context": dependency_review_context,
            "truncate_text": truncate_text,
            "dedupe_strings": dedupe_strings,
            "worker_field_unknown": worker_field_unknown,
            "default_dependency_summary_char_limit": DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT,
            "default_dependency_detail_item_limit": DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT,
            "default_dependency_detail_char_limit": DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT,
        },
    )


def truncate_command_text(text: str, max_chars: int) -> tuple[str, bool]:
    return worker_contracts_layer.truncate_command_text(text, max_chars)


def strip_optional_quotes(token: str) -> str:
    return worker_contracts_layer.strip_optional_quotes(token)


def contains_unquoted_shell_operator(text: str) -> bool:
    return worker_contracts_layer.contains_unquoted_shell_operator(text)


def normalize_worker_text_list(
    payload: Any,
    *,
    key: str,
    max_items: int,
    max_chars: int,
    compact: bool,
) -> tuple[list[str], bool]:
    return worker_contracts_layer.normalize_worker_text_list(
        payload,
        key=key,
        max_items=max_items,
        max_chars=max_chars,
        compact=compact,
        truncate_text=truncate_text,
        error_factory=OrchestratorError,
    )


def normalize_worker_files_touched(payload: Any) -> tuple[list[str], bool]:
    return worker_contracts_layer.normalize_worker_files_touched(
        payload,
        normalize_relative_path=normalize_relative_path,
        error_factory=OrchestratorError,
    )


def normalized_worker_unknown_fields(payload: Any) -> list[str]:
    return worker_contracts_layer.normalized_worker_unknown_fields(
        payload,
        worker_unknownable_fields=list(WORKER_UNKNOWNABLE_FIELDS),
    )


def worker_field_unknown(record: TaskRunRecord, field_name: str) -> bool:
    return worker_contracts_layer.worker_field_unknown(record, field_name)


def analyze_validation_command(command_text: str) -> dict[str, Any]:
    return worker_contracts_layer.analyze_validation_command(
        command_text,
        validation_allowed_executables=VALIDATION_ALLOWED_EXECUTABLES,
        validation_allowed_script_suffixes=VALIDATION_ALLOWED_SCRIPT_SUFFIXES,
        validation_allowed_relative_prefixes=VALIDATION_ALLOWED_RELATIVE_PREFIXES,
        validation_intent_factory=ValidationIntent,
        normalize_relative_path=normalize_relative_path,
        error_factory=OrchestratorError,
        asdict=asdict,
    )


def coerce_validation_intent_payload(payload: Any, *, location: str) -> ValidationIntent:
    return worker_contracts_layer.coerce_validation_intent_payload(
        payload,
        location=location,
        validation_intent_kinds=VALIDATION_INTENT_KINDS,
        max_worker_validation_intent_arg_chars=MAX_WORKER_VALIDATION_INTENT_ARG_CHARS,
        validation_intent_factory=ValidationIntent,
        normalize_relative_path=normalize_relative_path,
        truncate_text=truncate_text,
        error_factory=OrchestratorError,
    )


def normalize_worker_validation_intents(payload: Any) -> list[ValidationIntent]:
    return worker_contracts_layer.normalize_worker_validation_intents(
        payload,
        max_worker_validation_intents=MAX_WORKER_VALIDATION_INTENTS,
        coerce_validation_intent_payload=coerce_validation_intent_payload,
        error_factory=OrchestratorError,
    )


def validation_intent_policy(intent: ValidationIntent) -> dict[str, Any]:
    return worker_contracts_layer.validation_intent_policy(
        intent,
        validation_allowed_executables=VALIDATION_ALLOWED_EXECUTABLES,
        validation_allowed_script_suffixes=VALIDATION_ALLOWED_SCRIPT_SUFFIXES,
        validation_allowed_relative_prefixes=VALIDATION_ALLOWED_RELATIVE_PREFIXES,
    )


def render_command_tokens(tokens: list[str]) -> str:
    return worker_contracts_layer.render_command_tokens(tokens)


def validation_intent_command_text(intent: ValidationIntent) -> str:
    return worker_contracts_layer.validation_intent_command_text(intent)


def validation_intent_execution_tokens(intent: ValidationIntent) -> list[str]:
    return worker_contracts_layer.validation_intent_execution_tokens(intent, root=ROOT)


def validation_command_policy(command_text: str) -> dict[str, Any]:
    return worker_contracts_layer.validation_command_policy(
        command_text,
        analyze_validation_command=analyze_validation_command,
    )


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
    return prompt_contracts_layer.worker_prompt(
        plan,
        task,
        agent,
        workspace_mode,
        workspace_path,
        dependency_text,
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=dependency_layers_applied,
        dry_run=dry_run,
        worker_validation_mode=worker_validation_mode,
        deps={
            "default_context_mode": DEFAULT_CONTEXT_MODE,
            "default_dependency_materialization_mode": DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
            "normalize_dependency_materialization_mode": normalize_dependency_materialization_mode,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "prompt_task_read_paths": prompt_task_read_paths,
            "effective_task_write_scope": effective_task_write_scope,
            "dedupe_strings": dedupe_strings,
            "format_bullet_list": format_bullet_list,
            "validation_command_policy": validation_command_policy,
            "render_prompt": render_prompt,
            "prompt_section_factory": PromptSection,
        },
    )


def worker_result_schema(worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE) -> dict[str, Any]:
    return prompt_contracts_layer.worker_result_schema(
        worker_validation_mode,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        worker_result_schema_payload=WORKER_RESULT_SCHEMA,
    )


def task_output_schema_json(worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE) -> str:
    return prompt_contracts_layer.task_output_schema_json(
        worker_validation_mode,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        worker_result_schema_payload=WORKER_RESULT_SCHEMA,
    )


def plan_output_schema_json() -> str:
    return prompt_contracts_layer.plan_output_schema_json(PLAN_RESULT_SCHEMA)


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
    return worker_contracts_layer.coerce_worker_result(
        payload,
        worker_validation_mode=worker_validation_mode,
        worker_statuses=WORKER_STATUSES,
        max_worker_summary_chars=MAX_WORKER_SUMMARY_CHARS,
        max_worker_validation_commands=MAX_WORKER_VALIDATION_COMMANDS,
        max_worker_validation_command_chars=MAX_WORKER_VALIDATION_COMMAND_CHARS,
        max_worker_follow_ups=MAX_WORKER_FOLLOW_UPS,
        max_worker_follow_up_chars=MAX_WORKER_FOLLOW_UP_CHARS,
        max_worker_notes=MAX_WORKER_NOTES,
        max_worker_note_chars=MAX_WORKER_NOTE_CHARS,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        normalize_worker_files_touched=normalize_worker_files_touched,
        normalize_worker_text_list=normalize_worker_text_list,
        normalize_worker_validation_intents=normalize_worker_validation_intents,
        truncate_text=truncate_text,
        asdict=asdict,
        error_factory=OrchestratorError,
    )


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
    return planner_ops_layer.coerce_plan_result(payload, error_factory=OrchestratorError)


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
    return planner_ops_layer.plan_with_claude(
        args,
        deps={
            "error_factory": OrchestratorError,
            "load_agents": load_agents,
            "ensure_claude_available": ensure_claude_available,
            "planner_task_id": PLANNER_TASK_ID,
            "default_prompt_item_char_limit": DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
            "format_bullet_list": format_bullet_list,
            "prompt_section_factory": PromptSection,
            "render_prompt": render_prompt,
            "normalize_effort_override": normalize_effort_override,
            "evaluate_prompt_budget": evaluate_prompt_budget,
            "model_profile_to_model": MODEL_PROFILE_TO_MODEL,
            "agent_payload_for_claude": agent_payload_for_claude,
            "default_tasks_dir": DEFAULT_TASKS_DIR,
            "slugify": slugify,
            "plan_output_schema_json": plan_output_schema_json,
            "claude_command": claude_command,
            "asdict": asdict,
            "prompt_budget_failure_summary": prompt_budget_failure_summary,
            "run_subprocess": run_subprocess,
            "planner_wait_action": planner_wait_action,
            "extract_json_payload": extract_json_payload,
            "write_json": write_json,
            "extract_usage": extract_usage,
            "root": ROOT,
        },
    )


def effective_workspace_mode(task: TaskDefinition, agent: AgentDefinition) -> str:
    return task_execution_layer.effective_workspace_mode(task, agent)


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
    return task_execution_layer.blocked_record(
        task,
        agent_name,
        agent,
        workspace_mode,
        reason=reason,
        dependency_records=dependency_records,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        deps={
            "iso_now": iso_now,
            "resolve_worker_validation_mode": resolve_worker_validation_mode,
            "resolve_effort": resolve_effort,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "task_run_record_factory": TaskRunRecord,
            "task_branch_context_id": task_branch_context_id,
            "task_branch_parent_context_ids": task_branch_parent_context_ids,
            "resolved_model": resolved_model,
            "resolved_model_profile": resolved_model_profile,
            "prompt_budget_result_factory": PromptBudgetResult,
            "resolved_max_prompt_chars": resolved_max_prompt_chars,
            "resolved_max_prompt_estimated_tokens": resolved_max_prompt_estimated_tokens,
        },
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
    return task_execution_layer.planned_record(
        task,
        agent_name,
        agent,
        workspace_mode,
        workspace_path,
        summary=summary,
        dependency_records=dependency_records,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        deps={
            "iso_now": iso_now,
            "resolve_worker_validation_mode": resolve_worker_validation_mode,
            "resolve_effort": resolve_effort,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "task_run_record_factory": TaskRunRecord,
            "task_branch_context_id": task_branch_context_id,
            "task_branch_parent_context_ids": task_branch_parent_context_ids,
            "resolved_model": resolved_model,
            "resolved_model_profile": resolved_model_profile,
            "prompt_budget_result_factory": PromptBudgetResult,
        },
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
    return task_execution_layer.execute_task(
        run_dir,
        runtime_root,
        workspaces_dir,
        plan,
        agents,
        task,
        dependency_records,
        claude_bin=claude_bin,
        agents_json=agents_json,
        dry_run=dry_run,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        deps={
            "root": ROOT,
            "resolve_worker_validation_mode": resolve_worker_validation_mode,
            "resolve_effort": resolve_effort,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "resolved_model": resolved_model,
            "resolved_model_profile": resolved_model_profile,
            "effective_workspace_mode": effective_workspace_mode,
            "effective_task_write_scope": effective_task_write_scope,
            "worker_prompt": worker_prompt,
            "dependency_summary": dependency_summary,
            "evaluate_prompt_budget": evaluate_prompt_budget,
            "resolved_max_prompt_chars": resolved_max_prompt_chars,
            "resolved_max_prompt_estimated_tokens": resolved_max_prompt_estimated_tokens,
            "effective_tool_lists": _effective_tool_lists,
            "claude_command": claude_command,
            "task_output_schema_json": task_output_schema_json,
            "write_text": write_text,
            "write_json": write_json,
            "iso_now": iso_now,
            "prompt_budget_failure_summary": prompt_budget_failure_summary,
            "snapshot_workspace_files": snapshot_workspace_files,
            "diff_workspace_snapshots": diff_workspace_snapshots,
            "run_subprocess": run_subprocess,
            "task_wait_action": task_wait_action,
            "extract_json_payload": extract_json_payload,
            "extract_usage": extract_usage,
            "artifact_file_size": artifact_file_size,
            "apply_workspace_audit": apply_workspace_audit,
            "apply_repository_isolation_audit": apply_repository_isolation_audit,
            "coerce_worker_result": coerce_worker_result,
            "coerce_validation_intent_payload": coerce_validation_intent_payload,
            "prepare_workspace": prepare_workspace,
            "error_factory": OrchestratorError,
            "asdict": asdict,
            "task_run_record_factory": TaskRunRecord,
            "task_branch_context_id": task_branch_context_id,
            "task_branch_parent_context_ids": task_branch_parent_context_ids,
        },
    )


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
    return manifest_io_layer.manifest_payload(
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
        deps={
            "root": ROOT,
            "iso_now": iso_now,
            "asdict": asdict,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "normalize_effort_override": normalize_effort_override,
            "effective_plan_worker_validation_modes": effective_plan_worker_validation_modes,
            "effective_plan_worker_validation_mode_sources": effective_plan_worker_validation_mode_sources,
            "effective_plan_efforts": effective_plan_efforts,
            "effective_plan_effort_sources": effective_plan_effort_sources,
            "effective_plan_model_profiles": effective_plan_model_profiles,
            "effective_plan_models": effective_plan_models,
            "complex_model_task_ids": complex_model_task_ids,
            "analyze_plan_topology": analyze_plan_topology,
            "aggregate_usage": aggregate_usage,
            "evaluate_run_governance": evaluate_run_governance,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
            "summarized_worker_validation_mode": summarized_worker_validation_mode,
            "serialize_run_policy": serialize_run_policy,
        },
    )


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
    manifest_io_layer.write_manifest(
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
        deps={
            "write_json": write_json,
            "root": ROOT,
            "iso_now": iso_now,
            "asdict": asdict,
            "normalize_worker_validation_mode": normalize_worker_validation_mode,
            "normalize_effort_override": normalize_effort_override,
            "effective_plan_worker_validation_modes": effective_plan_worker_validation_modes,
            "effective_plan_worker_validation_mode_sources": effective_plan_worker_validation_mode_sources,
            "effective_plan_efforts": effective_plan_efforts,
            "effective_plan_effort_sources": effective_plan_effort_sources,
            "effective_plan_model_profiles": effective_plan_model_profiles,
            "effective_plan_models": effective_plan_models,
            "complex_model_task_ids": complex_model_task_ids,
            "analyze_plan_topology": analyze_plan_topology,
            "aggregate_usage": aggregate_usage,
            "evaluate_run_governance": evaluate_run_governance,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
            "summarized_worker_validation_mode": summarized_worker_validation_mode,
            "serialize_run_policy": serialize_run_policy,
        },
    )


def write_selected_plan_snapshot(run_dir: Path, plan: TaskPlan) -> None:
    manifest_io_layer.write_selected_plan_snapshot(
        run_dir,
        plan,
        serialize_run_policy=serialize_run_policy,
        write_json=write_json,
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
    return run_ops_layer.run_loaded_plan(
        plan_path,
        agents_path,
        agents,
        plan,
        claude_bin=claude_bin,
        runtime_root=runtime_root,
        max_parallel=max_parallel,
        continue_on_error=continue_on_error,
        dry_run=dry_run,
        worker_validation_mode=worker_validation_mode,
        effort_override=effort_override,
        initial_records=initial_records,
        retry_of_run_id=retry_of_run_id,
        requested_task_ids=requested_task_ids,
        retried_task_ids=retried_task_ids,
        existing_run_id=existing_run_id,
        existing_run_dir=existing_run_dir,
        existing_workspaces_dir=existing_workspaces_dir,
        write_plan_snapshot=write_plan_snapshot,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        normalize_effort_override=normalize_effort_override,
        effective_plan_worker_validation_modes=effective_plan_worker_validation_modes,
        effective_plan_worker_validation_mode_sources=effective_plan_worker_validation_mode_sources,
        effective_plan_efforts=effective_plan_efforts,
        effective_plan_effort_sources=effective_plan_effort_sources,
        topological_batches=topological_batches,
        validate_scope_contract=validate_scope_contract,
        ensure_claude_available=ensure_claude_available,
        write_selected_plan_snapshot=write_selected_plan_snapshot,
        agent_payload_for_claude=agent_payload_for_claude,
        append_run_event=append_run_event,
        task_branch_context_id=task_branch_context_id,
        evaluate_run_governance=evaluate_run_governance,
        blocked_record=blocked_record,
        effective_workspace_mode=effective_workspace_mode,
        write_manifest=write_manifest,
        select_parallel_ready_batch=select_parallel_ready_batch,
        execute_task=execute_task,
        aggregate_usage=aggregate_usage,
        effective_plan_model_profiles=effective_plan_model_profiles,
        effective_plan_models=effective_plan_models,
        complex_model_task_ids=complex_model_task_ids,
        analyze_plan_topology=analyze_plan_topology,
        serialize_run_policy=serialize_run_policy,
        summarized_worker_validation_mode=summarized_worker_validation_mode,
        summarize_branch_contexts=summarize_branch_contexts,
        slugify=slugify,
        error_factory=OrchestratorError,
    )


def run_plan(args: argparse.Namespace) -> dict[str, Any]:
    return run_ops_layer.run_plan(
        args,
        load_agents=load_agents,
        load_task_plan=load_task_plan,
        selected_plan=selected_plan,
        run_loaded_plan_fn=run_loaded_plan,
    )


def resume_run(args: argparse.Namespace) -> dict[str, Any]:
    return run_ops_layer.resume_run(
        args,
        root=ROOT,
        load_run_manifest=load_run_manifest,
        selected_run_records=selected_run_records,
        manifest_run_dir=manifest_run_dir,
        manifest_workspaces_dir=manifest_workspaces_dir,
        manifest_required_path=manifest_required_path,
        load_agents=load_agents,
        manifest_selected_plan_path=manifest_selected_plan_path,
        load_task_plan=load_task_plan,
        manifest_worker_validation_override=manifest_worker_validation_override,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        selected_plan=selected_plan,
        planned_record=planned_record,
        effective_workspace_mode=effective_workspace_mode,
        normalize_effort_override=normalize_effort_override,
        run_loaded_plan_fn=run_loaded_plan,
        error_factory=OrchestratorError,
    )


def retry_run(args: argparse.Namespace) -> dict[str, Any]:
    return run_ops_layer.retry_run(
        args,
        load_run_manifest=load_run_manifest,
        selected_run_records=selected_run_records,
        manifest_selected_plan_path=manifest_selected_plan_path,
        manifest_required_path=manifest_required_path,
        load_agents=load_agents,
        load_task_plan=load_task_plan,
        selected_plan=selected_plan,
        manifest_worker_validation_override=manifest_worker_validation_override,
        normalize_worker_validation_mode=normalize_worker_validation_mode,
        run_loaded_plan_fn=run_loaded_plan,
        error_factory=OrchestratorError,
    )


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
    return runtime_admin_layer.cleanup_loaded_run(
        manifest_path,
        manifest,
        root=ROOT,
        manifest_run_dir=manifest_run_dir,
        manifest_workspaces_dir=manifest_workspaces_dir,
        selected_run_records=selected_run_records,
        dedupe_strings=dedupe_strings,
        error_factory=OrchestratorError,
    )


def cleanup_run(args: argparse.Namespace) -> dict[str, Any]:
    return runtime_admin_layer.cleanup_run(
        args,
        load_run_manifest=load_run_manifest,
        cleanup_loaded_run_fn=cleanup_loaded_run,
    )


def runtime_manifest_entries(runtime_root: Path) -> list[tuple[Path, dict[str, Any]]]:
    return runtime_admin_layer.runtime_manifest_entries(
        runtime_root=runtime_root,
        load_run_manifest=load_run_manifest,
    )


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
    return runtime_admin_layer.status_run(
        args,
        deps={
            "load_run_manifest": load_run_manifest,
            "summarize_run_manifest": summarize_run_manifest,
            "selected_run_records": selected_run_records,
            "task_review_summary": task_review_summary,
            "task_promotion_operations": task_promotion_operations,
        },
    )


def _evaluation_check(
    name: str,
    status: str,
    summary: str,
    *,
    evidence: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return evals_layer.evaluation_check(name, status, summary, evidence=evidence)


def summarize_evaluation_score(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    return evals_layer.summarize_evaluation_score(checks, run_summary=run_summary)


def benchmark_dimensions_for_evaluation(
    checks: list[dict[str, Any]],
    *,
    run_summary: dict[str, Any],
) -> dict[str, Any]:
    return evals_layer.benchmark_dimensions_for_evaluation(checks, run_summary=run_summary)


def evaluate_loaded_run_quality(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    selected_tasks: list[str],
) -> dict[str, Any]:
    return evals_layer.evaluate_loaded_run_quality(
        manifest_path,
        manifest,
        selected_tasks=selected_tasks,
        summarize_run_manifest=summarize_run_manifest,
        selected_run_records=selected_run_records,
    )


def evaluate_run_quality(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    return evaluate_loaded_run_quality(
        manifest_path,
        manifest,
        selected_tasks=list(args.selected_tasks),
    )


def inventory_runs(args: argparse.Namespace) -> dict[str, Any]:
    return runtime_admin_layer.inventory_runs(
        args,
        deps={
            "summarize_run_manifest": summarize_run_manifest,
            "runtime_manifest_entries": runtime_manifest_entries,
        },
    )


def evaluate_run_corpus(args: argparse.Namespace) -> dict[str, Any]:
    runtime_root = Path(args.runtime_root).resolve()
    limit = max(int(args.limit), 0)
    return evals_layer.evaluate_run_corpus(
        runtime_root=runtime_root,
        limit=limit,
        runtime_manifest_entries=runtime_manifest_entries,
        summarize_run_manifest=summarize_run_manifest,
        evaluate_loaded_run_quality_fn=lambda manifest_path, manifest, selected_tasks: evaluate_loaded_run_quality(
            manifest_path,
            manifest,
            selected_tasks=selected_tasks,
        ),
    )


def prune_runs(args: argparse.Namespace) -> dict[str, Any]:
    return runtime_admin_layer.prune_runs(
        args,
        deps={
            "error_factory": OrchestratorError,
            "summarize_run_manifest": summarize_run_manifest,
            "runtime_manifest_entries": runtime_manifest_entries,
            "cleanup_loaded_run": cleanup_loaded_run,
        },
    )


def collect_validation_commands(
    records: list[TaskRunRecord],
    *,
    included_statuses: set[str],
    execution_scope: str = DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    return validation_ops_layer.collect_validation_commands(
        records,
        included_statuses=included_statuses,
        execution_scope=execution_scope,
        default_validate_run_execution_scope=DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
        validation_execution_target_fn=validation_execution_target,
        dedupe_strings=dedupe_strings,
        validation_intent_command_text=validation_intent_command_text,
        validation_intent_policy=validation_intent_policy,
        validation_command_policy=validation_command_policy,
        worker_field_unknown=worker_field_unknown,
    )


def validation_execution_target(
    record: TaskRunRecord,
    *,
    execution_scope: str,
) -> dict[str, Any]:
    return validation_ops_layer.validation_execution_target(
        record,
        execution_scope=execution_scope,
        validate_run_execution_scopes=VALIDATE_RUN_EXECUTION_SCOPES,
        root=ROOT,
        error_factory=OrchestratorError,
    )


def run_shell_command_text(
    command_text: str,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return validation_ops_layer.run_shell_command_text(
        command_text,
        cwd=cwd,
        timeout_sec=timeout_sec,
        progress_action=progress_action,
        run_process=run_process,
    )


def run_validation_intent(
    intent: ValidationIntent,
    *,
    cwd: Path,
    timeout_sec: int,
    progress_action: SlopLogAction | None = None,
) -> subprocess.CompletedProcess[str]:
    return validation_ops_layer.run_validation_intent(
        intent,
        cwd=cwd,
        timeout_sec=timeout_sec,
        progress_action=progress_action,
        run_process=run_process,
        validation_intent_execution_tokens=validation_intent_execution_tokens,
        validation_intent_command_text=validation_intent_command_text,
    )


def write_run_checkpoint(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    checkpoint_name: str,
    directory_name: str,
    payload: dict[str, Any],
) -> dict[str, Any]:
    return validation_ops_layer.write_run_checkpoint(
        manifest_path,
        manifest,
        checkpoint_name=checkpoint_name,
        directory_name=directory_name,
        payload=payload,
        write_json=write_json,
    )


def write_coordinator_validation_summary(
    manifest_path: Path,
    manifest: dict[str, Any],
    summary: dict[str, Any],
) -> dict[str, Any]:
    return validation_ops_layer.write_coordinator_validation_summary(
        manifest_path,
        manifest,
        summary,
        write_run_checkpoint_fn=write_run_checkpoint,
    )


def validate_run(args: argparse.Namespace) -> dict[str, Any]:
    manifest_path, manifest = load_run_manifest(args.run_ref)
    run_dir = manifest_run_dir(manifest_path, manifest)
    records = selected_run_records(manifest, args.selected_tasks)
    return validation_ops_layer.validate_run(
        manifest_path=manifest_path,
        manifest=manifest,
        run_dir=run_dir,
        records=records,
        intents_only=bool(getattr(args, "intents_only", False)),
        execution_scope=str(getattr(args, "execution_scope", DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE) or DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE),
        included_statuses={str(status) for status in (getattr(args, "include_statuses", None) or list(DEFAULT_VALIDATE_RUN_STATUSES))},
        allow_unsafe_commands=bool(args.allow_unsafe_commands),
        continue_on_error=bool(args.continue_on_error),
        timeout_sec=int(args.timeout_sec),
        dry_run=bool(args.dry_run),
        root=ROOT,
        default_validate_run_execution_scope=DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
        collect_validation_commands_fn=collect_validation_commands,
        coerce_validation_intent_payload=coerce_validation_intent_payload,
        run_validation_intent_fn=run_validation_intent,
        run_shell_command_text_fn=run_shell_command_text,
        write_text=write_text,
        write_json=write_json,
        slugify=slugify,
        validation_wait_action=validation_wait_action,
        count_statuses=count_statuses,
        write_coordinator_validation_summary_fn=write_coordinator_validation_summary,
    )


def validate_command(args: argparse.Namespace) -> dict[str, Any]:
    return validate_cli_layer.validate_command(
        args,
        deps={
            "load_agents": load_agents,
            "load_task_plan": load_task_plan,
            "validate_scope_contract": validate_scope_contract,
            "effective_plan_worker_validation_modes": effective_plan_worker_validation_modes,
            "effective_plan_worker_validation_mode_sources": effective_plan_worker_validation_mode_sources,
            "effective_plan_model_profiles": effective_plan_model_profiles,
            "effective_plan_models": effective_plan_models,
            "effective_plan_efforts": effective_plan_efforts,
            "effective_plan_effort_sources": effective_plan_effort_sources,
            "complex_model_task_ids": complex_model_task_ids,
            "analyze_plan_topology": analyze_plan_topology,
            "topological_batches": topological_batches,
            "serialize_run_policy": serialize_run_policy,
            "effective_task_read_paths": effective_task_read_paths,
            "effective_task_write_scope": effective_task_write_scope,
            "effective_dependency_materialization_mode": effective_dependency_materialization_mode,
            "detect_parallel_scope_conflicts": detect_parallel_scope_conflicts,
        },
    )


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
