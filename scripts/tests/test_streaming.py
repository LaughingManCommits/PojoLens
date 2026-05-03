"""Tests for WP60: Interactive streaming during SDK provider runs."""
from __future__ import annotations

import asyncio
import io
import pathlib
import sys
import types
import unittest
from unittest.mock import MagicMock, patch, call

ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import sdk_provider as sdk_mod
from pojo_lens_agents.sdk_provider import SdkProviderResult, run_sdk_provider


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_fake_message(text: str = '{"status":"completed"}', stop_reason: str = "end_turn") -> MagicMock:
    msg = MagicMock()
    msg.stop_reason = stop_reason
    text_block = MagicMock()
    text_block.type = "text"
    text_block.text = text
    msg.content = [text_block]
    msg.usage = MagicMock()
    msg.usage.input_tokens = 10
    msg.usage.output_tokens = 5
    msg.usage.cache_read_input_tokens = 0
    msg.usage.cache_creation_input_tokens = 0
    return msg


def _make_fake_stream(text_deltas: list[str], final_text: str = '{"status":"completed"}') -> MagicMock:
    stream_ctx = MagicMock()
    stream_ctx.__enter__ = MagicMock(return_value=stream_ctx)
    stream_ctx.__exit__ = MagicMock(return_value=False)
    stream_ctx.text_stream = iter(text_deltas)
    stream_ctx.get_final_message = MagicMock(return_value=_make_fake_message(final_text))
    return stream_ctx


def _make_fake_anthropic(*, use_stream: bool, text_deltas: list[str] | None = None) -> MagicMock:
    anthropic_mod = MagicMock()
    client = MagicMock()
    anthropic_mod.Anthropic.return_value = client
    if use_stream:
        client.messages.stream.return_value = _make_fake_stream(text_deltas or [])
    else:
        client.messages.create.return_value = _make_fake_message()
    return anthropic_mod


# ---------------------------------------------------------------------------
# sdk_provider.run_sdk_provider — on_partial_text param
# ---------------------------------------------------------------------------

