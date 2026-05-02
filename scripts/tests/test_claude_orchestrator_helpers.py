import contextlib
import importlib.util
import io
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
from dataclasses import asdict
from types import SimpleNamespace


def load_orchestrator_module():
    root = pathlib.Path(__file__).resolve().parents[2]
    module_path = root / "scripts" / "ai" / "claude-orchestrator.py"
    spec = importlib.util.spec_from_file_location("claude_orchestrator", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load orchestrator module from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def load_cli_module():
    root = pathlib.Path(__file__).resolve().parents[2]
    module_path = root / "scripts" / "ai" / "pojo_lens_agents" / "cli.py"
    spec = importlib.util.spec_from_file_location("pojo_lens_agents_cli", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load CLI module from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def make_task_run_record(
    orchestrator,
    task,
    *,
    status,
    summary,
    agent_name=None,
    branch_context_id=None,
    branch_parent_context_ids=None,
    workspace_mode="copy",
    workspace_path="",
    files_touched=None,
    actual_files_touched=None,
    dependency_materialization_mode=None,
    dependency_layers_applied=None,
    reviewer_findings=None,
    usage=None,
    stdout_path=None,
    stderr_path=None,
    result_path=None,
    stdout_bytes=0,
    stderr_bytes=0,
    result_bytes=0,
):
    return orchestrator.TaskRunRecord(
        id=task.id,
        title=task.title,
        agent=agent_name or task.agent,
        branch_context_id=branch_context_id or task.id,
        branch_parent_context_ids=list(branch_parent_context_ids or []),
        status=status,
        summary=summary,
        workspace_mode=workspace_mode,
        workspace_path=workspace_path,
        started_at="2026-04-04T00:00:00+00:00",
        finished_at="2026-04-04T00:00:01+00:00",
        files_touched=list(files_touched or []),
        actual_files_touched=list(actual_files_touched or []),
        protected_path_violations=[],
        write_scope_violations=[],
        validation_commands=[],
        follow_ups=[],
        notes=[],
        model="claude-haiku-4-5",
        model_profile="simple",
        prompt_chars=1,
        prompt_estimated_tokens=1,
        prompt_sections=[],
        prompt_budget=orchestrator.PromptBudgetResult(
            max_chars=None,
            max_estimated_tokens=None,
            exceeded=False,
            violations=[],
        ),
        usage=usage,
        return_code=0 if status == "completed" else 1,
        prompt_path="",
        command_path="",
        stdout_path=stdout_path,
        stderr_path=stderr_path,
        result_path=result_path,
        stdout_bytes=stdout_bytes,
        stderr_bytes=stderr_bytes,
        result_bytes=result_bytes,
        dependency_materialization_mode=(
            dependency_materialization_mode
            or orchestrator.DEFAULT_DEPENDENCY_MATERIALIZATION_MODE
        ),
        dependency_layers_applied=list(dependency_layers_applied or []),
        reviewer_findings=list(reviewer_findings or []),
    )
