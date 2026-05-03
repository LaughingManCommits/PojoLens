from __future__ import annotations

from dataclasses import asdict, is_dataclass
from typing import Any, Literal, cast

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator, model_validator

from pojo_lens_agents.orchestrator_contracts import (
    BASE_TOOL_NAMES,
    DEFAULT_ARTIFACT_BEHAVIOR,
    DEFAULT_CONTEXT_MODE,
    DEFAULT_FOLLOW_UP_BEHAVIOR,
    DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    DEFAULT_HITL_MODE,
    DEFAULT_OUTPUT_PROFILE,
    DEFAULT_RUN_BUDGET_BEHAVIOR,
    DEFAULT_TASK_TIMEOUT_SEC,
    DEFAULT_WORKER_VALIDATION_MODE,
    DEPENDENCY_MATERIALIZATION_MODES,
    HITL_MODES,
    MODEL_PROFILE_TO_MODEL,
    OUTPUT_PROFILES,
    OUTPUT_PROFILE_SOURCES,
    REVIEWER_FINDING_SEVERITIES,
    FOLLOW_UP_BEHAVIORS,
    RUN_POLICY_BEHAVIORS,
    TASK_ID_RE,
    VALIDATION_INTENT_KINDS,
    WORKER_VALIDATION_MODE_SOURCES,
    WORKER_VALIDATION_MODES,
    WORKSPACE_MODES,
)


class ContractModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class ManifestModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="ignore",
        str_strip_whitespace=True,
    )


def validation_error_summary(exc: ValidationError) -> str:
    errors = exc.errors()
    if not errors:
        return str(exc)
    first = errors[0]
    location = ".".join(str(item) for item in first.get("loc", ())) or "<root>"
    return f"{location}: {first.get('msg', str(exc))}"


def dump_contract(value: Any) -> Any:
    if isinstance(value, BaseModel):
        return value.model_dump(mode="json", exclude_none=False)
    if is_dataclass(value):
        return asdict(cast(Any, value))
    if isinstance(value, dict):
        return {key: dump_contract(item) for key, item in value.items()}
    if isinstance(value, list):
        return [dump_contract(item) for item in value]
    return value


class RunPolicyModel(ContractModel):
    run_budget_usd: float | None = Field(default=None, alias="runBudgetUsd", gt=0)
    budget_behavior: str = Field(default=DEFAULT_RUN_BUDGET_BEHAVIOR, alias="budgetBehavior")
    max_task_stdout_bytes: int | None = Field(default=None, alias="maxTaskStdoutBytes", ge=1)
    max_task_stderr_bytes: int | None = Field(default=None, alias="maxTaskStderrBytes", ge=1)
    max_task_result_bytes: int | None = Field(default=None, alias="maxTaskResultBytes", ge=1)
    artifact_behavior: str = Field(default=DEFAULT_ARTIFACT_BEHAVIOR, alias="artifactBehavior")
    follow_up_behavior: str = Field(default=DEFAULT_FOLLOW_UP_BEHAVIOR, alias="followUpBehavior")
    hitl: bool = False
    hitl_mode: str = Field(default=DEFAULT_HITL_MODE, alias="hitlMode")

    @field_validator("budget_behavior", "artifact_behavior")
    @classmethod
    def valid_behavior(cls, value: str) -> str:
        if value not in RUN_POLICY_BEHAVIORS:
            raise ValueError(f"expected one of {sorted(RUN_POLICY_BEHAVIORS)}")
        return value

    @field_validator("follow_up_behavior")
    @classmethod
    def valid_follow_up_behavior(cls, value: str) -> str:
        if value not in FOLLOW_UP_BEHAVIORS:
            raise ValueError(f"expected one of {sorted(FOLLOW_UP_BEHAVIORS)}")
        return value

    @field_validator("hitl_mode")
    @classmethod
    def valid_hitl_mode(cls, value: str) -> str:
        if value not in HITL_MODES:
            raise ValueError(f"expected one of {sorted(HITL_MODES)}")
        return value

    @model_validator(mode="after")
    def normalize_disabled_hitl_mode(self) -> "RunPolicyModel":
        if not self.hitl and self.hitl_mode != DEFAULT_HITL_MODE:
            raise ValueError("hitlMode requires hitl=true")
        if self.hitl and self.hitl_mode == DEFAULT_HITL_MODE:
            self.hitl_mode = "batch"
        return self