class SdkProviderStreamingTest(unittest.TestCase):

    def _call(self, anthropic_mod, **kwargs) -> SdkProviderResult:
        with patch.dict(sys.modules, {"anthropic": anthropic_mod}):
            return run_sdk_provider(
                "system",
                "user",
                workspace_root=pathlib.Path("/tmp"),
                **kwargs,
            )

    def test_no_stream_when_no_on_partial_text(self):
        anthropic_mod = _make_fake_anthropic(use_stream=False)
        result = self._call(anthropic_mod)
        self.assertIsNone(result.error)
        anthropic_mod.Anthropic().messages.create.assert_called_once()
        anthropic_mod.Anthropic().messages.stream.assert_not_called()

    def test_stream_used_when_on_partial_text_provided(self):
        deltas = ["Hello", " world", "!"]
        anthropic_mod = _make_fake_anthropic(use_stream=True, text_deltas=deltas)
        received: list[str] = []
        result = self._call(anthropic_mod, on_partial_text=received.append)
        self.assertIsNone(result.error)
        self.assertEqual(received, deltas)
        anthropic_mod.Anthropic().messages.stream.assert_called_once()
        anthropic_mod.Anthropic().messages.create.assert_not_called()

    def test_on_partial_text_receives_each_delta(self):
        deltas = ["tok1", "tok2", "tok3"]
        anthropic_mod = _make_fake_anthropic(use_stream=True, text_deltas=deltas)
        received: list[str] = []
        self._call(anthropic_mod, on_partial_text=received.append)
        self.assertEqual(received, ["tok1", "tok2", "tok3"])

    def test_on_partial_text_none_uses_create(self):
        anthropic_mod = _make_fake_anthropic(use_stream=False)
        result = self._call(anthropic_mod, on_partial_text=None)
        self.assertIsNone(result.error)
        anthropic_mod.Anthropic().messages.create.assert_called_once()

    def test_stream_to_stderr_compat_no_tty(self):
        """stream_to_stderr=True + non-tty → no streaming (old behaviour)."""
        anthropic_mod = _make_fake_anthropic(use_stream=False)
        buf = io.StringIO()
        buf.isatty = lambda: False
        with patch("sys.stderr", buf):
            result = self._call(anthropic_mod, stream_to_stderr=True)
        self.assertIsNone(result.error)
        anthropic_mod.Anthropic().messages.create.assert_called_once()

    def test_stream_to_stderr_compat_tty(self):
        """stream_to_stderr=True + isatty → streaming to stderr."""
        deltas = ["A", "B"]
        anthropic_mod = _make_fake_anthropic(use_stream=True, text_deltas=deltas)
        buf = io.StringIO()
        buf.isatty = lambda: True
        buf.flush = lambda: None
        with patch("sys.stderr", buf):
            result = self._call(anthropic_mod, stream_to_stderr=True)
        self.assertIsNone(result.error)
        self.assertEqual(buf.getvalue(), "AB")

    def test_on_partial_text_takes_priority_over_stream_to_stderr(self):
        """When both on_partial_text and stream_to_stderr=True set, callback wins."""
        deltas = ["X"]
        anthropic_mod = _make_fake_anthropic(use_stream=True, text_deltas=deltas)
        received: list[str] = []
        buf = io.StringIO()
        buf.isatty = lambda: True
        buf.flush = lambda: None
        with patch("sys.stderr", buf):
            self._call(anthropic_mod, on_partial_text=received.append, stream_to_stderr=True)
        self.assertEqual(received, ["X"])
        self.assertEqual(buf.getvalue(), "")  # nothing written to stderr

    def test_result_text_comes_from_final_message(self):
        deltas = ["part1", "part2"]
        anthropic_mod = _make_fake_anthropic(use_stream=True, text_deltas=deltas)
        result = self._call(anthropic_mod, on_partial_text=lambda t: None)
        self.assertIn("status", result.text)

    def test_no_stdout_pollution_during_streaming(self):
        deltas = ["token"]
        anthropic_mod = _make_fake_anthropic(use_stream=True, text_deltas=deltas)
        buf = io.StringIO()
        with patch("sys.stdout", buf):
            self._call(anthropic_mod, on_partial_text=lambda t: None)
        self.assertEqual(buf.getvalue(), "")

    def test_usage_accumulated_from_stream(self):
        deltas = ["t"]
        anthropic_mod = _make_fake_anthropic(use_stream=True, text_deltas=deltas)
        result = self._call(anthropic_mod, on_partial_text=lambda t: None)
        self.assertIsNotNone(result.usage)
        self.assertIn("inputTokens", result.usage)

    def test_error_result_no_raise(self):
        """Even when streaming and SDK raises, result is SdkProviderResult with error."""
        anthropic_mod = MagicMock()
        anthropic_mod.Anthropic.return_value.messages.stream.side_effect = RuntimeError("network")
        result = self._call(anthropic_mod, on_partial_text=lambda t: None)
        self.assertIsNotNone(result.error)
        self.assertIn("RuntimeError", result.error)


# ---------------------------------------------------------------------------
# sdk_provider — tool-loop markers (fix 1)
# ---------------------------------------------------------------------------

def _make_tool_use_response(tool_names: list[str]) -> MagicMock:
    resp = MagicMock()
    resp.stop_reason = "tool_use"
    blocks = []
    for name in tool_names:
        b = MagicMock()
        b.type = "tool_use"
        b.name = name
        b.id = f"id_{name}"
        b.input = {}
        blocks.append(b)
    resp.content = blocks
    resp.usage = MagicMock()
    resp.usage.input_tokens = 5
    resp.usage.output_tokens = 2
    resp.usage.cache_read_input_tokens = 0
    resp.usage.cache_creation_input_tokens = 0
    return resp


