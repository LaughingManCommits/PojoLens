from __future__ import annotations

import json
import pathlib
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents.run_ledger import (
    LEDGER_MAX_AGE_DAYS,
    RunLedgerEntry,
    RunLedgerTaskEntry,
    append_ledger_entry,
    build_ledger_entry,
    format_ledger_context,
    load_ledger_entries,
    prune_ledger_entries,
    summarize_ledger_entries,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _iso(dt: datetime) -> str:
    return dt.isoformat()


def _make_run_payload(
    *,
    run_id: str = "run-abc",
    plan: str = "test-plan",
    tasks: list[dict] | None = None,
) -> dict:
    if tasks is None:
        tasks = [
            {
                "id": "task-1",
                "status": "completed",
                "agent": "implementer",
                "summary": "done",
                "usage": {"cost_usd": 0.01},
                "actual_files_touched": ["scripts/ai/foo.py", "scripts/ai/bar.py"],
            }
        ]
    return {"runId": run_id, "plan": plan, "tasks": tasks}


def _make_entry(
    *,
    run_id: str = "run-abc",
    plan_name: str = "test-plan",
    generated_at: str | None = None,
    tasks: list[RunLedgerTaskEntry] | None = None,
) -> RunLedgerEntry:
    if generated_at is None:
        generated_at = datetime.now(timezone.utc).isoformat()
    if tasks is None:
        tasks = [RunLedgerTaskEntry(id="t1", status="completed", failure_kind=None, cost_usd=0.01, modules=["scripts"])]
    return RunLedgerEntry(
        run_id=run_id,
        plan_name=plan_name,
        generated_at=generated_at,
        task_count=len(tasks),
        tasks=tasks,
        reviewer_block_count=0,
        planner_notes=None,
    )


# ---------------------------------------------------------------------------
# build_ledger_entry
# ---------------------------------------------------------------------------

class BuildLedgerEntryTest(unittest.TestCase):
    def test_basic_completed(self):
        payload = _make_run_payload()
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.run_id, "run-abc")
        self.assertEqual(entry.plan_name, "test-plan")
        self.assertEqual(entry.task_count, 1)
        self.assertEqual(len(entry.tasks), 1)
        self.assertEqual(entry.tasks[0].status, "completed")
        self.assertIsNone(entry.tasks[0].failure_kind)
        self.assertAlmostEqual(entry.tasks[0].cost_usd, 0.01)
        self.assertIn("scripts", entry.tasks[0].modules)

    def test_failed_task_gets_failure_kind(self):
        payload = _make_run_payload(tasks=[
            {"id": "t1", "status": "failed", "agent": "implementer",
             "summary": "exit code 1", "usage": None, "actual_files_touched": []},
        ])
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.tasks[0].failure_kind, "permanent")

    def test_transient_failure_kind_from_rate_limit(self):
        payload = _make_run_payload(tasks=[
            {"id": "t1", "status": "failed", "agent": "implementer",
             "summary": "rate limit exceeded (429)", "usage": None, "actual_files_touched": []},
        ])
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.tasks[0].failure_kind, "transient")

    def test_transient_failure_kind_from_timeout(self):
        payload = _make_run_payload(tasks=[
            {"id": "t1", "status": "failed", "agent": "implementer",
             "summary": "timeout waiting for worker", "usage": None, "actual_files_touched": []},
        ])
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.tasks[0].failure_kind, "transient")

    def test_reviewer_blocked_increments_reviewer_block_count(self):
        payload = _make_run_payload(tasks=[
            {"id": "r1", "status": "blocked", "agent": "reviewer",
             "summary": "blocked", "usage": None, "actual_files_touched": []},
        ])
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.reviewer_block_count, 1)

    def test_non_reviewer_blocked_not_counted(self):
        payload = _make_run_payload(tasks=[
            {"id": "i1", "status": "blocked", "agent": "implementer",
             "summary": "dep failed", "usage": None, "actual_files_touched": []},
        ])
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.reviewer_block_count, 0)

    def test_cost_from_totalCostUsd(self):
        payload = _make_run_payload(tasks=[
            {"id": "t1", "status": "completed", "agent": "analyst",
             "summary": "ok", "usage": {"totalCostUsd": 0.05}, "actual_files_touched": []},
        ])
        entry = build_ledger_entry(payload)
        self.assertAlmostEqual(entry.tasks[0].cost_usd, 0.05)

    def test_modules_deduped_and_top_level(self):
        payload = _make_run_payload(tasks=[
            {"id": "t1", "status": "completed", "agent": "analyst",
             "summary": "ok", "usage": None,
             "actual_files_touched": ["scripts/a.py", "scripts/b.py", "pojo-lens-core/X.java"]},
        ])
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.tasks[0].modules, ["scripts", "pojo-lens-core"])

    def test_iso_now_fn_used(self):
        fixed = "2026-05-02T10:00:00+00:00"
        payload = _make_run_payload()
        entry = build_ledger_entry(payload, iso_now_fn=lambda: fixed)
        self.assertEqual(entry.generated_at, fixed)

    def test_planner_notes_forwarded(self):
        payload = _make_run_payload()
        entry = build_ledger_entry(payload, planner_notes="looked good")
        self.assertEqual(entry.planner_notes, "looked good")

    def test_empty_tasks(self):
        payload = _make_run_payload(tasks=[])
        entry = build_ledger_entry(payload)
        self.assertEqual(entry.task_count, 0)
        self.assertEqual(entry.tasks, [])


