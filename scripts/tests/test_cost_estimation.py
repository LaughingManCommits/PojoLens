import unittest

from scripts.tests.test_claude_orchestrator_helpers import load_orchestrator_module


class CostEstimationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_estimate_task_cost_canonicalizes_model_aliases(self):
        orchestrator = self.orchestrator
        pricing = orchestrator.cost_estimation_layer.load_model_pricing(
            read_json=orchestrator.read_json,
            error_factory=orchestrator.OrchestratorError,
        )
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="low",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect-cost",
            title="Inspect cost",
            agent="analyst",
            prompt="Inspect the current worker contract.",
            model="claude-haiku-4-5-20251001",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="cost-alias",
            goal="Canonicalize model aliases in estimates.",
            shared_context=orchestrator.SharedContext(
                summary="Alias estimate test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        estimate = orchestrator.cost_estimation_layer.estimate_task_cost(
            task,
            agent,
            plan,
            pricing=pricing,
            task_model=task.model,
            task_model_profile="simple",
            task_effort="low",
            prompt_estimated_tokens=240,
            batch_index=1,
            estimate_tokens=orchestrator.estimate_tokens,
            error_factory=orchestrator.OrchestratorError,
        )

        self.assertEqual("claude-haiku-4-5", estimate["model"])
        self.assertEqual("claude-haiku-4-5-20251001", estimate["modelAlias"])
        self.assertEqual("observed", estimate["promptEstimateSource"])
        self.assertGreater(estimate["costUsd"]["min"], 0.0)
        self.assertGreaterEqual(estimate["costUsd"]["max"], estimate["costUsd"]["min"])

    def test_estimate_plan_cost_reports_budget_warning_and_batches(self):
        orchestrator = self.orchestrator
        pricing = orchestrator.cost_estimation_layer.load_model_pricing(
            read_json=orchestrator.read_json,
            error_factory=orchestrator.OrchestratorError,
        )
        agent = orchestrator.AgentDefinition(
            name="implementer",
            description="implementation",
            prompt="Return JSON only.",
            model_profile="balanced",
            effort="medium",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read", "Edit"],
            disallowed_tools=[],
        )
        inspect_task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="implementer",
            prompt="Inspect the repo.",
            write_paths=["README.md"],
        )
        followup_task = orchestrator.TaskDefinition(
            id="follow-up",
            title="Follow up",
            agent="implementer",
            prompt="Follow up on the first change.",
            depends_on=["inspect"],
            write_paths=["CHANGELOG.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="cost-plan",
            goal="Estimate a two-batch plan.",
            shared_context=orchestrator.SharedContext(
                summary="Plan estimate test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[inspect_task, followup_task],
            run_policy=orchestrator.RunPolicy(run_budget_usd=0.0001, budget_behavior="warn"),
        )
        topology = orchestrator.analyze_plan_topology(plan, {"implementer": agent})

        estimate = orchestrator.cost_estimation_layer.estimate_plan_cost(
            plan,
            {"implementer": agent},
            pricing=pricing,
            task_models={
                "inspect": orchestrator.MODEL_PROFILE_TO_MODEL["balanced"],
                "follow-up": orchestrator.MODEL_PROFILE_TO_MODEL["balanced"],
            },
            task_model_profiles={"inspect": "balanced", "follow-up": "balanced"},
            task_efforts={"inspect": "medium", "follow-up": "medium"},
            prompt_estimated_tokens_by_task={"inspect": 300},
            topology=topology,
            topological_batches=orchestrator.topological_batches,
            estimate_tokens=orchestrator.estimate_tokens,
            error_factory=orchestrator.OrchestratorError,
        )

        self.assertEqual("mixed", estimate["estimateMode"])
        self.assertEqual(1, estimate["promptObservedTaskCount"])
        self.assertEqual(1, estimate["promptHeuristicTaskCount"])
        self.assertEqual(2, estimate["totals"]["taskCount"])
        self.assertEqual(2, estimate["wallClock"]["batchCount"])
        self.assertEqual(2, len(estimate["tasks"]))
        self.assertEqual("run-budget-below-min-estimate", estimate["warnings"][0]["kind"])


if __name__ == "__main__":
    unittest.main()