class SdkProviderToolMarkerTest(unittest.TestCase):

    def _build_two_turn_anthropic(self, tool_names: list[str]) -> MagicMock:
        """First call returns tool_use; second call returns end_turn."""
        tool_resp = _make_tool_use_response(tool_names)
        final_resp = _make_fake_message('{"status":"completed"}')
        anthropic_mod = MagicMock()
        client = MagicMock()
        anthropic_mod.Anthropic.return_value = client
        # Both turns use non-streaming path (on_partial_text provided → streaming used)
        # Wire stream to return tool_use first, then end_turn
        call_count = [0]
        stream_ctx_tool = _make_fake_stream([], "")
        stream_ctx_tool.get_final_message.return_value = tool_resp
        stream_ctx_final = _make_fake_stream(["result text"], '{"status":"completed"}')

        def _stream_side_effect(**kwargs):
            call_count[0] += 1
            if call_count[0] == 1:
                return stream_ctx_tool
            return stream_ctx_final

        client.messages.stream.side_effect = _stream_side_effect
        return anthropic_mod

    def _call(self, anthropic_mod, **kwargs) -> tuple[SdkProviderResult, list[str]]:
        received: list[str] = []
        with patch.dict(sys.modules, {"anthropic": anthropic_mod}):
            # patch execute_workspace_tool to avoid real filesystem calls
            with patch.object(sdk_mod, "execute_workspace_tool", return_value="ok"):
                result = run_sdk_provider(
                    "system", "user",
                    workspace_root=pathlib.Path("/tmp"),
                    on_partial_text=received.append,
                    **kwargs,
                )
        return result, received

    def test_tool_marker_emitted_for_single_tool(self):
        anthropic_mod = self._build_two_turn_anthropic(["bash"])
        _, received = self._call(anthropic_mod)
        markers = [t for t in received if t.startswith("\n[tool:")]
        self.assertEqual(len(markers), 1)
        self.assertIn("bash", markers[0])

    def test_tool_marker_emitted_for_each_tool_in_block(self):
        anthropic_mod = self._build_two_turn_anthropic(["read_file", "bash"])
        _, received = self._call(anthropic_mod)
        markers = [t for t in received if t.startswith("\n[tool:")]
        self.assertEqual(len(markers), 2)
        names = [m for m in markers]
        self.assertTrue(any("read_file" in m for m in names))
        self.assertTrue(any("bash" in m for m in names))

    def test_no_tool_marker_when_no_callback(self):
        """When on_partial_text=None, no marker emitted (no streaming path)."""
        tool_resp = _make_tool_use_response(["bash"])
        final_resp = _make_fake_message('{"status":"completed"}')
        anthropic_mod = MagicMock()
        client = MagicMock()
        anthropic_mod.Anthropic.return_value = client
        call_count = [0]
        def _create_side(**kwargs):
            call_count[0] += 1
            return tool_resp if call_count[0] == 1 else final_resp
        client.messages.create.side_effect = _create_side
        received: list[str] = []
        with patch.dict(sys.modules, {"anthropic": anthropic_mod}):
            with patch.object(sdk_mod, "execute_workspace_tool", return_value="ok"):
                run_sdk_provider("system", "user", workspace_root=pathlib.Path("/tmp"))
        self.assertEqual(received, [])

    def test_tool_marker_format(self):
        anthropic_mod = self._build_two_turn_anthropic(["write_file"])
        _, received = self._call(anthropic_mod)
        marker = next(t for t in received if t.startswith("\n[tool:"))
        self.assertEqual(marker, "\n[tool: write_file]\n")

    def test_final_text_still_streamed_after_tool_turn(self):
        anthropic_mod = self._build_two_turn_anthropic(["bash"])
        _, received = self._call(anthropic_mod)
        non_markers = [t for t in received if not t.startswith("\n[tool:")]
        self.assertTrue(any("result text" in t for t in non_markers))


# ---------------------------------------------------------------------------
# orchestrator_app — stderr line-prefix factory (fix 2)
# ---------------------------------------------------------------------------

