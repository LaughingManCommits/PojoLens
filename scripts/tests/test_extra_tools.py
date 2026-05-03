"""Regression tests for WP63: Worker Tool Registry (extra tools)."""
from __future__ import annotations

import json
import pathlib
import subprocess
import tempfile
import unittest
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import load_orchestrator_module


def _load_sdk_provider():
    import importlib
    return importlib.import_module("pojo_lens_agents.sdk_provider")


class ExtraToolDefModelValidationTest(unittest.TestCase):
    """ExtraToolDefModel: Pydantic validators — collision and traversal."""

    def _model(self):
        import importlib
        return importlib.import_module("pojo_lens_agents.orchestrator_models").ExtraToolDefModel

    def test_valid_shell_tool(self):
        m = self._model().model_validate({
            "name": "run_tests",
            "description": "Run the test suite.",
            "kind": "shell",
            "template": "pytest {args}",
        })
        self.assertEqual("run_tests", m.name)
        self.assertEqual("shell", m.kind)
        self.assertEqual(30, m.timeout_sec)

    def test_valid_script_tool_custom_timeout(self):
        m = self._model().model_validate({
            "name": "lint_file",
            "description": "Lint a file.",
            "kind": "script",
            "template": "scripts/lint.sh",
            "timeoutSec": 60,
        })
        self.assertEqual(60, m.timeout_sec)

    def test_kind_must_be_shell_or_script(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            self._model().model_validate({
                "name": "mytool",
                "description": "desc",
                "kind": "cmd",
                "template": "echo {args}",
            })

    def test_name_collision_with_bash(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            self._model().model_validate({
                "name": "bash",
                "description": "collides",
                "kind": "shell",
                "template": "bash {args}",
            })

    def test_name_collision_with_read_file(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            self._model().model_validate({
                "name": "read_file",
                "description": "collides",
                "kind": "shell",
                "template": "cat {args}",
            })

    def test_name_collision_with_write_file(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            self._model().model_validate({
                "name": "write_file",
                "description": "collides",
                "kind": "shell",
                "template": "tee {args}",
            })

    def test_name_collision_with_str_replace_based_edit_tool(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            self._model().model_validate({
                "name": "str_replace_based_edit_tool",
                "description": "collides",
                "kind": "shell",
                "template": "sed {args}",
            })

    def test_template_path_traversal_rejected(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            self._model().model_validate({
                "name": "sneaky",
                "description": "traversal",
                "kind": "script",
                "template": "../scripts/evil.sh",
            })

    def test_template_double_dot_in_middle_rejected(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            self._model().model_validate({
                "name": "sneaky2",
                "description": "traversal",
                "kind": "shell",
                "template": "run/../../etc/passwd {args}",
            })


class EffectiveTaskToolsMergeTest(unittest.TestCase):
    """effective_task_tools: task-level overrides agent-level when non-empty."""

    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def _etd(self, name="run_tests"):
        o = self.orchestrator
        return o.ExtraToolDef(
            name=name,
            description=f"Run {name}.",
            kind="shell",
            template=f"{name} {{args}}",
            timeout_sec=30,
        )

    def _agent(self, extra_tools=None):
        o = self.orchestrator
        return o.AgentDefinition(
            name="analyst",
            description="Analyze.",
            prompt="Return JSON.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
            extra_tools=extra_tools or [],
        )

    def _task(self, extra_tools=None):
        o = self.orchestrator
        return o.TaskDefinition(
            id="t1",
            title="T1",
            agent="analyst",
            prompt="Do it.",
            extra_tools=extra_tools or [],
        )

    def test_agent_level_tools_used_when_task_has_none(self):
        agent_tool = self._etd("agent_tool")
        agent = self._agent(extra_tools=[agent_tool])
        task = self._task(extra_tools=[])
        result = self.orchestrator.effective_task_tools(task, agent)
        self.assertEqual([agent_tool], result)

    def test_task_level_overrides_agent_level(self):
        agent_tool = self._etd("agent_tool")
        task_tool = self._etd("task_tool")
        agent = self._agent(extra_tools=[agent_tool])
        task = self._task(extra_tools=[task_tool])
        result = self.orchestrator.effective_task_tools(task, agent)
        self.assertEqual([task_tool], result)

    def test_empty_task_empty_agent_returns_empty(self):
        agent = self._agent()
        task = self._task()
        self.assertEqual([], self.orchestrator.effective_task_tools(task, agent))

    def test_multiple_task_tools_all_returned(self):
        t1 = self._etd("t1")
        t2 = self._etd("t2")
        agent = self._agent()
        task = self._task(extra_tools=[t1, t2])
        result = self.orchestrator.effective_task_tools(task, agent)
        self.assertEqual([t1, t2], result)


class ExtraToolSchemaConstructionTest(unittest.TestCase):
    """_build_extra_tool_schema: correct Anthropic tool schema."""

    def setUp(self):
        self.sdk = _load_sdk_provider()

    def test_shell_tool_schema(self):
        schema = self.sdk._build_extra_tool_schema({
            "name": "run_tests",
            "description": "Run the test suite.",
            "kind": "shell",
            "template": "pytest {args}",
            "timeout_sec": 30,
        })
        self.assertEqual("run_tests", schema["name"])
        self.assertEqual("Run the test suite.", schema["description"])
        props = schema["input_schema"]["properties"]
        self.assertIn("args", props)
        self.assertEqual(["args"], schema["input_schema"]["required"])

    def test_script_tool_schema(self):
        schema = self.sdk._build_extra_tool_schema({
            "name": "lint_file",
            "description": "Lint.",
            "kind": "script",
            "template": "scripts/lint.sh",
            "timeout_sec": 60,
        })
        self.assertEqual("lint_file", schema["name"])
        self.assertIn("args", schema["input_schema"]["properties"])


class ExecuteExtraToolTest(unittest.TestCase):
    """execute_extra_tool: shell execution with workspace-root cwd."""

    def setUp(self):
        self.sdk = _load_sdk_provider()

    def test_shell_template_runs_in_workspace(self):
        from unittest.mock import patch, MagicMock, call
        with tempfile.TemporaryDirectory() as d:
            workspace = pathlib.Path(d)
            extra_tools_by_name = {
                "list_files": {
                    "name": "list_files",
                    "description": "List files.",
                    "kind": "shell",
                    "template": "ls {args}",
                    "timeout_sec": 10,
                }
            }
            fake_result = MagicMock()
            fake_result.stdout = "marker.txt\nother.txt\n"
            fake_result.stderr = ""
            fake_result.returncode = 0
            with patch("subprocess.run", return_value=fake_result) as mock_run:
                result = self.sdk.execute_extra_tool(
                    "list_files",
                    {"args": "--all"},
                    extra_tools_by_name=extra_tools_by_name,
                    workspace_root=workspace,
                )
            self.assertIn("marker.txt", result)
            called_cmd = mock_run.call_args[0][0]
            self.assertIn("ls --all", called_cmd)
            self.assertEqual(mock_run.call_args[1]["cwd"], workspace)

    def test_script_kind_runs_with_args(self):
        from unittest.mock import patch, MagicMock
        with tempfile.TemporaryDirectory() as d:
            workspace = pathlib.Path(d)
            extra_tools_by_name = {
                "echo_tool": {
                    "name": "echo_tool",
                    "description": "Echo.",
                    "kind": "script",
                    "template": "my_script.py",
                    "timeout_sec": 10,
                }
            }
            fake_result = MagicMock()
            fake_result.stdout = "['hello', 'world']\n"
            fake_result.stderr = ""
            fake_result.returncode = 0
            with patch("subprocess.run", return_value=fake_result) as mock_run:
                result = self.sdk.execute_extra_tool(
                    "echo_tool",
                    {"args": "hello world"},
                    extra_tools_by_name=extra_tools_by_name,
                    workspace_root=workspace,
                )
            self.assertIn("hello", result)
            called_cmd = mock_run.call_args[0][0]
            self.assertIn("my_script.py", called_cmd)
            self.assertIn("hello world", called_cmd)

    def test_unknown_tool_returns_error(self):
        with tempfile.TemporaryDirectory() as d:
            result = self.sdk.execute_extra_tool(
                "nonexistent",
                {},
                extra_tools_by_name={},
                workspace_root=pathlib.Path(d),
            )
            self.assertIn("unknown extra tool", result)

    def test_unknown_kind_returns_error(self):
        with tempfile.TemporaryDirectory() as d:
            result = self.sdk.execute_extra_tool(
                "bad_kind",
                {"args": ""},
                extra_tools_by_name={
                    "bad_kind": {"name": "bad_kind", "description": "x", "kind": "magic", "template": "echo", "timeout_sec": 5}
                },
                workspace_root=pathlib.Path(d),
            )
            self.assertIn("unknown extra tool kind", result)

    def test_output_truncated_at_max(self):
        from unittest.mock import patch, MagicMock
        with tempfile.TemporaryDirectory() as d:
            workspace = pathlib.Path(d)
            extra_tools_by_name = {
                "big_output": {
                    "name": "big_output",
                    "description": "Big.",
                    "kind": "shell",
                    "template": "echo {args}",
                    "timeout_sec": 10,
                }
            }
            fake_result = MagicMock()
            fake_result.stdout = "x" * 20000
            fake_result.stderr = ""
            fake_result.returncode = 0
            with patch("subprocess.run", return_value=fake_result):
                result = self.sdk.execute_extra_tool(
                    "big_output",
                    {"args": ""},
                    extra_tools_by_name=extra_tools_by_name,
                    workspace_root=workspace,
                )
            self.assertLessEqual(len(result), self.sdk.MAX_TOOL_OUTPUT_CHARS + 50)
            self.assertIn("truncated", result)


class LoadExtraToolsFromJsonTest(unittest.TestCase):
    """Load agents/tasks with extraTools from JSON; verify collision and traversal validation."""

    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def _base_agents_json(self, extra_agent_fields=None):
        agent = {
            "description": "planning",
            "prompt": "Return JSON only.",
            "modelProfile": "simple",
            "effort": "high",
            "workspaceMode": "copy",
            "contextMode": "minimal",
            "permissionMode": "dontAsk",
            "allowedTools": ["Read"],
            "timeoutSec": 30,
        }
        if extra_agent_fields:
            agent.update(extra_agent_fields)
        return {"version": 1, "agents": {"planner": agent}}

    def test_agent_extra_tools_loaded(self):
        o = self.orchestrator
        with tempfile.TemporaryDirectory() as d:
            agents_path = pathlib.Path(d) / "agents.json"
            agents_path.write_text(json.dumps(self._base_agents_json({
                "extraTools": [
                    {"name": "run_tests", "description": "Run tests.", "kind": "shell", "template": "pytest {args}"}
                ]
            })), encoding="utf-8")
            agents = o.load_agents(agents_path)
        self.assertEqual(1, len(agents["planner"].extra_tools))
        self.assertEqual("run_tests", agents["planner"].extra_tools[0].name)
        self.assertEqual("shell", agents["planner"].extra_tools[0].kind)
        self.assertEqual("pytest {args}", agents["planner"].extra_tools[0].template)

    def test_task_extra_tools_loaded_from_plan(self):
        o = self.orchestrator
        with tempfile.TemporaryDirectory() as d:
            agents_path = pathlib.Path(d) / "agents.json"
            plan_path = pathlib.Path(d) / "plan.json"
            agents_path.write_text(json.dumps(self._base_agents_json()), encoding="utf-8")
            plan_path.write_text(json.dumps({
                "version": 1,
                "name": "extra-tools-test",
                "goal": "Test extra tools.",
                "sharedContext": {"summary": "Test.", "constraints": [], "readPaths": [], "validation": []},
                "tasks": [{"id": "t1", "title": "T1", "agent": "planner", "prompt": "Do it.",
                            "extraTools": [{"name": "lint_file", "description": "Lint.", "kind": "script", "template": "scripts/lint.sh"}]}],
            }), encoding="utf-8")
            agents = o.load_agents(agents_path)
            plan = o.load_task_plan(plan_path, agents)
        task = plan.tasks[0]
        self.assertEqual(1, len(task.extra_tools))
        self.assertEqual("lint_file", task.extra_tools[0].name)

    def test_agent_extra_tools_name_collision_rejected(self):
        o = self.orchestrator
        with tempfile.TemporaryDirectory() as d:
            agents_path = pathlib.Path(d) / "agents.json"
            agents_path.write_text(json.dumps(self._base_agents_json({
                "extraTools": [
                    {"name": "bash", "description": "Clash.", "kind": "shell", "template": "bash {args}"}
                ]
            })), encoding="utf-8")
            with self.assertRaises(o.OrchestratorError):
                o.load_agents(agents_path)

    def test_task_extra_tools_traversal_rejected(self):
        o = self.orchestrator
        with tempfile.TemporaryDirectory() as d:
            agents_path = pathlib.Path(d) / "agents.json"
            plan_path = pathlib.Path(d) / "plan.json"
            agents_path.write_text(json.dumps(self._base_agents_json()), encoding="utf-8")
            plan_path.write_text(json.dumps({
                "version": 1,
                "name": "traversal-test",
                "goal": "Test traversal.",
                "sharedContext": {"summary": "T.", "constraints": [], "readPaths": [], "validation": []},
                "tasks": [{"id": "t1", "title": "T1", "agent": "planner", "prompt": "Do.",
                            "extraTools": [{"name": "evil", "description": "x", "kind": "script", "template": "../evil.sh"}]}],
            }), encoding="utf-8")
            agents = o.load_agents(agents_path)
            with self.assertRaises(o.OrchestratorError):
                o.load_task_plan(plan_path, agents)

    def test_no_extra_tools_field_defaults_to_empty(self):
        o = self.orchestrator
        with tempfile.TemporaryDirectory() as d:
            agents_path = pathlib.Path(d) / "agents.json"
            agents_path.write_text(json.dumps(self._base_agents_json()), encoding="utf-8")
            agents = o.load_agents(agents_path)
        self.assertEqual([], agents["planner"].extra_tools)


class ValidateCommandExtraToolsOutputTest(unittest.TestCase):
    """validate_command exposes agentExtraTools and per-task extraTools."""

    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_validate_reports_agent_extra_tools(self):
        o = self.orchestrator
        with tempfile.TemporaryDirectory() as d:
            agents_path = pathlib.Path(d) / "agents.json"
            agents_path.write_text(json.dumps({
                "version": 1,
                "agents": {
                    "planner": {
                        "description": "planning",
                        "prompt": "Return JSON only.",
                        "modelProfile": "simple",
                        "effort": "high",
                        "workspaceMode": "copy",
                        "contextMode": "minimal",
                        "permissionMode": "dontAsk",
                        "allowedTools": ["Read"],
                        "timeoutSec": 30,
                        "extraTools": [
                            {"name": "run_tests", "description": "Tests.", "kind": "shell", "template": "pytest {args}"}
                        ],
                    }
                },
            }), encoding="utf-8")
            payload = o.validate_command(SimpleNamespace(
                agents=str(agents_path),
                task_plan=None,
                fingerprint_only=False,
            ))
        self.assertIn("agentExtraTools", payload)
        self.assertEqual(["run_tests"], payload["agentExtraTools"]["planner"])

    def test_validate_reports_task_extra_tools_in_task_entries(self):
        o = self.orchestrator
        with tempfile.TemporaryDirectory() as d:
            agents_path = pathlib.Path(d) / "agents.json"
            plan_path = pathlib.Path(d) / "plan.json"
            agents_path.write_text(json.dumps({
                "version": 1,
                "agents": {
                    "planner": {
                        "description": "planning",
                        "prompt": "Return JSON only.",
                        "modelProfile": "simple",
                        "effort": "high",
                        "workspaceMode": "copy",
                        "contextMode": "minimal",
                        "permissionMode": "dontAsk",
                        "allowedTools": ["Read"],
                        "timeoutSec": 30,
                        "extraTools": [
                            {"name": "agent_tool", "description": "Agent.", "kind": "shell", "template": "echo {args}"}
                        ],
                    }
                },
            }), encoding="utf-8")
            plan_path.write_text(json.dumps({
                "version": 1,
                "name": "et-validate-test",
                "goal": "Test.",
                "sharedContext": {"summary": "X.", "constraints": [], "readPaths": [], "validation": []},
                "tasks": [
                    {"id": "t1", "title": "T1", "agent": "planner", "prompt": "X."},
                    {"id": "t2", "title": "T2", "agent": "planner", "prompt": "Y.",
                     "extraTools": [{"name": "task_tool", "description": "Task.", "kind": "shell", "template": "cat {args}"}]},
                ],
            }), encoding="utf-8")
            payload = o.validate_command(SimpleNamespace(
                agents=str(agents_path),
                task_plan=str(plan_path),
                fingerprint_only=False,
            ))
        tasks_by_id = {t["id"]: t for t in payload["tasks"]}
        # t1 has no task-level extra tools → inherits agent_tool
        self.assertEqual(["agent_tool"], tasks_by_id["t1"]["extraTools"])
        # t2 has task-level override → task_tool
        self.assertEqual(["task_tool"], tasks_by_id["t2"]["extraTools"])


if __name__ == "__main__":
    unittest.main()
