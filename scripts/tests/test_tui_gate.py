"""WP75: HitlGateScreen manifest parsing and TUI gate integration tests."""
from __future__ import annotations

import importlib.util
import json
import os
import platform
import sys
import tempfile
import time
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from ai.pojo_lens_agents._tui_gate import _read_gate_manifest, _STALE_THRESHOLD_SEC

TEXTUAL_AVAILABLE = importlib.util.find_spec("textual") is not None

if TEXTUAL_AVAILABLE:
    import asyncio
    from ai.pojo_lens_agents._tui_gate import HitlGateScreen
    from ai.pojo_lens_agents.hitl import HitlGateContext
    from ai.pojo_lens_agents.tui_app import FooterBar, OrchestratorApp


# ── _read_gate_manifest unit tests ────────────────────────────────────────────

class TestReadGateManifest(unittest.TestCase):

    def _write_manifest(self, run_dir: Path, data: dict) -> None:
        (run_dir / "manifest.json").write_text(json.dumps(data), encoding="utf-8")

    def test_parses_gate_event_completed_and_pending(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {
                "events": [
                    {"phase": "task-started", "taskId": "t1", "details": {}},
                    {"phase": "hitl-gate", "details": {
                        "gateId": "gate-000",
                        "completedBatchTaskIds": ["t1"],
                        "failedTaskIds": [],
                        "pendingTaskIds": ["t2", "t3"],
                    }},
                ],
                "tasks": {
                    "t1": {"status": "completed", "usage": {"totalCostUsd": 0.25}},
                    "t2": {"status": "pending", "agent": "worker"},
                    "t3": {"status": "pending", "agent": "reviewer"},
                },
            })
            data = _read_gate_manifest(run_dir, "gate-000")
        self.assertIsNone(data["error"])
        self.assertEqual(data["completed_ids"], ["t1"])
        self.assertEqual(data["pending_ids"], ["t2", "t3"])
        self.assertEqual(data["failed_ids"], [])
        self.assertAlmostEqual(data["total_cost"], 0.25, places=5)
        self.assertAlmostEqual(data["task_costs"]["t1"], 0.25, places=5)
        self.assertFalse(data["stale"])

    def test_parses_failed_task_ids(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {
                "events": [
                    {"phase": "hitl-gate", "details": {
                        "gateId": "gate-001",
                        "completedBatchTaskIds": ["t1"],
                        "failedTaskIds": ["t2"],
                        "pendingTaskIds": [],
                    }},
                ],
                "tasks": {},
            })
            data = _read_gate_manifest(run_dir, "gate-001")
        self.assertEqual(data["failed_ids"], ["t2"])
        self.assertEqual(data["completed_ids"], ["t1"])

    def test_missing_manifest_returns_error(self):
        with tempfile.TemporaryDirectory() as td:
            data = _read_gate_manifest(Path(td), "gate-000")
        self.assertIsNotNone(data["error"])
        self.assertEqual(data["completed_ids"], [])
        self.assertEqual(data["pending_ids"], [])

    def test_no_matching_gate_event_returns_empty_lists(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {"events": [], "tasks": {}})
            data = _read_gate_manifest(run_dir, "gate-999")
        self.assertIsNone(data["error"])
        self.assertEqual(data["completed_ids"], [])
        self.assertEqual(data["pending_ids"], [])

    def test_picks_latest_matching_gate_event(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {
                "events": [
                    {"phase": "hitl-gate", "details": {
                        "gateId": "gate-001",
                        "completedBatchTaskIds": ["old"],
                        "pendingTaskIds": [],
                        "failedTaskIds": [],
                    }},
                    {"phase": "hitl-gate", "details": {
                        "gateId": "gate-001",
                        "completedBatchTaskIds": ["new1", "new2"],
                        "pendingTaskIds": [],
                        "failedTaskIds": [],
                    }},
                ],
                "tasks": {},
            })
            data = _read_gate_manifest(run_dir, "gate-001")
        # reversed() picks last event = "new1", "new2"
        self.assertEqual(data["completed_ids"], ["new1", "new2"])

    def test_ignores_events_for_different_gate_id(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {
                "events": [
                    {"phase": "hitl-gate", "details": {
                        "gateId": "gate-000",
                        "completedBatchTaskIds": ["wrong"],
                        "pendingTaskIds": [],
                        "failedTaskIds": [],
                    }},
                ],
                "tasks": {},
            })
            data = _read_gate_manifest(run_dir, "gate-001")
        self.assertEqual(data["completed_ids"], [])

    def test_task_costs_summed_correctly(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {
                "events": [
                    {"phase": "hitl-gate", "details": {
                        "gateId": "gate-000",
                        "completedBatchTaskIds": ["t1", "t2"],
                        "pendingTaskIds": [],
                        "failedTaskIds": [],
                    }},
                ],
                "tasks": {
                    "t1": {"usage": {"totalCostUsd": 0.1}},
                    "t2": {"usage": {"totalCostUsd": 0.2}},
                },
            })
            data = _read_gate_manifest(run_dir, "gate-000")
        self.assertAlmostEqual(data["total_cost"], 0.3, places=5)

    def test_tasks_without_cost_excluded_from_sum(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {
                "events": [{"phase": "hitl-gate", "details": {
                    "gateId": "gate-000",
                    "completedBatchTaskIds": ["t1"],
                    "pendingTaskIds": [],
                    "failedTaskIds": [],
                }}],
                "tasks": {"t1": {"status": "completed"}},
            })
            data = _read_gate_manifest(run_dir, "gate-000")
        self.assertEqual(data["total_cost"], 0.0)
        self.assertNotIn("t1", data["task_costs"])

    def test_stale_sentinel_detected(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {"events": [], "tasks": {}})
            sentinel = run_dir / "hitl-gate.lock"
            sentinel.write_text('{"gateId": "gate-000"}', encoding="utf-8")
            old_mtime = time.time() - (_STALE_THRESHOLD_SEC + 60)
            os.utime(sentinel, (old_mtime, old_mtime))
            data = _read_gate_manifest(run_dir, "gate-000")
        self.assertTrue(data["stale"])
        self.assertGreaterEqual(data["stale_minutes"], (_STALE_THRESHOLD_SEC + 60) // 60)

    def test_fresh_sentinel_not_stale(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {"events": [], "tasks": {}})
            sentinel = run_dir / "hitl-gate.lock"
            sentinel.write_text('{"gateId": "gate-000"}', encoding="utf-8")
            data = _read_gate_manifest(run_dir, "gate-000")
        self.assertFalse(data["stale"])

    def test_no_sentinel_file_not_stale(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {"events": [], "tasks": {}})
            data = _read_gate_manifest(run_dir, "gate-000")
        self.assertFalse(data["stale"])
        self.assertEqual(data["stale_minutes"], 0)

    def test_tasks_dict_returned_for_pending_lookup(self):
        with tempfile.TemporaryDirectory() as td:
            run_dir = Path(td)
            self._write_manifest(run_dir, {
                "events": [{"phase": "hitl-gate", "details": {
                    "gateId": "gate-000",
                    "completedBatchTaskIds": [],
                    "pendingTaskIds": ["t1"],
                    "failedTaskIds": [],
                }}],
                "tasks": {"t1": {"status": "pending", "agent": "my-worker"}},
            })
            data = _read_gate_manifest(run_dir, "gate-000")
        tasks = data["_tasks"]
        self.assertIn("t1", tasks)
        self.assertEqual(tasks["t1"]["agent"], "my-worker")


# ── Textual integration tests ─────────────────────────────────────────────────

@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class HitlGateScreenTuiTest(unittest.TestCase):
    # Snapshot + restore Win32 handles; Textual's headless runner mangles them.
    def setUp(self) -> None:
        self._saved_stdin  = sys.stdin
        self._saved_stdout = sys.stdout
        self._saved_stderr = sys.stderr
        if platform.system() == "Windows":
            import ctypes
            k32 = ctypes.windll.kernel32
            self._win_stdin  = k32.GetStdHandle(-10)
            self._win_stdout = k32.GetStdHandle(-11)
            self._win_stderr = k32.GetStdHandle(-12)

    def tearDown(self) -> None:
        sys.stdin  = self._saved_stdin
        sys.stdout = self._saved_stdout
        sys.stderr = self._saved_stderr
        if platform.system() == "Windows":
            import ctypes
            k32 = ctypes.windll.kernel32
            k32.SetStdHandle(-10, self._win_stdin)
            k32.SetStdHandle(-11, self._win_stdout)
            k32.SetStdHandle(-12, self._win_stderr)

    def _make_context(self, run_dir: Path, gate_id: str = "gate-001") -> "HitlGateContext":
        return HitlGateContext(
            gate_id=gate_id,
            mode="batch",
            batch_index=1,
            completed_batch_task_ids=["task-a"],
            failed_task_ids=[],
            pending_task_ids=["task-b"],
            run_dir=run_dir,
            dry_run=False,
        )

    def _make_app(self) -> "OrchestratorApp":
        return OrchestratorApp(
            event_queue=asyncio.Queue(),
            task_models={"task-a": "claude-haiku-4-5"},
            plan_name="demo",
        )

    def test_gate_screen_approve_returns_tui_decision(self):
        async def scenario() -> None:
            with tempfile.TemporaryDirectory() as td:
                app = self._make_app()
                async with app.run_test() as pilot:
                    wait_task = asyncio.create_task(
                        app.wait_for_hitl_decision(self._make_context(Path(td)))
                    )
                    await pilot.pause(0.3)
                    await pilot.press("a")
                    decision = await wait_task
                self.assertTrue(decision.approved)
                self.assertEqual("approve", decision.action)
                self.assertEqual("tui", decision.source)
        asyncio.run(scenario())

    def test_gate_screen_abort_returns_tui_decision(self):
        async def scenario() -> None:
            with tempfile.TemporaryDirectory() as td:
                app = self._make_app()
                async with app.run_test() as pilot:
                    wait_task = asyncio.create_task(
                        app.wait_for_hitl_decision(self._make_context(Path(td), gate_id="gate-002"))
                    )
                    await pilot.pause(0.3)
                    await pilot.press("x")
                    decision = await wait_task
                self.assertFalse(decision.approved)
                self.assertEqual("abort", decision.action)
                self.assertEqual("tui", decision.source)
        asyncio.run(scenario())

    def test_gate_back_then_sentinel_resolves(self):
        async def scenario() -> None:
            with tempfile.TemporaryDirectory() as td:
                run_dir = Path(td)
                app = self._make_app()
                async with app.run_test() as pilot:
                    wait_task = asyncio.create_task(
                        app.wait_for_hitl_decision(self._make_context(run_dir, gate_id="gate-003"))
                    )
                    await pilot.pause(0.3)
                    # Dismiss without deciding
                    await pilot.press("escape")
                    # Write approve to sentinel
                    await pilot.pause(0.1)
                    sentinel = run_dir / "hitl-gate.lock"
                    sentinel.write_text("approve", encoding="utf-8")
                    decision = await wait_task
                self.assertTrue(decision.approved)
                self.assertEqual("sentinel", decision.source)
        asyncio.run(scenario())

    def test_footer_resets_to_idle_after_gate_screen_approve(self):
        async def scenario() -> None:
            with tempfile.TemporaryDirectory() as td:
                app = self._make_app()
                async with app.run_test() as pilot:
                    wait_task = asyncio.create_task(
                        app.wait_for_hitl_decision(self._make_context(Path(td)))
                    )
                    await pilot.pause(0.3)
                    await pilot.press("a")
                    await wait_task
                    await pilot.pause(0.2)
                    footer_text = str(app.query_one(FooterBar).renderable).lower()
                    self.assertIn("live", footer_text)
        asyncio.run(scenario())

    def test_auto_approve_skips_screen(self):
        async def scenario() -> None:
            with tempfile.TemporaryDirectory() as td:
                app = self._make_app()
                async with app.run_test() as _pilot:
                    decision = await app.wait_for_hitl_decision(
                        self._make_context(Path(td)), auto_approve=True
                    )
                self.assertTrue(decision.approved)
                self.assertEqual("auto", decision.source)
        asyncio.run(scenario())


if __name__ == "__main__":
    unittest.main()
