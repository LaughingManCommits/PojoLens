from __future__ import annotations

import asyncio
import pathlib
import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents.orchestrator_contracts import (
    DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
    DEFAULT_OUTPUT_PROFILE,
    DEFAULT_WORKER_VALIDATION_MODE,
    PromptBudgetResult,
    TaskRunRecord,
)
from pojo_lens_agents.task_fingerprint import compute_task_fingerprint


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_task(
    *,
    id: str = "task-1",
    prompt: str = "do the thing",
    read_paths: list[str] | None = None,
    depends_on: list[str] | None = None,
):
    task = MagicMock()
    task.id = id
    task.prompt = prompt
    task.read_paths = read_paths or []
    task.depends_on = depends_on or []
    return task


def _make_agent(
    *,
    name: str = "implementer",
    prompt: str = "You are an implementer.",
    model: str | None = None,
    model_profile: str | None = "balanced",
    effort: str | None = None,
    skills: list[str] | None = None,
):
    agent = MagicMock()
    agent.name = name
    agent.prompt = prompt
    agent.model = model
    agent.model_profile = model_profile
    agent.effort = effort
    agent.skills = skills or []
    return agent


def _make_record(*, id: str = "dep-1", summary: str = "dep done", status: str = "completed", fingerprint: str | None = None):
    rec = MagicMock()
    rec.id = id
    rec.summary = summary
    rec.status = status
    rec.fingerprint = fingerprint
    return rec


def _static_hash(path: Path) -> str | None:
    """Always returns a fixed hash for any path — isolates tests from filesystem."""
    return "aabbccdd" * 8


def _none_hash(path: Path) -> str | None:
    return None


# ---------------------------------------------------------------------------
# FingerprintStabilityTest
# ---------------------------------------------------------------------------

class FingerprintStabilityTest(unittest.TestCase):
    def _fp(self, **kwargs):
        task = _make_task(**{k: v for k, v in kwargs.items() if k in ("id", "prompt", "read_paths", "depends_on")})
        agent = _make_agent()
        return compute_task_fingerprint(
            task, agent, {}, [],
            Path("/repo"),
            resolved_model="claude-sonnet-4-6",
            resolved_effort=None,
            read_file_hash=_none_hash,
        )

    def test_same_inputs_produce_same_fingerprint(self):
        fp1, _ = self._fp(prompt="do the thing")
        fp2, _ = self._fp(prompt="do the thing")
        self.assertEqual(fp1, fp2)

    def test_fingerprint_is_64_char_hex(self):
        fp, _ = self._fp(prompt="hello")
        self.assertRegex(fp, r"^[0-9a-f]{64}$")

    def test_different_prompts_produce_different_fingerprints(self):
        fp1, _ = self._fp(prompt="do A")
        fp2, _ = self._fp(prompt="do B")
        self.assertNotEqual(fp1, fp2)

    def test_inputs_dict_contains_expected_keys(self):
        _, inputs = self._fp(prompt="test")
        self.assertIn("prompt", inputs)
        self.assertIn("readPaths", inputs)
        self.assertIn("agent", inputs)
        self.assertIn("depSummaries", inputs)
        self.assertIn("model", inputs)
        self.assertIn("effort", inputs)

    def test_model_change_changes_fingerprint(self):
        task = _make_task(prompt="do the thing")
        agent = _make_agent()
        fp1, _ = compute_task_fingerprint(task, agent, {}, [], Path("/repo"), resolved_model="claude-sonnet-4-6", resolved_effort=None, read_file_hash=_none_hash)
        fp2, _ = compute_task_fingerprint(task, agent, {}, [], Path("/repo"), resolved_model="claude-opus-4-7", resolved_effort=None, read_file_hash=_none_hash)
        self.assertNotEqual(fp1, fp2)

    def test_effort_change_changes_fingerprint(self):
        task = _make_task(prompt="do the thing")
        agent = _make_agent()
        fp1, _ = compute_task_fingerprint(task, agent, {}, [], Path("/repo"), resolved_model="claude-sonnet-4-6", resolved_effort="low", read_file_hash=_none_hash)
        fp2, _ = compute_task_fingerprint(task, agent, {}, [], Path("/repo"), resolved_model="claude-sonnet-4-6", resolved_effort="high", read_file_hash=_none_hash)
        self.assertNotEqual(fp1, fp2)

    def test_agent_prompt_change_changes_fingerprint(self):
        task = _make_task(prompt="do the thing")
        agent_a = _make_agent(prompt="You are agent A.")
        agent_b = _make_agent(prompt="You are agent B.")
        fp1, _ = compute_task_fingerprint(task, agent_a, {}, [], Path("/repo"), resolved_model=None, resolved_effort=None, read_file_hash=_none_hash)
        fp2, _ = compute_task_fingerprint(task, agent_b, {}, [], Path("/repo"), resolved_model=None, resolved_effort=None, read_file_hash=_none_hash)
        self.assertNotEqual(fp1, fp2)