class ExtraToolDefModel(ContractModel):
    name: str
    description: str
    kind: Literal["shell", "script"]
    template: str
    timeout_sec: int = Field(default=30, alias="timeoutSec", ge=1)

    @field_validator("name")
    @classmethod
    def name_not_base_tool(cls, value: str) -> str:
        if value in BASE_TOOL_NAMES:
            raise ValueError(
                f"extraTool name '{value}' collides with a base workspace tool; "
                f"choose a different name (base tools: {sorted(BASE_TOOL_NAMES)})"
            )
        return value

    @field_validator("template")
    @classmethod
    def template_no_traversal(cls, value: str) -> str:
        if ".." in value:
            raise ValueError("extraTool template must not contain '..' (path traversal)")
        return value


class AgentDefinitionModel(ContractModel):
    name: str
    description: str
    prompt: str
    prompt_path: str | None = Field(default=None, alias="promptPath")
    skills: list[str] = Field(default_factory=list)
    model: str | None = None
    model_profile: str | None = Field(default=None, alias="modelProfile")
    effort: str | None = None
    permission_mode: str | None = Field(default=None, alias="permissionMode")
    workspace_mode: str = Field(default="copy", alias="workspaceMode")
    context_mode: str = Field(default=DEFAULT_CONTEXT_MODE, alias="contextMode")
    output_profile: str = Field(default=DEFAULT_OUTPUT_PROFILE, alias="outputProfile")
    worker_validation_mode: str | None = Field(default=None, alias="workerValidationMode")
    timeout_sec: int = Field(default=DEFAULT_TASK_TIMEOUT_SEC, alias="timeoutSec", ge=1)
    max_budget_usd: float | None = Field(default=None, alias="maxBudgetUsd", gt=0)
    max_prompt_chars: int | None = Field(default=None, alias="maxPromptChars", ge=1)
    max_prompt_estimated_tokens: int | None = Field(default=None, alias="maxPromptEstimatedTokens", ge=1)
    allowed_tools: list[str] = Field(default_factory=list, alias="allowedTools")
    disallowed_tools: list[str] = Field(default_factory=list, alias="disallowedTools")
    max_retries: int | None = Field(default=None, alias="maxRetries", ge=0)
    extra_tools: list[ExtraToolDefModel] = Field(default_factory=list, alias="extraTools")

    @field_validator("workspace_mode")
    @classmethod
    def valid_workspace_mode(cls, value: str) -> str:
        if value not in WORKSPACE_MODES:
            raise ValueError(f"expected one of {sorted(WORKSPACE_MODES)}")
        return value

    @field_validator("model_profile")
    @classmethod
    def valid_model_profile(cls, value: str | None) -> str | None:
        if value is not None and value not in MODEL_PROFILE_TO_MODEL:
            raise ValueError(f"expected one of {sorted(MODEL_PROFILE_TO_MODEL)}")
        return value

    @field_validator("output_profile")
    @classmethod
    def valid_output_profile(cls, value: str) -> str:
        if value not in OUTPUT_PROFILES:
            raise ValueError(f"expected one of {sorted(OUTPUT_PROFILES)}")
        return value

    @field_validator("worker_validation_mode")
    @classmethod
    def valid_worker_validation_mode(cls, value: str | None) -> str | None:
        if value is not None and value not in WORKER_VALIDATION_MODES:
            raise ValueError(f"expected one of {sorted(WORKER_VALIDATION_MODES)}")
        return value


class SharedContextModel(ContractModel):
    summary: str
    constraints: list[str] = Field(default_factory=list)
    read_paths: list[str] = Field(default_factory=list, alias="readPaths")
    validation: list[str] = Field(default_factory=list)

    @property
    def files(self) -> list[str]:
        return self.read_paths

    @model_validator(mode="before")
    @classmethod
    def reject_legacy_files(cls, payload: Any) -> Any:
        if isinstance(payload, dict) and "files" in payload:
            raise ValueError("legacy 'files' was replaced by 'readPaths'")
        return payload


