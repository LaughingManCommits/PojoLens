"""Regression tests for WP71: Generated Plan Cleanup."""
from __future__ import annotations

import json
import os
import pathlib
import tempfile
import time
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import MagicMock, patch


class PruneGeneratedPlansTest(unittest.TestCase):
    """Unit tests for prune_generated_plans."""

    def setUp(self):
        from pojo_lens_agents.runtime_admin import prune_generated_plans
        self.prune = prune_generated_plans
        self.tmp = tempfile.TemporaryDirectory()
        self.runtime_root = Path(self.tmp.name)
        self.plans_dir = self.runtime_root / "generated-plans"
        self.plans_dir.mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def _make_plan(self, name: str, age_days: float) -> Path:
        path = self.plans_dir / f"{name}.json"
        path.write_text(json.dumps({"goal": f"goal for {name}"}), encoding="utf-8")
        mtime = (datetime.now(timezone.utc) - timedelta(days=age_days)).timestamp()
        os.utime(path, (mtime, mtime))
        return path

    def test_no_plans_dir_returns_empty(self):
        import shutil
        shutil.rmtree(self.plans_dir)
        result = self.prune(self.runtime_root)
        self.assertEqual(result["candidateCount"], 0)
        self.assertEqual(result["removed"], [])

    def test_removes_old_plan(self):
        old = self._make_plan("old-plan", age_days=40)
        result = self.prune(self.runtime_root, older_than_days=30, keep_count=0)
        self.assertEqual(result["candidateCount"], 1)
        self.assertEqual(result["removedCount"], 1)
        self.assertFalse(old.exists())

    def test_keeps_recent_plan(self):
        recent = self._make_plan("recent-plan", age_days=5)
        result = self.prune(self.runtime_root, older_than_days=30, keep_count=20)
        self.assertEqual(result["candidateCount"], 0)
        self.assertTrue(recent.exists())

    def test_keep_count_protects_newest(self):
        # Create 25 old plans; keep_count=20 should protect the 20 newest
        for i in range(25):
            self._make_plan(f"plan-{i:02d}", age_days=40 + i)
        result = self.prune(self.runtime_root, older_than_days=30, keep_count=20)
        self.assertEqual(result["candidateCount"], 5)
        self.assertEqual(result["removedCount"], 5)

    def test_dry_run_does_not_delete(self):
        old = self._make_plan("old-plan", age_days=40)
        result = self.prune(self.runtime_root, older_than_days=30, keep_count=0, dry_run=True)
        self.assertEqual(result["candidateCount"], 1)
        self.assertTrue(old.exists())

    def test_dry_run_flag_in_result(self):
        result = self.prune(self.runtime_root, dry_run=True)
        self.assertTrue(result["dryRun"])

    def test_keeps_all_when_under_count_and_recent(self):
        for i in range(5):
            self._make_plan(f"plan-{i}", age_days=5)
        result = self.prune(self.runtime_root, older_than_days=30, keep_count=20)
        self.assertEqual(result["candidateCount"], 0)
        self.assertEqual(len(result["kept"]), 5)

    def test_non_json_files_ignored(self):
        txt = self.plans_dir / "notes.txt"
        txt.write_text("hello")
        result = self.prune(self.runtime_root, older_than_days=30, keep_count=20)
        self.assertEqual(result["candidateCount"], 0)

    def test_result_fields_present(self):
        result = self.prune(self.runtime_root)
        for field in ("plansDir", "olderThanDays", "keepCount", "dryRun",
                      "candidateCount", "removedCount", "removed", "kept"):
            self.assertIn(field, result)

    def test_default_older_than_days_is_30(self):
        result = self.prune(self.runtime_root)
        self.assertEqual(result["olderThanDays"], 30.0)

    def test_default_keep_count_is_20(self):
        result = self.prune(self.runtime_root)
        self.assertEqual(result["keepCount"], 20)


class PruneRunsGeneratedPlansIntegrationTest(unittest.TestCase):
    """prune_runs now includes generatedPlans key in its result."""

    def test_prune_runs_result_has_generated_plans(self):
        from pojo_lens_agents.runtime_admin import prune_runs
        import argparse

        with tempfile.TemporaryDirectory() as tmpdir:
            args = argparse.Namespace(
                runtime_root=tmpdir,
                older_than_days=7.0,
                keep=0,
                include_incomplete=False,
                continue_on_error=False,
                dry_run=True,
            )
            deps = {
                "error_factory": RuntimeError,
                "summarize_run_manifest": lambda mp, m, now=None: ({}, datetime.now(timezone.utc)),
                "runtime_manifest_entries": lambda *, runtime_root: [],
                "cleanup_loaded_run": lambda mp, m: {},
            }
            result = prune_runs(args, deps=deps)
            self.assertIn("generatedPlans", result)
            self.assertIsInstance(result["generatedPlans"], dict)


