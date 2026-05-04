"""Tests for WP77: workspace_manager.py and related wiring."""
from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from pojo_lens_agents.workspace_manager import (
    WorkspaceError,
    cleanup_workspace,
    effective_codebase_path,
    effective_workspace_root,
    effective_workspace_strategy,
    prepare_workspace,
    resolve_workspace_root,
    DEFAULT_WORKSPACE_ROOT,
    WORKSPACE_STRATEGIES,
)


# ---------------------------------------------------------------------------
# resolve_workspace_root
# ---------------------------------------------------------------------------

def test_resolve_workspace_root_explicit():
    p = resolve_workspace_root("/tmp/custom")
    assert str(p).endswith("custom")


def test_resolve_workspace_root_env(tmp_path):
    p = resolve_workspace_root(None, env={"POJOLENS_WORKSPACE_ROOT": str(tmp_path)})
    assert p == tmp_path


def test_resolve_workspace_root_default():
    p = resolve_workspace_root(None, env={})
    assert p == DEFAULT_WORKSPACE_ROOT


def test_resolve_workspace_root_explicit_beats_env(tmp_path):
    p = resolve_workspace_root("/tmp/explicit", env={"POJOLENS_WORKSPACE_ROOT": str(tmp_path)})
    assert str(p).endswith("explicit")


# ---------------------------------------------------------------------------
# prepare_workspace — repo strategy
# ---------------------------------------------------------------------------

def test_prepare_workspace_repo_returns_codebase(tmp_path):
    result = prepare_workspace(str(tmp_path), "repo", DEFAULT_WORKSPACE_ROOT, "slug", "run1")
    assert result == tmp_path.resolve()


def test_prepare_workspace_repo_none_returns_cwd():
    import os
    result = prepare_workspace(None, "repo", DEFAULT_WORKSPACE_ROOT, "slug", "run1")
    assert result == Path(os.getcwd()).resolve()


# ---------------------------------------------------------------------------
# prepare_workspace — copy strategy
# ---------------------------------------------------------------------------

