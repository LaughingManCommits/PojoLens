from __future__ import annotations

from typing import Callable, Protocol, TypeVar


class TaskLike(Protocol):
    id: str
    depends_on: list[str]


TaskT = TypeVar("TaskT", bound=TaskLike)


class RuntimePlanError(RuntimeError):
    """Raised when task topology or runtime scheduling input is invalid."""


def topological_batches(
    tasks: list[TaskT],
    *,
    error_factory: Callable[[str], Exception] = RuntimePlanError,
) -> list[list[TaskT]]:
    by_id = {task.id: task for task in tasks}
    pending = {task.id: set(task.depends_on) for task in tasks}
    batches: list[list[TaskT]] = []
    while pending:
        ready_ids = sorted(task_id for task_id, dependencies in pending.items() if not dependencies)
        if not ready_ids:
            raise error_factory("Task plan contains a dependency cycle")
        batches.append([by_id[task_id] for task_id in ready_ids])
        for task_id in ready_ids:
            pending.pop(task_id)
        for dependencies in pending.values():
            dependencies.difference_update(ready_ids)
    return batches


def task_dependency_hops(tasks: list[TaskT]) -> dict[str, int]:
    by_id = {task.id: task for task in tasks}
    memo: dict[str, int] = {}

    def hops(task_id: str) -> int:
        cached = memo.get(task_id)
        if cached is not None:
            return cached
        task = by_id[task_id]
        value = 0 if not task.depends_on else 1 + max(hops(dependency_id) for dependency_id in task.depends_on)
        memo[task_id] = value
        return value

    return {task.id: hops(task.id) for task in tasks}


def upstream_task_ids(tasks: list[TaskT], task_id: str) -> set[str]:
    by_id = {task.id: task for task in tasks}
    seen: set[str] = set()
    stack = list(by_id[task_id].depends_on)
    while stack:
        dependency_id = stack.pop()
        if dependency_id in seen:
            continue
        seen.add(dependency_id)
        stack.extend(by_id[dependency_id].depends_on)
    return seen


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


def detect_parallel_scope_conflicts(
    tasks: list[TaskT],
    *,
    task_may_write: Callable[[TaskT], bool],
    task_write_scope: Callable[[TaskT], list[str]],
    error_factory: Callable[[str], Exception] = RuntimePlanError,
) -> list[dict[str, object]]:
    conflicts: list[dict[str, object]] = []
    for batch_index, batch in enumerate(topological_batches(tasks, error_factory=error_factory), start=1):
        for left_index, left_task in enumerate(batch):
            if not task_may_write(left_task):
                continue
            left_scopes = task_write_scope(left_task)
            for right_task in batch[left_index + 1 :]:
                if not task_may_write(right_task):
                    continue
                overlaps = overlapping_scope_entries(left_scopes, task_write_scope(right_task))
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
    ready: list[TaskT],
    *,
    max_parallel: int,
    task_may_write: Callable[[TaskT], bool],
    task_write_scope: Callable[[TaskT], list[str]],
) -> list[TaskT]:
    ordered = sorted(ready, key=lambda task: task.id)
    selected: list[TaskT] = []
    for candidate in ordered:
        if len(selected) >= max_parallel:
            break
        if not task_may_write(candidate):
            selected.append(candidate)
            continue
        candidate_scopes = task_write_scope(candidate)
        if any(
            task_may_write(chosen)
            and overlapping_scope_entries(
                candidate_scopes,
                task_write_scope(chosen),
            )
            for chosen in selected
        ):
            continue
        selected.append(candidate)
    if selected:
        return selected
    return ordered[:1]


def dedupe_strings(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result
