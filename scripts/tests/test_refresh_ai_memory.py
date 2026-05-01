import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


def load_refresh_module():
    root = pathlib.Path(__file__).resolve().parents[2]
    module_path = root / "scripts" / "ai" / "refresh-ai-memory.py"
    spec = importlib.util.spec_from_file_location("refresh_ai_memory", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load refresh module from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class RefreshAiMemoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.refresh = load_refresh_module()

    def test_wait_for_publish_ready_observes_ready_transition(self):
        refresh = self.refresh
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            publish_state_path = temp_path / "publish-state.json"
            old_path = refresh.PUBLISH_STATE_PATH
            refresh.PUBLISH_STATE_PATH = publish_state_path
            try:
                refresh.write_json(
                    publish_state_path,
                    {
                        "schemaVersion": refresh.PUBLISH_STATE_SCHEMA_VERSION,
                        "status": "refreshing",
                        "generation": "gen-a",
                    },
                )

                def flip_state(_seconds: float) -> None:
                    refresh.write_json(
                        publish_state_path,
                        {
                            "schemaVersion": refresh.PUBLISH_STATE_SCHEMA_VERSION,
                            "status": "ready",
                            "generation": "gen-b",
                        },
                    )

                with mock.patch.object(refresh.time, "sleep", side_effect=flip_state):
                    state = refresh.wait_for_publish_ready(timeout_sec=1.0, poll_sec=0.01)
            finally:
                refresh.PUBLISH_STATE_PATH = old_path

        self.assertEqual("ready", state["status"])
        self.assertEqual("gen-b", state["generation"])

    def test_run_check_retries_when_publish_generation_changes(self):
        refresh = self.refresh
        hot_stats = {"totalLines": 91, "totalBytes": 7722}
        sqlite_state = {"status": "built"}

        with mock.patch.object(
            refresh,
            "load_publish_state",
            side_effect=[
                {"status": "ready", "generation": "gen-a"},
                {"status": "ready", "generation": "gen-b"},
                {"status": "ready", "generation": "gen-b"},
            ],
        ), mock.patch.object(
            refresh,
            "wait_for_publish_ready",
            return_value={"status": "ready", "generation": "gen-b"},
        ) as wait_mock, mock.patch.object(
            refresh,
            "check_memory_state",
            side_effect=[
                (["inputs-hash-mismatch"], [], "old", hot_stats, sqlite_state),
                ([], [], "new", hot_stats, sqlite_state),
            ],
        ):
            result = refresh.run_check()

        self.assertEqual(0, result)
        wait_mock.assert_called_once()


if __name__ == "__main__":
    unittest.main()
