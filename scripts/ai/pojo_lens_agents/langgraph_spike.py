from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any

from pojo_lens_agents import runtime


@dataclass(frozen=True)
class GraphLifecycleNode:
    node_id: str
    orchestrator_step: str
    purpose: str
    checkpoint_after: bool = False
    interrupt_kind: str | None = None
    retained_layers: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class SemanticComparison:
    topic: str
    current_behavior: str
    langgraph_mapping: str
    fit: str
    risk: str


@dataclass(frozen=True)
class SpikeTask:
    id: str
    depends_on: list[str] = field(default_factory=list)
    write_scope: list[str] = field(default_factory=list)

    @property
    def may_write(self) -> bool:
        return bool(self.write_scope)


def current_run_lifecycle_nodes() -> list[GraphLifecycleNode]:
    return [
        GraphLifecycleNode(
            node_id="load-plan",
            orchestrator_step="load plan",
            purpose="Load the selected tracked task plan and normalize the run contract.",
            retained_layers=["plan loading", "agent resolution"],
        ),
        GraphLifecycleNode(
            node_id="validate-scope",
            orchestrator_step="validate scope",
            purpose="Reject invalid read paths, write scopes, dependency materialization, and protected-path violations before execution.",
            retained_layers=["governance", "path safety"],
        ),
        GraphLifecycleNode(
            node_id="hydrate-workspace",
            orchestrator_step="hydrate workspace",
            purpose="Build the sparse copy or worktree input for the task from declared context and reviewed dependency layers.",
            retained_layers=["workspace hydration", "dependency materialization"],
        ),
        GraphLifecycleNode(
            node_id="invoke-worker",
            orchestrator_step="invoke worker",
            purpose="Run the provider process for one task and capture stdout, stderr, JSON result, and usage.",
            checkpoint_after=True,
            retained_layers=["provider adapter"],
        ),
        GraphLifecycleNode(
            node_id="parse-result",
            orchestrator_step="parse result",
            purpose="Normalize worker JSON into the task record schema and preserve unknown vs known-empty fields.",
            retained_layers=["worker result normalization"],
        ),
        GraphLifecycleNode(
            node_id="audit-diff",
            orchestrator_step="audit diff",
            purpose="Compare actual workspace changes against declared write scope and protected paths.",
            checkpoint_after=True,
            retained_layers=["workspace review", "path safety"],
        ),
        GraphLifecycleNode(
            node_id="checkpoint-run",
            orchestrator_step="checkpoint",
            purpose="Persist manifest state after each task or ready batch so resume and retry stay manifest-backed.",
            checkpoint_after=True,
            retained_layers=["run store", "manifest writing"],
        ),
        GraphLifecycleNode(
            node_id="review-gate",
            orchestrator_step="review interrupt",
            purpose="Pause for human review before replaying isolated workspace changes into the repo.",
            interrupt_kind="human-review",
            retained_layers=["workspace review", "promotion safety"],
        ),
        GraphLifecycleNode(
            node_id="validate-run",
            orchestrator_step="validate run",
            purpose="Execute accepted validation intents from the repo root or task workspaces and record outcomes separately from worker suggestions.",
            checkpoint_after=True,
            retained_layers=["validation intent execution", "run store"],
        ),
        GraphLifecycleNode(
            node_id="promote",
            orchestrator_step="promote",
            purpose="Apply reviewed isolated workspace changes back into the repo when ownership and safety checks pass.",
            interrupt_kind="promotion-approval",
            retained_layers=["promotion safety", "workspace review"],
        ),
    ]


def backend_boundary_summary() -> dict[str, Any]:
    nodes = current_run_lifecycle_nodes()
    return {
        "langgraphCandidateNodes": [node.node_id for node in nodes],
        "langgraphCouldOwn": [
            "durable graph state progression",
            "interrupt routing",
            "node-level replay",
            "resume from checkpointed state",
        ],
        "mustRemainCustom": [
            "plan loading",
            "scope validation",
            "parallel write-scope conflict detection",
            "workspace hydration",
            "diff audit",
            "run manifest schema",
            "promotion safety",
            "coordinator validation intent policy",
        ],
        "checkpointBoundaries": [node.node_id for node in nodes if node.checkpoint_after],
        "interruptNodes": {
            node.node_id: node.interrupt_kind
            for node in nodes
            if node.interrupt_kind is not None
        },
    }