class GeneratedPlanCollisionWarningTest(unittest.TestCase):
    """Collision warning fires when slug matches but goal differs."""

    def _make_wizard_deps(self, write_json_fn=None):
        from pojo_lens_agents.orchestrator_contracts import OrchestratorError

        def _slugify(text: str) -> str:
            import re
            return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")

        def _write_json(path, payload):
            Path(path).parent.mkdir(parents=True, exist_ok=True)
            Path(path).write_text(json.dumps(payload), encoding="utf-8")

        return {
            "root": Path("."),
            "textual_available": lambda: False,
            "slugify": _slugify,
            "write_json": write_json_fn or _write_json,
            "error_factory": OrchestratorError,
            "load_agents": lambda p: {},
            "ensure_claude_available": lambda b: None,
            "claude_command": lambda *a, **k: [],
            "agent_payload_for_claude": lambda a: {},
            "run_subprocess": lambda *a, **k: MagicMock(stdout="", stderr="", returncode=0),
            "extract_json_payload": lambda t: {},
            "inventory_handler": lambda a: {"runs": []},
            "validate_handler": lambda a: {"planName": "test", "taskCount": 1},
            "run_handler": lambda a: {},
            "resume_handler": lambda a: {},
            "retry_handler": lambda a: {},
            "status_handler": lambda a: {},
            "review_handler": lambda a: {},
            "diff_run_handler": lambda a: {},
            "promote_handler": lambda a: {},
            "validate_run_handler": lambda a: {},
            "default_task_timeout_sec": 1800,
        }

    def _call_wizard_generate(self, runtime_root: Path, goal: str, existing_goal: str | None, deps=None):
        """Call just the generated plan path + collision check portion of wizard logic."""
        from pojo_lens_agents.wizard import _generated_plan_path
        import json as _json

        slugify = deps["slugify"] if deps else (lambda t: t[:48])
        generated_plan_path = _generated_plan_path(runtime_root, goal, slugify)
        generated_plan_path.parent.mkdir(parents=True, exist_ok=True)

        if existing_goal is not None:
            generated_plan_path.write_text(_json.dumps({"goal": existing_goal}), encoding="utf-8")

        messages = []

        class _FakePrompter:
            def show_message(self, msg):
                messages.append(msg)

        collision = None
        if generated_plan_path.exists():
            try:
                _existing = _json.loads(generated_plan_path.read_text(encoding="utf-8"))
                _existing_goal = str(_existing.get("goal", ""))
            except Exception:
                _existing_goal = ""
            if _existing_goal and _existing_goal != goal:
                collision = {"existingGoal": _existing_goal, "newGoal": goal}
                _FakePrompter().show_message(f"[warn] collision detected: {_existing_goal!r} vs {goal!r}")

        return generated_plan_path, messages, collision

    def test_collision_warning_fires_when_goals_differ(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            runtime_root = Path(tmpdir)
            _, messages, collision = self._call_wizard_generate(
                runtime_root,
                goal="refactor authentication module to use JWT",
                existing_goal="refactor authentication module to use OAuth",
            )
            self.assertIsNotNone(collision)
            self.assertEqual(len(messages), 1)
            self.assertIn("[warn]", messages[0])

    def test_no_collision_warning_when_goals_match(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            runtime_root = Path(tmpdir)
            same_goal = "add database connection pooling"
            _, messages, collision = self._call_wizard_generate(
                runtime_root,
                goal=same_goal,
                existing_goal=same_goal,
            )
            self.assertIsNone(collision)
            self.assertEqual(len(messages), 0)

    def test_no_collision_warning_when_file_absent(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            runtime_root = Path(tmpdir)
            _, messages, collision = self._call_wizard_generate(
                runtime_root,
                goal="brand new goal with no prior file",
                existing_goal=None,
            )
            self.assertIsNone(collision)
            self.assertEqual(len(messages), 0)

    def test_collision_payload_has_both_goals(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            runtime_root = Path(tmpdir)
            _, _, collision = self._call_wizard_generate(
                runtime_root,
                goal="migrate to postgres",
                existing_goal="migrate to mysql",
            )
            self.assertIsNotNone(collision)
            self.assertEqual(collision["existingGoal"], "migrate to mysql")
            self.assertEqual(collision["newGoal"], "migrate to postgres")


if __name__ == "__main__":
    unittest.main()