class TaskDefinitionModel(ContractModel):
    id: str
    title: str
    agent: str
    prompt: str
    skills: list[str] = Field(default_factory=list)
    depends_on: list[str] = Field(default_factory=list, alias="dependsOn")
    read_paths: list[str] = Field(default_factory=list, alias="readPaths")
    write_paths: list[str] = Field(default_factory=list, alias="writePaths")
    constraints: list[str] = Field(default_factory=list)
    validation: list[str] = Field(default_factory=list)
    workspace_mode: str | None = Field(default=None, alias="workspaceMode")
    model: str | None = None
    model_profile: str | None = Field(default=None, alias="modelProfile")
    effort: str | None = None
    permission_mode: str | None = Field(default=None, alias="permissionMode")
    context_mode: str | None = Field(default=None, alias="contextMode")
    output_profile: str | None = Field(default=None, alias="outputProfile")
    dependency_materialization: str | None = Field(default=None, alias="dependencyMaterialization")
    worker_validation_mode: str | None = Field(default=None, alias="workerValidationMode")
    timeout_sec: int | None = Field(default=None, alias="timeoutSec", ge=1)
    max_budget_usd: float | None = Field(default=None, alias="maxBudgetUsd", gt=0)
    max_prompt_chars: int | None = Field(default=None, alias="maxPromptChars", ge=1)
    max_prompt_estimated_tokens: int | None = Field(default=None, alias="maxPromptEstimatedTokens", ge=1)
    allowed_tools: list[str] = Field(default_factory=list, alias="allowedTools")
    disallowed_tools: list[str] = Field(default_factory=list, alias="disallowedTools")
    max_retries: int | None = Field(default=None, alias="maxRetries", ge=0)
    injected_from: str | None = Field(default=None, alias="injectedFrom")
    condition_field: str | None = Field(default=None, alias="conditionField")
    condition_value: str | None = Field(default=None, alias="conditionValue")
    extra_tools: list[ExtraToolDefModel] = Field(default_factory=list, alias="extraTools")

    @property
    def files(self) -> list[str]:
        return self.read_paths

    @model_validator(mode="before")
    @classmethod
    def reject_legacy_files(cls, payload: Any) -> Any:
        if isinstance(payload, dict) and "files" in payload:
            raise ValueError("legacy 'files' was replaced by 'readPaths' and 'writePaths'")
        return payload

    @field_validator("id")
    @classmethod
    def valid_task_id(cls, value: str) -> str:
        if not TASK_ID_RE.match(value):
            raise ValueError(f"must match {TASK_ID_RE.pattern}")
        return value

    @field_validator("workspace_mode")
    @classmethod
    def valid_workspace_mode(cls, value: str | None) -> str | None:
        if value is not None and value not in WORKSPACE_MODES:
            raise ValueError(f"expected one of {sorted(WORKSPACE_MODES)}")
        return value

    @field_validator("model_profile")
    @classmethod
    def valid_model_profile(cls, value: str | None) -> str | None:
        if value is not None and value not in MODEL_PROFILE_TO_MODEL:
            raise ValueError(f"expected one of {sorted(MODEL_PROFILE_TO_MODEL)}")
        return value

    @field_validator("output_profile")
    @classmethod
    def valid_output_profile(cls, value: str | None) -> str | None:
        if value is not None and value not in OUTPUT_PROFILES:
            raise ValueError(f"expected one of {sorted(OUTPUT_PROFILES)}")
        return value

    @field_validator("dependency_materialization")
    @classmethod
    def valid_dependency_materialization(cls, value: str | None) -> str | None:
        if value is not None and value not in DEPENDENCY_MATERIALIZATION_MODES:
            raise ValueError(f"expected one of {sorted(DEPENDENCY_MATERIALIZATION_MODES)}")
        return value

    @field_validator("worker_validation_mode")
    @classmethod
    def valid_worker_validation_mode(cls, value: str | None) -> str | None:
        if value is not None and value not in WORKER_VALIDATION_MODES:
            raise ValueError(f"expected one of {sorted(WORKER_VALIDATION_MODES)}")
        return value

    @model_validator(mode="after")
    def condition_fields_both_or_neither(self) -> "TaskDefinitionModel":
        has_field = self.condition_field is not None
        has_value = self.condition_value is not None
        if has_field != has_value:
            raise ValueError(
                "conditionField and conditionValue must both be set or both omitted"
            )
        return self


class TaskPlanModel(ContractModel):
    version: Literal[1]
    name: str
    goal: str
    shared_context: SharedContextModel = Field(alias="sharedContext")
    tasks: list[TaskDefinitionModel]
    run_policy: RunPolicyModel = Field(default_factory=RunPolicyModel, alias="runPolicy")

    @model_validator(mode="after")
    def validate_task_graph(self) -> "TaskPlanModel":
        if not self.tasks:
            raise ValueError("tasks must not be empty")
        seen: set[str] = set()
        for task in self.tasks:
            if task.id in seen:
                raise ValueError(f"duplicate task id '{task.id}'")
            seen.add(task.id)
        for task in self.tasks:
            missing = [dependency for dependency in task.depends_on if dependency not in seen]
            if missing:
                raise ValueError(f"{task.id}: unknown dependencies {missing}")
            if task.id in task.depends_on:
                raise ValueError(f"{task.id}: task cannot depend on itself")
        return self


