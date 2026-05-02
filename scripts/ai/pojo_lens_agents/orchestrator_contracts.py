#!/usr/bin/env python3
from __future__ import annotations

import re
import threading
from dataclasses import field
from pathlib import Path
from typing import Any

from pydantic.dataclasses import dataclass

ROOT = Path(__file__).resolve().parents[3]
AI_ORCHESTRATOR_DIR = ROOT / "ai" / "orchestrator"
DEFAULT_AGENTS_PATH = AI_ORCHESTRATOR_DIR / "agents.json"
DEFAULT_TASKS_DIR = AI_ORCHESTRATOR_DIR / "tasks"
DEFAULT_SKILL_REGISTRY_PATH = AI_ORCHESTRATOR_DIR / "skills" / "registry.json"
DEFAULT_RUNTIME_ROOT = ROOT / ".claude-orchestrator"
DEFAULT_CLAUDE_BIN = "claude"
AI_ORCHESTRATOR_DIR = ROOT / "ai" / "orchestrator"
DEFAULT_AGENTS_PATH = AI_ORCHESTRATOR_DIR / "agents.json"
DEFAULT_TASKS_DIR = AI_ORCHESTRATOR_DIR / "tasks"
DEFAULT_SKILL_REGISTRY_PATH = AI_ORCHESTRATOR_DIR / "skills" / "registry.json"
DEFAULT_RUNTIME_ROOT = ROOT / ".claude-orchestrator"
DEFAULT_CLAUDE_BIN = "claude"
DEFAULT_TASK_TIMEOUT_SEC = 30 * 60
PLANNER_TASK_ID = "planner"
WORKSPACE_MODES = {"copy", "repo", "worktree"}
TASK_ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
CONTEXT_MODES = {"minimal", "full"}
DEFAULT_CONTEXT_MODE = "minimal"
OUTPUT_PROFILES = {"standard", "lean"}
DEFAULT_OUTPUT_PROFILE = "standard"
OUTPUT_PROFILE_SOURCES = {"task", "agent", "default"}
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
FOLLOW_UP_BEHAVIORS = {"ignore", "inject"}
DEFAULT_FOLLOW_UP_BEHAVIOR = "ignore"
HITL_MODES = {"none", "batch", "on-failure", "always"}
DEFAULT_HITL_MODE = "none"
MODEL_PROFILE_TO_MODEL = {
    "simple": "claude-haiku-4-5-20251001",
    "balanced": "claude-sonnet-4-6",
    "complex": "claude-opus-4-7",
}
MODEL_TO_PROFILE = {value: key for key, value in MODEL_PROFILE_TO_MODEL.items()}
MAX_HYDRATED_FILE_BYTES = 512 * 1024
DEFAULT_PROMPT_SECTION_ITEM_LIMIT = 8
DEFAULT_PROMPT_ITEM_CHAR_LIMIT = 180
AGENT_PROMPT_WARN_BYTES = 6 * 1024
AGENT_PROMPT_FAIL_BYTES = 8 * 1024
SKILL_PROMPT_WARN_BYTES = 3 * 1024
SKILL_PROMPT_FAIL_BYTES = 4 * 1024
RESOLVED_SKILLS_WARN_COUNT = 4
RESOLVED_SKILLS_FAIL_COUNT = 5
DEFAULT_DEPENDENCY_SUMMARY_CHAR_LIMIT = 220
DEFAULT_DEPENDENCY_DETAIL_ITEM_LIMIT = 2
DEFAULT_DEPENDENCY_DETAIL_CHAR_LIMIT = 120
DEFAULT_REVIEW_DEPENDENCY_CONTEXT_LINES = 1
DEFAULT_REVIEW_DEPENDENCY_PATCH_CHAR_LIMIT = 900
REVIEWER_FINDING_SEVERITIES: frozenset[str] = frozenset({"block", "info", "warn"})
MAX_WORKER_FINDINGS = 10
MAX_WORKER_FINDING_MESSAGE_CHARS = 280
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
LEAN_MAX_WORKER_SUMMARY_CHARS = 180
LEAN_MAX_WORKER_NOTES = 2
LEAN_MAX_WORKER_NOTE_CHARS = 140
LEAN_MAX_WORKER_FOLLOW_UPS = 1
LEAN_MAX_WORKER_FOLLOW_UP_CHARS = 120
LEAN_MAX_WORKER_VALIDATION_INTENTS = 1
LEAN_MAX_WORKER_VALIDATION_INTENT_ARG_CHARS = 120
MAX_RUN_SUMMARY_TOP_TASKS = 3
STANDARD_VERBOSE_RESULT_BYTES = 4096
STANDARD_VERBOSE_STDOUT_BYTES = 4096
LEAN_VERBOSE_RESULT_BYTES = 2048
LEAN_VERBOSE_STDOUT_BYTES = 2048
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
        "followUpTasks": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "title": {"type": "string"},
                    "agent": {"type": "string"},
                    "prompt": {"type": "string"},
                    "skills": {"type": "array", "items": {"type": "string"}},
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
                    "outputProfile": {
                        "type": "string",
                        "enum": sorted(OUTPUT_PROFILES),
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
                    "maxRetries": {"type": "integer", "minimum": 0},
                },
                "required": ["id", "title", "agent", "prompt"],
                "additionalProperties": False,
            },
        },
        "notes": {"type": ["array", "null"], "items": {"type": "string"}},
        "findings": {
            "type": ["array", "null"],
            "items": {
                "type": "object",
                "properties": {
                    "severity": {"type": "string", "enum": sorted(REVIEWER_FINDING_SEVERITIES)},
                    "message": {"type": "string"},
                },
                "required": ["severity", "message"],
                "additionalProperties": False,
            },
        },
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
                "followUpBehavior": {
                    "type": "string",
                    "enum": sorted(FOLLOW_UP_BEHAVIORS),
                },
                "hitl": {"type": "boolean"},
                "hitlMode": {
                    "type": "string",
                    "enum": sorted(HITL_MODES),
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
                    "skills": {"type": "array", "items": {"type": "string"}},
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
                    "outputProfile": {
                        "type": "string",
                        "enum": sorted(OUTPUT_PROFILES),
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
                    "maxRetries": {"type": "integer", "minimum": 0},
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
    prompt_path: str | None = None
    skills: list[str] = field(default_factory=list)
    model: str | None = None
    model_profile: str | None = None
    effort: str | None = None
    permission_mode: str | None = None
    workspace_mode: str = "copy"
    context_mode: str = DEFAULT_CONTEXT_MODE
    output_profile: str = DEFAULT_OUTPUT_PROFILE
    worker_validation_mode: str | None = None
    timeout_sec: int = DEFAULT_TASK_TIMEOUT_SEC
    max_budget_usd: float | None = None
    max_prompt_chars: int | None = None
    max_prompt_estimated_tokens: int | None = None
    allowed_tools: list[str] = field(default_factory=list)
    disallowed_tools: list[str] = field(default_factory=list)
    max_retries: int | None = None


@dataclass(frozen=True)
class SkillDefinition:
    name: str
    description: str
    prompt_path: str


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
    skills: list[str] = field(default_factory=list)
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
    output_profile: str | None = None
    dependency_materialization: str | None = None
    worker_validation_mode: str | None = None
    timeout_sec: int | None = None
    max_budget_usd: float | None = None
    max_prompt_chars: int | None = None
    max_prompt_estimated_tokens: int | None = None
    allowed_tools: list[str] = field(default_factory=list)
    disallowed_tools: list[str] = field(default_factory=list)
    max_retries: int | None = None
    injected_from: str | None = None

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
    follow_up_behavior: str = DEFAULT_FOLLOW_UP_BEHAVIOR
    hitl: bool = False
    hitl_mode: str = DEFAULT_HITL_MODE


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
class ReviewFinding:
    severity: str
    message: str


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
    resolved_skills: list[str]
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
    follow_up_tasks: list[dict[str, Any]]
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
    output_profile: str = DEFAULT_OUTPUT_PROFILE
    output_profile_source: str | None = None
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
    reviewer_findings: list[ReviewFinding] = field(default_factory=list)
    injected_from: str | None = None
    attempt: int = 1
    attempt_errors: list[dict[str, Any]] = field(default_factory=list)


