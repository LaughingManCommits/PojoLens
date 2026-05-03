"""Tests for sdk_provider: workspace tools, usage mapping, provider mode detection,
and the SDK agentic loop (run_sdk_provider)."""
from __future__ import annotations

import os
import pathlib
import sys
import tempfile
import unittest
from dataclasses import dataclass
from types import SimpleNamespace
from unittest.mock import MagicMock, patch, call

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents.sdk_provider import (
    DEFAULT_BASH_TIMEOUT_SEC,
    DEFAULT_MAX_TOKENS,
    MAX_TOOL_ITERATIONS,
    MAX_TOOL_OUTPUT_CHARS,
    SDK_PROVIDER_DEFAULT_MODEL,
    WORKSPACE_TOOLS,
    SdkProviderResult,
    _accumulate_usage,
    _safe_workspace_path,
    detect_provider_mode,
    execute_workspace_tool,
    map_sdk_usage,
    run_sdk_provider,
    sdk_available,
)


# ---------------------------------------------------------------------------
# SdkProviderResult
# ---------------------------------------------------------------------------


class SdkProviderResultTest(unittest.TestCase):
    def test_fields_default(self):
        r = SdkProviderResult(text="hello", usage=None)
        self.assertEqual("hello", r.text)
        self.assertIsNone(r.usage)
        self.assertEqual("sdk", r.provider_mode)
        self.assertIsNone(r.error)

    def test_error_field(self):
        r = SdkProviderResult(text="", usage=None, error="RateLimitError: too many")
        self.assertEqual("RateLimitError: too many", r.error)

    def test_no_error_means_success(self):
        r = SdkProviderResult(text='{"status":"completed"}', usage=None)
        self.assertIsNone(r.error)


# ---------------------------------------------------------------------------
# _safe_workspace_path
# ---------------------------------------------------------------------------


