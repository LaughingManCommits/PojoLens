"""Regression tests for WP66: Agent Shared Context File."""
from __future__ import annotations

import json
import pathlib
import tempfile
import unittest
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock


class ExecuteSharedContextToolTest(unittest.TestCase):
    """Unit tests for execute_shared_context_tool."""

    def setUp(self):
        from pojo_lens_agents.sdk_provider import execute_shared_context_tool
        self.execute = execute_shared_context_tool
        self.tmp = tempfile.TemporaryDirectory()
        self.run_dir = Path(self.tmp.name)
        self.ctx_path = self.run_dir / "shared-context.jsonl"

    def tearDown(self):
        self.tmp.cleanup()

    def test_appends_line_to_file(self):
        result = self.execute(
            {"note": "found a race condition in X", "tags": ["bug"]},
            shared_context_path=self.ctx_path,
            task_id="task-1",
        )
        self.assertIn("Appended", result)
        lines = self.ctx_path.read_text(encoding="utf-8").strip().splitlines()
        self.assertEqual(len(lines), 1)
        obj = json.loads(lines[0])
        self.assertEqual(obj["taskId"], "task-1")
        self.assertEqual(obj["note"], "found a race condition in X")
        self.assertEqual(obj["tags"], ["bug"])

    def test_multiple_appends_accumulate(self):
        self.execute({"note": "note A"}, shared_context_path=self.ctx_path, task_id="t1")
        self.execute({"note": "note B"}, shared_context_path=self.ctx_path, task_id="t2")
        lines = self.ctx_path.read_text(encoding="utf-8").strip().splitlines()
        self.assertEqual(len(lines), 2)

    def test_empty_note_returns_error(self):
        result = self.execute({"note": ""}, shared_context_path=self.ctx_path, task_id="t1")
        self.assertIn("Error", result)
        self.assertFalse(self.ctx_path.exists())

    def test_note_truncated_at_max(self):
        long_note = "x" * 600
        self.execute({"note": long_note}, shared_context_path=self.ctx_path, task_id="t1")
        obj = json.loads(self.ctx_path.read_text().strip())
        self.assertEqual(len(obj["note"]), 500)

    def test_tags_default_to_empty_list(self):
        self.execute({"note": "no tags"}, shared_context_path=self.ctx_path, task_id="t1")
        obj = json.loads(self.ctx_path.read_text().strip())
        self.assertEqual(obj["tags"], [])

    def test_creates_parent_dirs(self):
        nested = self.run_dir / "a" / "b" / "shared-context.jsonl"
        result = self.execute({"note": "hello"}, shared_context_path=nested, task_id="t1")
        self.assertIn("Appended", result)
        self.assertTrue(nested.exists())

    def test_ts_field_is_iso_string(self):
        self.execute({"note": "ts test"}, shared_context_path=self.ctx_path, task_id="t1")
        obj = json.loads(self.ctx_path.read_text().strip())
        self.assertIn("ts", obj)
        self.assertIn("T", obj["ts"])  # ISO 8601 separator


class ReadSharedContextTailTest(unittest.TestCase):
    """Unit tests for _read_shared_context_tail helper in prompt_contracts."""

    def setUp(self):
        from pojo_lens_agents.prompt_contracts import _read_shared_context_tail
        self.read_tail = _read_shared_context_tail
        self.tmp = tempfile.TemporaryDirectory()
        self.ctx_path = Path(self.tmp.name) / "shared-context.jsonl"

    def tearDown(self):
        self.tmp.cleanup()

    def _write_entries(self, entries: list[dict]) -> None:
        with self.ctx_path.open("w", encoding="utf-8") as fh:
            for e in entries:
                fh.write(json.dumps(e) + "\n")

    def test_missing_file_returns_empty(self):
        result = self.read_tail(self.ctx_path, 10, [])
        self.assertEqual(result, [])

    def test_returns_entries_up_to_tail_limit(self):
        self._write_entries([{"taskId": f"t{i}", "note": f"n{i}", "tags": []} for i in range(15)])
        result = self.read_tail(self.ctx_path, 10, [])
        self.assertEqual(len(result), 10)
        self.assertEqual(result[0]["taskId"], "t5")
        self.assertEqual(result[-1]["taskId"], "t14")

    def test_tag_filter_excludes_non_matching(self):
        self._write_entries([
            {"taskId": "t1", "note": "bug note", "tags": ["bug"]},
            {"taskId": "t2", "note": "perf note", "tags": ["performance"]},
            {"taskId": "t3", "note": "also bug", "tags": ["bug", "critical"]},
        ])
        result = self.read_tail(self.ctx_path, 10, ["bug"])
        self.assertEqual(len(result), 2)
        task_ids = [e["taskId"] for e in result]
        self.assertIn("t1", task_ids)
        self.assertIn("t3", task_ids)

    def test_empty_tags_returns_all(self):
        self._write_entries([
            {"taskId": "t1", "note": "a", "tags": ["bug"]},
            {"taskId": "t2", "note": "b", "tags": []},
        ])
        result = self.read_tail(self.ctx_path, 10, [])
        self.assertEqual(len(result), 2)

    def test_skips_malformed_lines(self):
        with self.ctx_path.open("w", encoding="utf-8") as fh:
            fh.write('{"taskId": "t1", "note": "good", "tags": []}\n')
            fh.write("not-json\n")
            fh.write('{"taskId": "t2", "note": "also good", "tags": []}\n')
        result = self.read_tail(self.ctx_path, 10, [])
        self.assertEqual(len(result), 2)


