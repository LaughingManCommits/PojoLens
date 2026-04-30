from __future__ import annotations

import hashlib
import os
import shutil
from pathlib import Path
from typing import Callable


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def snapshot_workspace_files(
    workspace_root: Path,
    *,
    ignore_dir_names: set[str],
) -> dict[str, str]:
    if not workspace_root.exists():
        return {}
    snapshots: dict[str, str] = {}
    for current_root, dir_names, file_names in os.walk(workspace_root, topdown=True):
        dir_names[:] = sorted(name for name in dir_names if name not in ignore_dir_names)
        current_path = Path(current_root)
        for file_name in sorted(file_names):
            file_path = current_path / file_name
            snapshots[file_path.relative_to(workspace_root).as_posix()] = file_sha256(file_path)
    return snapshots


def diff_workspace_snapshots(before: dict[str, str], after: dict[str, str]) -> list[str]:
    changed: list[str] = []
    for relative_path in sorted(set(before) | set(after)):
        if before.get(relative_path) != after.get(relative_path):
            changed.append(relative_path)
    return changed


def hydrate_copy_workspace(
    *,
    source_root: Path,
    workspace_path: Path,
    file_paths: list[str],
    base_files: tuple[str, ...] = (),
    max_file_bytes: int,
    path_is_relative_to: Callable[[Path, Path], bool],
) -> None:
    copied: set[Path] = set()
    workspace_path.mkdir(parents=True, exist_ok=True)
    source_root_resolved = source_root.resolve()
    for hint in dedupe_strings([*base_files, *file_paths]):
        relative = Path(hint)
        if relative.is_absolute():
            continue
        source = (source_root / relative).resolve()
        if not source.exists() or not path_is_relative_to(source, source_root_resolved):
            continue
        if source.is_dir():
            continue
        if source.stat().st_size > max_file_bytes:
            continue
        destination = workspace_path / relative
        if destination.exists() or source in copied:
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied.add(source)


def artifact_file_size(path: Path | None) -> int:
    if path is None or not path.exists() or not path.is_file():
        return 0
    return int(path.stat().st_size)


def dedupe_strings(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result
