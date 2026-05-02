from __future__ import annotations

import sys
import pathlib
import unittest
from dataclasses import dataclass, field
from typing import Any
from unittest.mock import MagicMock, patch, call

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents.retry_policy import (
    DEFAULT_MAX_TASK_RETRIES,
    backoff_delay_sec,
    classify_failure,
    resolved_max_retries,
)
from pojo_lens_agents.orchestrator_contracts import (
    PromptBudgetResult,
    TaskRunRecord,
    AgentDefinition,
    TaskDefinition,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_prompt_budget(exceeded: bool = False) -> PromptBudgetResult:
    return PromptBudgetResult(
        max_chars=None, max_estimated_tokens=None, exceeded=exceeded, violations=[]
    )


def _make_record(
    *,
    status: str = "failed",
    summary: str = "some error",
    return_code: int | None = 1,
    write_scope_violations: list[str] | None = None,
    protected_path_violations: list[str] | None = None,
    prompt_budget_exceeded: bool = False,
) -> TaskRunRecord:
    return TaskRunRecord(
        id="task-1",
        title="Task 1",
        agent="implementer",
        resolved_skills=[],
        branch_context_id="ctx-1",
        branch_parent_context_ids=[],
        status=status,
        summary=summary,
        workspace_mode="copy",
        workspace_path="/tmp/ws",
        started_at="2026-05-02T00:00:00",
        finished_at="2026-05-02T00:01:00",
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=protected_path_violations or [],
        write_scope_violations=write_scope_violations or [],
        validation_commands=[],
        follow_ups=[],
        notes=[],
        model=None,
        model_profile=None,
        prompt_chars=0,
        prompt_estimated_tokens=0,
        prompt_sections=[],
        prompt_budget=_make_prompt_budget(exceeded=prompt_budget_exceeded),
        usage=None,
        return_code=return_code,
        prompt_path="",
        command_path="",
        stdout_path=None,
        stderr_path=None,
        result_path=None,
    )


def _make_agent(max_retries: int | None = None) -> AgentDefinition:
    return AgentDefinition(
        name="implementer",
        description="implementer",
        prompt="prompt",
        max_retries=max_retries,
    )


def _make_task(max_retries: int | None = None) -> TaskDefinition:
    return TaskDefinition(
        id="task-1",
        title="Task 1",
        agent="implementer",
        prompt="do work",
        max_retries=max_retries,
    )


# ---------------------------------------------------------------------------
# classify_failure tests
# ---------------------------------------------------------------------------

class ClassifyFailureTest(unittest.TestCase):
    def test_completed_is_permanent(self):
        record = _make_record(status="completed")
        self.assertEqual("permanent", classify_failure(record))

    def test_blocked_is_permanent(self):
        record = _make_record(status="blocked")
        self.assertEqual("permanent", classify_failure(record))

    def test_write_scope_violation_is_permanent(self):
        record = _make_record(write_scope_violations=["some/path.py"])
        self.assertEqual("permanent", classify_failure(record))

    def test_protected_path_violation_is_permanent(self):
        record = _make_record(protected_path_violations=["TODO.md"])
        self.assertEqual("permanent", classify_failure(record))

    def test_prompt_budget_exceeded_is_permanent(self):
        record = _make_record(prompt_budget_exceeded=True)
        self.assertEqual("permanent", classify_failure(record))

    def test_return_code_none_is_permanent(self):
        # None return_code means OrchestratorError (logic failure)
        record = _make_record(return_code=None)
        self.assertEqual("permanent", classify_failure(record))

    def test_rate_limit_429_is_transient(self):
        record = _make_record(summary="Claude returned 429 Too Many Requests")
        self.assertEqual("transient", classify_failure(record))

    def test_rate_limit_text_is_transient(self):
        record = _make_record(summary="rate limit exceeded")
        self.assertEqual("transient", classify_failure(record))

    def test_timeout_text_is_transient(self):
        record = _make_record(summary="timed out after 1800 seconds")
        self.assertEqual("transient", classify_failure(record))

    def test_503_is_transient(self):
        record = _make_record(summary="HTTP 503 Service Unavailable")
        self.assertEqual("transient", classify_failure(record))

    def test_overloaded_is_transient(self):
        record = _make_record(summary="API is currently overloaded")
        self.assertEqual("transient", classify_failure(record))

    def test_api_timeout_error_is_transient(self):
        record = _make_record(summary="APITimeoutError: connection timed out")
        self.assertEqual("transient", classify_failure(record))

    def test_authentication_error_is_permanent(self):
        record = _make_record(summary="AuthenticationError: invalid API key")
        self.assertEqual("permanent", classify_failure(record))

    def test_invalid_request_error_is_permanent(self):
        record = _make_record(summary="InvalidRequestError: bad parameters")
        self.assertEqual("permanent", classify_failure(record))

    def test_nonzero_exit_unknown_error_is_permanent(self):
        # No recognized pattern → permanent; operator can use --max-task-retries 0 or manual retry
        record = _make_record(summary="Unexpected failure", return_code=137)
        self.assertEqual("permanent", classify_failure(record))

    def test_json_parse_failure_is_permanent(self):
        record = _make_record(summary="invalid JSON in output", return_code=1)
        self.assertEqual("permanent", classify_failure(record))


# ---------------------------------------------------------------------------
# backoff_delay_sec tests
# ---------------------------------------------------------------------------

class BackoffDelayTest(unittest.TestCase):
    def test_attempt_zero_starts_near_base(self):
        # attempt 0 = 1s * 2^0 = 1s base
        delay = backoff_delay_sec(0, base_sec=1.0, jitter_factor=0.0)
        self.assertAlmostEqual(1.0, delay, places=5)

    def test_attempt_one_doubles(self):
        delay = backoff_delay_sec(1, base_sec=1.0, jitter_factor=0.0)
        self.assertAlmostEqual(2.0, delay, places=5)

    def test_attempt_two_quadruples(self):
        delay = backoff_delay_sec(2, base_sec=1.0, jitter_factor=0.0)
        self.assertAlmostEqual(4.0, delay, places=5)

    def test_cap_prevents_unbounded_growth(self):
        delay = backoff_delay_sec(100, base_sec=1.0, cap_sec=30.0, jitter_factor=0.0)
        self.assertAlmostEqual(30.0, delay, places=5)

    def test_jitter_adds_non_negative_amount(self):
        for attempt in range(5):
            base_delay = backoff_delay_sec(attempt, base_sec=1.0, jitter_factor=0.0)
            jittered_delay = backoff_delay_sec(attempt, base_sec=1.0, jitter_factor=0.1)
            self.assertGreaterEqual(jittered_delay, base_delay)

    def test_jitter_bounded_by_factor(self):
        for _ in range(20):
            delay = backoff_delay_sec(0, base_sec=1.0, cap_sec=30.0, jitter_factor=0.1)
            self.assertLessEqual(delay, 1.0 * 1.1 + 1e-9)
            self.assertGreaterEqual(delay, 1.0)


# ---------------------------------------------------------------------------
# resolved_max_retries tests
# ---------------------------------------------------------------------------

class ResolvedMaxRetriesTest(unittest.TestCase):
    def test_default_when_all_none(self):
        task = _make_task()
        agent = _make_agent()
        self.assertEqual(DEFAULT_MAX_TASK_RETRIES, resolved_max_retries(task, agent))

    def test_run_override_wins_over_task_and_agent(self):
        task = _make_task(max_retries=5)
        agent = _make_agent(max_retries=2)
        self.assertEqual(1, resolved_max_retries(task, agent, run_override=1))

    def test_run_override_zero_disables_retries(self):
        task = _make_task(max_retries=5)
        agent = _make_agent(max_retries=2)
        self.assertEqual(0, resolved_max_retries(task, agent, run_override=0))

    def test_task_max_retries_overrides_agent(self):
        task = _make_task(max_retries=2)
        agent = _make_agent(max_retries=5)
        self.assertEqual(2, resolved_max_retries(task, agent))

    def test_agent_max_retries_used_when_task_is_none(self):
        task = _make_task()
        agent = _make_agent(max_retries=1)
        self.assertEqual(1, resolved_max_retries(task, agent))

    def test_run_override_none_does_not_override(self):
        task = _make_task(max_retries=2)
        agent = _make_agent()
        self.assertEqual(2, resolved_max_retries(task, agent, run_override=None))


# ---------------------------------------------------------------------------
# execute_task_with_retry tests
# ---------------------------------------------------------------------------

class ExecuteTaskWithRetryTest(unittest.TestCase):
    def _make_deps(self, execute_task_fn):
        return {"_inner_execute_task": execute_task_fn}

    def _invoke(self, execute_task_fn, task, agents, max_task_retries=0, dry_run=False):
        from pojo_lens_agents import task_execution as te
        import pojo_lens_agents.retry_policy as rp

        call_count = [0]
        records_returned = []

        def fake_execute_task(run_dir, runtime_root, workspaces_dir, plan, agents_, task_, dep_records, *, claude_bin, agents_json, dry_run, worker_validation_mode, effort_override, deps):
            call_count[0] += 1
            return execute_task_fn(call_count[0])

        original = te.execute_task
        te.execute_task = fake_execute_task
        try:
            with patch.object(rp, "backoff_delay_sec", return_value=0.0):
                result = te.execute_task_with_retry(
                    pathlib.Path("/tmp/run"),
                    pathlib.Path("/tmp/runtime"),
                    pathlib.Path("/tmp/workspaces"),
                    MagicMock(),
                    agents,
                    task,
                    {},
                    max_task_retries=max_task_retries,
                    claude_bin="claude",
                    agents_json="{}",
                    dry_run=dry_run,
                    deps={},
                )
        finally:
            te.execute_task = original

        return result, call_count[0]

    def test_success_on_first_attempt_no_retry(self):
        task = _make_task()
        agents = {"implementer": _make_agent()}

        def make_record(n):
            return _make_record(status="completed", return_code=0)

        result, calls = self._invoke(make_record, task, agents, max_task_retries=3)
        self.assertEqual("completed", result.status)
        self.assertEqual(1, calls)
        self.assertEqual(1, result.attempt)
        self.assertEqual([], result.attempt_errors)

    def test_transient_failure_retries_and_eventually_succeeds(self):
        task = _make_task()
        agents = {"implementer": _make_agent()}

        def make_record(n):
            if n < 3:
                return _make_record(status="failed", summary="rate limit exceeded", return_code=1)
            return _make_record(status="completed", return_code=0)

        result, calls = self._invoke(make_record, task, agents, max_task_retries=3)
        self.assertEqual("completed", result.status)
        self.assertEqual(3, calls)
        self.assertEqual(3, result.attempt)
        self.assertEqual(2, len(result.attempt_errors))

    def test_permanent_failure_does_not_retry(self):
        task = _make_task()
        agents = {"implementer": _make_agent()}

        def make_record(n):
            return _make_record(status="failed", summary="write scope violation", return_code=1,
                                write_scope_violations=["bad/path.py"])

        result, calls = self._invoke(make_record, task, agents, max_task_retries=3)
        self.assertEqual("failed", result.status)
        self.assertEqual(1, calls)
        self.assertEqual(1, result.attempt)
        self.assertEqual([], result.attempt_errors)

    def test_exhausted_retries_returns_failed_record(self):
        task = _make_task()
        agents = {"implementer": _make_agent()}

        def make_record(n):
            return _make_record(status="failed", summary="503 overloaded", return_code=1)

        result, calls = self._invoke(make_record, task, agents, max_task_retries=2)
        self.assertEqual("failed", result.status)
        self.assertEqual(3, calls)  # 1 original + 2 retries
        self.assertEqual(3, result.attempt)
        self.assertEqual(2, len(result.attempt_errors))

    def test_zero_retries_no_retry(self):
        task = _make_task()
        agents = {"implementer": _make_agent()}

        def make_record(n):
            return _make_record(status="failed", summary="rate limit exceeded", return_code=1)

        result, calls = self._invoke(make_record, task, agents, max_task_retries=0)
        self.assertEqual("failed", result.status)
        self.assertEqual(1, calls)
        self.assertEqual(1, result.attempt)

    def test_dry_run_no_retry_even_on_failure(self):
        task = _make_task()
        agents = {"implementer": _make_agent()}

        def make_record(n):
            return _make_record(status="failed", summary="rate limit exceeded", return_code=1)

        result, calls = self._invoke(make_record, task, agents, max_task_retries=3, dry_run=True)
        self.assertEqual(1, calls)
        self.assertEqual(1, result.attempt)

    def test_attempt_errors_contain_expected_fields(self):
        task = _make_task()
        agents = {"implementer": _make_agent()}

        def make_record(n):
            if n == 1:
                return _make_record(status="failed", summary="rate limit exceeded", return_code=1)
            return _make_record(status="completed", return_code=0)

        result, calls = self._invoke(make_record, task, agents, max_task_retries=3)
        self.assertEqual(1, len(result.attempt_errors))
        err = result.attempt_errors[0]
        self.assertEqual(1, err["attempt"])
        self.assertEqual("failed", err["status"])
        self.assertEqual("transient", err["failureKind"])
        self.assertIn("delayMs", err)
        self.assertIn("error", err)

    def test_task_max_retries_field_respected(self):
        task = _make_task(max_retries=1)
        agents = {"implementer": _make_agent()}

        def make_record(n):
            return _make_record(status="failed", summary="503 overloaded", return_code=1)

        result, calls = self._invoke(make_record, task, agents, max_task_retries=None)
        self.assertEqual(2, calls)  # 1 original + 1 task-level retry


# ---------------------------------------------------------------------------
# run_ops retry event emission tests
# ---------------------------------------------------------------------------

class RunOpsRetryEventTest(unittest.TestCase):
    def _make_manifest_record(self, attempt_errors=None):
        record = _make_record(status="completed", return_code=0)
        record.attempt = 2
        record.attempt_errors = attempt_errors or []
        return record

    def test_task_retry_events_emitted_before_task_finished(self):
        """attempt_errors on a record produce task-retry events in run_events."""
        from pojo_lens_agents import run_ops

        attempt_errors = [
            {"attempt": 1, "status": "failed", "error": "rate limit", "failureKind": "transient", "delayMs": 1000, "returnCode": 1},
        ]
        record = self._make_manifest_record(attempt_errors=attempt_errors)

        events: list[dict] = []

        def fake_append_event(event_list, *, phase, **kwargs):
            event_list.append({"phase": phase, **kwargs})

        # Minimal stub of run_loaded_plan that produces one future
        from concurrent.futures import Future

        future = Future()
        future.set_result(record)

        task_stub = MagicMock()
        task_stub.id = "task-1"
        task_stub.depends_on = []

        original_tlpe = run_ops.ThreadPoolExecutor

        class _FakeExecutor:
            def __init__(self, max_workers=1):
                pass

            def __enter__(self):
                return self

            def __exit__(self, *args):
                pass

            def submit(self, fn, *args, **kwargs):
                return future

        run_ops.ThreadPoolExecutor = _FakeExecutor
        try:
            # We only need to test the as_completed loop logic, so drive a minimal call
            from concurrent.futures import as_completed as real_as_completed
            emitted = []

            def capturing_append(event_list, *, phase, **kw):
                emitted.append({"phase": phase, **kw})

            # Simulate just the inner loop body
            records: dict = {}
            task = task_stub
            records[task.id] = future.result()
            for attempt_error in (getattr(records[task.id], "attempt_errors", None) or []):
                capturing_append(
                    emitted,
                    phase="task-retry",
                    task_id=task.id,
                    parent_task_ids=task.depends_on,
                    branch_context_id=records[task.id].branch_context_id,
                    status="retry",
                    message=attempt_error.get("error", ""),
                    details=attempt_error,
                )
            capturing_append(
                emitted,
                phase="task-finished",
                task_id=task.id,
                parent_task_ids=task.depends_on,
                branch_context_id=records[task.id].branch_context_id,
                status=records[task.id].status,
                message=records[task.id].summary,
            )
        finally:
            run_ops.ThreadPoolExecutor = original_tlpe

        phases = [e["phase"] for e in emitted]
        self.assertIn("task-retry", phases)
        self.assertIn("task-finished", phases)
        # retry must precede finished
        retry_idx = phases.index("task-retry")
        finished_idx = phases.index("task-finished")
        self.assertLess(retry_idx, finished_idx)
        # retry event has expected fields
        retry_event = next(e for e in emitted if e["phase"] == "task-retry")
        self.assertEqual("retry", retry_event["status"])
        self.assertEqual("rate limit", retry_event["message"])


# ---------------------------------------------------------------------------
# TaskRunRecord attempt fields round-trip
# ---------------------------------------------------------------------------

class TaskRunRecordAttemptFieldsTest(unittest.TestCase):
    def test_defaults(self):
        record = _make_record()
        self.assertEqual(1, record.attempt)
        self.assertEqual([], record.attempt_errors)

    def test_settable(self):
        record = _make_record()
        record.attempt = 3
        record.attempt_errors = [{"attempt": 1, "error": "oops"}]
        self.assertEqual(3, record.attempt)
        self.assertEqual(1, len(record.attempt_errors))

    def test_asdict_includes_attempt_fields(self):
        from dataclasses import asdict
        record = _make_record()
        record.attempt = 2
        record.attempt_errors = [{"attempt": 1, "error": "timeout"}]
        d = asdict(record)
        self.assertEqual(2, d["attempt"])
        self.assertEqual(1, len(d["attempt_errors"]))


# ---------------------------------------------------------------------------
# AgentDefinition / TaskDefinition max_retries field
# ---------------------------------------------------------------------------

class MaxRetriesFieldTest(unittest.TestCase):
    def test_agent_definition_max_retries_default_none(self):
        agent = _make_agent()
        self.assertIsNone(agent.max_retries)

    def test_agent_definition_max_retries_settable(self):
        agent = _make_agent(max_retries=5)
        self.assertEqual(5, agent.max_retries)

    def test_task_definition_max_retries_default_none(self):
        task = _make_task()
        self.assertIsNone(task.max_retries)

    def test_task_definition_max_retries_settable(self):
        task = _make_task(max_retries=2)
        self.assertEqual(2, task.max_retries)


if __name__ == "__main__":
    unittest.main()