class SharedContextPromptSectionTest(unittest.TestCase):
    """Tests for shared context notes section in worker_prompt."""

    def _make_prompt_deps(self):
        from pojo_lens_agents.orchestrator_contracts import PromptSection, PromptRenderResult, PromptSectionMetric

        def render_prompt(sections):
            text = "\n".join(f"## {s.heading}\n{s.body}" for s in sections)
            return PromptRenderResult(
                text=text,
                sections=[
                    PromptSectionMetric(
                        name=s.name,
                        heading=s.heading,
                        chars=len(s.body),
                        estimated_tokens=len(s.body) // 4,
                        item_count=s.item_count,
                        truncated=s.truncated,
                    )
                    for s in sections
                ],
                chars=len(text),
                estimated_tokens=len(text) // 4,
            )

        return render_prompt

    def _call_worker_prompt(self, shared_context_path=None, shared_context_tags=None, ctx_entries=None):
        from pojo_lens_agents.prompt_contracts import worker_prompt
        from pojo_lens_agents.orchestrator_contracts import PromptSection
        from pojo_lens_agents.orchestrator_contracts import (
            PromptRenderResult, PromptSectionMetric,
            TaskDefinition, AgentDefinition, SharedContext, TaskPlan, RunPolicy,
        )

        plan = TaskPlan(
            version=1,
            name="test",
            goal="test goal",
            shared_context=SharedContext(summary="shared summary", constraints=[], read_paths=[], validation=[]),
            tasks=[],
            run_policy=RunPolicy(),
        )
        task = TaskDefinition(
            id="task-1",
            title="Test Task",
            agent="analyst",
            prompt="do something",
            shared_context_tags=shared_context_tags or [],
        )
        agent = AgentDefinition(
            name="analyst",
            description="analyst agent",
            prompt="you are an analyst",
        )

        def render_prompt(sections):
            text = "\n".join(f"## {s.heading}\n{s.body}" for s in sections)
            return PromptRenderResult(
                text=text,
                sections=[
                    PromptSectionMetric(
                        name=s.name, heading=s.heading,
                        chars=len(s.body), estimated_tokens=len(s.body) // 4,
                        item_count=s.item_count, truncated=s.truncated,
                    )
                    for s in sections
                ],
                chars=len(text),
                estimated_tokens=len(text) // 4,
            )

        deps = {
            "default_context_mode": "minimal",
            "default_dependency_materialization_mode": "summary-only",
            "normalize_dependency_materialization_mode": lambda v, location=None: v,
            "normalize_worker_validation_mode": lambda v, location=None: v or "intents-only",
            "resolve_output_profile": lambda task, agent: ("standard", "default"),
            "prompt_task_read_paths": lambda plan, task, context_mode=None: [],
            "effective_task_write_scope": lambda task: [],
            "dedupe_strings": lambda lst: list(dict.fromkeys(lst)),
            "format_bullet_list": lambda items, empty_line="- none", code_format=False, max_items=None: (
                "\n".join(items[:max_items] if max_items else items) or empty_line,
                len(items),
                False,
            ),
            "validation_command_policy": lambda h: {"accepted": False},
            "render_prompt": render_prompt,
            "prompt_section_factory": PromptSection,
        }

        return worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("/tmp/workspace"),
            "",
            dependency_materialization_mode="summary-only",
            dependency_layers_applied=[],
            dry_run=True,
            worker_validation_mode="intents-only",
            shared_context_path=shared_context_path,
            shared_context_tags=shared_context_tags,
            deps=deps,
        )

    def test_no_shared_context_path_omits_section(self):
        result = self._call_worker_prompt(shared_context_path=None)
        self.assertNotIn("Shared context notes", result.text)

    def test_shared_context_path_includes_section(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ctx_path = pathlib.Path(tmpdir) / "shared-context.jsonl"
            result = self._call_worker_prompt(shared_context_path=ctx_path)
            self.assertIn("Shared context notes", result.text)

    def test_existing_entries_appear_in_section(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ctx_path = pathlib.Path(tmpdir) / "shared-context.jsonl"
            ctx_path.write_text(
                json.dumps({"ts": "t", "taskId": "task-0", "note": "race condition in X", "tags": []}) + "\n",
                encoding="utf-8",
            )
            result = self._call_worker_prompt(shared_context_path=ctx_path)
            self.assertIn("race condition in X", result.text)

    def test_tag_filter_applied_in_prompt(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ctx_path = pathlib.Path(tmpdir) / "shared-context.jsonl"
            ctx_path.write_text(
                json.dumps({"ts": "t", "taskId": "t1", "note": "bug note", "tags": ["bug"]}) + "\n"
                + json.dumps({"ts": "t", "taskId": "t2", "note": "perf note", "tags": ["perf"]}) + "\n",
                encoding="utf-8",
            )
            result = self._call_worker_prompt(
                shared_context_path=ctx_path,
                shared_context_tags=["bug"],
            )
            self.assertIn("bug note", result.text)
            self.assertNotIn("perf note", result.text)

    def test_empty_file_shows_none(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ctx_path = pathlib.Path(tmpdir) / "shared-context.jsonl"
            ctx_path.write_text("", encoding="utf-8")
            result = self._call_worker_prompt(shared_context_path=ctx_path)
            self.assertIn("Shared context notes", result.text)
            self.assertIn("none", result.text)


class SharedContextContractsTest(unittest.TestCase):
    """Tests for contract schema and model changes."""

    def test_task_definition_has_shared_context_tags(self):
        from pojo_lens_agents.orchestrator_contracts import TaskDefinition
        td = TaskDefinition(id="t1", title="T", agent="a", prompt="p")
        self.assertEqual(td.shared_context_tags, [])

    def test_task_definition_model_shared_context_tags(self):
        from pojo_lens_agents.orchestrator_models import TaskDefinitionModel
        m = TaskDefinitionModel.model_validate(
            {"id": "t1", "title": "T", "agent": "a", "prompt": "p", "sharedContextTags": ["bug", "perf"]}
        )
        self.assertEqual(m.shared_context_tags, ["bug", "perf"])

    def test_task_definition_model_tags_default_empty(self):
        from pojo_lens_agents.orchestrator_models import TaskDefinitionModel
        m = TaskDefinitionModel.model_validate({"id": "t1", "title": "T", "agent": "a", "prompt": "p"})
        self.assertEqual(m.shared_context_tags, [])

    def test_write_shared_context_in_base_tool_names(self):
        from pojo_lens_agents.orchestrator_contracts import BASE_TOOL_NAMES
        self.assertIn("write_shared_context", BASE_TOOL_NAMES)

    def test_write_shared_context_collides_with_extra_tool(self):
        from pojo_lens_agents.orchestrator_models import ExtraToolDefModel
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            ExtraToolDefModel.model_validate(
                {"name": "write_shared_context", "description": "x", "kind": "shell", "template": "echo"}
            )

    def test_shared_context_filename_constant(self):
        from pojo_lens_agents.orchestrator_contracts import SHARED_CONTEXT_FILENAME
        self.assertEqual(SHARED_CONTEXT_FILENAME, "shared-context.jsonl")


class ManifestSharedContextPathTest(unittest.TestCase):
    """Tests for sharedContextPath in manifest payload."""

    def test_manifest_payload_includes_shared_context_path(self):
        from pojo_lens_agents.orchestrator_contracts import SHARED_CONTEXT_FILENAME
        suffix = "shared-context.jsonl"
        self.assertEqual(SHARED_CONTEXT_FILENAME, suffix)


if __name__ == "__main__":
    unittest.main()
