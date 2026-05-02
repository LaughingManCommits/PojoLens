from __future__ import annotations

import json
import os
import pathlib
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import orchestrator_utils
from pojo_lens_agents.orchestrator_utils import (
    _ORPHANED_TMP_RE,
    _atomic_replace,
    recover_orphaned_write_temps,
    write_json,
    write_text,
)


class WriteTextAtomicTest(unittest.TestCase):
    def test_write_text_produces_correct_content(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "out.txt"
            write_text(path, "hello world")
            self.assertEqual("hello world", path.read_text(encoding="utf-8"))

    def test_write_text_none_produces_empty_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "out.txt"
            write_text(path, None)
            self.assertEqual("", path.read_text(encoding="utf-8"))

    def test_write_text_creates_parent_directories(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "a" / "b" / "c" / "out.txt"
            write_text(path, "deep")
            self.assertEqual("deep", path.read_text(encoding="utf-8"))

    def test_write_text_leaves_no_tmp_file_after_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "manifest.json"
            write_text(path, '{"ok": true}')
            tmp_files = [f for f in pathlib.Path(tmp).iterdir() if f.suffix == ".tmp"]
            self.assertEqual([], tmp_files, "no .tmp file should remain after successful write")

    def test_write_text_overwrites_existing_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "manifest.json"
            write_text(path, "first")
            write_text(path, "second")
            self.assertEqual("second", path.read_text(encoding="utf-8"))

    def test_write_text_cleans_tmp_file_when_os_replace_raises(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "manifest.json"
            with patch.object(orchestrator_utils.os, "replace", side_effect=OSError("simulated replace failure")):
                with self.assertRaises(OSError):
                    write_text(path, "content that should not land")
            tmp_files = [f for f in pathlib.Path(tmp).iterdir() if f.suffix == ".tmp"]
            self.assertEqual([], tmp_files, "temp file must be cleaned up after os.replace failure")
            self.assertFalse(path.exists(), "target file must not exist when write failed")

    def test_write_text_concurrent_distinct_files_all_correct(self):
        # Primary concurrency test: each task writes its own result.json —
        # matches the real orchestrator pattern where parallel workers write
        # to task-specific paths, never the same file simultaneously.
        with tempfile.TemporaryDirectory() as tmp:
            paths = [pathlib.Path(tmp) / f"task-{i}" / "result.json" for i in range(12)]
            errors: list[Exception] = []

            def worker(index: int) -> None:
                try:
                    write_json(paths[index], {"task": index, "data": "x" * 200})
                except Exception as exc:  # noqa: BLE001
                    errors.append(exc)

            threads = [threading.Thread(target=worker, args=(i,)) for i in range(12)]
            for t in threads:
                t.start()
            for t in threads:
                t.join()

            self.assertEqual([], errors, f"no thread should raise: {errors}")
            for i, path in enumerate(paths):
                payload = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(i, payload["task"])

    def test_write_text_concurrent_same_file_produces_valid_json(self):
        # Secondary test: same-file concurrent writes (e.g. operator 'status'
        # races with run's write_manifest).  The retry logic in _atomic_replace
        # must ensure every write either lands or raises cleanly — no partial
        # JSON must survive.  We allow PermissionError on saturated Windows
        # but any surviving file must be valid JSON.
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "manifest.json"
            errors: list[Exception] = []

            def worker(index: int) -> None:
                try:
                    write_json(path, {"thread": index, "data": "x" * 200})
                except PermissionError:
                    pass  # acceptable under heavy Windows contention
                except Exception as exc:  # noqa: BLE001
                    errors.append(exc)

            threads = [threading.Thread(target=worker, args=(i,)) for i in range(8)]
            for t in threads:
                t.start()
            for t in threads:
                t.join()

            self.assertEqual([], errors, f"unexpected non-PermissionError: {errors}")
            if path.exists():
                payload = json.loads(path.read_text(encoding="utf-8"))
                self.assertIn("thread", payload)

    def test_write_text_concurrent_no_tmp_files_remaining(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "result.json"
            threads = [
                threading.Thread(target=write_text, args=(path, f"content-{i}"))
                for i in range(8)
            ]
            for t in threads:
                t.start()
            for t in threads:
                t.join()
            tmp_files = [f for f in pathlib.Path(tmp).iterdir() if ".tmp" in f.name]
            self.assertEqual([], tmp_files)


class AtomicReplaceRetryTest(unittest.TestCase):
    def test_atomic_replace_succeeds_on_first_attempt(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = pathlib.Path(tmp) / "src.tmp"
            dst = pathlib.Path(tmp) / "dst.json"
            src.write_bytes(b"content")
            _atomic_replace(src, dst)
            self.assertFalse(src.exists())
            self.assertEqual(b"content", dst.read_bytes())

    def test_atomic_replace_retries_on_permission_error_then_succeeds(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = pathlib.Path(tmp) / "src.tmp"
            dst = pathlib.Path(tmp) / "dst.json"
            src.write_bytes(b"data")
            call_count = 0

            real_replace = os.replace

            def flaky_replace(a, b):
                nonlocal call_count
                call_count += 1
                if call_count < 3:
                    raise PermissionError(13, "Access is denied")
                real_replace(a, b)

            with patch.object(orchestrator_utils.os, "replace", side_effect=flaky_replace):
                _atomic_replace(src, dst, _max_attempts=5)

            self.assertEqual(3, call_count)
            self.assertEqual(b"data", dst.read_bytes())

    def test_atomic_replace_raises_after_max_attempts(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = pathlib.Path(tmp) / "src.tmp"
            dst = pathlib.Path(tmp) / "dst.json"
            src.write_bytes(b"x")
            with patch.object(orchestrator_utils.os, "replace", side_effect=PermissionError(13, "always denied")):
                with self.assertRaises(PermissionError):
                    _atomic_replace(src, dst, _max_attempts=3)


class WriteJsonAtomicTest(unittest.TestCase):
    def test_write_json_round_trips_dict(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "payload.json"
            data = {"runId": "abc123", "tasks": {"t1": {"status": "completed"}}}
            write_json(path, data)
            loaded = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(data, loaded)

    def test_write_json_produces_indented_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "payload.json"
            write_json(path, {"key": "value"})
            text = path.read_text(encoding="utf-8")
            self.assertIn("\n", text, "write_json should produce indented multi-line JSON")
            self.assertTrue(text.endswith("\n"), "write_json output should end with newline")

    def test_write_json_leaves_no_tmp_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "manifest.json"
            write_json(path, {"tasks": {}})
            tmp_files = [f for f in pathlib.Path(tmp).iterdir() if ".tmp" in f.name]
            self.assertEqual([], tmp_files)


class OprhanedTmpPatternTest(unittest.TestCase):
    def test_pattern_matches_manifest_tmp(self):
        self.assertIsNotNone(_ORPHANED_TMP_RE.match(".manifest.json.a1b2c3d4.tmp"))

    def test_pattern_matches_result_tmp(self):
        self.assertIsNotNone(_ORPHANED_TMP_RE.match(".result.json.deadbeef.tmp"))

    def test_pattern_matches_selected_plan_tmp(self):
        self.assertIsNotNone(_ORPHANED_TMP_RE.match(".selected-plan.json.00112233.tmp"))

    def test_pattern_matches_summary_txt_tmp(self):
        self.assertIsNotNone(_ORPHANED_TMP_RE.match(".summary.json.aabbccdd.tmp"))

    def test_pattern_rejects_plain_tmp(self):
        self.assertIsNone(_ORPHANED_TMP_RE.match("foo.tmp"))

    def test_pattern_rejects_dotfile_without_hex_suffix(self):
        self.assertIsNone(_ORPHANED_TMP_RE.match(".gitignore"))

    def test_pattern_rejects_tmp_without_leading_dot(self):
        self.assertIsNone(_ORPHANED_TMP_RE.match("manifest.json.a1b2c3d4.tmp"))

    def test_pattern_rejects_short_hex(self):
        self.assertIsNone(_ORPHANED_TMP_RE.match(".manifest.json.a1b2c3.tmp"))

    def test_pattern_rejects_non_hex_suffix(self):
        self.assertIsNone(_ORPHANED_TMP_RE.match(".manifest.json.zzzzzzzz.tmp"))

    def test_pattern_rejects_nine_hex_chars(self):
        self.assertIsNone(_ORPHANED_TMP_RE.match(".manifest.json.a1b2c3d4e.tmp"))


class RecoverOrphanedWriteTempsTest(unittest.TestCase):
    def test_empty_directory_returns_empty_list(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = recover_orphaned_write_temps(pathlib.Path(tmp))
            self.assertEqual([], result)

    def test_non_existent_directory_returns_empty_list(self):
        result = recover_orphaned_write_temps(pathlib.Path("/nonexistent/path/that/does/not/exist"))
        self.assertEqual([], result)

    def test_removes_matching_temp_and_returns_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            orphan = tmp_path / ".manifest.json.a1b2c3d4.tmp"
            orphan.write_bytes(b"partial content")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual([".manifest.json.a1b2c3d4.tmp"], removed)
            self.assertFalse(orphan.exists())

    def test_removes_multiple_matching_temps(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            names = [
                ".manifest.json.a1b2c3d4.tmp",
                ".result.json.deadbeef.tmp",
                ".selected-plan.json.00aabbcc.tmp",
            ]
            for name in names:
                (tmp_path / name).write_bytes(b"orphan")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual(sorted(names), sorted(removed))
            for name in names:
                self.assertFalse((tmp_path / name).exists())

    def test_ignores_regular_json_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            real_file = tmp_path / "manifest.json"
            real_file.write_text('{"tasks": {}}', encoding="utf-8")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual([], removed)
            self.assertTrue(real_file.exists())

    def test_ignores_plain_tmp_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            plain_tmp = tmp_path / "something.tmp"
            plain_tmp.write_bytes(b"not an orchestrator temp")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual([], removed)
            self.assertTrue(plain_tmp.exists())

    def test_ignores_tmp_without_leading_dot(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            no_dot = tmp_path / "manifest.json.a1b2c3d4.tmp"
            no_dot.write_bytes(b"no leading dot")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual([], removed)
            self.assertTrue(no_dot.exists())

    def test_removes_temps_in_subdirectories(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            sub = tmp_path / "tasks" / "task-1"
            sub.mkdir(parents=True)
            orphan = sub / ".result.json.cafebabe.tmp"
            orphan.write_bytes(b"orphan in subdirectory")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual(["tasks/task-1/.result.json.cafebabe.tmp"], [r.replace(os.sep, "/") for r in removed])
            self.assertFalse(orphan.exists())

    def test_returns_relative_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            orphan = tmp_path / ".manifest.json.a1b2c3d4.tmp"
            orphan.write_bytes(b"x")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual(1, len(removed))
            # must be relative, not absolute
            self.assertFalse(pathlib.Path(removed[0]).is_absolute())

    def test_does_not_remove_real_content_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = pathlib.Path(tmp)
            real = tmp_path / "manifest.json"
            real.write_text('{"tasks": {}}', encoding="utf-8")
            orphan = tmp_path / ".manifest.json.00112233.tmp"
            orphan.write_bytes(b"orphan")
            removed = recover_orphaned_write_temps(tmp_path)
            self.assertEqual([".manifest.json.00112233.tmp"], removed)
            self.assertTrue(real.exists(), "real manifest.json must survive recovery")


class LoadRunManifestRecoveryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from pojo_lens_agents import workspace_run_review
        cls.module = workspace_run_review

    def _make_run_dir(self, tmp: str) -> pathlib.Path:
        run_dir = pathlib.Path(tmp) / "runs" / "test-run-001"
        run_dir.mkdir(parents=True)
        manifest = {"runId": "test-run-001", "tasks": {}}
        (run_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return run_dir

    def test_load_run_manifest_loads_valid_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = self._make_run_dir(tmp)
            manifest_path, manifest = self.module.load_run_manifest(str(run_dir))
            self.assertEqual("test-run-001", manifest.get("runId"))
            self.assertEqual(run_dir / "manifest.json", manifest_path)

    def test_load_run_manifest_removes_orphaned_temps(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = self._make_run_dir(tmp)
            orphan = run_dir / ".manifest.json.deadbeef.tmp"
            orphan.write_bytes(b"orphaned partial write")
            manifest_path, manifest = self.module.load_run_manifest(str(run_dir))
            self.assertFalse(orphan.exists(), "orphaned temp must be removed during load_run_manifest")
            self.assertEqual("test-run-001", manifest.get("runId"))

    def test_load_run_manifest_recovers_task_subdir_temps(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = self._make_run_dir(tmp)
            task_dir = run_dir / "tasks" / "task-1"
            task_dir.mkdir(parents=True)
            orphan = task_dir / ".result.json.a1b2c3d4.tmp"
            orphan.write_bytes(b"partial task result")
            manifest_path, manifest = self.module.load_run_manifest(str(run_dir))
            self.assertFalse(orphan.exists(), "subdirectory orphan must be removed")

    def test_load_run_manifest_does_not_remove_real_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = self._make_run_dir(tmp)
            real = run_dir / "selected-plan.json"
            real.write_text('{"version": 1, "tasks": []}', encoding="utf-8")
            self.module.load_run_manifest(str(run_dir))
            self.assertTrue(real.exists(), "real files must not be removed by recovery")

    def test_load_run_manifest_no_temps_proceeds_cleanly(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = self._make_run_dir(tmp)
            manifest_path, manifest = self.module.load_run_manifest(str(run_dir))
            self.assertIsNotNone(manifest)


class AtomicWriteIntegrationTest(unittest.TestCase):
    """End-to-end: write a manifest, simulate crash scenario, recover and load."""

    def test_crash_scenario_recovery(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = pathlib.Path(tmp) / "runs" / "run-crash-sim"
            run_dir.mkdir(parents=True)

            # Write a valid manifest
            manifest = {"runId": "run-crash-sim", "tasks": {}}
            write_json(run_dir / "manifest.json", manifest)

            # Simulate a crash mid-write: leave a temp file alongside manifest.json
            orphan = run_dir / ".manifest.json.12345678.tmp"
            orphan.write_text("partial overwrite that never completed", encoding="utf-8")

            # Task subdir with an orphaned result temp
            task_dir = run_dir / "tasks" / "task-a"
            task_dir.mkdir(parents=True)
            task_orphan = task_dir / ".result.json.abcdef01.tmp"
            task_orphan.write_bytes(b'{"status": "executing"')  # truncated JSON

            # Recovery via load_run_manifest
            from pojo_lens_agents import workspace_run_review
            manifest_path, loaded = workspace_run_review.load_run_manifest(str(run_dir))

            self.assertFalse(orphan.exists(), "root orphan must be cleaned up")
            self.assertFalse(task_orphan.exists(), "task orphan must be cleaned up")
            self.assertEqual("run-crash-sim", loaded.get("runId"))
            # The real manifest.json must still be intact
            self.assertTrue((run_dir / "manifest.json").exists())


if __name__ == "__main__":
    unittest.main()
