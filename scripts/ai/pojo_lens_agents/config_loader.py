from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import Any

try:
    import tomllib
except ImportError:  # Python < 3.11
    try:
        import tomli as tomllib  # type: ignore[no-redef]
    except ImportError:
        tomllib = None  # type: ignore[assignment]

ALLOWED_DEFAULTS: dict[str, type] = {
    "runtime_root": str,
    "claude_bin": str,
    "max_parallel": int,
    "continue_on_error": bool,
    "dry_run": bool,
    "worker_validation_mode": str,
}

ALLOWED_NOTIFICATIONS: dict[str, type] = {
    "desktop": bool,
    "webhook_url": str,
    "slack_webhook_url": str,
    "notify_on": list,
}

VALID_NOTIFY_ON = frozenset({"success", "failure", "always"})

_DEFAULT_CONFIG_FILENAME = "pojolens-agents.toml"


def _find_config_path(
    config_path: str | Path | None,
    env: dict[str, str],
    root: Path,
) -> Path | None:
    if config_path is not None:
        return Path(config_path).resolve()
    env_path = env.get("POJOLENS_CONFIG", "").strip()
    if env_path:
        return Path(env_path).resolve()
    candidate = root / _DEFAULT_CONFIG_FILENAME
    if candidate.exists():
        return candidate
    return None


def load_config(
    config_path: str | Path | None = None,
    *,
    env: dict[str, str] | None = None,
    root: Path | None = None,
) -> dict[str, Any]:
    if env is None:
        env = dict(os.environ)
    if root is None:
        root = Path(__file__).resolve().parents[3]

    resolved_path = _find_config_path(config_path, env, root)

    if resolved_path is None:
        return {}
    if not resolved_path.exists():
        raise FileNotFoundError(f"Config file not found: {resolved_path}")

    if tomllib is None:
        raise ImportError(
            "TOML support requires Python 3.11+ (tomllib) or the 'tomli' package. "
            "Install tomli: pip install tomli"
        )

    with open(resolved_path, "rb") as fh:
        raw = tomllib.load(fh)

    defaults_section = raw.get("defaults", {})
    if not isinstance(defaults_section, dict):
        raise ValueError(f"{resolved_path}: [defaults] section must be a TOML table")

    unknown = set(defaults_section) - set(ALLOWED_DEFAULTS)
    if unknown:
        raise ValueError(
            f"{resolved_path}: [defaults] contains unknown keys: {sorted(unknown)}. "
            f"Allowed keys: {sorted(ALLOWED_DEFAULTS)}"
        )

    result: dict[str, Any] = {}
    for key, expected_type in ALLOWED_DEFAULTS.items():
        if key not in defaults_section:
            continue
        value = defaults_section[key]
        if not isinstance(value, expected_type):
            raise ValueError(
                f"{resolved_path}: [defaults].{key} must be {expected_type.__name__}, "
                f"got {type(value).__name__}"
            )
        result[key] = value

    return result


def load_notifications_config(
    config_path: str | Path | None = None,
    *,
    env: dict[str, str] | None = None,
    root: Path | None = None,
) -> dict[str, Any]:
    if env is None:
        env = dict(os.environ)
    if root is None:
        root = Path(__file__).resolve().parents[3]

    resolved_path = _find_config_path(config_path, env, root)

    if resolved_path is None:
        return {}
    if not resolved_path.exists():
        raise FileNotFoundError(f"Config file not found: {resolved_path}")

    if tomllib is None:
        raise ImportError(
            "TOML support requires Python 3.11+ (tomllib) or the 'tomli' package. "
            "Install tomli: pip install tomli"
        )

    with open(resolved_path, "rb") as fh:
        raw = tomllib.load(fh)

    notif_section = raw.get("notifications", {})
    if not isinstance(notif_section, dict):
        raise ValueError(f"{resolved_path}: [notifications] section must be a TOML table")

    unknown = set(notif_section) - set(ALLOWED_NOTIFICATIONS)
    if unknown:
        raise ValueError(
            f"{resolved_path}: [notifications] contains unknown keys: {sorted(unknown)}. "
            f"Allowed keys: {sorted(ALLOWED_NOTIFICATIONS)}"
        )

    result: dict[str, Any] = {}
    for key, expected_type in ALLOWED_NOTIFICATIONS.items():
        if key not in notif_section:
            continue
        value = notif_section[key]
        if not isinstance(value, expected_type):
            raise ValueError(
                f"{resolved_path}: [notifications].{key} must be {expected_type.__name__}, "
                f"got {type(value).__name__}"
            )
        if key == "notify_on":
            invalid = [v for v in value if v not in VALID_NOTIFY_ON]
            if invalid:
                raise ValueError(
                    f"{resolved_path}: [notifications].notify_on contains invalid values: {sorted(invalid)}. "
                    f"Valid values: {sorted(VALID_NOTIFY_ON)}"
                )
        result[key] = value

    return result


WATCH_PHASES = frozenset({"task-finished", "task-retry", "batch-ready", "run-finished", "task-reused"})


def format_watch_line(
    ts: str,
    phase: str,
    *,
    task_id: str | None = None,
    task_ids: list[str] | None = None,
    status: str | None = None,
    message: str | None = None,
    details: dict[str, Any] | None = None,
    **_: Any,
) -> str:
    if phase in {"task-finished", "task-reused"}:
        label = task_id or "?"
        stat = status or "?"
        msg = (message or "").strip()
        summary_part = f"  {msg[:100]}" if msg else ""
        return f"[{ts}] {label}  {stat}{summary_part}"
    if phase == "task-retry":
        label = task_id or "?"
        err = ""
        if details:
            err_text = str(details.get("error", "") or "").strip()
            if err_text:
                err = f"  {err_text[:80]}"
        return f"[{ts}] {label}  retry{err}"
    if phase == "batch-ready":
        ids = task_ids or []
        return f"[{ts}] batch-ready  [{', '.join(ids)}]"
    if phase == "run-finished":
        remaining = (details or {}).get("remainingTaskIds", [])
        suffix = f"  remaining={len(remaining)}" if remaining else ""
        return f"[{ts}] run-finished{suffix}"
    return f"[{ts}] {phase}"
