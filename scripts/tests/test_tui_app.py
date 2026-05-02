from __future__ import annotations

import asyncio
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

TEXTUAL_AVAILABLE = importlib.util.find_spec("textual") is not None

if TEXTUAL_AVAILABLE:
    from pojo_lens_agents.hitl import HitlGateContext
    from pojo_lens_agents.tui_app import FooterBar, LogPane, OrchestratorApp, RunSummaryBar, TaskGrid


@unittest.skipUnless(TEXTUAL_AVAILABLE, "textual is not installed")
class TuiAppTest(unittest.TestCase):
    def test_dashboard_updates_grid_summary_and_log_tail(self):
        async def scenario() -> None:
            with tempfile.TemporaryDirectory() as tempdir:
                stderr_path = Path(tempdir) / "stderr.txt"
                stderr_path.write_text("line 1\nline 2\n", encoding="utf-8")
                queue: asyncio.Queue[dict[str, object]] = asyncio.Queue()
                app = OrchestratorApp(
                    event_queue=queue,
                    task_models={"task-a": "claude-haiku-4-5"},
                    plan_name="demo",
                )
                async with app.run_test() as pilot:
                    await queue.put(
                        {
                            "phase": "task-started",
                            "taskId": "task-a",
                            "details": {
                                "startedAt": "2026-05-02T00:00:00+00:00",
                                "stderrPath": str(stderr_path),
                                "model": "claude-haiku-4-5",
                            },
                        }
                    )
                    await pilot.pause(0.3)
                    grid = app.query_one(TaskGrid)
                    started_row = grid.get_row("task-a")
                    self.assertEqual("running", started_row[1].plain)
                    self.assertEqual("claude-haiku-4-5", started_row[2])
                    await pilot.pause(0.6)
                    self.assertEqual(["line 1", "line 2"], app._last_log_lines)

                    await queue.put(
                        {
                            "phase": "task-finished",
                            "taskId": "task-a",
                            "status": "completed",
                            "message": "Done.",
                            "details": {
                                "startedAt": "2026-05-02T00:00:00+00:00",
                                "finishedAt": "2026-05-02T00:00:05+00:00",
                                "stderrPath": str(stderr_path),
                                "usage": {"totalCostUsd": 0.125},
                                "model": "claude-haiku-4-5",
                            },
                        }
                    )
                    await pilot.pause(0.3)
                    finished_row = grid.get_row("task-a")
                    self.assertEqual("completed", finished_row[1].plain)
                    self.assertEqual("$0.12500", finished_row[3])
                    self.assertEqual("00:05", finished_row[4])

                    summary = app.query_one(RunSummaryBar)
                    self.assertIn("done 1/1", str(summary.renderable))
                    self.assertIn("cost $0.12500", str(summary.renderable))

        asyncio.run(scenario())

    def test_hitl_wait_uses_footer_and_tui_actions(self):
        async def scenario() -> None:
            with tempfile.TemporaryDirectory() as tempdir:
                queue: asyncio.Queue[dict[str, object]] = asyncio.Queue()
                app = OrchestratorApp(
                    event_queue=queue,
                    task_models={"task-a": "claude-haiku-4-5"},
                    plan_name="demo",
                )
                async with app.run_test() as pilot:
                    wait_task = asyncio.create_task(
                        app.wait_for_hitl_decision(
                            HitlGateContext(
                                gate_id="gate-001",
                                mode="batch",
                                batch_index=1,
                                completed_batch_task_ids=["task-a"],
                                failed_task_ids=[],
                                pending_task_ids=["task-b"],
                                run_dir=Path(tempdir),
                                dry_run=False,
                            )
                        )
                    )
                    await pilot.pause(0.3)
                    footer = app.query_one(FooterBar)
                    self.assertIn("[a] approve", str(footer.renderable))
                    app.action_approve_gate()
                    decision = await wait_task
                    self.assertTrue(decision.approved)
                    self.assertEqual("tui", decision.source)
                    await pilot.pause(0.2)
                    self.assertEqual("Live dashboard", str(footer.renderable))

        asyncio.run(scenario())
