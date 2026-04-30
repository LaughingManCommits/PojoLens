from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import workspace_review


class WorkspaceReviewTest(unittest.TestCase):
    def test_snapshot_ignores_runtime_directories_and_diff_sorts_changes(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            (root / "src").mkdir()
            (root / "src" / "a.txt").write_text("before", encoding="utf-8")
            (root / ".claude-orchestrator").mkdir()
            (root / ".claude-orchestrator" / "ignored.txt").write_text("ignored", encoding="utf-8")

            before = workspace_review.snapshot_workspace_files(
                root,
                ignore_dir_names={".claude-orchestrator"},
            )
            (root / "src" / "a.txt").write_text("after", encoding="utf-8")
            (root / "src" / "b.txt").write_text("new", encoding="utf-8")
            after = workspace_review.snapshot_workspace_files(
                root,
                ignore_dir_names={".claude-orchestrator"},
            )

        self.assertEqual(["src/a.txt"], sorted(before))
        self.assertEqual(["src/a.txt", "src/b.txt"], workspace_review.diff_workspace_snapshots(before, after))

    def test_artifact_file_size_handles_missing_paths(self):
        self.assertEqual(0, workspace_review.artifact_file_size(None))
        self.assertEqual(0, workspace_review.artifact_file_size(ROOT / "missing-file.txt"))

    def test_hydrate_copy_workspace_copies_declared_small_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_root = pathlib.Path(temp_dir) / "source"
            workspace_root = pathlib.Path(temp_dir) / "workspace"
            (source_root / "src").mkdir(parents=True)
            (source_root / "src" / "a.txt").write_text("content", encoding="utf-8")

            workspace_review.hydrate_copy_workspace(
                source_root=source_root,
                workspace_path=workspace_root,
                file_paths=["src/a.txt", "missing.txt"],
                max_file_bytes=1024,
                path_is_relative_to=lambda path, parent: path.is_relative_to(parent),
            )

            self.assertEqual("content", (workspace_root / "src" / "a.txt").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