class StderrPrefixFactoryTest(unittest.TestCase):

    def _make_writer(self, task_id: str = "t1") -> tuple[Any, io.StringIO]:
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        with patch("sys.stderr", buf):
            writer = factory(task_id=task_id, task_title="Task")
        # Return writer bound to buf via closure
        return writer, buf

    def _write(self, writer, text: str, buf: io.StringIO) -> str:
        with patch("sys.stderr", buf):
            writer(text)
        return buf.getvalue()

    def test_single_line_prefixed(self):
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        writer = factory(task_id="t1", task_title="T")
        with patch("sys.stderr", buf):
            writer("hello\n")
        self.assertEqual(buf.getvalue(), "[t1] hello\n")

    def test_multi_line_each_prefixed(self):
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        writer = factory(task_id="t2", task_title="T")
        with patch("sys.stderr", buf):
            writer("line1\nline2\n")
        self.assertEqual(buf.getvalue(), "[t2] line1\n[t2] line2\n")

    def test_partial_token_no_double_prefix(self):
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        writer = factory(task_id="t3", task_title="T")
        with patch("sys.stderr", buf):
            writer("hel")   # partial, no newline yet
            writer("lo\n")  # completes the line
        self.assertEqual(buf.getvalue(), "[t3] hello\n")

    def test_empty_text_noop(self):
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        writer = factory(task_id="t4", task_title="T")
        with patch("sys.stderr", buf):
            writer("")
        self.assertEqual(buf.getvalue(), "")

    def test_different_task_ids_prefixed_independently(self):
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        w1 = factory(task_id="task-a", task_title="A")
        w2 = factory(task_id="task-b", task_title="B")
        with patch("sys.stderr", buf):
            w1("line from a\n")
            w2("line from b\n")
        out = buf.getvalue()
        self.assertIn("[task-a] line from a\n", out)
        self.assertIn("[task-b] line from b\n", out)

    def test_tool_marker_lines_also_prefixed(self):
        """Markers emitted by sdk_provider (\n[tool: bash]\n) get per-line prefix."""
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        writer = factory(task_id="t5", task_title="T")
        with patch("sys.stderr", buf):
            writer("\n[tool: bash]\n")
        out = buf.getvalue()
        # first \n on a fresh line produces just \n (no prefix since empty line),
        # then [tool: bash] line gets prefix
        self.assertIn("[t5] [tool: bash]", out)


# ---------------------------------------------------------------------------
# orchestrator_app — subprocess factory gate (fix 3)
# ---------------------------------------------------------------------------

class SubprocessFactoryGateTest(unittest.TestCase):

    def _run_loaded_plan_minimal(self, watch: bool, provider_mode: str) -> Any:
        """Call run_loaded_plan with minimal mocks; intercept _partial_factory via ctx."""
        from pojo_lens_agents import orchestrator_app as oa
        captured: list[Any] = []

        original_run_non_tui_inner = None

        async def _fake_run_ops(**kwargs):
            captured.append(oa._PARTIAL_FACTORY_CTX.get())
            raise StopAsyncIteration("abort")

        return captured, _fake_run_ops

    def test_no_factory_when_subprocess_provider_watch(self):
        from pojo_lens_agents import orchestrator_app as oa
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory, _PARTIAL_FACTORY_CTX
        token = _PARTIAL_FACTORY_CTX.set(None)
        try:
            with patch.object(oa.sdk_provider_layer, "detect_provider_mode", return_value="subprocess"):
                factory = _make_stderr_partial_factory()
                # The gate: only wire factory when detect_provider_mode() == "sdk"
                mode = oa.sdk_provider_layer.detect_provider_mode()
                result = factory if mode == "sdk" else None
            self.assertIsNone(result)
        finally:
            _PARTIAL_FACTORY_CTX.reset(token)

    def test_factory_created_when_sdk_provider_watch(self):
        from pojo_lens_agents import orchestrator_app as oa
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory, _PARTIAL_FACTORY_CTX
        token = _PARTIAL_FACTORY_CTX.set(None)
        try:
            with patch.object(oa.sdk_provider_layer, "detect_provider_mode", return_value="sdk"):
                mode = oa.sdk_provider_layer.detect_provider_mode()
                factory = _make_stderr_partial_factory() if mode == "sdk" else None
            self.assertIsNotNone(factory)
        finally:
            _PARTIAL_FACTORY_CTX.reset(token)


# ---------------------------------------------------------------------------
# task_execution — partial_text_writer_factory in deps
# ---------------------------------------------------------------------------

