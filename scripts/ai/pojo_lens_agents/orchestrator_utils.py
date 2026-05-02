#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import sys
import textwrap
from datetime import datetime, timezone
from json import JSONDecodeError
from pathlib import Path
from typing import Any
from uuid import uuid4

from pojo_lens_agents import workspace_review as workspace_review_layer
from pojo_lens_agents.orchestrator_contracts import (
    DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
    DEFAULT_PROMPT_SECTION_ITEM_LIMIT,
    OrchestratorError,
    PromptRenderResult,
    PromptSection,
    PromptSectionMetric,
    SlopLogAction,
    SLOP_LOG_LOCK,
    SLOP_PROGRESS_DOTS,
    TaskDefinition,
)

# Matches temp files written by write_text: .<original-name>.<8-hex-chars>.tmp
# Leading dot + original name + dot + 8 lowercase hex chars + .tmp
_ORPHANED_TMP_RE = re.compile(r"^\..+\.[0-9a-f]{8}\.tmp$")


def iso_now() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat()


def slugify(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return normalized or "task"


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def read_json(path: Path) -> Any:
    try:
        return json.loads(read_text(path))
    except JSONDecodeError as exc:
        raise OrchestratorError(f"Invalid JSON in {path}: {exc}") from exc
    except OSError as exc:
        raise OrchestratorError(f"Cannot read {path}: {exc}") from exc


def _atomic_replace(src: Path, dst: Path, *, _max_attempts: int = 5) -> None:
    """Rename src → dst atomically, retrying on transient PermissionError.

    On Windows, os.replace() can raise PermissionError(13) if a concurrent
    reader briefly holds a shared lock on the destination (e.g., an operator
    running 'status' while a run is in progress).  Retrying with exponential
    backoff handles this narrow window without masking genuine permission
    errors (the last attempt raises unconditionally).
    """
    import time
    delay = 0.01
    for attempt in range(_max_attempts):
        try:
            os.replace(src, dst)
            return
        except PermissionError:
            if attempt == _max_attempts - 1:
                raise
            time.sleep(delay)
            delay = min(delay * 2, 0.1)


def write_text(path: Path, text: str | None) -> None:
    """Write text to path atomically.

    Writes to a unique .tmp sibling first, then renames into place with
    os.replace() so a crash or KeyboardInterrupt never leaves a partial file.
    The temp file is cleaned up on any exception before re-raising.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    content = (text or "").encode("utf-8")
    tmp_path = path.parent / f".{path.name}.{uuid4().hex[:8]}.tmp"
    try:
        tmp_path.write_bytes(content)
        _atomic_replace(tmp_path, path)
    except BaseException:
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass
        raise


def write_json(path: Path, payload: object) -> None:
    write_text(path, json.dumps(payload, indent=2) + "\n")


def recover_orphaned_write_temps(directory: Path) -> list[str]:
    """Remove atomic write temps left by a prior crash.

    Scans the directory tree recursively for files matching the write_text
    temp pattern (.<name>.<8-hex>.tmp) and deletes them.  Returns the
    relative paths of every file removed so callers can log a warning.
    Silently skips any file it cannot remove (e.g. concurrent writer).
    Returns an empty list if the directory does not exist.
    """
    if not directory.is_dir():
        return []
    removed: list[str] = []
    for candidate in directory.rglob("*.tmp"):
        if candidate.is_file() and _ORPHANED_TMP_RE.match(candidate.name):
            try:
                candidate.unlink()
                removed.append(str(candidate.relative_to(directory)))
            except OSError:
                pass
    return removed


def read_bytes(path: Path) -> bytes:
    return path.read_bytes()


def file_sha256(path: Path) -> str:
    return workspace_review_layer.file_sha256(path)



def summarize_paths(paths: list[str], *, limit: int = 4) -> str:
    visible = paths[:limit]
    if not visible:
        return "none"
    summary = ", ".join(visible)
    hidden = len(paths) - len(visible)
    if hidden > 0:
        summary += f", ... ({hidden} more)"
    return summary


def format_issue_block(header: str, issues: list[str]) -> str:
    visible = dedupe_strings(issues)
    return header if not visible else f"{header}:\n- " + "\n- ".join(visible)


def dedupe_strings(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def estimate_tokens(text: str) -> int:
    return max(1, (len(text) + 3) // 4)


def compact_text(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def truncate_text(text: str, max_chars: int | None) -> tuple[str, bool]:
    compacted = compact_text(text)
    if max_chars is None or len(compacted) <= max_chars:
        return compacted, False
    if max_chars <= 3:
        return compacted[:max_chars], True
    return compacted[: max_chars - 3].rstrip() + "...", True


def truncate_multiline_text(text: str, max_chars: int | None) -> tuple[str, bool]:
    stripped = text.strip()
    if max_chars is None or len(stripped) <= max_chars:
        return stripped, False
    if max_chars <= 3:
        return stripped[:max_chars], True
    return stripped[: max_chars - 3].rstrip() + "...", True


def format_bullet_list(
    values: list[str],
    *,
    empty_line: str,
    code_format: bool = False,
    max_items: int = DEFAULT_PROMPT_SECTION_ITEM_LIMIT,
    max_chars: int | None = DEFAULT_PROMPT_ITEM_CHAR_LIMIT,
) -> tuple[str, int, bool]:
    if not values:
        return empty_line, 0, False
    truncated = False
    visible_values = values[:max_items] if max_items > 0 else values
    lines: list[str] = []
    for value in visible_values:
        item_text, item_truncated = truncate_text(value, max_chars)
        if code_format:
            item_text = f"`{item_text}`"
        lines.append(f"- {item_text}")
        truncated = truncated or item_truncated
    hidden_count = len(values) - len(visible_values)
    if hidden_count > 0:
        lines.append(f"- ... ({hidden_count} more omitted)")
        truncated = True
    return "\n".join(lines), len(values), truncated


def render_prompt(sections: list[PromptSection]) -> PromptRenderResult:
    rendered_sections: list[str] = []
    metrics: list[PromptSectionMetric] = []
    for section in sections:
        rendered = f"{section.heading}:\n{section.body}"
        rendered_sections.append(rendered)
        metrics.append(
            PromptSectionMetric(
                name=section.name,
                heading=section.heading,
                chars=len(rendered),
                estimated_tokens=estimate_tokens(rendered),
                item_count=section.item_count,
                truncated=section.truncated,
            )
        )
    prompt = "\n\n".join(rendered_sections)
    return PromptRenderResult(
        text=prompt,
        sections=metrics,
        chars=len(prompt),
        estimated_tokens=estimate_tokens(prompt),
    )



def slop_log_action(actor: str, phase: str, phrase: str, reason: str) -> SlopLogAction:
    normalized_phase = phase.strip().upper()
    if not normalized_phase:
        raise OrchestratorError("Slop log phase must not be empty")
    return SlopLogAction(
        actor=actor.strip().upper() or "AGENT",
        phase=normalized_phase,
        phrase=phrase.strip(),
        reason=reason.strip(),
    )


def emit_slop_log(action: SlopLogAction, *, frame: int | None = None) -> None:
    if not getattr(sys.stderr, "isatty", lambda: False)():
        return
    dots = ""
    if frame is not None:
        dots = f" {SLOP_PROGRESS_DOTS[frame % len(SLOP_PROGRESS_DOTS)]}"
    reason = f" ({action.reason})" if action.reason else ""
    with SLOP_LOG_LOCK:
        print(
            f"[{action.actor}][{action.phase}] {action.phrase}{dots}{reason}",
            file=sys.stderr,
            flush=True,
        )


def planner_wait_action(agent_name: str) -> SlopLogAction:
    return slop_log_action("planner", "PROCESS", "Slopchurning", f"waiting for {agent_name}")


def task_wait_action(task: TaskDefinition) -> SlopLogAction:
    return slop_log_action(task.id, "FLOW", "Slopsloshing", f"waiting for {task.agent}")


def workspace_prep_action(task: TaskDefinition, workspace_mode: str) -> SlopLogAction | None:
    if workspace_mode == "copy":
        return slop_log_action(task.id, "INGEST", "Slurping up slop", "hydrating copy workspace")
    if workspace_mode == "worktree":
        return slop_log_action(task.id, "INGEST", "Slophoovering", "creating detached worktree")
    return None


def validation_wait_action(command_text: str, source_kind: str) -> SlopLogAction:
    summarized = textwrap.shorten(" ".join(command_text.split()), width=88, placeholder="...")
    if source_kind == "intent":
        reason = f"running validation intent {summarized}"
        phrase = "Slopcessing"
    else:
        reason = f"running validation command {summarized}"
        phrase = "Slopcrunching"
    return slop_log_action("validate-run", "PROCESS", phrase, reason)