class PromptSectionMetricModel(ContractModel):
    name: str
    heading: str
    chars: int = Field(ge=0)
    estimated_tokens: int = Field(ge=0)
    item_count: int = Field(ge=0)
    truncated: bool = False


class PromptBudgetResultModel(ContractModel):
    max_chars: int | None = Field(default=None, ge=1)
    max_estimated_tokens: int | None = Field(default=None, ge=1)
    exceeded: bool
    violations: list[str] = Field(default_factory=list)


class ValidationIntentModel(ContractModel):
    kind: str
    entrypoint: str
    args: list[str] = Field(default_factory=list)

    @field_validator("kind")
    @classmethod
    def valid_kind(cls, value: str) -> str:
        if value not in VALIDATION_INTENT_KINDS:
            raise ValueError(f"expected one of {sorted(VALIDATION_INTENT_KINDS)}")
        return value


class ReviewFindingModel(ContractModel):
    severity: str
    message: str

    @field_validator("severity")
    @classmethod
    def valid_severity(cls, value: str) -> str:
        if value not in REVIEWER_FINDING_SEVERITIES:
            raise ValueError(f"expected one of {sorted(REVIEWER_FINDING_SEVERITIES)}")
        return value


class DependencyLayerOperationModel(ContractModel):
    path: str
    action: str
    is_binary: bool = False


class DependencyLayerRecordModel(ContractModel):
    task_id: str
    workspace_mode: str
    workspace_path: str
    operations: list[DependencyLayerOperationModel] = Field(default_factory=list)

    @field_validator("workspace_mode")
    @classmethod
    def valid_workspace_mode(cls, value: str) -> str:
        if value not in WORKSPACE_MODES:
            raise ValueError(f"expected one of {sorted(WORKSPACE_MODES)}")
        return value


class DependencyOutputModel(ContractModel):
    task_id: str
    branch_context_id: str | None = None
    status: str
    summary: str
    notes: list[str] | None = None
    follow_ups: list[str] | None = Field(default=None, alias="followUps")
    changed_files: list[str] = Field(default_factory=list, alias="changedFiles")
    diff_preview: str | None = Field(default=None, alias="diffPreview")


class CoordinatorCheckpointModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="allow",
        str_strip_whitespace=True,
    )

    summary_path: str = Field(alias="summaryPath")