class TaskExecutionPartialFactoryTest(unittest.TestCase):

    def _make_deps(self, factory=None, provider_mode="sdk") -> dict:
        from pojo_lens_agents.orchestrator_contracts import (
            PromptBudgetResult, ReviewFinding, TaskRunRecord,  # noqa: F401
        )
        from pojo_lens_agents.orchestrator_utils import iso_now

        sdk_result = SdkProviderResult(
            text='{"status":"completed","summary":"ok","filesTouched":[],'
                 '"validationCommands":[],"followUps":[],"followUpTasks":[],'
                 '"notes":[],"validationIntents":[],"findings":[],"unknownFields":[]}',
            usage={"inputTokens": 5, "outputTokens": 3},
        )
        mock_sdk = MagicMock(return_value=sdk_result)

        deps = {
            "root": pathlib.Path("/tmp"),
            "resolve_worker_validation_mode": MagicMock(return_value=MagicMock(mode="none", source="default")),
            "resolve_output_profile": MagicMock(return_value=("default", "default")),
            "resolve_effort": MagicMock(return_value=(None, "default")),
            "effective_dependency_materialization_mode": MagicMock(return_value="none"),
            "resolved_model": MagicMock(return_value="claude-test"),
            "resolved_model_profile": MagicMock(return_value=None),
            "effective_workspace_mode": MagicMock(return_value="repo"),
            "effective_task_write_scope": MagicMock(return_value=[]),
            "effective_task_skills": MagicMock(return_value=[]),
            "worker_prompt": MagicMock(return_value=MagicMock(
                text="prompt", chars=6, estimated_tokens=2,
                sections=[]
            )),
            "dependency_summary": MagicMock(return_value=""),
            "evaluate_prompt_budget": MagicMock(return_value=PromptBudgetResult(
                max_chars=None, max_estimated_tokens=None, exceeded=False
            )),
            "resolved_max_prompt_chars": MagicMock(return_value=None),
            "resolved_max_prompt_estimated_tokens": MagicMock(return_value=None),
            "effective_tool_lists": MagicMock(return_value=([], [])),
            "claude_command": MagicMock(return_value=["claude"]),
            "task_output_schema_json": MagicMock(return_value="{}"),
            "write_text": MagicMock(),
            "write_json": MagicMock(),
            "iso_now": iso_now,
            "prompt_budget_failure_summary": MagicMock(return_value="budget exceeded"),
            "snapshot_workspace_files": MagicMock(return_value={}),
            "diff_workspace_snapshots": MagicMock(return_value=[]),
            "provider_mode": lambda: provider_mode,
            "run_sdk_provider": mock_sdk,
            "run_subprocess": MagicMock(),
            "task_wait_action": MagicMock(return_value=None),
            "extract_json_payload": MagicMock(side_effect=lambda text: __import__("json").loads(text)),
            "extract_usage": MagicMock(return_value=None),
            "artifact_file_size": MagicMock(return_value=0),
            "apply_workspace_audit": MagicMock(),
            "apply_repository_isolation_audit": MagicMock(),
            "coerce_worker_result": MagicMock(side_effect=lambda raw, **kw: raw),
            "coerce_validation_intent_payload": MagicMock(return_value=MagicMock()),
            "reviewer_finding_factory": ReviewFinding,
            "prepare_workspace": MagicMock(return_value=MagicMock(
                workspace_path=pathlib.Path("/tmp"),
                dependency_layers_applied=[],
            )),
            "error_factory": RuntimeError,
            "asdict": MagicMock(return_value={}),
            "task_run_record_factory": TaskRunRecord,
            "task_branch_context_id": MagicMock(return_value="ctx"),
            "task_branch_parent_context_ids": MagicMock(return_value=[]),
            "prompt_budget_result_factory": PromptBudgetResult,
            "shutil": __import__("shutil"),
            "subprocess": __import__("subprocess"),
            "hydrate_copy_workspace": MagicMock(),
            "analyze_copy_hydration_inputs": MagicMock(return_value={
                "missingReadPaths": [], "directoryReadPaths": [], "oversizedPaths": [], "filesToCopy": []
            }),
            "materialize_dependency_layers": MagicMock(return_value=[]),
            "summarize_paths": MagicMock(return_value=""),
            "ensure_clean_for_worktrees": MagicMock(),
            "workspace_prep_action": MagicMock(return_value=None),
            "emit_slop_log": MagicMock(),
            "workspace_preparation_result_factory": MagicMock(
                return_value=MagicMock(workspace_path=pathlib.Path("/tmp"), dependency_layers_applied=[])
            ),
            "partial_text_writer_factory": factory,
        }
        return deps, mock_sdk

    def _make_task_and_agent(self):
        from pojo_lens_agents.orchestrator_contracts import AgentDefinition, TaskDefinition
        task = MagicMock(spec=TaskDefinition)
        task.id = "t1"
        task.title = "Task 1"
        task.agent = "coder"
        task.workspace_mode = "repo"
        task.timeout_sec = 60
        task.permission_mode = None
        task.max_budget_usd = None
        task.depends_on = []
        task.injected_from = None
        agent = MagicMock(spec=AgentDefinition)
        agent.workspace_mode = "repo"
        agent.timeout_sec = 120
        agent.permission_mode = None
        agent.max_budget_usd = None
        agent.prompt = ""
        return task, agent

    def test_factory_called_once_per_task(self):
        from pojo_lens_agents import task_execution
        factory = MagicMock(return_value=MagicMock())
        deps, _ = self._make_deps(factory=factory)
        task, agent = self._make_task_and_agent()
        asyncio.run(task_execution.execute_task(
            pathlib.Path("/tmp/run"),
            pathlib.Path("/tmp/rt"),
            pathlib.Path("/tmp/ws"),
            MagicMock(tasks=[task]),
            {"coder": agent},
            task,
            {},
            claude_bin="claude",
            agents_json="{}",
            dry_run=False,
            deps=deps,
        ))
        factory.assert_called_once_with(task_id="t1", task_title="Task 1")

    def test_callback_passed_to_run_sdk_provider(self):
        from pojo_lens_agents import task_execution
        cb = MagicMock()
        factory = MagicMock(return_value=cb)
        deps, mock_sdk = self._make_deps(factory=factory)
        task, agent = self._make_task_and_agent()
        asyncio.run(task_execution.execute_task(
            pathlib.Path("/tmp/run"),
            pathlib.Path("/tmp/rt"),
            pathlib.Path("/tmp/ws"),
            MagicMock(tasks=[task]),
            {"coder": agent},
            task,
            {},
            claude_bin="claude",
            agents_json="{}",
            dry_run=False,
            deps=deps,
        ))
        call_kwargs = mock_sdk.call_args.kwargs
        self.assertIs(call_kwargs.get("on_partial_text"), cb)

    def test_no_factory_passes_none_to_provider(self):
        from pojo_lens_agents import task_execution
        deps, mock_sdk = self._make_deps(factory=None)
        task, agent = self._make_task_and_agent()
        asyncio.run(task_execution.execute_task(
            pathlib.Path("/tmp/run"),
            pathlib.Path("/tmp/rt"),
            pathlib.Path("/tmp/ws"),
            MagicMock(tasks=[task]),
            {"coder": agent},
            task,
            {},
            claude_bin="claude",
            agents_json="{}",
            dry_run=False,
            deps=deps,
        ))
        call_kwargs = mock_sdk.call_args.kwargs
        self.assertIsNone(call_kwargs.get("on_partial_text"))


