from __future__ import annotations

import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import langgraph_spike


class LangGraphSpikeTest(unittest.TestCase):
    def test_lifecycle_mapping_covers_required_run_steps(self):
        node_ids = [node["node_id"] for node in langgraph_spike.serialize_lifecycle_nodes()]

        self.assertEqual(
            [
                "load-plan",
                "validate-scope",
                "hydrate-workspace",
                "invoke-worker",
                "parse-result",
                "audit-diff",
                "checkpoint-run",
                "review-gate",
                "validate-run",
                "promote",
            ],
            node_ids,
        )

    def test_simulated_checkpointed_execution_preserves_parallelism_and_serializes_conflicts(self):
        payload = langgraph_spike.simulate_checkpointed_execution(
            [
                langgraph_spike.SpikeTask("inspect-a"),
                langgraph_spike.SpikeTask("inspect-b"),
                langgraph_spike.SpikeTask("write-a", ["inspect-a"], ["docs"]),
                langgraph_spike.SpikeTask("write-b", ["inspect-b"], ["docs/guide.md"]),
            ],
            max_parallel=2,
        )

        self.assertEqual(
            [
                {"batch": 1, "taskIds": ["inspect-a", "inspect-b"]},
                {"batch": 2, "taskIds": ["write-a"]},
                {"batch": 3, "taskIds": ["write-b"]},
            ],
            payload["batches"],
        )
        self.assertEqual(
            [
                {
                    "batch": 2,
                    "taskIds": ["write-a", "write-b"],
                    "overlappingScopes": ["docs", "docs/guide.md"],
                }
            ],
            payload["parallelConflicts"],
        )
        self.assertEqual(
            ["write-a", "write-b"],
            payload["checkpoints"][0]["resumableTaskIds"],
        )
        self.assertEqual([], payload["checkpoints"][-1]["remainingTaskIds"])

    def test_resume_retry_semantics_capture_manifest_first_behavior(self):
        comparisons = {
            item.topic: item
            for item in langgraph_spike.resume_retry_semantics()
        }

        self.assertEqual("partial", comparisons["same-run resume"].fit)
        self.assertIn("existing run id", comparisons["same-run resume"].current_behavior)
        self.assertIn("new run", comparisons["retry into a new run"].current_behavior)
        self.assertEqual("strong", comparisons["partial checkpoint behavior"].fit)

    def test_decision_summary_recommends_keeping_custom_scheduler(self):
        decision = langgraph_spike.decision_summary()

        self.assertEqual("keep-custom-scheduler", decision["decision"])
        self.assertIn("manifest model", decision["recommendation"])
        self.assertGreaterEqual(len(decision["reasons"]), 3)
        self.assertIn("parallel write-scope conflict detection", decision["boundary"]["mustRemainCustom"])


if __name__ == "__main__":
    unittest.main()
