from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import path_safety


class PathSafetyTest(unittest.TestCase):
    def test_normalize_relative_path_rejects_traversal(self):
        with self.assertRaisesRegex(path_safety.PathSafetyError, "parent-directory traversal"):
            path_safety.normalize_relative_path("../outside.txt", location="test")

    def test_paths_outside_scope_normalizes_and_filters_scope(self):
        self.assertEqual(
            ["src/other.py"],
            path_safety.paths_outside_scope(
                ["src/pkg/file.py", "src/other.py"],
                ["src/pkg"],
            ),
        )

    def test_protected_path_violations_are_deduped(self):
        self.assertEqual(
            ["TODO.md", "ai/state/handoff.md"],
            path_safety.protected_path_violations(
                ["TODO.md", "TODO.md", "ai/state/handoff.md", "docs/guide.md"],
                exact_paths={"TODO.md"},
                path_prefixes=("ai/state/",),
            ),
        )

    def test_resolve_relative_path_stays_within_root(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            normalized, resolved = path_safety.resolve_relative_path(
                root,
                "nested/file.txt",
                location="test",
            )

        self.assertEqual("nested/file.txt", normalized)
        self.assertTrue(str(resolved).endswith("nested\\file.txt") or str(resolved).endswith("nested/file.txt"))


if __name__ == "__main__":
    unittest.main()
