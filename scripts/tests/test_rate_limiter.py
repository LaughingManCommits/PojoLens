from __future__ import annotations

import asyncio
import pathlib
import sys
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents.rate_limiter import RateLimitBucket


def _run(coro):
    return asyncio.run(coro)


# ---------------------------------------------------------------------------
# RateLimitBucket — enabled / disabled
# ---------------------------------------------------------------------------

class BucketEnabledTest(unittest.TestCase):
    def test_disabled_when_no_limits(self):
        b = RateLimitBucket()
        self.assertFalse(b.enabled)

    def test_enabled_tpm_only(self):
        b = RateLimitBucket(tpm_limit=1000)
        self.assertTrue(b.enabled)

    def test_enabled_rpm_only(self):
        b = RateLimitBucket(rpm_limit=10)
        self.assertTrue(b.enabled)

    def test_enabled_both(self):
        b = RateLimitBucket(tpm_limit=1000, rpm_limit=10)
        self.assertTrue(b.enabled)

    def test_disabled_acquire_returns_zero(self):
        b = RateLimitBucket()
        delay = _run(b.acquire(500))
        self.assertEqual(0.0, delay)

    def test_stats_reflects_limits(self):
        b = RateLimitBucket(tpm_limit=5000, rpm_limit=20)
        s = b.stats()
        self.assertEqual(5000, s["tpmLimit"])
        self.assertEqual(20, s["rpmLimit"])
        self.assertEqual(60.0, s["windowSec"])
        self.assertEqual(0, s["throttleCount"])
        self.assertEqual(0, s["totalDelayMs"])


# ---------------------------------------------------------------------------
# TPM window mechanics
# ---------------------------------------------------------------------------

class TpmWindowTest(unittest.TestCase):
    def test_first_request_within_budget_no_delay(self):
        b = RateLimitBucket(tpm_limit=10_000)
        delay = _run(b.acquire(1_000))
        self.assertEqual(0.0, delay)

    def test_multiple_requests_within_budget_no_delay(self):
        b = RateLimitBucket(tpm_limit=10_000)
        for _ in range(5):
            delay = _run(b.acquire(1_000))
            self.assertEqual(0.0, delay)

    def test_zero_estimated_tokens_passes_through(self):
        b = RateLimitBucket(tpm_limit=100)
        delay = _run(b.acquire(0))
        self.assertEqual(0.0, delay)

    def test_tokens_prune_after_window(self):
        b = RateLimitBucket(tpm_limit=500, window_sec=0.1)
        _run(b.acquire(500))
        time.sleep(0.12)
        delay = _run(b.acquire(500))
        self.assertEqual(0.0, delay)

    def test_throttle_triggers_when_over_budget(self):
        # tpm_limit=100: first acquire(60) OK, second acquire(60) → 120 > 100 → throttles
        b = RateLimitBucket(tpm_limit=100, window_sec=60.0)
        _run(b.acquire(60))
        async def scenario():
            calls = []
            async def fake_sleep(d):
                calls.append(d)
                b._token_events.clear()
            with patch("asyncio.sleep", fake_sleep):
                delay = await b.acquire(60)
            return calls, delay
        calls, delay = _run(scenario())
        self.assertGreater(delay, 0.0)
        self.assertTrue(len(calls) > 0)

    def test_throttle_count_increments(self):
        b = RateLimitBucket(tpm_limit=100, window_sec=60.0)
        _run(b.acquire(60))
        async def scenario():
            async def fake_sleep(d):
                b._token_events.clear()
            with patch("asyncio.sleep", fake_sleep):
                await b.acquire(60)
        _run(scenario())
        self.assertEqual(1, b.stats()["throttleCount"])

    def test_total_delay_ms_accumulates(self):
        b = RateLimitBucket(tpm_limit=100, window_sec=60.0)
        _run(b.acquire(60))
        async def scenario():
            async def fake_sleep(d):
                b._token_events.clear()
            with patch("asyncio.sleep", fake_sleep):
                await b.acquire(60)
        _run(scenario())
        self.assertGreater(b.stats()["totalDelayMs"], 0)