def resume_retry_semantics() -> list[SemanticComparison]:
    return [
        SemanticComparison(
            topic="same-run resume",
            current_behavior="Resume keeps the existing run id, run directory, workspaces directory, and completed task records, then rebuilds unfinished copy/worktree task workspaces before rerun.",
            langgraph_mapping="LangGraph checkpoints can model resumable graph state, but the repo should still treat the retained manifest plus selected-plan snapshot as the operator-facing continuity record.",
            fit="partial",
            risk="Letting LangGraph become the primary continuity record would weaken the current manifest-first operator workflow.",
        ),
        SemanticComparison(
            topic="retry into a new run",
            current_behavior="Retry creates a new run, narrows to failed or blocked tasks, and seeds completed dependencies from the prior manifest when safe.",
            langgraph_mapping="LangGraph replay can express node-level reruns, but cross-run retry still needs repo-owned manifest seeding and explicit new-run boundaries.",
            fit="partial",
            risk="A graph-native replay model does not naturally replace the current distinction between same-run resume and new-run retry.",
        ),
        SemanticComparison(
            topic="partial checkpoint behavior",
            current_behavior="The coordinator writes the manifest after each completed future inside a ready batch, so partial progress survives worker failure or blocking.",
            langgraph_mapping="LangGraph durable execution can improve checkpoint mechanics, but only if checkpoint granularity stays at least as fine as the current per-task manifest updates.",
            fit="strong",
            risk="Batch-only checkpointing would be weaker than the current per-task manifest persistence.",
        ),
        SemanticComparison(
            topic="parallel scheduling",
            current_behavior="Ready tasks run up to --max-parallel, but overlapping write-capable tasks are serialized conservatively by declared write scope.",
            langgraph_mapping="Parallel graph nodes are viable only if the backend delegates ready-batch selection and write-scope conflict detection back to repo-owned runtime logic.",
            fit="strong",
            risk="Naive graph parallelism would violate the current conservative write-scope contract.",
        ),
    ]


def interrupt_evaluation() -> list[SemanticComparison]:
    return [
        SemanticComparison(
            topic="review interrupt",
            current_behavior="Review happens after task execution and diff audit, using repo-owned workspace summaries and patch export surfaces before any promotion.",
            langgraph_mapping="A graph interrupt can pause at review, but the review payload, diff audit, and approval semantics should remain coordinator-owned outer logic.",
            fit="partial",
            risk="Moving review semantics into the graph runtime would blur the current repo-owned audit and promotion boundary.",
        ),
        SemanticComparison(
            topic="promotion approval",
            current_behavior="Promotion is a separate coordinator-controlled step that rejects protected-path violations, repo-mode changes, duplicate ownership, and traversal outside the repo root.",
            langgraph_mapping="LangGraph can represent an approval gate, but it should trigger the existing promotion checks rather than replace them.",
            fit="partial",
            risk="Treating promotion as a generic graph node could weaken the existing safety refusal path unless the node is only a wrapper around current checks.",
        ),
    ]


def decision_summary() -> dict[str, Any]:
    resume_retry = resume_retry_semantics()
    interrupts = interrupt_evaluation()
    return {
        "decision": "keep-custom-scheduler",
        "recommendation": "Keep the current scheduler and manifest model as the production path; revisit LangGraph only as an optional wrapper for checkpointing and interrupt routing after a concrete durability gap appears.",
        "reasons": [
            "The current runtime already provides manifest-backed resume, retry, review, validation, and promotion semantics with conservative parallel scheduling.",
            "LangGraph is a strong fit for durable execution and graph visibility, but only a partial fit for the repo's same-run resume vs new-run retry distinction.",
            "Review and promotion are better treated as coordinator-owned gates wrapped by a backend, not delegated into a generic graph runtime.",
        ],
        "prerequisitesForRevisit": [
            "A measured need for richer checkpoint recovery than current per-task manifest writes provide.",
            "A backend boundary that keeps manifests operator-facing and reuses repo-owned scope, hydration, diff-audit, and promotion checks unchanged.",
            "Proof that per-task checkpoint granularity and conservative write-scope serialization remain intact under an alternate runtime.",
        ],
        "resumeRetry": [asdict(item) for item in resume_retry],
        "interrupts": [asdict(item) for item in interrupts],
        "boundary": backend_boundary_summary(),
    }


def simulate_checkpointed_execution(
    tasks: list[SpikeTask],
    *,
    max_parallel: int,
) -> dict[str, Any]:
    pending = {task.id: task for task in tasks}
    completed: list[str] = []
    batches: list[dict[str, Any]] = []
    checkpoints: list[dict[str, Any]] = []

    while pending:
        ready = [
            task
            for task in pending.values()
            if all(dependency_id in completed for dependency_id in task.depends_on)
        ]
        if not ready:
            raise runtime.RuntimePlanError("No schedulable tasks remain in the simulated graph")
        batch = runtime.select_parallel_ready_batch(
            ready,
            max_parallel=max(1, max_parallel),
            task_may_write=lambda task: task.may_write,
            task_write_scope=lambda task: task.write_scope,
        )
        batch_ids = [task.id for task in batch]
        completed.extend(batch_ids)
        for task_id in batch_ids:
            pending.pop(task_id, None)
        batches.append(
            {
                "batch": len(batches) + 1,
                "taskIds": batch_ids,
            }
        )
        checkpoints.append(
            {
                "checkpointId": f"after-batch-{len(batches)}",
                "completedTaskIds": sorted(completed),
                "remainingTaskIds": sorted(pending),
                "resumableTaskIds": sorted(
                    task.id
                    for task in pending.values()
                    if all(dependency_id in completed for dependency_id in task.depends_on)
                ),
            }
        )

    return {
        "batches": batches,
        "checkpoints": checkpoints,
        "parallelConflicts": runtime.detect_parallel_scope_conflicts(
            tasks,
            task_may_write=lambda task: task.may_write,
            task_write_scope=lambda task: task.write_scope,
        ),
        "boundary": backend_boundary_summary(),
    }


def serialize_lifecycle_nodes() -> list[dict[str, Any]]:
    return [asdict(node) for node in current_run_lifecycle_nodes()]