class TaskRunRecordModel(ManifestModel):
    id: str
    title: str
    agent: str
    resolved_skills: list[str] = Field(default_factory=list)
    branch_context_id: str
    branch_parent_context_ids: list[str] = Field(default_factory=list)
    status: str
    summary: str
    workspace_mode: str
    workspace_path: str
    started_at: str
    finished_at: str
    files_touched: list[str] = Field(default_factory=list)
    actual_files_touched: list[str] = Field(default_factory=list)
    protected_path_violations: list[str] = Field(default_factory=list)
    validation_commands: list[str] = Field(default_factory=list)
    follow_ups: list[str] = Field(default_factory=list)
    follow_up_tasks: list[dict[str, Any]] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)
    model: str | None = None
    model_profile: str | None = None
    prompt_chars: int = Field(default=0, ge=0)
    prompt_estimated_tokens: int = Field(default=0, ge=0)
    prompt_sections: list[PromptSectionMetricModel] = Field(default_factory=list)
    prompt_budget: PromptBudgetResultModel = Field(
        default_factory=lambda: PromptBudgetResultModel(
            max_chars=None,
            max_estimated_tokens=None,
            exceeded=False,
            violations=[],
        )
    )
    usage: dict[str, Any] | None = None
    return_code: int | None = None
    prompt_path: str = ""
    command_path: str = ""
    stdout_path: str | None = None
    stderr_path: str | None = None
    result_path: str | None = None
    output_profile: str = DEFAULT_OUTPUT_PROFILE
    output_profile_source: str | None = None
    stdout_bytes: int = Field(default=0, ge=0)
    stderr_bytes: int = Field(default=0, ge=0)
    result_bytes: int = Field(default=0, ge=0)
    validation_intents: list[ValidationIntentModel] = Field(default_factory=list)
    unknown_fields: list[str] = Field(default_factory=list)
    dependency_materialization_mode: str = DEFAULT_DEPENDENCY_MATERIALIZATION_MODE
    dependency_layers_applied: list[DependencyLayerRecordModel] = Field(default_factory=list)
    write_scope_violations: list[str] = Field(default_factory=list)
    worker_validation_mode: str = DEFAULT_WORKER_VALIDATION_MODE
    worker_validation_mode_source: str | None = None
    effort: str | None = None
    effort_source: str | None = None
    reviewer_findings: list[ReviewFindingModel] = Field(default_factory=list)
    injected_from: str | None = None
    attempt: int = Field(default=1, ge=1)
    attempt_errors: list[dict[str, Any]] = Field(default_factory=list)
    fingerprint: str | None = None
    fingerprint_inputs: dict[str, Any] | None = None

    @field_validator("output_profile")
    @classmethod
    def valid_output_profile(cls, value: str) -> str:
        if value not in OUTPUT_PROFILES:
            raise ValueError(f"expected one of {sorted(OUTPUT_PROFILES)}")
        return value

    @field_validator("output_profile_source")
    @classmethod
    def valid_output_profile_source(cls, value: str | None) -> str | None:
        if value is not None and value not in OUTPUT_PROFILE_SOURCES:
            raise ValueError(f"expected one of {sorted(OUTPUT_PROFILE_SOURCES)}")
        return value

    @field_validator("dependency_materialization_mode")
    @classmethod
    def valid_dependency_materialization(cls, value: str) -> str:
        if value not in DEPENDENCY_MATERIALIZATION_MODES:
            raise ValueError(f"expected one of {sorted(DEPENDENCY_MATERIALIZATION_MODES)}")
        return value

    @field_validator("worker_validation_mode")
    @classmethod
    def valid_worker_validation_mode(cls, value: str) -> str:
        if value not in WORKER_VALIDATION_MODES:
            raise ValueError(f"expected one of {sorted(WORKER_VALIDATION_MODES)}")
        return value

    @field_validator("worker_validation_mode_source")
    @classmethod
    def valid_worker_validation_mode_source(cls, value: str | None) -> str | None:
        if value is not None and value not in WORKER_VALIDATION_MODE_SOURCES:
            raise ValueError(f"expected one of {sorted(WORKER_VALIDATION_MODE_SOURCES)}")
        return value


class CostRangeModel(ContractModel):
    min: float = Field(ge=0)
    max: float = Field(ge=0)

    @model_validator(mode="after")
    def validate_order(self) -> "CostRangeModel":
        if self.max < self.min:
            raise ValueError("max must be greater than or equal to min")
        return self


class TokenRangeModel(ContractModel):
    min: int = Field(ge=0)
    max: int = Field(ge=0)

    @model_validator(mode="after")
    def validate_order(self) -> "TokenRangeModel":
        if self.max < self.min:
            raise ValueError("max must be greater than or equal to min")
        return self


class TaskCostEstimateModel(ManifestModel):
    task_id: str = Field(alias="taskId")
    title: str
    agent: str
    model: str
    model_alias: str | None = Field(default=None, alias="modelAlias")
    model_profile: str = Field(alias="modelProfile")
    effort: str
    batch_index: int | None = Field(default=None, alias="batchIndex", ge=1)
    depends_on: list[str] = Field(default_factory=list, alias="dependsOn")
    write_task: bool = Field(alias="writeTask")
    prompt_estimate_source: str = Field(alias="promptEstimateSource")
    prompt_estimated_tokens: int = Field(alias="promptEstimatedTokens", ge=0)
    prompt_budget_tokens: int | None = Field(default=None, alias="promptBudgetTokens", ge=1)
    input_tokens: TokenRangeModel = Field(alias="inputTokens")
    output_tokens: TokenRangeModel = Field(alias="outputTokens")
    total_tokens: TokenRangeModel = Field(alias="totalTokens")
    cost_usd: CostRangeModel = Field(alias="costUsd")
    duration_minutes: CostRangeModel = Field(alias="durationMinutes")


class BatchCostEstimateModel(ManifestModel):
    batch_index: int = Field(alias="batchIndex", ge=1)
    task_ids: list[str] = Field(default_factory=list, alias="taskIds")
    parallel_width: int = Field(alias="parallelWidth", ge=0)
    cost_usd: CostRangeModel = Field(alias="costUsd")
    duration_minutes: CostRangeModel = Field(alias="durationMinutes")