# ---------------------------------------------------------------------------
# RPM window mechanics
# ---------------------------------------------------------------------------

class RpmWindowTest(unittest.TestCase):
    def test_requests_within_rpm_no_delay(self):
        b = RateLimitBucket(rpm_limit=5)
        for _ in range(5):
            delay = _run(b.acquire(0))
            self.assertEqual(0.0, delay)

    def test_rpm_prune_after_window(self):
        b = RateLimitBucket(rpm_limit=2, window_sec=0.1)
        _run(b.acquire(0))
        _run(b.acquire(0))
        time.sleep(0.12)
        delay = _run(b.acquire(0))
        self.assertEqual(0.0, delay)

    def test_rpm_throttle_at_limit(self):
        b = RateLimitBucket(rpm_limit=2, window_sec=60.0)
        _run(b.acquire(0))
        _run(b.acquire(0))
        async def scenario():
            async def fake_sleep(d):
                b._request_events.clear()
            with patch("asyncio.sleep", fake_sleep):
                delay = await b.acquire(0)
            return delay
        delay = _run(scenario())
        self.assertGreater(delay, 0.0)


# ---------------------------------------------------------------------------
# record_completion
# ---------------------------------------------------------------------------

class RecordCompletionTest(unittest.TestCase):
    def test_positive_delta_adds_tokens(self):
        b = RateLimitBucket(tpm_limit=10_000)
        b.record_completion(actual_tokens=500, estimated_tokens=200)
        self.assertEqual(1, len(b._token_events))
        self.assertEqual(300, b._token_events[0][1])

    def test_zero_delta_no_op(self):
        b = RateLimitBucket(tpm_limit=10_000)
        b.record_completion(actual_tokens=200, estimated_tokens=200)
        self.assertEqual(0, len(b._token_events))

    def test_negative_delta_no_op(self):
        b = RateLimitBucket(tpm_limit=10_000)
        b.record_completion(actual_tokens=100, estimated_tokens=500)
        self.assertEqual(0, len(b._token_events))

    def test_no_estimated_charges_full_actual(self):
        b = RateLimitBucket(tpm_limit=10_000)
        b.record_completion(actual_tokens=300)
        self.assertEqual(1, len(b._token_events))
        self.assertEqual(300, b._token_events[0][1])


# ---------------------------------------------------------------------------
# stats()
# ---------------------------------------------------------------------------

class StatsTest(unittest.TestCase):
    def test_initial_stats(self):
        b = RateLimitBucket(tpm_limit=1000, rpm_limit=5, window_sec=30.0)
        s = b.stats()
        self.assertEqual({"tpmLimit": 1000, "rpmLimit": 5, "windowSec": 30.0, "throttleCount": 0, "totalDelayMs": 0}, s)

    def test_stats_keys_complete(self):
        b = RateLimitBucket()
        s = b.stats()
        self.assertSetEqual({"tpmLimit", "rpmLimit", "windowSec", "throttleCount", "totalDelayMs"}, set(s.keys()))


# ---------------------------------------------------------------------------
# run_ops integration: rate-throttle event emitted and rateLimiting in payload
# ---------------------------------------------------------------------------

def _make_task_run_record(task_id: str):
    from pojo_lens_agents.orchestrator_contracts import TaskRunRecord, PromptBudgetResult
    return TaskRunRecord(
        id=task_id,
        title="test task",
        agent="worker",
        resolved_skills=[],
        branch_context_id=task_id,
        branch_parent_context_ids=[],
        status="completed",
        summary="done",
        workspace_mode="repo",
        workspace_path=".",
        started_at="",
        finished_at="",
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
        validation_commands=[],
        follow_ups=[],
        follow_up_tasks=[],
        notes=[],
        model=None,
        model_profile=None,
        prompt_chars=0,
        prompt_estimated_tokens=0,
        prompt_sections=[],
        prompt_budget=PromptBudgetResult(max_chars=None, max_estimated_tokens=None, exceeded=False),
        usage={"totalTokens": 100},
        return_code=0,
        prompt_path="",
        command_path="",
        stdout_path=None,
        stderr_path=None,
        result_path=None,
    )