# ---------------------------------------------------------------------------
# append + load roundtrip
# ---------------------------------------------------------------------------

class AppendLoadTest(unittest.TestCase):
    def test_roundtrip_single_entry(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            entry = _make_entry()
            append_ledger_entry(ledger_path, entry)
            loaded = load_ledger_entries(ledger_path)
            self.assertEqual(len(loaded), 1)
            self.assertEqual(loaded[0].run_id, entry.run_id)
            self.assertEqual(loaded[0].plan_name, entry.plan_name)
            self.assertEqual(loaded[0].task_count, entry.task_count)

    def test_multiple_entries_returned_newest_first(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            now = datetime.now(timezone.utc)
            for i in range(3):
                ts = (now + timedelta(seconds=i)).isoformat()
                append_ledger_entry(ledger_path, _make_entry(run_id=f"run-{i}", generated_at=ts))
            loaded = load_ledger_entries(ledger_path)
            self.assertEqual(len(loaded), 3)
            self.assertEqual(loaded[0].run_id, "run-2")
            self.assertEqual(loaded[2].run_id, "run-0")

    def test_limit_respected(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            for i in range(5):
                append_ledger_entry(ledger_path, _make_entry(run_id=f"run-{i}"))
            loaded = load_ledger_entries(ledger_path, limit=2)
            self.assertEqual(len(loaded), 2)

    def test_plan_name_prefix_filter(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            append_ledger_entry(ledger_path, _make_entry(plan_name="alpha-plan"))
            append_ledger_entry(ledger_path, _make_entry(plan_name="beta-plan"))
            append_ledger_entry(ledger_path, _make_entry(plan_name="alpha-v2"))
            loaded = load_ledger_entries(ledger_path, plan_name_prefix="alpha")
            self.assertEqual(len(loaded), 2)
            for e in loaded:
                self.assertTrue(e.plan_name.startswith("alpha"))

    def test_since_filter(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            now = datetime.now(timezone.utc)
            old_ts = (now - timedelta(days=5)).isoformat()
            new_ts = (now - timedelta(hours=1)).isoformat()
            append_ledger_entry(ledger_path, _make_entry(run_id="old", generated_at=old_ts))
            append_ledger_entry(ledger_path, _make_entry(run_id="new", generated_at=new_ts))
            since = now - timedelta(days=1)
            loaded = load_ledger_entries(ledger_path, since=since)
            self.assertEqual(len(loaded), 1)
            self.assertEqual(loaded[0].run_id, "new")

    def test_missing_ledger_returns_empty(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "nonexistent.jsonl"
            loaded = load_ledger_entries(ledger_path)
            self.assertEqual(loaded, [])

    def test_corrupt_lines_skipped(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            ledger_path.write_text("not-json\n", encoding="utf-8")
            append_ledger_entry(ledger_path, _make_entry())
            loaded = load_ledger_entries(ledger_path)
            self.assertEqual(len(loaded), 1)

    def test_cost_usd_null_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            entry = _make_entry(tasks=[
                RunLedgerTaskEntry(id="t1", status="completed", failure_kind=None, cost_usd=None, modules=[])
            ])
            append_ledger_entry(ledger_path, entry)
            loaded = load_ledger_entries(ledger_path)
            self.assertIsNone(loaded[0].tasks[0].cost_usd)

    def test_parent_dir_created(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "nested" / "dir" / "ledger.jsonl"
            append_ledger_entry(ledger_path, _make_entry())
            self.assertTrue(ledger_path.exists())


# ---------------------------------------------------------------------------
# prune_ledger_entries
# ---------------------------------------------------------------------------

class PruneLedgerTest(unittest.TestCase):
    def test_prune_old_entries(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            now = datetime.now(timezone.utc)
            old_ts = (now - timedelta(days=100)).isoformat()
            new_ts = (now - timedelta(days=1)).isoformat()
            append_ledger_entry(ledger_path, _make_entry(run_id="old", generated_at=old_ts))
            append_ledger_entry(ledger_path, _make_entry(run_id="new", generated_at=new_ts))
            result = prune_ledger_entries(ledger_path, max_age_days=90)
            self.assertEqual(result["pruned"], 1)
            self.assertEqual(result["kept"], 1)
            remaining = load_ledger_entries(ledger_path)
            self.assertEqual(len(remaining), 1)
            self.assertEqual(remaining[0].run_id, "new")

    def test_prune_nonexistent_ledger(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "nonexistent.jsonl"
            result = prune_ledger_entries(ledger_path)
            self.assertEqual(result["pruned"], 0)
            self.assertEqual(result["kept"], 0)

    def test_prune_keeps_all_recent(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            now = datetime.now(timezone.utc)
            for i in range(3):
                ts = (now - timedelta(days=i)).isoformat()
                append_ledger_entry(ledger_path, _make_entry(run_id=f"run-{i}", generated_at=ts))
            result = prune_ledger_entries(ledger_path, max_age_days=90)
            self.assertEqual(result["pruned"], 0)
            self.assertEqual(result["kept"], 3)

    def test_prune_returns_max_age_days(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            result = prune_ledger_entries(ledger_path, max_age_days=30)
            self.assertEqual(result["maxAgeDays"], 30)

    def test_prune_preserves_corrupt_lines(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            ledger_path.write_text("not-json\n", encoding="utf-8")
            result = prune_ledger_entries(ledger_path, max_age_days=90)
            # corrupt lines have no parseable timestamp and are kept
            self.assertEqual(result["kept"], 1)
            self.assertEqual(result["pruned"], 0)


# ---------------------------------------------------------------------------
# summarize_ledger_entries
# ---------------------------------------------------------------------------

class SummarizeLedgerTest(unittest.TestCase):
    def test_empty_entries(self):
        summary = summarize_ledger_entries([])
        self.assertEqual(summary["totalRuns"], 0)
        self.assertIsNone(summary["successRate"])
        self.assertIsNone(summary["averageCostUsd"])
        self.assertEqual(summary["commonFailureKinds"], [])
        self.assertEqual(summary["highCostTasksByModule"], [])

    def test_all_completed(self):
        entries = [
            _make_entry(tasks=[
                RunLedgerTaskEntry("t1", "completed", None, 0.01, ["scripts"]),
                RunLedgerTaskEntry("t2", "completed", None, 0.02, ["scripts"]),
            ])
        ]
        summary = summarize_ledger_entries(entries)
        self.assertEqual(summary["totalRuns"], 1)
        self.assertEqual(summary["successRate"], 1.0)
        self.assertAlmostEqual(summary["averageCostUsd"], 0.015, places=4)

    def test_partial_failure_success_rate(self):
        entries = [
            _make_entry(tasks=[
                RunLedgerTaskEntry("t1", "completed", None, None, []),
                RunLedgerTaskEntry("t2", "failed", "permanent", None, []),
            ])
        ]
        summary = summarize_ledger_entries(entries)
        self.assertAlmostEqual(summary["successRate"], 0.5, places=4)

    def test_failure_kind_counts(self):
        entries = [
            _make_entry(tasks=[
                RunLedgerTaskEntry("t1", "failed", "transient", None, []),
                RunLedgerTaskEntry("t2", "failed", "transient", None, []),
                RunLedgerTaskEntry("t3", "failed", "permanent", None, []),
            ])
        ]
        summary = summarize_ledger_entries(entries)
        kinds = {k["kind"]: k["count"] for k in summary["commonFailureKinds"]}
        self.assertEqual(kinds["transient"], 2)
        self.assertEqual(kinds["permanent"], 1)

    def test_high_cost_modules(self):
        entries = [
            _make_entry(tasks=[
                RunLedgerTaskEntry("t1", "completed", None, 1.0, ["scripts"]),
                RunLedgerTaskEntry("t2", "completed", None, 0.5, ["pojo-lens-core"]),
                RunLedgerTaskEntry("t3", "completed", None, 0.3, ["scripts"]),
            ])
        ]
        summary = summarize_ledger_entries(entries)
        modules = {m["module"]: m["totalCostUsd"] for m in summary["highCostTasksByModule"]}
        self.assertIn("scripts", modules)
        self.assertAlmostEqual(modules["scripts"], 1.3, places=4)
        # scripts should be first (highest cost)
        self.assertEqual(summary["highCostTasksByModule"][0]["module"], "scripts")

    def test_no_cost_data_average_is_none(self):
        entries = [
            _make_entry(tasks=[
                RunLedgerTaskEntry("t1", "completed", None, None, []),
            ])
        ]
        summary = summarize_ledger_entries(entries)
        self.assertIsNone(summary["averageCostUsd"])


# ---------------------------------------------------------------------------
# format_ledger_context
# ---------------------------------------------------------------------------

class FormatLedgerContextTest(unittest.TestCase):
    def test_empty_entries(self):
        result = format_ledger_context([], "my-plan")
        self.assertIn("my-plan", result)
        self.assertIn("No prior runs", result)

    def test_basic_format(self):
        entry = _make_entry(
            run_id="run-abc123",
            plan_name="my-plan",
            generated_at="2026-05-01T10:00:00+00:00",
            tasks=[
                RunLedgerTaskEntry("t1", "completed", None, 0.01, ["scripts"]),
                RunLedgerTaskEntry("t2", "failed", "permanent", None, ["pojo-lens-core"]),
            ],
        )
        result = format_ledger_context([entry], "my-plan")
        self.assertIn("2026-05-01", result)
        self.assertIn("run-abc123", result[:50 + len(result)])
        self.assertIn("ok=1", result)
        self.assertIn("fail=1", result)
        # failed task should appear
        self.assertIn("t2", result)

    def test_failed_tasks_listed(self):
        tasks = [
            RunLedgerTaskEntry(f"t{i}", "failed", "permanent", None, ["scripts"])
            for i in range(5)
        ]
        entry = _make_entry(tasks=tasks)
        result = format_ledger_context([entry], "my-plan")
        # only first 3 failed tasks shown
        self.assertIn("t0", result)
        self.assertIn("t1", result)
        self.assertIn("t2", result)

    def test_planner_notes_shown(self):
        entry = _make_entry()
        entry.planner_notes = "prefer narrower read paths"
        result = format_ledger_context([entry], "my-plan")
        self.assertIn("prefer narrower read paths", result)

    def test_multiple_runs_count(self):
        entries = [_make_entry(run_id=f"run-{i}") for i in range(3)]
        result = format_ledger_context(entries, "my-plan")
        self.assertIn("3 run(s)", result)


# ---------------------------------------------------------------------------
# schema validation via load
# ---------------------------------------------------------------------------

class SchemaValidationTest(unittest.TestCase):
    def test_invalid_cost_usd_skipped_gracefully(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            raw = json.dumps({
                "runId": "r1", "planName": "p", "generatedAt": datetime.now(timezone.utc).isoformat(),
                "taskCount": 1,
                "tasks": [{"id": "t1", "status": "completed", "failureKind": None, "costUsd": None, "modules": []}],
                "reviewerBlockCount": 0, "plannerNotes": None,
            })
            ledger_path.write_text(raw + "\n", encoding="utf-8")
            loaded = load_ledger_entries(ledger_path)
            self.assertEqual(len(loaded), 1)
            self.assertIsNone(loaded[0].tasks[0].cost_usd)

    def test_missing_fields_handled(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ledger_path = pathlib.Path(tmpdir) / "run-ledger.jsonl"
            # missing planName and tasks
            raw = json.dumps({"runId": "r1"})
            ledger_path.write_text(raw + "\n", encoding="utf-8")
            loaded = load_ledger_entries(ledger_path)
            # should still load as best-effort
            self.assertEqual(len(loaded), 1)
            self.assertEqual(loaded[0].plan_name, "")


if __name__ == "__main__":
    unittest.main()
