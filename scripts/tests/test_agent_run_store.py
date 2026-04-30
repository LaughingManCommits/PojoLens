from __future__ import annotations

import json
import pathlib
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import run_store


class RunStoreTest(unittest.TestCase):
    def test_resolve_manifest_path_accepts_run_directory(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            run_dir = pathlib.Path(temp_dir) / "runs" / "run-1"
            run_dir.mkdir(parents=True)
            manifest_path = run_dir / "manifest.json"
            manifest_path.write_text(json.dumps({"tasks": {}}), encoding="utf-8")

            self.assertEqual(manifest_path.resolve(), run_store.resolve_manifest_path(str(run_dir)))

    def test_validate_manifest_payload_requires_task_object(self):
        with self.assertRaisesRegex(run_store.RunStoreError, "expected object 'tasks'"):
            run_store.validate_manifest_payload(
                {"tasks": []},
                manifest_path=pathlib.Path("manifest.json"),
            )

    def test_manifest_workspaces_dir_supports_legacy_run_id_fallback(self):
        run_dir = (ROOT / ".claude-orchestrator" / "runs" / "run-1").resolve()

        self.assertEqual(
            (ROOT / ".claude-orchestrator" / "workspaces" / "run-1").resolve(),
            run_store.manifest_workspaces_dir({"runId": "run-1"}, run_dir=run_dir),
        )

    def test_manifest_selected_plan_prefers_snapshot(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            run_dir = pathlib.Path(temp_dir)
            manifest_path = run_dir / "manifest.json"
            selected_plan = run_dir / "selected-plan.json"
            selected_plan.write_text("{}", encoding="utf-8")

            self.assertEqual(
                selected_plan.resolve(),
                run_store.manifest_selected_plan_path(
                    manifest_path,
                    {"planPath": str(run_dir / "original.json")},
                    location="test",
                ),
            )


if __name__ == "__main__":
    unittest.main()
