from __future__ import annotations

import pathlib
import sys
import unittest
from dataclasses import dataclass
from typing import Any


ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import governance


@dataclass
class Record:
    id: str
    status: str = "completed"
    model: str | None = "model-a"
    prompt_estimated_tokens: int = 0
    usage: dict[str, Any] | None = None
    stdout_bytes: int = 0
    stderr_bytes: int = 0
    result_bytes: int = 0


@dataclass
class Policy:
    run_budget_usd: float | None = None
    budget_behavior: str = "stop"
    max_task_stdout_bytes: int | None = None
    max_task_stderr_bytes: int | None = None
    max_task_result_bytes: int | None = None
    artifact_behavior: str = "warn"


class GovernanceTest(unittest.TestCase):
    def test_aggregate_usage_and_artifacts(self):
        records = {
            "a": Record(
                "a",
                prompt_estimated_tokens=10,
                usage={
                    "inputTokens": 2,
                    "outputTokens": 3,
                    "totalCostUsd": 0.125,
                    "modelUsage": {"model-a": {"inputTokens": 2, "costUSD": 0.125}},
                },
                stdout_bytes=5,
            ),
            "b": Record("b", prompt_estimated_tokens=7, stderr_bytes=8),
        }

        self.assertEqual(17, governance.aggregate_usage(records)["promptEstimatedTokens"])
        self.assertEqual(0.125, governance.aggregate_usage(records)["totalCostUsd"])
        self.assertEqual(
            ["b", "a"],
            [item["taskId"] for item in governance.aggregate_artifacts(records, top_task_limit=2)["largestTasks"]],
        )

    def test_evaluate_governance_reports_blocking_budget_and_artifact_warning(self):
        records = {
            "a": Record(
                "a",
                usage={"inputTokens": 2, "outputTokens": 3, "totalCostUsd": 1.0},
                result_bytes=20,
            )
        }

        result = governance.evaluate_run_governance(
            records,
            Policy(run_budget_usd=0.5, max_task_result_bytes=10),
            serialize_run_policy=lambda policy: {"budgetBehavior": policy.budget_behavior},
            top_task_limit=3,
        )

        self.assertEqual("stop", result["status"])
        self.assertEqual(2, result["alertCount"])
        self.assertEqual(1, result["blockingAlertCount"])
        self.assertTrue(result["shouldStopScheduling"])


if __name__ == "__main__":
    unittest.main()