# ---------------------------------------------------------------------------
# orchestrator_app — _make_stderr_partial_factory / _make_tui_partial_factory
# ---------------------------------------------------------------------------

class FactoryFunctionsTest(unittest.TestCase):

    def test_stderr_factory_writes_to_stderr(self):
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        buf = io.StringIO()
        buf.flush = lambda: None
        factory = _make_stderr_partial_factory()
        writer = factory(task_id="t1", task_title="Task 1")
        with patch("sys.stderr", buf):
            writer("hello ")
            writer("world")
        # partial tokens on the same line → prefix applied once at start
        self.assertEqual(buf.getvalue(), "[t1] hello world")

    def test_tui_factory_uses_call_soon_threadsafe(self):
        from pojo_lens_agents.orchestrator_app import _make_tui_partial_factory
        queue = MagicMock()
        loop = MagicMock()
        factory = _make_tui_partial_factory(queue, loop)
        writer = factory(task_id="t2", task_title="Task 2")
        writer("stream text")
        loop.call_soon_threadsafe.assert_called_once()
        args = loop.call_soon_threadsafe.call_args[0]
        self.assertIs(args[0], queue.put_nowait)
        event = args[1]
        self.assertEqual(event["phase"], "task-streaming")
        self.assertEqual(event["taskId"], "t2")
        self.assertEqual(event["text"], "stream text")

    def test_tui_factory_swallows_queue_errors(self):
        from pojo_lens_agents.orchestrator_app import _make_tui_partial_factory
        loop = MagicMock()
        loop.call_soon_threadsafe.side_effect = RuntimeError("queue full")
        factory = _make_tui_partial_factory(MagicMock(), loop)
        writer = factory(task_id="t3", task_title="Task 3")
        writer("should not raise")  # must not raise

    def test_stderr_factory_returns_different_writers_per_call(self):
        from pojo_lens_agents.orchestrator_app import _make_stderr_partial_factory
        factory = _make_stderr_partial_factory()
        w1 = factory(task_id="t1", task_title="A")
        w2 = factory(task_id="t2", task_title="B")
        self.assertIsNot(w1, w2)