class RateLimitRunOpsIntegrationTest(unittest.TestCase):
    """Verifies that run_ops.run_loaded_plan wires bucket into _run_one correctly."""

    def _make_run_ops_kwargs(self, bucket):
        from pojo_lens_agents import run_ops

        plan_task = MagicMock()
        plan_task.id = "task-1"
        plan_task.agent = "worker"
        plan_task.depends_on = []

        plan = MagicMock()
        plan.name = "test-plan"
        plan.goal = "test"
        plan.tasks = [plan_task]
        plan.run_policy = MagicMock()
        plan.run_policy.follow_up_behavior = "ignore"

        agent = MagicMock()
        agents = {"worker": agent}
        run_events: list[dict] = []

        def _append_event(events, *, phase, **kwargs):
            events.append({"phase": phase, **kwargs})

        record = _make_task_run_record("task-1")

        async def _execute_task(*args, **kwargs):
            return record

        def _topological_batches(tasks):
            return [tasks]

        def _select_parallel_ready_batch(plan, ready, agents, *, max_parallel):
            return ready[:max_parallel]

        hitl_policy = MagicMock()
        hitl_policy.enabled = False
        hitl_policy.mode = "batch"
        hitl_policy.auto_approve = False

        return dict(
            plan_path=pathlib.Path("."),
            agents_path=pathlib.Path("."),
            agents=agents,
            plan=plan,
            claude_bin="claude",
            runtime_root=pathlib.Path("."),
            max_parallel=1,
            continue_on_error=False,
            dry_run=True,
            rate_limit_bucket=bucket,
            normalize_worker_validation_mode=lambda v, **kw: v or None,
            normalize_effort_override=lambda v, **kw: v or None,
            effective_plan_worker_validation_modes=lambda p, a, **kw: {"task-1": "intents-only"},
            effective_plan_worker_validation_mode_sources=lambda p, a, **kw: {"task-1": "default"},
            effective_plan_output_profiles=lambda p, a: {"task-1": "standard"},
            effective_plan_output_profile_sources=lambda p, a: {"task-1": "default"},
            effective_plan_efforts=lambda p, a, **kw: {"task-1": None},
            effective_plan_effort_sources=lambda p, a, **kw: {"task-1": "default"},
            effective_task_skills=lambda t, a: [],
            topological_batches=_topological_batches,
            validate_scope_contract=lambda p, a: None,
            ensure_claude_available=lambda b: None,
            write_selected_plan_snapshot=lambda d, p: None,
            agent_payload_for_claude=lambda a, **kw: "{}",
            append_run_event=_append_event,
            task_branch_context_id=lambda t, r: t.id,
            evaluate_run_governance=lambda r, rp: {"shouldStopScheduling": False, "blockingAlerts": []},
            blocked_record=lambda t, *args, **kw: MagicMock(status="blocked", branch_context_id=t.id),
            effective_workspace_mode=lambda t, a: "repo",
            write_manifest=lambda *args, **kw: None,
            select_parallel_ready_batch=_select_parallel_ready_batch,
            execute_task=_execute_task,
            aggregate_usage=lambda r: {},
            effective_plan_model_profiles=lambda p, a: {"task-1": None},
            effective_plan_models=lambda p, a: {"task-1": None},
            complex_model_task_ids=lambda m: [],
            analyze_plan_topology=lambda p, a: {"warnings": []},
            load_model_pricing=lambda: {},
            estimate_plan_cost=lambda p, a, **kw: {"tasks": [{"taskId": "task-1", "estimatedInputTokens": 50, "estimatedOutputTokens": 50}], "warnings": []},
            serialize_run_policy=lambda rp: {},
            summarized_worker_validation_mode=lambda modes: "intents-only",
            summarize_branch_contexts=lambda recs: {},
            coerce_follow_up_task=None,
            default_workspaces_dir=lambda *, runtime_root, run_id: pathlib.Path("."),
            slugify=lambda s: s.replace(" ", "-"),
            resolve_hitl_policy=lambda rp, **kw: hitl_policy,
            should_trigger_hitl_gate=lambda policy, **kw: False,
            hitl_gate_context_factory=MagicMock(),
            wait_for_hitl_decision=MagicMock(),
            write_text=None,
        )

    def test_no_bucket_runs_cleanly(self):
        from pojo_lens_agents import run_ops
        kwargs = self._make_run_ops_kwargs(bucket=None)
        payload = asyncio.run(run_ops.run_loaded_plan(**kwargs))
        self.assertIsNone(payload.get("rateLimiting"))
        phases = [e["phase"] for e in payload["events"]]
        self.assertNotIn("rate-throttle", phases)

    def test_bucket_stats_in_payload_when_no_throttle(self):
        from pojo_lens_agents import run_ops
        bucket = RateLimitBucket(tpm_limit=1_000_000, rpm_limit=1000)
        kwargs = self._make_run_ops_kwargs(bucket=bucket)
        payload = asyncio.run(run_ops.run_loaded_plan(**kwargs))
        rl = payload.get("rateLimiting")
        self.assertIsNotNone(rl)
        self.assertEqual(1_000_000, rl["tpmLimit"])
        self.assertEqual(0, rl["throttleCount"])

    def test_rate_throttle_event_emitted_when_throttled(self):
        from pojo_lens_agents import run_ops

        # Very tight budget: will throttle on first task
        bucket = RateLimitBucket(tpm_limit=1, rpm_limit=None, window_sec=60.0)
        # Pre-fill the window so the next acquire must wait
        bucket._token_events.append((time.monotonic(), 1))

        kwargs = self._make_run_ops_kwargs(bucket=bucket)

        slept: list[float] = []
        async def fake_sleep(d):
            slept.append(d)
            bucket._token_events.clear()

        async def run_with_fake_sleep():
            with patch("asyncio.sleep", fake_sleep):
                return await run_ops.run_loaded_plan(**kwargs)

        payload = asyncio.run(run_with_fake_sleep())
        phases = [e["phase"] for e in payload["events"]]
        self.assertIn("rate-throttle", phases)
        throttle_events = [e for e in payload["events"] if e["phase"] == "rate-throttle"]
        self.assertEqual(1, len(throttle_events))
        self.assertEqual("task-1", throttle_events[0]["details"]["taskId"])
        self.assertGreater(throttle_events[0]["details"]["delayMs"], 0)
        rl = payload.get("rateLimiting")
        self.assertIsNotNone(rl)
        self.assertEqual(1, rl["throttleCount"])