class RunCostEstimateModel(ManifestModel):
    currency: str
    pricing_version: str = Field(alias="pricingVersion")
    pricing_path: str = Field(alias="pricingPath")
    estimate_mode: str = Field(alias="estimateMode")
    prompt_observed_task_count: int = Field(alias="promptObservedTaskCount", ge=0)
    prompt_heuristic_task_count: int = Field(alias="promptHeuristicTaskCount", ge=0)
    totals: dict[str, Any]
    wall_clock: dict[str, Any] = Field(alias="wallClock")
    tasks: list[TaskCostEstimateModel] = Field(default_factory=list)
    task_by_id: dict[str, TaskCostEstimateModel] = Field(default_factory=dict, alias="taskById")
    batches: list[BatchCostEstimateModel] = Field(default_factory=list)
    warnings: list[dict[str, Any]] = Field(default_factory=list)


class RunManifestModel(ManifestModel):
    run_id: str = Field(alias="runId")
    generated_at: str = Field(alias="generatedAt")
    dry_run: bool = Field(alias="dryRun")
    follow_up_behavior: str = Field(default=DEFAULT_FOLLOW_UP_BEHAVIOR, alias="followUpBehavior")
    follow_up_behavior_override: str | None = Field(default=None, alias="followUpBehaviorOverride")
    worker_validation_mode: str = Field(alias="workerValidationMode")
    worker_validation_mode_override: str | None = Field(default=None, alias="workerValidationModeOverride")
    effort_override: str | None = Field(default=None, alias="effortOverride")
    task_worker_validation_modes: dict[str, str] = Field(default_factory=dict, alias="taskWorkerValidationModes")
    task_worker_validation_mode_sources: dict[str, str] = Field(default_factory=dict, alias="taskWorkerValidationModeSources")
    task_output_profiles: dict[str, str] = Field(default_factory=dict, alias="taskOutputProfiles")
    task_output_profile_sources: dict[str, str] = Field(default_factory=dict, alias="taskOutputProfileSources")
    task_efforts: dict[str, str | None] = Field(default_factory=dict, alias="taskEfforts")
    task_effort_sources: dict[str, str] = Field(default_factory=dict, alias="taskEffortSources")
    task_models: dict[str, str | None] = Field(default_factory=dict, alias="taskModels")
    task_model_profiles: dict[str, str | None] = Field(default_factory=dict, alias="taskModelProfiles")
    task_resolved_skills: dict[str, list[str]] = Field(default_factory=dict, alias="taskResolvedSkills")
    complex_model_task_ids: list[str] = Field(default_factory=list, alias="complexModelTaskIds")
    complex_model_task_count: int = Field(default=0, alias="complexModelTaskCount", ge=0)
    topology: dict[str, Any] = Field(default_factory=dict)
    repo_root: str = Field(alias="repoRoot")
    plan_path: str = Field(alias="planPath")
    agents_path: str = Field(alias="agentsPath")
    runtime_root: str = Field(alias="runtimeRoot")
    run_dir: str = Field(alias="runDir")
    workspaces_dir: str = Field(alias="workspacesDir")
    run_policy: dict[str, Any] = Field(default_factory=dict, alias="runPolicy")
    run_governance: dict[str, Any] = Field(default_factory=dict, alias="runGovernance")
    plan: dict[str, Any]
    events: list[dict[str, Any]] = Field(default_factory=list)
    usage_totals: dict[str, Any] = Field(default_factory=dict, alias="usageTotals")
    cost_estimate: RunCostEstimateModel | None = Field(default=None, alias="costEstimate")
    tasks: dict[str, TaskRunRecordModel] = Field(default_factory=dict)

    @field_validator("follow_up_behavior")
    @classmethod
    def valid_follow_up_behavior(cls, value: str) -> str:
        if value not in FOLLOW_UP_BEHAVIORS:
            raise ValueError(f"expected one of {sorted(FOLLOW_UP_BEHAVIORS)}")
        return value

    @field_validator("follow_up_behavior_override")
    @classmethod
    def valid_follow_up_behavior_override(cls, value: str | None) -> str | None:
        if value is not None and value not in FOLLOW_UP_BEHAVIORS:
            raise ValueError(f"expected one of {sorted(FOLLOW_UP_BEHAVIORS)}")
        return value