def test_prepare_workspace_copy_creates_dir_with_files(tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    (src / "file.txt").write_text("hello")
    ws_root = tmp_path / "workspaces"
    result = prepare_workspace(str(src), "copy", ws_root, "slug", "run1")
    assert result.exists()
    assert (result / "file.txt").read_text() == "hello"


def test_prepare_workspace_copy_requires_codebase_path(tmp_path):
    with pytest.raises(WorkspaceError, match="codebasePath"):
        prepare_workspace(None, "copy", tmp_path, "slug", "run1")


def test_prepare_workspace_copy_requires_existing_src(tmp_path):
    with pytest.raises(WorkspaceError, match="does not exist"):
        prepare_workspace(str(tmp_path / "missing"), "copy", tmp_path, "slug", "run1")


# ---------------------------------------------------------------------------
# prepare_workspace — scratch strategy
# ---------------------------------------------------------------------------

def test_prepare_workspace_scratch_creates_empty_dir(tmp_path):
    ws_root = tmp_path / "workspaces"
    result = prepare_workspace(None, "scratch", ws_root, "slug", "run1")
    assert result.exists()
    assert list(result.iterdir()) == []


# ---------------------------------------------------------------------------
# prepare_workspace — unknown strategy
# ---------------------------------------------------------------------------

def test_prepare_workspace_unknown_strategy_raises():
    with pytest.raises(WorkspaceError, match="Unknown workspace strategy"):
        prepare_workspace(None, "invalid", DEFAULT_WORKSPACE_ROOT, "slug", "run1")


# ---------------------------------------------------------------------------
# cleanup_workspace
# ---------------------------------------------------------------------------

def test_cleanup_workspace_removes_copy_dir(tmp_path):
    d = tmp_path / "ws"
    d.mkdir()
    assert cleanup_workspace(d, "copy") is True
    assert not d.exists()


def test_cleanup_workspace_removes_scratch_dir(tmp_path):
    d = tmp_path / "ws"
    d.mkdir()
    assert cleanup_workspace(d, "scratch") is True
    assert not d.exists()


def test_cleanup_workspace_skips_repo(tmp_path):
    d = tmp_path / "ws"
    d.mkdir()
    assert cleanup_workspace(d, "repo") is False
    assert d.exists()


def test_cleanup_workspace_missing_dir_returns_false(tmp_path):
    assert cleanup_workspace(tmp_path / "missing", "copy") is False


# ---------------------------------------------------------------------------
# effective_* helpers
# ---------------------------------------------------------------------------

class _FakePlan:
    def __init__(self, codebase_path=None, workspace_strategy="repo"):
        self.codebase_path = codebase_path
        self.workspace_strategy = workspace_strategy


class _FakeArgs:
    def __init__(self, **kwargs):
        for k, v in kwargs.items():
            setattr(self, k, v)


def test_effective_codebase_path_cli_beats_plan():
    plan = _FakePlan(codebase_path="/plan/path")
    args = _FakeArgs(codebase_path="/cli/path")
    assert effective_codebase_path(plan, args) == "/cli/path"


def test_effective_codebase_path_falls_back_to_plan():
    plan = _FakePlan(codebase_path="/plan/path")
    args = _FakeArgs(codebase_path="")
    assert effective_codebase_path(plan, args) == "/plan/path"


def test_effective_codebase_path_none_when_both_empty():
    plan = _FakePlan(codebase_path=None)
    args = _FakeArgs(codebase_path="")
    assert effective_codebase_path(plan, args) is None


def test_effective_workspace_strategy_cli_beats_plan():
    plan = _FakePlan(workspace_strategy="copy")
    args = _FakeArgs(workspace_strategy="scratch")
    assert effective_workspace_strategy(plan, args) == "scratch"


def test_effective_workspace_strategy_falls_back_to_plan():
    plan = _FakePlan(workspace_strategy="copy")
    args = _FakeArgs(workspace_strategy="")
    assert effective_workspace_strategy(plan, args) == "copy"


def test_effective_workspace_strategy_invalid_cli_falls_back_to_plan():
    plan = _FakePlan(workspace_strategy="copy")
    args = _FakeArgs(workspace_strategy="bogus")
    assert effective_workspace_strategy(plan, args) == "copy"


def test_effective_workspace_strategy_defaults_to_repo():
    plan = _FakePlan(workspace_strategy="")
    args = _FakeArgs(workspace_strategy="")
    assert effective_workspace_strategy(plan, args) == "repo"


def test_effective_workspace_root_from_args(tmp_path):
    plan = _FakePlan()
    args = _FakeArgs(workspace_root=str(tmp_path))
    result = effective_workspace_root(plan, args)
    assert result == tmp_path.resolve()


# ---------------------------------------------------------------------------
# orchestrator_contracts: TaskPlan fields
# ---------------------------------------------------------------------------

def _make_plan(**kwargs):
    from pojo_lens_agents.orchestrator_contracts import TaskPlan, RunPolicy, SharedContext
    defaults = dict(
        version="1",
        name="test",
        goal="g",
        shared_context=SharedContext(summary="", constraints=[], read_paths=[], validation=[]),
        tasks=[],
        run_policy=RunPolicy(),
    )
    defaults.update(kwargs)
    return TaskPlan(**defaults)


def test_task_plan_default_workspace_fields():
    plan = _make_plan()
    assert plan.codebase_path is None
    assert plan.workspace_strategy == "repo"


def test_task_plan_custom_workspace_fields():
    plan = _make_plan(codebase_path="/some/path", workspace_strategy="copy")
    assert plan.codebase_path == "/some/path"
    assert plan.workspace_strategy == "copy"


# ---------------------------------------------------------------------------
# task_plan_ops: load codebasePath / workspaceStrategy from JSON
# ---------------------------------------------------------------------------

_SHARED_CONTEXT_PAYLOAD = {
    "summary": "",
    "constraints": [],
    "readPaths": [],
    "validation": [],
}

_MINIMAL_TASK = {
    "id": "t1",
    "title": "Task 1",
    "agent": "agent1",
    "prompt": "do stuff",
}


def test_task_plan_model_parses_workspace_fields():
    from pojo_lens_agents.orchestrator_models import TaskPlanModel
    m = TaskPlanModel.model_validate({
        "version": 1,
        "name": "ws-test",
        "goal": "g",
        "sharedContext": _SHARED_CONTEXT_PAYLOAD,
        "codebasePath": "/repo/path",
        "workspaceStrategy": "copy",
        "tasks": [_MINIMAL_TASK],
    })
    assert m.codebase_path == "/repo/path"
    assert m.workspace_strategy == "copy"


def test_task_plan_model_default_workspace_strategy():
    from pojo_lens_agents.orchestrator_models import TaskPlanModel
    m = TaskPlanModel.model_validate({
        "version": 1,
        "name": "ws-test2",
        "goal": "g",
        "sharedContext": _SHARED_CONTEXT_PAYLOAD,
        "tasks": [_MINIMAL_TASK],
    })
    assert m.codebase_path is None
    assert m.workspace_strategy == "repo"


# ---------------------------------------------------------------------------
# WORKSPACE_STRATEGIES constant
# ---------------------------------------------------------------------------

def test_workspace_strategies_set():
    assert WORKSPACE_STRATEGIES == frozenset({"repo", "copy", "scratch"})