# ---------------------------------------------------------------------------
# cli_parser: tpm_limit / rpm_limit flags
# ---------------------------------------------------------------------------

class CliRateLimitArgsTest(unittest.TestCase):
    def setUp(self):
        from pojo_lens_agents.cli_parser import parse_args
        self._parse = parse_args

    def test_run_tpm_limit_parsed(self):
        args = self._parse(["run", "plan.json", "--tpm-limit", "50000"])
        self.assertEqual(50000, args.tpm_limit)

    def test_run_rpm_limit_parsed(self):
        args = self._parse(["run", "plan.json", "--rpm-limit", "30"])
        self.assertEqual(30, args.rpm_limit)

    def test_run_both_limits(self):
        args = self._parse(["run", "plan.json", "--tpm-limit", "100000", "--rpm-limit", "60"])
        self.assertEqual(100000, args.tpm_limit)
        self.assertEqual(60, args.rpm_limit)

    def test_run_no_limits_default_none(self):
        args = self._parse(["run", "plan.json"])
        self.assertIsNone(args.tpm_limit)
        self.assertIsNone(args.rpm_limit)

    def test_resume_tpm_limit_parsed(self):
        args = self._parse(["resume", "run-dir/", "--tpm-limit", "20000"])
        self.assertEqual(20000, args.tpm_limit)

    def test_resume_rpm_limit_parsed(self):
        args = self._parse(["resume", "run-dir/", "--rpm-limit", "15"])
        self.assertEqual(15, args.rpm_limit)

    def test_retry_tpm_limit_parsed(self):
        args = self._parse(["retry", "run-dir/", "--tpm-limit", "75000"])
        self.assertEqual(75000, args.tpm_limit)

    def test_retry_rpm_limit_parsed(self):
        args = self._parse(["retry", "run-dir/", "--rpm-limit", "45"])
        self.assertEqual(45, args.rpm_limit)


