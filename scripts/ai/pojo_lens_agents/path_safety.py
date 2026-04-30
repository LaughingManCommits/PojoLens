from __future__ import annotations

from pathlib import Path
from typing import Callable


class PathSafetyError(RuntimeError):
    """Raised when repo-relative path or write-scope safety checks fail."""


def normalize_relative_path(
    path_value: str,
    *,
    location: str,
    error_factory: Callable[[str], Exception] = PathSafetyError,
) -> str:
    candidate = Path(path_value)
    if candidate.is_absolute():
        raise error_factory(f"{location}: absolute paths are not allowed: {path_value}")
    parts: list[str] = []
    for part in candidate.parts:
        if part in {"", "."}:
            continue
        if part == "..":
            raise error_factory(
                f"{location}: parent-directory traversal is not allowed: {path_value}"
            )
        parts.append(part)
    if not parts:
        raise error_factory(f"{location}: expected non-empty relative path")
    return Path(*parts).as_posix()


def path_is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def resolve_relative_path(
    root: Path,
    relative_path: str,
    *,
    location: str,
    error_factory: Callable[[str], Exception] = PathSafetyError,
) -> tuple[str, Path]:
    normalized = normalize_relative_path(
        relative_path,
        location=location,
        error_factory=error_factory,
    )
    root_resolved = root.resolve()
    candidate = (root / normalized).resolve()
    if not path_is_relative_to(candidate, root_resolved):
        raise error_factory(f"{location}: path escapes root '{root_resolved}'")
    return normalized, candidate


def path_within_scope(path: str, scope: str) -> bool:
    return scope == "." or path == scope or path.startswith(f"{scope}/")


def paths_outside_scope(
    paths: list[str],
    declared_scope: list[str],
    *,
    error_factory: Callable[[str], Exception] = PathSafetyError,
) -> list[str]:
    if "." in declared_scope:
        return []
    outside: list[str] = []
    for path in dedupe_strings(paths):
        normalized = normalize_relative_path(
            path,
            location=f"scope audit path '{path}'",
            error_factory=error_factory,
        )
        if any(path_within_scope(normalized, scope) for scope in declared_scope):
            continue
        outside.append(normalized)
    return outside


def protected_path_violations(
    paths: list[str],
    *,
    exact_paths: set[str],
    path_prefixes: tuple[str, ...],
    error_factory: Callable[[str], Exception] = PathSafetyError,
) -> list[str]:
    violations: list[str] = []
    for path in paths:
        normalized = normalize_relative_path(
            path,
            location=f"changed path '{path}'",
            error_factory=error_factory,
        )
        if normalized in exact_paths or normalized.startswith(path_prefixes):
            violations.append(normalized)
    return dedupe_strings(violations)


def dedupe_strings(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result
