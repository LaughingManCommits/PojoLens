from __future__ import annotations

import pathlib
import sys
import unittest
from dataclasses import dataclass, field


ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import runtime


@dataclass(frozen=True)
class RuntimeTask:
    id: str
    depends_on: list[str] = field(default_factory=list)
    write_scope: list[str] = field(default_factory=list)


class RuntimeSchedulingTest(unittest.TestCase):
    def test_topological_batches_preserve_parallel_ready_tasks(self):
        tasks = [
            RuntimeTask("review", ["implement"]),
            RuntimeTask("inspect-b"),
            RuntimeTask("inspect-a"),
            RuntimeTask("implement", ["inspect-a", "inspect-b"]),
        ]

        batches = runtime.topological_batches(tasks)

        self.assertEqual(
            [["inspect-a", "inspect-b"], ["implement"], ["review"]],
            [[task.id for task in batch] for batch in batches],
        )
        self.assertEqual(
            {"inspect-a": 0, "inspect-b": 0, "implement": 1, "review": 2},
            runtime.task_dependency_hops(tasks),
        )
        self.assertEqual({"inspect-a", "inspect-b", "implement"}, runtime.upstream_task_ids(tasks, "review"))

    def test_detect_parallel_scope_conflicts_reports_overlapping_writers(self):
        tasks = [
            RuntimeTask("write-a", write_scope=["docs"]),
            RuntimeTask("write-b", write_scope=["docs/guide.md"]),
            RuntimeTask("read-only"),
            RuntimeTask("later", ["write-a", "write-b"], write_scope=["docs/other.md"]),
        ]

        conflicts = runtime.detect_parallel_scope_conflicts(
            tasks,
            task_may_write=lambda task: bool(task.write_scope),
            task_write_scope=lambda task: task.write_scope,
        )

        self.assertEqual(
            [{"batch": 1, "taskIds": ["write-a", "write-b"], "overlappingScopes": ["docs", "docs/guide.md"]}],
            conflicts,
        )

    def test_select_parallel_ready_batch_serializes_conflicting_writers(self):
        ready = [
            RuntimeTask("write-b", write_scope=["docs/guide.md"]),
            RuntimeTask("read-only"),
            RuntimeTask("write-a", write_scope=["docs"]),
        ]

        selected = runtime.select_parallel_ready_batch(
            ready,
            max_parallel=3,
            task_may_write=lambda task: bool(task.write_scope),
            task_write_scope=lambda task: task.write_scope,
        )

        self.assertEqual(["read-only", "write-a"], [task.id for task in selected])


if __name__ == "__main__":
    unittest.main()
