#!/usr/bin/env python3
from __future__ import annotations

import random
import re
from typing import Any

DEFAULT_MAX_TASK_RETRIES = 3
BACKOFF_BASE_SEC = 1.0
BACKOFF_CAP_SEC = 30.0
BACKOFF_JITTER_FACTOR = 0.1

_TRANSIENT_PATTERNS = [
    re.compile(p, re.IGNORECASE)
    for p in [
        r"rate.?limit",
        r"\b429\b",
        r"too many requests",
        r"timed?.?out",
        r"\b503\b",
        r"\b502\b",
        r"overload",
        r"APITimeoutError",
        r"RateLimitError",
        r"InternalServerError",
        r"connection (reset|refused|error)",
        r"network error",
        r"temporarily unavailable",
    ]
]

_PERMANENT_PATTERNS = [
    re.compile(p, re.IGNORECASE)
    for p in [
        r"write.?scope violation",
        r"protected.?path",
        r"invalid JSON",
        r"JSON parse",
        r"AuthenticationError",
        r"InvalidRequestError",
        r"\b401\b.{0,20}[Uu]nauthorized",
        r"\b403\b.{0,20}[Ff]orbidden",
    ]
]


def classify_failure(record: Any) -> str:
    """Return 'transient' or 'permanent' for a failed task record."""
    if record.status != "failed":
        return "permanent"
    if getattr(record, "write_scope_violations", None):
        return "permanent"
    if getattr(record, "protected_path_violations", None):
        return "permanent"
    if getattr(record, "prompt_budget", None) and record.prompt_budget.exceeded:
        return "permanent"
    combined_text = " ".join(
        filter(None, [record.summary or ""])
    )
    for pat in _PERMANENT_PATTERNS:
        if pat.search(combined_text):
            return "permanent"
    for pat in _TRANSIENT_PATTERNS:
        if pat.search(combined_text):
            return "transient"
    return "permanent"


def backoff_delay_sec(
    attempt: int,
    base_sec: float = BACKOFF_BASE_SEC,
    cap_sec: float = BACKOFF_CAP_SEC,
    jitter_factor: float = BACKOFF_JITTER_FACTOR,
) -> float:
    """Exponential backoff with jitter. attempt is 0-indexed (first retry = 0)."""
    delay = min(base_sec * (2 ** attempt), cap_sec)
    jitter = delay * jitter_factor * random.random()
    return delay + jitter


def resolved_max_retries(
    task: Any,
    agent: Any,
    run_override: int | None = None,
) -> int:
    """Resolve max retries from run override > task > agent > default."""
    if run_override is not None and run_override >= 0:
        return run_override
    task_retries = getattr(task, "max_retries", None)
    if task_retries is not None and task_retries >= 0:
        return task_retries
    agent_retries = getattr(agent, "max_retries", None)
    if agent_retries is not None and agent_retries >= 0:
        return agent_retries
    return DEFAULT_MAX_TASK_RETRIES