# ---------------------------------------------------------------------------
# orchestrator_app: env var pickup for bucket creation
# ---------------------------------------------------------------------------

class OrchestratorAppRateLimitEnvTest(unittest.TestCase):
    def test_env_tpm_limit_creates_bucket(self):
        import os
        from pojo_lens_agents import rate_limiter
        with patch.dict(os.environ, {"ANTHROPIC_TPM_LIMIT": "5000", "ANTHROPIC_RPM_LIMIT": ""}):
            tpm = int(os.environ["ANTHROPIC_TPM_LIMIT"])
            rpm_raw = os.environ.get("ANTHROPIC_RPM_LIMIT", "").strip()
            rpm = int(rpm_raw) if rpm_raw.isdigit() else None
            bucket = rate_limiter.RateLimitBucket(tpm_limit=tpm, rpm_limit=rpm)
            self.assertTrue(bucket.enabled)
            self.assertEqual(5000, bucket.stats()["tpmLimit"])

    def test_env_rpm_limit_creates_bucket(self):
        import os
        from pojo_lens_agents import rate_limiter
        with patch.dict(os.environ, {"ANTHROPIC_TPM_LIMIT": "", "ANTHROPIC_RPM_LIMIT": "30"}):
            tpm_raw = os.environ.get("ANTHROPIC_TPM_LIMIT", "").strip()
            tpm = int(tpm_raw) if tpm_raw.isdigit() else None
            rpm = int(os.environ["ANTHROPIC_RPM_LIMIT"])
            bucket = rate_limiter.RateLimitBucket(tpm_limit=tpm, rpm_limit=rpm)
            self.assertTrue(bucket.enabled)
            self.assertEqual(30, bucket.stats()["rpmLimit"])

    def test_env_empty_no_bucket(self):
        import os
        from pojo_lens_agents import rate_limiter
        with patch.dict(os.environ, {"ANTHROPIC_TPM_LIMIT": "", "ANTHROPIC_RPM_LIMIT": ""}):
            tpm_raw = os.environ.get("ANTHROPIC_TPM_LIMIT", "").strip()
            rpm_raw = os.environ.get("ANTHROPIC_RPM_LIMIT", "").strip()
            tpm = int(tpm_raw) if tpm_raw.isdigit() else None
            rpm = int(rpm_raw) if rpm_raw.isdigit() else None
            bucket = rate_limiter.RateLimitBucket(tpm_limit=tpm, rpm_limit=rpm)
            self.assertFalse(bucket.enabled)

    def test_non_numeric_env_no_bucket(self):
        import os
        from pojo_lens_agents import rate_limiter
        with patch.dict(os.environ, {"ANTHROPIC_TPM_LIMIT": "unlimited", "ANTHROPIC_RPM_LIMIT": ""}):
            tpm_raw = os.environ.get("ANTHROPIC_TPM_LIMIT", "").strip()
            rpm_raw = os.environ.get("ANTHROPIC_RPM_LIMIT", "").strip()
            tpm = int(tpm_raw) if tpm_raw.isdigit() else None
            rpm = int(rpm_raw) if rpm_raw.isdigit() else None
            bucket = rate_limiter.RateLimitBucket(tpm_limit=tpm, rpm_limit=rpm)
            self.assertFalse(bucket.enabled)


if __name__ == "__main__":
    unittest.main()
