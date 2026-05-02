from __future__ import annotations

from pathlib import Path
from typing import Any


def registry_candidates(anchor_path: Path) -> list[Path]:
    parent = anchor_path.parent
    candidates = [
        parent / "skills" / "registry.json",
        parent.parent / "skills" / "registry.json",
    ]
    unique: list[Path] = []
    seen: set[Path] = set()
    for candidate in candidates:
        resolved = candidate.resolve()
        if resolved not in seen:
            unique.append(resolved)
            seen.add(resolved)
    return unique


def discover_skill_registry(anchor_path: Path) -> Path | None:
    for candidate in registry_candidates(anchor_path):
        if candidate.exists():
            return candidate
    return None


def load_skill_registry(anchor_path: Path, *, deps: dict[str, Any]) -> dict[str, Any]:
    registry_path = deps["discover_skill_registry"](anchor_path)
    if registry_path is None:
        return {}
    payload = deps["read_json"](registry_path)
    if not isinstance(payload, dict):
        raise deps["error_factory"](f"{registry_path}: expected JSON object")
    if payload.get("version") != 1:
        raise deps["error_factory"](f"{registry_path}: expected version=1")
    raw_skills = payload.get("skills")
    if not isinstance(raw_skills, dict):
        raise deps["error_factory"](f"{registry_path}: expected 'skills' object")
    skills: dict[str, Any] = {}
    for name, definition in raw_skills.items():
        location = f"{registry_path}:{name}"
        if not isinstance(name, str) or not name.strip():
            raise deps["error_factory"](f"{location}: invalid skill name")
        if not isinstance(definition, dict):
            raise deps["error_factory"](f"{location}: expected object definition")
        prompt_path_value = deps["require_string"](definition, "promptFile", location=location)
        prompt_path = (registry_path.parent / Path(prompt_path_value)).resolve()
        if Path(prompt_path_value).is_absolute():
            raise deps["error_factory"](f"{location}: 'promptFile' must be a relative path")
        if not prompt_path.exists():
            raise deps["error_factory"](f"{location}: prompt file '{prompt_path_value}' does not exist")
        if not prompt_path.is_file():
            raise deps["error_factory"](f"{location}: prompt file '{prompt_path_value}' must be a file")
        try:
            prompt_text = deps["read_text"](prompt_path).strip()
        except OSError as exc:
            raise deps["error_factory"](f"{location}: cannot read prompt file '{prompt_path_value}': {exc}") from exc
        if not prompt_text:
            raise deps["error_factory"](f"{location}: prompt file '{prompt_path_value}' is empty")
        skill = deps["skill_definition_factory"](
            name=name.strip(),
            description=deps["require_string"](definition, "description", location=location),
            prompt_path=str(prompt_path),
        )
        skills[skill.name] = skill
    return skills


def validate_known_skills(
    skills: list[str],
    registry: dict[str, Any],
    *,
    registry_path: Path | None,
    location: str,
    deps: dict[str, Any],
) -> list[str]:
    normalized = deps["dedupe_strings"]([skill.strip() for skill in skills if skill and skill.strip()])
    if not normalized:
        return []
    if registry_path is None:
        return normalized
    unknown = [skill for skill in normalized if skill not in registry]
    if unknown:
        raise deps["error_factory"](
            f"{location}: unknown skills {unknown}; register them in '{registry_path}'"
        )
    return normalized


def infer_task_skills(task: Any, registry: dict[str, Any]) -> list[str]:
    if not registry:
        return []
    scope = list(task.read_paths) + list(task.write_paths)
    inferred: list[str] = []
    if "docs" in registry and any(
        path.startswith("docs/") or path in {"README.md", "MIGRATION.md"} for path in scope
    ):
        inferred.append("docs")
    if "release" in registry and any(
        path == "RELEASE.md"
        or path == "pom.xml"
        or path.startswith("pojo-lens")
        and path.endswith("/pom.xml")
        or path == ".github/workflows/release.yml"
        for path in scope
    ):
        inferred.append("release")
    if "benchmark" in registry and any(
        path.startswith("pojo-lens-benchmarks/")
        or path.startswith("benchmarks/")
        or path.startswith("scripts/benchmarks/")
        for path in scope
    ):
        inferred.append("benchmark")
    if "orchestrator" in registry and any(
        path.startswith("ai/orchestrator/") or path.startswith("scripts/ai/")
        for path in scope
    ):
        inferred.append("orchestrator")
    return inferred


def resolve_task_skills(task: Any, agent: Any, registry: dict[str, Any], *, dedupe_strings) -> list[str]:
    return dedupe_strings(list(task.skills) + list(agent.skills) + infer_task_skills(task, registry))
