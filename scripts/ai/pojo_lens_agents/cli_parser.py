#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pojo_lens_agents.orchestrator_contracts import (
    DEFAULT_AGENTS_PATH,
    DEFAULT_CLAUDE_BIN,
    DEFAULT_RUNTIME_ROOT,
    DEFAULT_TASK_TIMEOUT_SEC,
    DEFAULT_VALIDATE_RUN_EXECUTION_SCOPE,
    HITL_MODES,
    PLANNER_TASK_ID,
    VALIDATE_RUN_EXECUTION_SCOPES,
    WORKER_STATUSES,
    WORKER_VALIDATION_MODES,
)
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


def _add_max_task_retries_arg(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--max-task-retries",
        type=int,
        default=None,
        metavar="N",
        help=(
            "Maximum automatic retries per task for transient failures (rate limits, "
            "timeouts, provider errors). Defaults to task/agent definition, then 3. "
            "Pass 0 to disable automatic retry."
        ),
    )


def _add_hitl_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--hitl",
        action="store_true",
        help="Pause at human-in-the-loop approval gates before dispatching later batches.",
    )
    parser.add_argument(
        "--hitl-mode",
        choices=sorted(HITL_MODES - {"none"}),
        default="batch",
        help="HITL gate mode when --hitl is enabled. Defaults to 'batch'.",
    )
    parser.add_argument(
        "--hitl-auto-approve",
        action="store_true",
        help="Emit HITL gate events and continue without blocking. Intended for CI and gate regression tests.",
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
    _add_max_task_retries_arg(run_parser)
    _add_hitl_args(run_parser)
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
    _add_max_task_retries_arg(resume_parser)
    _add_hitl_args(resume_parser)
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
    _add_max_task_retries_arg(retry_parser)
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

    export_trace_parser = subparsers.add_parser(
        "export-trace",
        help="Export a span-style JSON trace from a retained run manifest.",
    )
    export_trace_parser.add_argument(
        "run_ref",
        help="Path to a run directory or its manifest.json file.",
    )
    export_trace_parser.add_argument(
        "--task",
        dest="selected_tasks",
        action="append",
        default=[],
        help="Restrict exported task spans to one or more task ids. Repeatable.",
    )
    export_trace_parser.add_argument(
        "--out",
        default="",
        help="Destination trace path. Defaults under the run directory trace/ subfolder.",
    )
    export_trace_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Build the trace payload without writing it to disk.",
    )
    export_trace_parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the trace export summary as JSON.",
    )
    _add_verbose_arg(export_trace_parser)
    _add_provider_bin_arg(export_trace_parser)

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