class SafeWorkspacePathTest(unittest.TestCase):
    def test_valid_relative_path(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = _safe_workspace_path(root, "subdir/file.py")
            self.assertIsNotNone(result)
            self.assertTrue(str(result).startswith(str(root)))

    def test_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            self.assertIsNone(_safe_workspace_path(root, "../../etc/passwd"))

    def test_root_relative_path(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = _safe_workspace_path(root, "file.txt")
            self.assertIsNotNone(result)

    def test_empty_path_resolves_to_root(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = _safe_workspace_path(root, "")
            self.assertIsNotNone(result)


# ---------------------------------------------------------------------------
# execute_workspace_tool
# ---------------------------------------------------------------------------


class ExecuteWorkspaceToolReadTest(unittest.TestCase):
    def test_read_existing_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            (root / "hello.txt").write_text("hello world", encoding="utf-8")
            result = execute_workspace_tool("read_file", {"path": "hello.txt"}, workspace_root=root)
            self.assertEqual("hello world", result)

    def test_read_missing_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool("read_file", {"path": "missing.txt"}, workspace_root=root)
            self.assertIn("Error", result)
            self.assertIn("not found", result)

    def test_read_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool("read_file", {"path": "../../etc/passwd"}, workspace_root=root)
            self.assertIn("Error", result)
            self.assertIn("traversal", result)

    def test_read_truncates_large_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            big_content = "x" * (MAX_TOOL_OUTPUT_CHARS + 100)
            (root / "big.txt").write_text(big_content, encoding="utf-8")
            result = execute_workspace_tool("read_file", {"path": "big.txt"}, workspace_root=root)
            self.assertLessEqual(len(result), MAX_TOOL_OUTPUT_CHARS + 200)
            self.assertIn("truncated", result)


class ExecuteWorkspaceToolWriteTest(unittest.TestCase):
    def test_write_creates_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool(
                "write_file", {"path": "out.txt", "content": "hello"}, workspace_root=root
            )
            self.assertIn("Wrote", result)
            self.assertEqual("hello", (root / "out.txt").read_text(encoding="utf-8"))

    def test_write_creates_parent_dirs(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool(
                "write_file", {"path": "sub/dir/file.txt", "content": "x"}, workspace_root=root
            )
            self.assertIn("Wrote", result)
            self.assertTrue((root / "sub" / "dir" / "file.txt").exists())

    def test_write_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool(
                "write_file", {"path": "../../evil.txt", "content": "bad"}, workspace_root=root
            )
            self.assertIn("Error", result)

    def test_write_overwrites_existing(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            (root / "f.txt").write_text("old", encoding="utf-8")
            execute_workspace_tool("write_file", {"path": "f.txt", "content": "new"}, workspace_root=root)
            self.assertEqual("new", (root / "f.txt").read_text(encoding="utf-8"))


class ExecuteWorkspaceToolStrReplaceTest(unittest.TestCase):
    def test_str_replace_unique(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            (root / "code.py").write_text("def foo():\n    return 1\n", encoding="utf-8")
            result = execute_workspace_tool(
                "str_replace_based_edit_tool",
                {"path": "code.py", "old_str": "return 1", "new_str": "return 2"},
                workspace_root=root,
            )
            self.assertIn("Edited", result)
            self.assertIn("return 2", (root / "code.py").read_text(encoding="utf-8"))

    def test_str_replace_not_found(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            (root / "f.py").write_text("hello", encoding="utf-8")
            result = execute_workspace_tool(
                "str_replace_based_edit_tool",
                {"path": "f.py", "old_str": "world", "new_str": "earth"},
                workspace_root=root,
            )
            self.assertIn("Error", result)
            self.assertIn("not found", result)

    def test_str_replace_not_unique(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            (root / "f.py").write_text("dup\ndup\n", encoding="utf-8")
            result = execute_workspace_tool(
                "str_replace_based_edit_tool",
                {"path": "f.py", "old_str": "dup", "new_str": "x"},
                workspace_root=root,
            )
            self.assertIn("Error", result)
            self.assertIn("unique", result)

    def test_str_replace_missing_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool(
                "str_replace_based_edit_tool",
                {"path": "nope.py", "old_str": "x", "new_str": "y"},
                workspace_root=root,
            )
            self.assertIn("Error", result)
            self.assertIn("not found", result)

    def test_str_replace_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool(
                "str_replace_based_edit_tool",
                {"path": "../../bad.py", "old_str": "x", "new_str": "y"},
                workspace_root=root,
            )
            self.assertIn("Error", result)


class ExecuteWorkspaceToolBashTest(unittest.TestCase):
    def test_bash_echo(self):
        import subprocess as _sp
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            fake = _sp.CompletedProcess("echo hello", 0, stdout="hello\n", stderr="")
            with patch.object(_sp, "run", return_value=fake) as mock_run:
                result = execute_workspace_tool(
                    "bash", {"command": "echo hello"}, workspace_root=root
                )
            self.assertIn("hello", result)
            self.assertEqual(mock_run.call_args[0][0], "echo hello")

    def test_bash_exit_code_nonzero_returns_output(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool(
                "bash", {"command": "exit 1"}, workspace_root=root
            )
            self.assertIsNotNone(result)  # Just returns something; doesn't raise

    def test_bash_timeout(self):
        import subprocess as _sp
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            with patch.object(_sp, "run", side_effect=_sp.TimeoutExpired("cmd", 1)):
                result = execute_workspace_tool(
                    "bash",
                    {"command": "sleep 10"},
                    workspace_root=root,
                    bash_timeout_sec=1,
                )
            self.assertIn("Error", result)
            self.assertIn("timed out", result)


class ExecuteWorkspaceToolUnknownTest(unittest.TestCase):
    def test_unknown_tool_returns_error(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = pathlib.Path(tmpdir)
            result = execute_workspace_tool("nonexistent_tool", {}, workspace_root=root)
            self.assertIn("Error", result)
            self.assertIn("unknown tool", result)


# ---------------------------------------------------------------------------
# map_sdk_usage
# ---------------------------------------------------------------------------


class MapSdkUsageTest(unittest.TestCase):
    def _make_usage(self, input_tokens=10, output_tokens=5,
                    cache_read=0, cache_write=0):
        u = SimpleNamespace(
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            cache_read_input_tokens=cache_read,
            cache_creation_input_tokens=cache_write,
        )
        return u

    def test_maps_basic_fields(self):
        usage = map_sdk_usage(self._make_usage(input_tokens=100, output_tokens=50))
        self.assertEqual(100, usage["inputTokens"])
        self.assertEqual(50, usage["outputTokens"])

    def test_maps_cache_fields(self):
        usage = map_sdk_usage(self._make_usage(cache_read=20, cache_write=10))
        self.assertEqual(20, usage["cacheReadInputTokens"])
        self.assertEqual(10, usage["cacheCreationInputTokens"])

    def test_none_returns_none(self):
        self.assertIsNone(map_sdk_usage(None))

    def test_missing_attrs_default_zero(self):
        usage = map_sdk_usage(SimpleNamespace())
        self.assertEqual(0, usage["inputTokens"])
        self.assertEqual(0, usage["outputTokens"])

    def test_contains_expected_keys(self):
        usage = map_sdk_usage(self._make_usage())
        for key in ("inputTokens", "outputTokens", "cacheReadInputTokens",
                    "cacheCreationInputTokens", "serviceTier", "durationMs",
                    "durationApiMs", "numTurns", "stopReason", "isError",
                    "totalCostUsd", "modelUsage"):
            self.assertIn(key, usage)


# ---------------------------------------------------------------------------
# _accumulate_usage
# ---------------------------------------------------------------------------


class AccumulateUsageTest(unittest.TestCase):
    def _make_usage(self, input_tokens=0, output_tokens=0):
        return SimpleNamespace(
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            cache_read_input_tokens=0,
            cache_creation_input_tokens=0,
        )

    def test_accumulate_first_call_returns_mapped(self):
        result = _accumulate_usage(None, self._make_usage(input_tokens=10, output_tokens=5))
        self.assertEqual(10, result["inputTokens"])
        self.assertEqual(5, result["outputTokens"])

    def test_accumulate_adds_to_total(self):
        first = _accumulate_usage(None, self._make_usage(input_tokens=10, output_tokens=5))
        second = _accumulate_usage(first, self._make_usage(input_tokens=20, output_tokens=8))
        self.assertEqual(30, second["inputTokens"])
        self.assertEqual(13, second["outputTokens"])

    def test_accumulate_none_usage_no_change(self):
        first = _accumulate_usage(None, self._make_usage(input_tokens=5))
        result = _accumulate_usage(first, None)
        self.assertIs(first, result)

    def test_accumulate_none_total_none_usage(self):
        result = _accumulate_usage(None, None)
        self.assertIsNone(result)


# ---------------------------------------------------------------------------
# sdk_available
# ---------------------------------------------------------------------------


class SdkAvailableTest(unittest.TestCase):
    def test_returns_bool(self):
        result = sdk_available()
        self.assertIsInstance(result, bool)

    def test_false_when_import_fails(self):
        import builtins
        real_import = builtins.__import__

        def mock_import(name, *args, **kwargs):
            if name == "anthropic":
                raise ImportError("no module")
            return real_import(name, *args, **kwargs)

        with patch("builtins.__import__", side_effect=mock_import):
            result = sdk_available()
        self.assertFalse(result)


# ---------------------------------------------------------------------------
# detect_provider_mode
# ---------------------------------------------------------------------------


class DetectProviderModeTest(unittest.TestCase):
    def test_explicit_sdk_env_var(self):
        with patch.dict(os.environ, {"POJO_LENS_PROVIDER": "sdk"}):
            self.assertEqual("sdk", detect_provider_mode())

    def test_explicit_subprocess_env_var(self):
        with patch.dict(os.environ, {"POJO_LENS_PROVIDER": "subprocess"}):
            self.assertEqual("subprocess", detect_provider_mode())

    def test_explicit_claude_alias(self):
        with patch.dict(os.environ, {"POJO_LENS_PROVIDER": "claude"}):
            self.assertEqual("subprocess", detect_provider_mode())

    def test_env_var_case_insensitive(self):
        with patch.dict(os.environ, {"POJO_LENS_PROVIDER": "SDK"}):
            self.assertEqual("sdk", detect_provider_mode())

    def test_auto_detect_sdk_when_key_set(self):
        env = {"ANTHROPIC_API_KEY": "sk-test", "POJO_LENS_PROVIDER": ""}
        with patch.dict(os.environ, env, clear=False):
            os.environ.pop("POJO_LENS_PROVIDER", None)
            with patch("pojo_lens_agents.sdk_provider.sdk_available", return_value=True):
                result = detect_provider_mode()
        self.assertEqual("sdk", result)

    def test_auto_detect_subprocess_when_no_key(self):
        env = {"POJO_LENS_PROVIDER": ""}
        with patch.dict(os.environ, env):
            os.environ.pop("ANTHROPIC_API_KEY", None)
            os.environ.pop("POJO_LENS_PROVIDER", None)
            with patch("pojo_lens_agents.sdk_provider.sdk_available", return_value=True):
                result = detect_provider_mode()
        self.assertEqual("subprocess", result)

    def test_auto_detect_subprocess_when_sdk_not_available(self):
        env = {"ANTHROPIC_API_KEY": "sk-test", "POJO_LENS_PROVIDER": ""}
        with patch.dict(os.environ, env):
            os.environ.pop("POJO_LENS_PROVIDER", None)
            with patch("pojo_lens_agents.sdk_provider.sdk_available", return_value=False):
                result = detect_provider_mode()
        self.assertEqual("subprocess", result)


# ---------------------------------------------------------------------------
# WORKSPACE_TOOLS schema
# ---------------------------------------------------------------------------


class WorkspaceToolsSchemaTest(unittest.TestCase):
    def test_five_tools_defined(self):
        names = [t["name"] for t in WORKSPACE_TOOLS]
        self.assertIn("read_file", names)
        self.assertIn("write_file", names)
        self.assertIn("str_replace_based_edit_tool", names)
        self.assertIn("bash", names)
        self.assertIn("write_shared_context", names)
        self.assertEqual(5, len(WORKSPACE_TOOLS))

    def test_each_tool_has_input_schema(self):
        for tool in WORKSPACE_TOOLS:
            self.assertIn("input_schema", tool)
            self.assertIn("type", tool["input_schema"])

    def test_each_tool_has_description(self):
        for tool in WORKSPACE_TOOLS:
            self.assertIn("description", tool)
            self.assertTrue(len(tool["description"]) > 0)


# ---------------------------------------------------------------------------
# run_sdk_provider: missing anthropic package
# ---------------------------------------------------------------------------


class RunSdkProviderImportErrorTest(unittest.TestCase):
    def test_returns_error_when_anthropic_missing(self):
        import builtins
        real_import = builtins.__import__

        def mock_import(name, *args, **kwargs):
            if name == "anthropic":
                raise ImportError("no module named anthropic")
            return real_import(name, *args, **kwargs)

        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            with patch("builtins.__import__", side_effect=mock_import):
                result = run_sdk_provider(
                    "system", "user", workspace_root=workspace
                )
        self.assertIsNotNone(result.error)
        self.assertIn("ImportError", result.error)
        self.assertEqual("", result.text)


# ---------------------------------------------------------------------------
# run_sdk_provider: helpers for mocking anthropic
# ---------------------------------------------------------------------------


def _make_text_block(text: str) -> MagicMock:
    block = MagicMock()
    block.type = "text"
    block.text = text
    return block


def _make_tool_use_block(tool_id: str, name: str, input_data: dict) -> MagicMock:
    block = MagicMock()
    block.type = "tool_use"
    block.id = tool_id
    block.name = name
    block.input = input_data
    return block


def _make_response(stop_reason: str, content: list, usage=None) -> MagicMock:
    resp = MagicMock()
    resp.stop_reason = stop_reason
    resp.content = content
    resp.usage = usage
    return resp


def _make_usage(input_tokens: int = 10, output_tokens: int = 5) -> MagicMock:
    u = MagicMock()
    u.input_tokens = input_tokens
    u.output_tokens = output_tokens
    u.cache_read_input_tokens = 0
    u.cache_creation_input_tokens = 0
    return u


class RunSdkProviderSuccessTest(unittest.TestCase):
    """SDK provider returns final text on end_turn."""

    def _run(self, responses: list, *, workspace_root: pathlib.Path) -> SdkProviderResult:
        mock_anthropic = MagicMock()
        mock_client = MagicMock()
        mock_anthropic.Anthropic.return_value = mock_client
        response_iter = iter(responses)
        mock_client.messages.create.side_effect = lambda **kw: next(response_iter)

        with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
            return run_sdk_provider(
                "system prompt",
                "user prompt",
                model="claude-test",
                workspace_root=workspace_root,
            )

    def test_end_turn_returns_text(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            usage = _make_usage(input_tokens=20, output_tokens=10)
            resp = _make_response("end_turn", [_make_text_block('{"status":"completed"}')], usage)
            result = self._run([resp], workspace_root=workspace)
        self.assertIsNone(result.error)
        self.assertEqual('{"status":"completed"}', result.text)
        self.assertIsNotNone(result.usage)
        self.assertEqual(20, result.usage["inputTokens"])

    def test_stop_sequence_treated_as_success(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            resp = _make_response("stop_sequence", [_make_text_block("ok")], _make_usage())
            result = self._run([resp], workspace_root=workspace)
        self.assertIsNone(result.error)
        self.assertEqual("ok", result.text)

    def test_usage_accumulated_across_turns(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            (workspace / "f.txt").write_text("old content", encoding="utf-8")
            tool_resp = _make_response(
                "tool_use",
                [_make_tool_use_block("t1", "read_file", {"path": "f.txt"})],
                _make_usage(input_tokens=10, output_tokens=3),
            )
            end_resp = _make_response(
                "end_turn",
                [_make_text_block('{"status":"completed"}')],
                _make_usage(input_tokens=15, output_tokens=8),
            )

            mock_anthropic = MagicMock()
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            responses = iter([tool_resp, end_resp])
            mock_client.messages.create.side_effect = lambda **kw: next(responses)

            with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
                result = run_sdk_provider(
                    "system", "user", model="m", workspace_root=workspace
                )

        self.assertIsNone(result.error)
        self.assertEqual(25, result.usage["inputTokens"])
        self.assertEqual(11, result.usage["outputTokens"])


class RunSdkProviderToolUseTest(unittest.TestCase):
    """SDK provider handles tool use and loops correctly."""

    def _run_with_tool(
        self,
        tool_name: str,
        tool_input: dict,
        *,
        workspace_root: pathlib.Path,
        capture_tool_results: list | None = None,
    ) -> SdkProviderResult:
        tool_resp = _make_response(
            "tool_use",
            [_make_tool_use_block("id-1", tool_name, tool_input)],
            _make_usage(),
        )
        end_resp = _make_response(
            "end_turn",
            [_make_text_block('{"status":"completed","filesTouched":[]}')],
            _make_usage(),
        )
        mock_anthropic = MagicMock()
        mock_client = MagicMock()
        mock_anthropic.Anthropic.return_value = mock_client
        responses = iter([tool_resp, end_resp])

        if capture_tool_results is not None:
            def side_effect(**kw):
                resp = next(responses)
                messages = kw.get("messages", [])
                if messages and messages[-1]["role"] == "user":
                    content = messages[-1]["content"]
                    if isinstance(content, list):
                        for item in content:
                            if isinstance(item, dict) and item.get("type") == "tool_result":
                                capture_tool_results.append(item["content"])
                return resp
            mock_client.messages.create.side_effect = side_effect
        else:
            mock_client.messages.create.side_effect = lambda **kw: next(responses)

        with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
            return run_sdk_provider(
                "system", "user", model="m", workspace_root=workspace_root
            )

    def test_read_file_tool_executes(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            (workspace / "data.txt").write_text("content here", encoding="utf-8")
            captured: list[str] = []
            result = self._run_with_tool(
                "read_file", {"path": "data.txt"},
                workspace_root=workspace, capture_tool_results=captured
            )
        self.assertIsNone(result.error)
        self.assertEqual(1, len(captured))
        self.assertEqual("content here", captured[0])

    def test_write_file_tool_creates_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            result = self._run_with_tool(
                "write_file", {"path": "new.txt", "content": "written"},
                workspace_root=workspace,
            )
            self.assertIsNone(result.error)
            self.assertEqual("written", (workspace / "new.txt").read_text())

    def test_str_replace_tool_edits_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            (workspace / "src.py").write_text("x = 1\n", encoding="utf-8")
            result = self._run_with_tool(
                "str_replace_based_edit_tool",
                {"path": "src.py", "old_str": "x = 1", "new_str": "x = 2"},
                workspace_root=workspace,
            )
            self.assertIsNone(result.error)
            self.assertIn("x = 2", (workspace / "src.py").read_text())

    def test_multiple_tool_calls_in_one_response(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            (workspace / "a.txt").write_text("a", encoding="utf-8")
            (workspace / "b.txt").write_text("b", encoding="utf-8")
            tool_resp = _make_response(
                "tool_use",
                [
                    _make_tool_use_block("id-1", "read_file", {"path": "a.txt"}),
                    _make_tool_use_block("id-2", "read_file", {"path": "b.txt"}),
                ],
                _make_usage(),
            )
            end_resp = _make_response(
                "end_turn", [_make_text_block('{"status":"completed"}')], _make_usage()
            )
            mock_anthropic = MagicMock()
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            responses = iter([tool_resp, end_resp])
            tool_result_contents: list[str] = []

            def side_effect(**kw):
                r = next(responses)
                msgs = kw.get("messages", [])
                if msgs and msgs[-1]["role"] == "user":
                    for item in (msgs[-1]["content"] or []):
                        if isinstance(item, dict) and item.get("type") == "tool_result":
                            tool_result_contents.append(item["content"])
                return r

            mock_client.messages.create.side_effect = side_effect
            with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
                result = run_sdk_provider(
                    "system", "user", model="m", workspace_root=workspace
                )

        self.assertIsNone(result.error)
        self.assertEqual(["a", "b"], tool_result_contents)


class RunSdkProviderMaxTokensTest(unittest.TestCase):
    def test_max_tokens_returns_error(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            resp = _make_response("max_tokens", [_make_text_block("partial")], _make_usage())
            mock_anthropic = MagicMock()
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = resp
            with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
                result = run_sdk_provider(
                    "system", "user", model="m", workspace_root=workspace
                )
        self.assertIsNotNone(result.error)
        self.assertIn("APITimeoutError", result.error)
        self.assertIn("max_tokens", result.error)


class RunSdkProviderExceptionTest(unittest.TestCase):
    """SDK exceptions are captured in error field with type name for retry_policy."""

    def _run_raising(self, exc_class_name: str, exc_message: str) -> SdkProviderResult:
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)

            ExcClass = type(exc_class_name, (Exception,), {})
            exc = ExcClass(exc_message)

            mock_anthropic = MagicMock()
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.side_effect = exc

            with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
                return run_sdk_provider(
                    "system", "user", model="m", workspace_root=workspace
                )

    def test_rate_limit_error_captured(self):
        result = self._run_raising("RateLimitError", "quota exceeded")
        self.assertIsNotNone(result.error)
        self.assertIn("RateLimitError", result.error)
        self.assertEqual("", result.text)

    def test_auth_error_captured(self):
        result = self._run_raising("AuthenticationError", "invalid key")
        self.assertIsNotNone(result.error)
        self.assertIn("AuthenticationError", result.error)

    def test_timeout_error_captured(self):
        result = self._run_raising("APITimeoutError", "timed out")
        self.assertIsNotNone(result.error)
        self.assertIn("APITimeoutError", result.error)

    def test_internal_server_error_captured(self):
        result = self._run_raising("InternalServerError", "server error")
        self.assertIsNotNone(result.error)
        self.assertIn("InternalServerError", result.error)


class RunSdkProviderProgressCallbackTest(unittest.TestCase):
    def test_on_progress_called_per_iteration(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            (workspace / "f.txt").write_text("data", encoding="utf-8")

            tool_resp = _make_response(
                "tool_use",
                [_make_tool_use_block("id-1", "read_file", {"path": "f.txt"})],
                _make_usage(),
            )
            end_resp = _make_response(
                "end_turn", [_make_text_block("done")], _make_usage()
            )

            mock_anthropic = MagicMock()
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            responses = iter([tool_resp, end_resp])
            mock_client.messages.create.side_effect = lambda **kw: next(responses)

            progress_calls: list[int] = []

            with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
                run_sdk_provider(
                    "system", "user", model="m",
                    workspace_root=workspace,
                    on_progress=lambda i: progress_calls.append(i),
                )

        self.assertEqual([0, 1], progress_calls)


class RunSdkProviderUnexpectedStopReasonTest(unittest.TestCase):
    def test_unexpected_stop_reason_returns_permanent_error(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            workspace = pathlib.Path(tmpdir)
            resp = _make_response("unknown_reason", [_make_text_block("")], _make_usage())
            mock_anthropic = MagicMock()
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = resp
            with patch.dict("sys.modules", {"anthropic": mock_anthropic}):
                result = run_sdk_provider(
                    "system", "user", model="m", workspace_root=workspace
                )
        self.assertIsNotNone(result.error)
        self.assertIn("InvalidRequestError", result.error)


# ---------------------------------------------------------------------------
# Integration: error messages match WP42 retry_policy patterns
# ---------------------------------------------------------------------------


class SdkErrorRetryClassificationTest(unittest.TestCase):
    """Verify that SdkProviderResult.error strings match retry_policy patterns."""

    def setUp(self):
        from pojo_lens_agents.retry_policy import classify_failure
        from pojo_lens_agents.orchestrator_contracts import TaskRunRecord, PromptBudgetResult

        def _record(summary: str) -> TaskRunRecord:
            return TaskRunRecord(
                id="t", title="T", agent="a", resolved_skills=[],
                branch_context_id="c", branch_parent_context_ids=[],
                status="failed", summary=summary,
                workspace_mode="copy", workspace_path="",
                started_at="2026-01-01T00:00:00",
                finished_at="2026-01-01T00:01:00",
                files_touched=[], actual_files_touched=[],
                protected_path_violations=[], write_scope_violations=[],
                validation_commands=[], follow_ups=[], follow_up_tasks=[], notes=[],
                model=None, model_profile=None,
                prompt_chars=0, prompt_estimated_tokens=0,
                prompt_sections=[],
                prompt_budget=PromptBudgetResult(
                    max_chars=None, max_estimated_tokens=None,
                    exceeded=False, violations=[]
                ),
                usage=None, return_code=1,
                prompt_path="", command_path="",
                stdout_path=None, stderr_path=None, result_path=None,
            )

        self.classify = classify_failure
        self.make_record = _record

    def test_rate_limit_error_is_transient(self):
        r = self.make_record("RateLimitError: quota exceeded")
        self.assertEqual("transient", self.classify(r))

    def test_api_timeout_error_is_transient(self):
        r = self.make_record("APITimeoutError: timed out")
        self.assertEqual("transient", self.classify(r))

    def test_internal_server_error_is_transient(self):
        r = self.make_record("InternalServerError: 500")
        self.assertEqual("transient", self.classify(r))

    def test_authentication_error_is_permanent(self):
        r = self.make_record("AuthenticationError: invalid key")
        self.assertEqual("permanent", self.classify(r))

    def test_invalid_request_error_is_permanent(self):
        r = self.make_record("InvalidRequestError: unexpected stop_reason")
        self.assertEqual("permanent", self.classify(r))


if __name__ == "__main__":
    unittest.main()
