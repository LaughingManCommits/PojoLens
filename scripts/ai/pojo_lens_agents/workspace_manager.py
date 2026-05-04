"""workspace_manager.py — Plan-level workspace preparation for WP77.

Strategies
----------
repo    — agents work directly in ``codebase_path`` (or repo root).  No copy.
copy    — ``codebase_path`` is recursively copied into
          ``workspace_root/{plan_slug}/{run_id}/``; agents work on the copy.
scratch — an empty directory at ``workspace_root/{plan_slug}/{run_id}/``;
          agents build from scratch with no seed codebase.
"""
from __future__ import annotations

import shutil
from pathlib import Path
from typing import Any

WORKSPACE_STRATEGIES = frozenset({"repo", "copy", "scratch"})
DEFAULT_WORKSPACE_ROOT = Path.home() / ".pojolens" / "workspaces"


class WorkspaceError(RuntimeError):
    """Raised for invalid workspace configuration or preparation failures."""


def resolve_workspace_root(
    config_root: str | Path | None,
    *,
    env: dict[str, str] | None = None,
) -> Path:
    """Return the effective workspace root directory.

    Priority: explicit ``config_root`` > ``POJOLENS_WORKSPACE_ROOT`` env var > default.
    """
    import os as _os
    if config_root:
        return Path(config_root).expanduser().resolve()
    _env = env if env is not None else dict(_os.environ)
    env_val = _env.get("POJOLENS_WORKSPACE_ROOT", "").strip()
    if env_val:
        return Path(env_val).expanduser().resolve()
    return DEFAULT_WORKSPACE_ROOT


def prepare_workspace(
    codebase_path: str | Path | None,
    strategy: str,
    workspace_root: Path,
    plan_slug: str,
    run_id: str,
) -> Path:
    """Create and populate a per-run workspace directory.

    Returns the effective ``repo_root`` that agents should use as their cwd.

    repo    → returns ``codebase_path`` resolved (or cwd if None).
    copy    → copies ``codebase_path`` into ``workspace_root/slug/run_id/``; returns that dir.
    scratch → creates empty ``workspace_root/slug/run_id/``; returns that dir.
    """
    if strategy not in WORKSPACE_STRATEGIES:
        raise WorkspaceError(
            f"Unknown workspace strategy {strategy!r}. "
            f"Must be one of {sorted(WORKSPACE_STRATEGIES)}."
        )

    if strategy == "repo":
        if codebase_path:
            return Path(codebase_path).expanduser().resolve()
        return Path.cwd()

    dest = (workspace_root / plan_slug / run_id).resolve()
    dest.mkdir(parents=True, exist_ok=True)

    if strategy == "copy":
        if not codebase_path:
            raise WorkspaceError(
                "workspace strategy 'copy' requires a codebasePath but none was specified."
            )
        src = Path(codebase_path).expanduser().resolve()
        if not src.exists():
            raise WorkspaceError(
                f"workspace strategy 'copy': codebasePath {src} does not exist."
            )
        shutil.copytree(str(src), str(dest), dirs_exist_ok=True)

    return dest


def cleanup_workspace(workspace_dir: Path | str, strategy: str) -> bool:
    """Remove a prepared workspace directory.

    Only removes for copy/scratch strategies; repo dirs are never deleted.
    Returns True if the directory was removed, False otherwise.
    """
    if strategy not in ("copy", "scratch"):
        return False
    path = Path(workspace_dir)
    if path.exists():
        shutil.rmtree(path, ignore_errors=True)
        return True
    return False


def effective_workspace_root(plan: Any, args: Any, config_root: str | None = None) -> Path:
    """Resolve the effective workspace root from plan, args, and config."""
    _ws_root = (
        getattr(args, "workspace_root", None)
        or config_root
        or None
    )
    return resolve_workspace_root(_ws_root)


def effective_codebase_path(plan: Any, args: Any) -> str | None:
    """Return the effective codebase_path from CLI override or plan field."""
    cli_path = str(getattr(args, "codebase_path", "") or "").strip()
    if cli_path:
        return cli_path
    plan_path = getattr(plan, "codebase_path", None)
    return str(plan_path).strip() or None if plan_path else None


def effective_workspace_strategy(plan: Any, args: Any) -> str:
    """Return the effective workspace strategy from CLI override or plan field."""
    cli_strategy = str(getattr(args, "workspace_strategy", "") or "").strip()
    if cli_strategy and cli_strategy in WORKSPACE_STRATEGIES:
        return cli_strategy
    plan_strategy = str(getattr(plan, "workspace_strategy", "") or "repo").strip()
    return plan_strategy if plan_strategy in WORKSPACE_STRATEGIES else "repo"
