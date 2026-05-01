from __future__ import annotations

import hashlib
import os
import tempfile
from pathlib import Path
from typing import Any, Callable


class RunStoreError(RuntimeError):
    """Raised when retained run manifests are missing or structurally invalid."""


def external_workspaces_root(
    *,
    repo_root: Path,
    env: dict[str, str] | None = None,
) -> Path:
    variables = env if env is not None else os.environ
    override = str(variables.get("POJOLENS_AGENT_WORKSPACES_ROOT", "")).strip()
    if override:
        return Path(override).resolve()
    resolved_repo_root = repo_root.resolve()
    repo_slug = resolved_repo_root.name or "repo"
    repo_hash = hashlib.sha1(str(resolved_repo_root).encode("utf-8")).hexdigest()[:12]
    return (Path(tempfile.gettempdir()) / "pojolens-agent-workspaces" / f"{repo_slug}-{repo_hash}").resolve()


def default_workspaces_dir(
    *,
    runtime_root: Path,
    run_id: str,
    repo_root: Path,
    env: dict[str, str] | None = None,
) -> Path:
    _ = runtime_root
    return external_workspaces_root(repo_root=repo_root, env=env) / run_id


def resolve_manifest_path(
    run_ref: str,
    *,
    error_factory: Callable[[str], Exception] = RunStoreError,
) -> Path:
    candidate = Path(run_ref).resolve()
    if candidate.is_dir():
        candidate = candidate / "manifest.json"
    if not candidate.exists():
        raise error_factory(f"Run manifest '{candidate}' does not exist")
    if candidate.name != "manifest.json":
        raise error_factory("Run commands expect a run directory or manifest.json path")
    return candidate


def validate_manifest_payload(
    payload: Any,
    *,
    manifest_path: Path,
    error_factory: Callable[[str], Exception] = RunStoreError,
) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise error_factory(f"{manifest_path}: expected JSON object")
    tasks = payload.get("tasks")
    if not isinstance(tasks, dict):
        raise error_factory(f"{manifest_path}: expected object 'tasks'")
    return payload


def manifest_required_path(
    manifest: dict[str, Any],
    key: str,
    *,
    location: str,
    error_factory: Callable[[str], Exception] = RunStoreError,
) -> Path:
    value = str(manifest.get(key, "")).strip()
    if not value:
        raise error_factory(f"{location}: run manifest is missing '{key}'")
    return Path(value).resolve()


def manifest_run_dir(manifest_path: Path, manifest: dict[str, Any]) -> Path:
    value = str(manifest.get("runDir", "")).strip()
    return Path(value).resolve() if value else manifest_path.parent.resolve()


def manifest_workspaces_dir(
    manifest: dict[str, Any],
    *,
    run_dir: Path,
    error_factory: Callable[[str], Exception] = RunStoreError,
) -> Path:
    value = str(manifest.get("workspacesDir", "")).strip()
    if value:
        return Path(value).resolve()
    run_id = str(manifest.get("runId", "")).strip()
    if not run_id:
        raise error_factory("Run manifest is missing 'workspacesDir' and 'runId'")
    return (run_dir.parent.parent / "workspaces" / run_id).resolve()


def manifest_selected_plan_path(
    manifest_path: Path,
    manifest: dict[str, Any],
    *,
    location: str,
    error_factory: Callable[[str], Exception] = RunStoreError,
) -> Path:
    run_dir = manifest_run_dir(manifest_path, manifest)
    selected_plan_path = (run_dir / "selected-plan.json").resolve()
    if selected_plan_path.exists():
        return selected_plan_path
    return manifest_required_path(
        manifest,
        "planPath",
        location=location,
        error_factory=error_factory,
    )
