from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Callable


def _file_sha256(path: Path) -> str | None:
    try:
        data = path.read_bytes()
    except OSError:
        return None
    return hashlib.sha256(data).hexdigest()


def compute_task_fingerprint(
    task: Any,
    agent: Any,
    dep_records: dict[str, Any],
    effective_read_paths: list[str],
    workspace_root: Path,
    *,
    resolved_model: str | None,
    resolved_effort: str | None,
    read_file_hash: Callable[[Path], str | None] | None = None,
) -> tuple[str, dict[str, Any]]:
    _hash_fn = read_file_hash if read_file_hash is not None else _file_sha256

    read_path_hashes: dict[str, str | None] = {}
    for rel_path in sorted(effective_read_paths):
        abs_path = (workspace_root / rel_path).resolve()
        read_path_hashes[rel_path] = _hash_fn(abs_path)

    agent_dict: dict[str, Any] = {
        "name": str(getattr(agent, "name", "") or ""),
        "prompt": str(getattr(agent, "prompt", "") or ""),
        "model": getattr(agent, "model", None),
        "model_profile": getattr(agent, "model_profile", None),
        "effort": getattr(agent, "effort", None),
        "skills": sorted(str(s) for s in (getattr(agent, "skills", None) or [])),
    }

    dep_summaries: dict[str, str] = {
        dep_id: str(getattr(dep_records[dep_id], "summary", "") or "")
        for dep_id in sorted(dep_records)
    }

    inputs: dict[str, Any] = {
        "prompt": str(getattr(task, "prompt", "") or ""),
        "readPaths": read_path_hashes,
        "agent": agent_dict,
        "depSummaries": dep_summaries,
        "model": resolved_model,
        "effort": resolved_effort,
    }

    canonical = json.dumps(inputs, sort_keys=True, ensure_ascii=True)
    fingerprint = hashlib.sha256(canonical.encode()).hexdigest()
    return fingerprint, inputs