# ---------------------------------------------------------------------------
# ReadPathFingerprintTest
# ---------------------------------------------------------------------------

class ReadPathFingerprintTest(unittest.TestCase):
    def _fp_with_hashes(self, read_paths: list[str], hash_map: dict[str, str | None]):
        task = _make_task(prompt="read files")
        agent = _make_agent()
        def fake_hash(path: Path) -> str | None:
            return hash_map.get(path.name)
        return compute_task_fingerprint(
            task, agent, {}, read_paths,
            Path("/repo"),
            resolved_model=None,
            resolved_effort=None,
            read_file_hash=fake_hash,
        )

    def test_read_path_hash_included_in_inputs(self):
        _, inputs = self._fp_with_hashes(["src/foo.py"], {"foo.py": "deadbeef" * 8})
        self.assertIn("src/foo.py", inputs["readPaths"])
        self.assertEqual(inputs["readPaths"]["src/foo.py"], "deadbeef" * 8)

    def test_changed_file_content_changes_fingerprint(self):
        fp1, _ = self._fp_with_hashes(["src/foo.py"], {"foo.py": "aaa" + "0" * 61})
        fp2, _ = self._fp_with_hashes(["src/foo.py"], {"foo.py": "bbb" + "0" * 61})
        self.assertNotEqual(fp1, fp2)

    def test_missing_file_gives_none_hash(self):
        _, inputs = self._fp_with_hashes(["missing.py"], {})
        self.assertIsNone(inputs["readPaths"]["missing.py"])

    def test_read_paths_sorted_in_inputs(self):
        _, inputs = self._fp_with_hashes(["b.py", "a.py"], {"a.py": "aa", "b.py": "bb"})
        self.assertEqual(list(inputs["readPaths"].keys()), ["a.py", "b.py"])

    def test_same_files_different_order_same_fingerprint(self):
        fp1, _ = self._fp_with_hashes(["a.py", "b.py"], {"a.py": "aa", "b.py": "bb"})
        fp2, _ = self._fp_with_hashes(["b.py", "a.py"], {"a.py": "aa", "b.py": "bb"})
        self.assertEqual(fp1, fp2)

    def test_filesystem_read_used_when_no_override(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            (repo / "hello.py").write_text("print('hello')")
            task = _make_task(prompt="read real file")
            agent = _make_agent()
            fp1, inputs1 = compute_task_fingerprint(
                task, agent, {}, ["hello.py"], repo, resolved_model=None, resolved_effort=None
            )
            fp2, inputs2 = compute_task_fingerprint(
                task, agent, {}, ["hello.py"], repo, resolved_model=None, resolved_effort=None
            )
            self.assertEqual(fp1, fp2)
            self.assertIsNotNone(inputs1["readPaths"]["hello.py"])

    def test_file_content_change_changes_fingerprint(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            f = repo / "target.py"
            f.write_text("version 1")
            task = _make_task(prompt="read file")
            agent = _make_agent()
            fp1, _ = compute_task_fingerprint(task, agent, {}, ["target.py"], repo, resolved_model=None, resolved_effort=None)
            f.write_text("version 2")
            fp2, _ = compute_task_fingerprint(task, agent, {}, ["target.py"], repo, resolved_model=None, resolved_effort=None)
            self.assertNotEqual(fp1, fp2)


# ---------------------------------------------------------------------------
# DepSummaryFingerprintTest
# ---------------------------------------------------------------------------

class DepSummaryFingerprintTest(unittest.TestCase):
    def _fp(self, dep_records: dict[str, Any]):
        task = _make_task(prompt="use deps")
        agent = _make_agent()
        return compute_task_fingerprint(
            task, agent, dep_records, [],
            Path("/repo"),
            resolved_model=None,
            resolved_effort=None,
            read_file_hash=_none_hash,
        )

    def test_dep_summary_included_in_inputs(self):
        dep = _make_record(id="dep-a", summary="dep-a done")
        _, inputs = self._fp({"dep-a": dep})
        self.assertIn("dep-a", inputs["depSummaries"])
        self.assertEqual(inputs["depSummaries"]["dep-a"], "dep-a done")

    def test_changed_dep_summary_changes_fingerprint(self):
        dep_v1 = _make_record(id="dep-a", summary="output v1")
        dep_v2 = _make_record(id="dep-a", summary="output v2")
        fp1, _ = self._fp({"dep-a": dep_v1})
        fp2, _ = self._fp({"dep-a": dep_v2})
        self.assertNotEqual(fp1, fp2)

    def test_dep_order_does_not_affect_fingerprint(self):
        dep_a = _make_record(id="dep-a", summary="a done")
        dep_b = _make_record(id="dep-b", summary="b done")
        fp1, _ = self._fp({"dep-a": dep_a, "dep-b": dep_b})
        fp2, _ = self._fp({"dep-b": dep_b, "dep-a": dep_a})
        self.assertEqual(fp1, fp2)

    def test_no_deps_stable(self):
        fp1, _ = self._fp({})
        fp2, _ = self._fp({})
        self.assertEqual(fp1, fp2)


# ---------------------------------------------------------------------------
# RunLoadedPlanReuseTest — integration tests for the task-reused event path
# ---------------------------------------------------------------------------

class RunLoadedPlanReuseTest(unittest.TestCase):
    """Tests that run_ops.run_loaded_plan correctly skips and reuses tasks."""

    def _run_sync(self, coro):
        return asyncio.run(coro)

    def _build_minimal_deps(self):
        """Build the minimal dependency dict required by run_loaded_plan."""
        from pojo_lens_agents import run_ops
        from pojo_lens_agents.orchestrator_contracts import TaskRunRecord, PromptBudgetResult

        def _blocked_record(task, agent_name, agent, workspace_mode, *, reason, dependency_records=None, worker_validation_mode=None, effort_override=None):
            return _make_completed_record(task.id, "blocked", summary=reason)

        def _make_completed_record_inner(task_id, status="completed", summary="done", fp=None, fpi=None):
            return _make_completed_record(task_id, status, summary=summary, fp=fp, fpi=fpi)

        return _blocked_record, _make_completed_record_inner

    def _make_plan_and_agents(self, task_ids: list[str], prompt: str = "do thing"):
        from pojo_lens_agents.orchestrator_contracts import (
            AgentDefinition,
            RunPolicy,
            SharedContext,
            TaskDefinition,
            TaskPlan,
        )
        task_list = []
        for tid in task_ids:
            task_list.append(TaskDefinition(
                id=tid,
                title=f"Task {tid}",
                agent="implementer",
                prompt=prompt,
                workspace_mode="repo",
            ))

        plan = TaskPlan(
            version=1,
            name="test-plan",
            goal="test goal",
            shared_context=SharedContext(summary="", constraints=[], read_paths=[], validation=[]),
            tasks=task_list,
            run_policy=RunPolicy(),
        )
        agent = AgentDefinition(
            name="implementer",
            description="impl",
            prompt="You implement things.",
            model_profile="balanced",
        )
        agents = {"implementer": agent}
        return plan, agents

    def test_task_reused_when_fingerprint_matches(self):
        """A task with a matching prior fingerprint emits task-reused and skips dispatch."""
        from pojo_lens_agents import run_ops

        plan, agents = self._make_plan_and_agents(["task-1"])
        task = plan.tasks[0]

        # Pre-compute with same inputs run_loaded_plan will use:
        # _effective_models returns "claude-sonnet-4-6", _effective_efforts returns None
        fp_hex, fp_inputs = compute_task_fingerprint(
            task, agents["implementer"], {}, [],
            Path("/repo"),
            resolved_model="claude-sonnet-4-6",
            resolved_effort=None,
            read_file_hash=_none_hash,
        )

        prior_record = _make_completed_record("task-1", "completed", fp=fp_hex)

        dispatched: list[str] = []
        events: list[dict] = []

        async def fake_execute(run_dir, runtime_root, workspaces_dir, plan, agents, task, records, **kwargs):
            dispatched.append(task.id)
            return _make_completed_record(task.id, "completed")

        def fake_append_event(run_events, *, phase, **kwargs):
            run_events.append({"phase": phase, **kwargs})
            events.append({"phase": phase, **kwargs})

        with tempfile.TemporaryDirectory() as tmp:
            result = self._run_sync(run_ops.run_loaded_plan(
                Path(tmp) / "plan.json",
                Path(tmp) / "agents.json",
                agents,
                plan,
                claude_bin="claude",
                runtime_root=Path(tmp),
                max_parallel=2,
                continue_on_error=False,
                dry_run=True,
                reuse_unchanged=True,
                prior_completed_records={"task-1": prior_record},
                compute_task_fingerprint=lambda t, ag, dr, rp, model, effort: compute_task_fingerprint(
                    t, ag, dr, rp, Path("/repo"),
                    resolved_model=model, resolved_effort=effort,
                    read_file_hash=_none_hash,
                ),
                effective_task_read_paths=lambda plan, task: [],
                **_minimal_run_kwargs(tmp, fake_execute, fake_append_event),
            ))

        self.assertNotIn("task-1", dispatched, "Task should not be dispatched when reused")
        reused_phases = [e["phase"] for e in events if e.get("task_id") == "task-1"]
        self.assertIn("task-reused", reused_phases)

    def test_task_not_reused_when_fingerprint_mismatches(self):
        """A task with a different fingerprint gets dispatched normally."""
        from pojo_lens_agents import run_ops

        plan, agents = self._make_plan_and_agents(["task-1"])

        # Give prior record a different fingerprint
        prior_record = _make_completed_record("task-1", "completed", fp="0" * 64)

        dispatched: list[str] = []
        events: list[dict] = []

        async def fake_execute(run_dir, runtime_root, workspaces_dir, plan, agents, task, records, **kwargs):
            dispatched.append(task.id)
            return _make_completed_record(task.id, "completed")

        def fake_append_event(run_events, *, phase, **kwargs):
            run_events.append({"phase": phase, **kwargs})
            events.append({"phase": phase, **kwargs})

        with tempfile.TemporaryDirectory() as tmp:
            self._run_sync(run_ops.run_loaded_plan(
                Path(tmp) / "plan.json",
                Path(tmp) / "agents.json",
                agents,
                plan,
                claude_bin="claude",
                runtime_root=Path(tmp),
                max_parallel=2,
                continue_on_error=False,
                dry_run=True,
                reuse_unchanged=True,
                prior_completed_records={"task-1": prior_record},
                compute_task_fingerprint=lambda t, ag, dr, rp, model, effort: compute_task_fingerprint(
                    t, ag, dr, rp, Path("/repo"),
                    resolved_model=model, resolved_effort=effort,
                    read_file_hash=_none_hash,
                ),
                effective_task_read_paths=lambda plan, task: [],
                **_minimal_run_kwargs(tmp, fake_execute, fake_append_event),
            ))

        self.assertIn("task-1", dispatched, "Task should be dispatched when fingerprint differs")
        reused_phases = [e["phase"] for e in events if e.get("task_id") == "task-1"]
        self.assertNotIn("task-reused", reused_phases)

    def test_fingerprint_stored_on_executed_record(self):
        """After execution, the task record has fingerprint and fingerprint_inputs set."""
        from pojo_lens_agents import run_ops

        plan, agents = self._make_plan_and_agents(["task-1"])
        captured_records: dict[str, Any] = {}

        async def fake_execute(run_dir, runtime_root, workspaces_dir, plan, agents, task, records, **kwargs):
            rec = _make_completed_record(task.id, "completed")
            return rec

        def fake_append_event(run_events, *, phase, **kwargs):
            run_events.append({"phase": phase, **kwargs})

        def fake_write_manifest(*args, **kwargs):
            # Capture the records at the final write
            captured_records.update(args[8])  # records is 9th positional arg

        with tempfile.TemporaryDirectory() as tmp:
            self._run_sync(run_ops.run_loaded_plan(
                Path(tmp) / "plan.json",
                Path(tmp) / "agents.json",
                agents,
                plan,
                claude_bin="claude",
                runtime_root=Path(tmp),
                max_parallel=2,
                continue_on_error=False,
                dry_run=True,
                compute_task_fingerprint=lambda t, ag, dr, rp, model, effort: compute_task_fingerprint(
                    t, ag, dr, rp, Path("/repo"),
                    resolved_model=model, resolved_effort=effort,
                    read_file_hash=_none_hash,
                ),
                effective_task_read_paths=lambda plan, task: [],
                **_minimal_run_kwargs(tmp, fake_execute, fake_append_event, write_manifest_fn=fake_write_manifest),
            ))

        rec = captured_records.get("task-1")
        self.assertIsNotNone(rec)
        self.assertIsNotNone(getattr(rec, "fingerprint", None))
        self.assertRegex(rec.fingerprint, r"^[0-9a-f]{64}$")
        self.assertIsNotNone(getattr(rec, "fingerprint_inputs", None))

    def test_reuse_unchanged_false_no_reuse_even_with_matching_fingerprint(self):
        """When reuse_unchanged=False, tasks are always dispatched."""
        from pojo_lens_agents import run_ops

        plan, agents = self._make_plan_and_agents(["task-1"])
        task = plan.tasks[0]

        fp_hex, _ = compute_task_fingerprint(
            task, agents["implementer"], {}, [], Path("/repo"),
            resolved_model="claude-sonnet-4-6", resolved_effort=None, read_file_hash=_none_hash,
        )
        prior_record = _make_completed_record("task-1", "completed", fp=fp_hex)
        dispatched: list[str] = []

        async def fake_execute_4(run_dir, runtime_root, workspaces_dir, plan, agents, task, records, **kwargs):
            dispatched.append(task.id)
            return _make_completed_record(task.id, "completed")

        def fake_append_event_4(run_events, *, phase, **kwargs):
            run_events.append({"phase": phase, **kwargs})

        with tempfile.TemporaryDirectory() as tmp:
            self._run_sync(run_ops.run_loaded_plan(
                Path(tmp) / "plan.json",
                Path(tmp) / "agents.json",
                agents,
                plan,
                claude_bin="claude",
                runtime_root=Path(tmp),
                max_parallel=2,
                continue_on_error=False,
                dry_run=True,
                reuse_unchanged=False,  # <-- disabled
                prior_completed_records={"task-1": prior_record},
                compute_task_fingerprint=lambda t, ag, dr, rp, model, effort: compute_task_fingerprint(
                    t, ag, dr, rp, Path("/repo"),
                    resolved_model=model, resolved_effort=effort,
                    read_file_hash=_none_hash,
                ),
                effective_task_read_paths=lambda plan, task: [],
                **_minimal_run_kwargs(tmp, fake_execute_4, fake_append_event_4),
            ))

        self.assertIn("task-1", dispatched)


# ---------------------------------------------------------------------------
# Minimal run_loaded_plan kwargs builder
# ---------------------------------------------------------------------------

def _make_completed_record(task_id: str, status: str = "completed", summary: str = "done", fp: str | None = None, fpi: dict | None = None) -> TaskRunRecord:
    return TaskRunRecord(
        id=task_id,
        title=task_id,
        agent="implementer",
        resolved_skills=[],
        branch_context_id=task_id,
        branch_parent_context_ids=[],
        status=status,
        summary=summary,
        workspace_mode="repo",
        workspace_path="",
        started_at="2026-01-01T00:00:00+00:00",
        finished_at="2026-01-01T00:00:01+00:00",
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
        validation_commands=[],
        follow_ups=[],
        follow_up_tasks=[],
        notes=[],
        model="claude-sonnet-4-6",
        model_profile="balanced",
        output_profile=DEFAULT_OUTPUT_PROFILE,
        output_profile_source="default",
        prompt_chars=0,
        prompt_estimated_tokens=0,
        prompt_sections=[],
        prompt_budget=PromptBudgetResult(max_chars=None, max_estimated_tokens=None, exceeded=False, violations=[]),
        usage=None,
        return_code=0 if status == "completed" else 1,
        prompt_path="",
        command_path="",
        stdout_path=None,
        stderr_path=None,
        result_path=None,
        dependency_materialization_mode=DEFAULT_DEPENDENCY_MATERIALIZATION_MODE,
        write_scope_violations=[],
        fingerprint=fp,
        fingerprint_inputs=fpi,
    )


def _minimal_run_kwargs(tmp: str, fake_execute, fake_append_event, *, write_manifest_fn=None):
    """Returns the minimal kwargs needed by run_loaded_plan in tests."""
    tmp_path = Path(tmp)

    def _noop(*a, **kw):
        pass

    def _identity_batches(tasks):
        return [tasks]

    def _select_batch(plan, ready, agents, *, max_parallel):
        return ready[:max_parallel]

    def _blocked_record(task, agent_name, agent, workspace_mode, *, reason, dependency_records=None, worker_validation_mode=None, effort_override=None):
        return _make_completed_record(task.id, "blocked", summary=reason)

    def _aggregate_usage(records):
        return {"inputTokens": 0, "outputTokens": 0, "cacheReadInputTokens": 0, "cacheCreationInputTokens": 0}

    def _effective_profiles(plan, agents, **kw):
        return {t.id: "standard" for t in plan.tasks}

    def _effective_profile_sources(plan, agents, **kw):
        return {t.id: "default" for t in plan.tasks}

    def _effective_efforts(plan, agents, **kw):
        return {t.id: None for t in plan.tasks}

    def _effective_effort_sources(plan, agents, **kw):
        return {t.id: "default" for t in plan.tasks}

    def _effective_models(plan, agents):
        return {t.id: "claude-sonnet-4-6" for t in plan.tasks}

    def _effective_model_profiles(plan, agents):
        return {t.id: "balanced" for t in plan.tasks}

    def _effective_task_skills(task, agent):
        return []

    def _branch_context_id(task, records=None):
        return task.id

    def _analyze_topology(plan, agents):
        return {"warnings": [], "warningCount": 0, "taskCount": len(plan.tasks)}

    def _estimate_cost(plan, agents, **kw):
        return {"estimatedCostUsd": 0.0, "warnings": []}

    def _load_pricing():
        return {}

    def _serialize_policy(policy):
        return {}

    def _summarize_worker_validation_mode(modes):
        return modes[0] if modes else "intents-only"

    def _summarize_branch_contexts(records):
        return {}

    def _validate_scope_contract(plan, agents):
        pass

    def _ensure_available(bin):
        pass

    def _write_plan_snapshot(run_dir, plan):
        pass

    def _agent_payload(agents, *, selected_names, resolved_skills_by_name):
        return "{}"

    def _evaluate_governance(records, policy):
        return {"shouldStopScheduling": False, "blockingAlerts": []}

    def _effective_workspace_mode(task, agent):
        return "repo"

    def _default_workspaces_dir(*, runtime_root, run_id):
        return tmp_path / "workspaces" / run_id

    def _slugify(s):
        return s.replace(" ", "-").lower()

    def _hitl_policy(policy, *, hitl_override, hitl_mode_override, hitl_auto_approve):
        pol = MagicMock()
        pol.enabled = False
        pol.mode = "none"
        pol.auto_approve = False
        return pol

    def _should_hitl(policy, *, batch_index, failed_task_ids):
        return False

    def _write_manifest(*args, **kwargs):
        pass

    def _normalize_worker_validation_mode(mode, *, location):
        return mode or None

    def _normalize_effort(effort, *, location):
        return effort or None

    def _effective_plan_worker_validation_modes(plan, agents, **kw):
        return {t.id: "intents-only" for t in plan.tasks}

    def _effective_plan_wvm_sources(plan, agents, **kw):
        return {t.id: "default" for t in plan.tasks}

    def _summarize_run_manifest(*a, **kw):
        return {}, None

    def _parse_iso(*a):
        return None

    def _datetime_to_iso(*a):
        return ""

    def _emit_otel(trace, *, endpoint):
        return {"enabled": False}

    def _complex_model_task_ids(profiles):
        return []

    return {
        "normalize_worker_validation_mode": _normalize_worker_validation_mode,
        "normalize_effort_override": _normalize_effort,
        "effective_plan_worker_validation_modes": _effective_plan_worker_validation_modes,
        "effective_plan_worker_validation_mode_sources": _effective_plan_wvm_sources,
        "effective_plan_output_profiles": _effective_profiles,
        "effective_plan_output_profile_sources": _effective_profile_sources,
        "effective_plan_efforts": _effective_efforts,
        "effective_plan_effort_sources": _effective_effort_sources,
        "effective_task_skills": _effective_task_skills,
        "topological_batches": _identity_batches,
        "validate_scope_contract": _validate_scope_contract,
        "ensure_claude_available": _ensure_available,
        "write_selected_plan_snapshot": _write_plan_snapshot,
        "agent_payload_for_claude": _agent_payload,
        "append_run_event": fake_append_event,
        "task_branch_context_id": _branch_context_id,
        "evaluate_run_governance": _evaluate_governance,
        "blocked_record": _blocked_record,
        "effective_workspace_mode": _effective_workspace_mode,
        "write_manifest": write_manifest_fn or _write_manifest,
        "select_parallel_ready_batch": _select_batch,
        "execute_task": fake_execute,
        "aggregate_usage": _aggregate_usage,
        "effective_plan_model_profiles": _effective_model_profiles,
        "effective_plan_models": _effective_models,
        "complex_model_task_ids": _complex_model_task_ids,
        "analyze_plan_topology": _analyze_topology,
        "load_model_pricing": _load_pricing,
        "estimate_plan_cost": _estimate_cost,
        "serialize_run_policy": _serialize_policy,
        "summarized_worker_validation_mode": _summarize_worker_validation_mode,
        "summarize_branch_contexts": _summarize_branch_contexts,
        "coerce_follow_up_task": None,
        "default_workspaces_dir": _default_workspaces_dir,
        "slugify": _slugify,
        "resolve_hitl_policy": _hitl_policy,
        "should_trigger_hitl_gate": _should_hitl,
        "hitl_gate_context_factory": MagicMock(),
        "wait_for_hitl_decision": MagicMock(),
        "write_text": None,
        "otel_endpoint": None,
        "manifest_payload_builder": None,
        "build_trace_payload": None,
        "summarize_run_manifest": _summarize_run_manifest,
        "parse_iso_datetime": _parse_iso,
        "datetime_to_iso": _datetime_to_iso,
        "emit_otel_trace": _emit_otel,
        "error_factory": RuntimeError,
    }


if __name__ == "__main__":
    unittest.main()