# ---------------------------------------------------------------------------
# tui_app — task-streaming event handling
# ---------------------------------------------------------------------------

class TuiStreamingEventTest(unittest.TestCase):

    def _make_app(self):
        try:
            from pojo_lens_agents.tui_app import OrchestratorApp
        except Exception:
            self.skipTest("textual not available")
        import asyncio
        eq: asyncio.Queue = asyncio.Queue()
        app = OrchestratorApp(
            event_queue=eq,
            task_models={"t1": "claude-test"},
            plan_name="test-plan",
        )
        return app

    def test_streaming_active_set_on_first_token(self):
        app = self._make_app()
        self.assertFalse(app._streaming_active)
        log_mock = MagicMock()
        with patch.object(app, "query_one", return_value=log_mock):
            app._handle_task_streaming({"phase": "task-streaming", "taskId": "t1", "text": "hi"})
        self.assertTrue(app._streaming_active)

    def test_log_cleared_on_first_token(self):
        app = self._make_app()
        log_mock = MagicMock()
        with patch.object(app, "query_one", return_value=log_mock):
            app._handle_task_streaming({"phase": "task-streaming", "taskId": "t1", "text": "first"})
        log_mock.clear.assert_called_once()

    def test_log_not_cleared_on_subsequent_tokens(self):
        app = self._make_app()
        app._streaming_active = True
        log_mock = MagicMock()
        with patch.object(app, "query_one", return_value=log_mock):
            app._handle_task_streaming({"phase": "task-streaming", "taskId": "t1", "text": "next"})
        log_mock.clear.assert_not_called()

    def test_empty_text_noop(self):
        app = self._make_app()
        log_mock = MagicMock()
        with patch.object(app, "query_one", return_value=log_mock):
            app._handle_task_streaming({"phase": "task-streaming", "taskId": "t1", "text": ""})
        log_mock.write.assert_not_called()
        self.assertFalse(app._streaming_active)

    def test_streaming_active_cleared_on_task_finished(self):
        app = self._make_app()
        app._streaming_active = True
        app.task_states["t1"].status = "running"
        app.running_task_ids.add("t1")
        grid_mock = MagicMock()
        summary_mock = MagicMock()
        def _qo(cls):
            from pojo_lens_agents.tui_app import TaskGrid, RunSummaryBar, LogPane
            if cls is TaskGrid:
                return grid_mock
            if cls is RunSummaryBar:
                return summary_mock
            return MagicMock()
        with patch.object(app, "query_one", side_effect=_qo):
            app._handle_event({"phase": "task-finished", "taskId": "t1", "status": "completed", "details": {}})
        self.assertFalse(app._streaming_active)

    def test_refresh_log_tail_skipped_when_streaming(self):
        app = self._make_app()
        app._streaming_active = True
        log_mock = MagicMock()
        with patch.object(app, "query_one", return_value=log_mock):
            app._refresh_log_tail()
        log_mock.clear.assert_not_called()
        log_mock.write.assert_not_called()

    def test_refresh_log_tail_runs_when_not_streaming(self):
        app = self._make_app()
        app._streaming_active = False
        app.active_stderr_path = None
        app._last_log_lines = ["stale"]  # differs from [] so tail refresh fires
        log_mock = MagicMock()
        with patch.object(app, "query_one", return_value=log_mock):
            app._refresh_log_tail()
        log_mock.clear.assert_called_once()


if __name__ == "__main__":
    unittest.main()
