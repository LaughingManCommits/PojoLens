from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from ai.pojo_lens_agents.config_loader import (
    ALLOWED_DEFAULTS,
    format_watch_line,
    load_config,
)
from ai.pojo_lens_agents.cli_parser import _pre_parse_config_path, parse_args


def _write_toml(tmp_path: Path, content: str) -> Path:
    p = tmp_path / "pojolens-agents.toml"
    p.write_text(content, encoding="utf-8")
    return p


class LoadConfigEmptyTest(unittest.TestCase):
    def test_no_file_returns_empty(self, tmp_path=None):
        import tempfile, os
        with tempfile.TemporaryDirectory() as d:
            result = load_config(root=Path(d), env={})
        self.assertEqual(result, {})

    def test_explicit_missing_path_raises(self):
        import tempfile
        with tempfile.TemporaryDirectory() as d:
            missing = Path(d) / "nope.toml"
            with self.assertRaises(FileNotFoundError):
                load_config(missing)

    def test_env_var_missing_path_raises(self):
        import tempfile
        with tempfile.TemporaryDirectory() as d:
            missing = Path(d) / "nope.toml"
            with self.assertRaises(FileNotFoundError):
                load_config(env={"POJOLENS_CONFIG": str(missing)}, root=Path(d))


class LoadConfigValidTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        self._td = tempfile.TemporaryDirectory()
        self.tmp = Path(self._td.name)

    def tearDown(self):
        self._td.cleanup()

    def test_all_defaults_loaded(self):
        _write_toml(self.tmp, """
[defaults]
runtime_root = "/tmp/runs"
claude_bin = "claude"
max_parallel = 4
continue_on_error = true
dry_run = false
worker_validation_mode = "intents-only"
""")
        result = load_config(root=self.tmp, env={})
        self.assertEqual(result["runtime_root"], "/tmp/runs")
        self.assertEqual(result["claude_bin"], "claude")
        self.assertEqual(result["max_parallel"], 4)
        self.assertIs(result["continue_on_error"], True)
        self.assertIs(result["dry_run"], False)
        self.assertEqual(result["worker_validation_mode"], "intents-only")

    def test_partial_defaults_loaded(self):
        _write_toml(self.tmp, "[defaults]\nmax_parallel = 8\n")
        result = load_config(root=self.tmp, env={})
        self.assertEqual(result["max_parallel"], 8)
        self.assertNotIn("claude_bin", result)

    def test_empty_defaults_section_ok(self):
        _write_toml(self.tmp, "[defaults]\n")
        result = load_config(root=self.tmp, env={})
        self.assertEqual(result, {})

    def test_env_var_overrides_root_search(self):
        other = self.tmp / "sub"
        other.mkdir()
        _write_toml(other, "[defaults]\nmax_parallel = 3\n")
        result = load_config(env={"POJOLENS_CONFIG": str(other / "pojolens-agents.toml")}, root=self.tmp)
        self.assertEqual(result["max_parallel"], 3)

    def test_explicit_path_overrides_env(self):
        explicit = self.tmp / "explicit.toml"
        explicit.write_text("[defaults]\nmax_parallel = 7\n", encoding="utf-8")
        env_file = self.tmp / "env.toml"
        env_file.write_text("[defaults]\nmax_parallel = 2\n", encoding="utf-8")
        result = load_config(explicit, env={"POJOLENS_CONFIG": str(env_file)}, root=self.tmp)
        self.assertEqual(result["max_parallel"], 7)


class LoadConfigRejectionTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        self._td = tempfile.TemporaryDirectory()
        self.tmp = Path(self._td.name)

    def tearDown(self):
        self._td.cleanup()

    def test_unknown_key_raises(self):
        _write_toml(self.tmp, "[defaults]\nunknown_key = true\n")
        with self.assertRaises(ValueError) as ctx:
            load_config(root=self.tmp, env={})
        self.assertIn("unknown_key", str(ctx.exception))
        self.assertIn("unknown keys", str(ctx.exception))

    def test_wrong_type_int_as_string_raises(self):
        _write_toml(self.tmp, '[defaults]\nmax_parallel = "four"\n')
        with self.assertRaises(ValueError) as ctx:
            load_config(root=self.tmp, env={})
        self.assertIn("max_parallel", str(ctx.exception))
        self.assertIn("int", str(ctx.exception))

    def test_wrong_type_bool_as_string_raises(self):
        _write_toml(self.tmp, '[defaults]\ncontinue_on_error = "yes"\n')
        with self.assertRaises(ValueError) as ctx:
            load_config(root=self.tmp, env={})
        self.assertIn("continue_on_error", str(ctx.exception))

    def test_non_table_defaults_raises(self):
        _write_toml(self.tmp, "defaults = 42\n")
        with self.assertRaises(ValueError) as ctx:
            load_config(root=self.tmp, env={})
        self.assertIn("[defaults]", str(ctx.exception))


class FlagOverridePrecedenceTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        self._td = tempfile.TemporaryDirectory()
        self.tmp = Path(self._td.name)
        _write_toml(self.tmp, "[defaults]\nmax_parallel = 2\ncontinue_on_error = true\n")

    def tearDown(self):
        self._td.cleanup()

    def _parse(self, extra_args: list[str]) -> object:
        toml_path = self.tmp / "pojolens-agents.toml"
        argv = ["--config", str(toml_path), "run", "plan.json"] + extra_args
        with patch("pojo_lens_agents.config_loader.load_config") as mock_load:
            mock_load.return_value = {"max_parallel": 2, "continue_on_error": True}
            return parse_args(argv)

    def test_config_default_applied_when_no_cli_flag(self):
        args = self._parse([])
        self.assertEqual(args.max_parallel, 2)

    def test_cli_flag_overrides_config_default(self):
        args = self._parse(["--max-parallel", "6"])
        self.assertEqual(args.max_parallel, 6)

    def test_continue_on_error_from_config(self):
        args = self._parse([])
        self.assertTrue(args.continue_on_error)

    def test_watch_flag_defaults_false(self):
        args = self._parse([])
        self.assertFalse(args.watch)

    def test_watch_flag_set(self):
        args = self._parse(["--watch"])
        self.assertTrue(args.watch)


class PreParseConfigPathTest(unittest.TestCase):
    def test_no_config_flag_returns_none(self):
        self.assertIsNone(_pre_parse_config_path(["run", "plan.json"]))

    def test_space_separated_config(self):
        self.assertEqual(_pre_parse_config_path(["--config", "/tmp/c.toml", "run"]), "/tmp/c.toml")

    def test_equals_config(self):
        self.assertEqual(_pre_parse_config_path(["--config=/tmp/c.toml", "run"]), "/tmp/c.toml")

    def test_config_at_end_no_value_returns_none(self):
        self.assertIsNone(_pre_parse_config_path(["run", "--config"]))


class ParseArgsConfigSubcommandTest(unittest.TestCase):
    def test_config_show_parses(self):
        with patch("pojo_lens_agents.config_loader.load_config", return_value={}):
            args = parse_args(["config", "show"])
        self.assertEqual(args.command, "config")
        self.assertEqual(args.config_command, "show")

    def test_config_show_json_flag(self):
        with patch("pojo_lens_agents.config_loader.load_config", return_value={}):
            args = parse_args(["config", "show", "--json"])
        self.assertTrue(args.json)


class WatchLineFormatTest(unittest.TestCase):
    def test_task_finished_basic(self):
        line = format_watch_line("12:34:56", "task-finished", task_id="task-1", status="completed", message="Done editing")
        self.assertIn("[12:34:56]", line)
        self.assertIn("task-1", line)
        self.assertIn("completed", line)
        self.assertIn("Done editing", line)

    def test_task_finished_no_message(self):
        line = format_watch_line("12:34:56", "task-finished", task_id="task-2", status="failed")
        self.assertIn("task-2", line)
        self.assertIn("failed", line)

    def test_task_retry_with_error(self):
        line = format_watch_line("09:00:00", "task-retry", task_id="task-3", details={"error": "rate limited"})
        self.assertIn("task-3", line)
        self.assertIn("retry", line)
        self.assertIn("rate limited", line)

    def test_task_retry_no_details(self):
        line = format_watch_line("09:00:00", "task-retry", task_id="task-3")
        self.assertIn("retry", line)

    def test_batch_ready(self):
        line = format_watch_line("10:00:00", "batch-ready", task_ids=["a", "b", "c"])
        self.assertIn("batch-ready", line)
        self.assertIn("a", line)
        self.assertIn("b", line)
        self.assertIn("c", line)

    def test_run_finished_no_remaining(self):
        line = format_watch_line("11:00:00", "run-finished", details={"remainingTaskIds": []})
        self.assertIn("run-finished", line)

    def test_run_finished_with_remaining(self):
        line = format_watch_line("11:00:00", "run-finished", details={"remainingTaskIds": ["x", "y"]})
        self.assertIn("remaining=2", line)

    def test_task_reused(self):
        line = format_watch_line("11:30:00", "task-reused", task_id="task-4", status="reused")
        self.assertIn("task-4", line)
        self.assertIn("reused", line)

    def test_unknown_phase_passthrough(self):
        line = format_watch_line("00:00:00", "some-other-phase")
        self.assertIn("some-other-phase", line)

    def test_message_truncated_to_100_chars(self):
        long_msg = "x" * 200
        line = format_watch_line("00:00:00", "task-finished", task_id="t", status="completed", message=long_msg)
        msg_part = line.split("completed")[1].strip() if "completed" in line else ""
        self.assertLessEqual(len(msg_part), 102)


if __name__ == "__main__":
    unittest.main()
