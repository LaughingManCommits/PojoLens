from __future__ import annotations

import subprocess
import time
import json
import re
from json import JSONDecodeError
from pathlib import Path
from typing import Any, Callable


class ProviderExecutionError(RuntimeError):
    """Raised when provider or validation subprocess execution fails locally."""


def run_process(
    command: list[str] | str,
    *,
    cwd: Path,
    timeout_sec: int,
    shell: bool,
    timeout_error: str,
    on_progress_frame: Callable[[int], None] | None = None,
    progress_interval_sec: float = 2.0,
    error_factory: Callable[[str], Exception] = ProviderExecutionError,
) -> subprocess.CompletedProcess[str]:
    process = subprocess.Popen(
        command,
        cwd=cwd,
        shell=shell,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    frame = 0
    if on_progress_frame is not None:
        on_progress_frame(frame)
    deadline = time.monotonic() + timeout_sec
    stdout = ""
    stderr = ""
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise subprocess.TimeoutExpired(process.args, timeout_sec)
            try:
                stdout, stderr = process.communicate(timeout=min(progress_interval_sec, remaining))
                break
            except subprocess.TimeoutExpired:
                if on_progress_frame is None:
                    continue
                frame += 1
                on_progress_frame(frame)
        return subprocess.CompletedProcess(process.args, process.returncode, stdout, stderr)
    except subprocess.TimeoutExpired as exc:
        process.kill()
        process.communicate()
        raise error_factory(timeout_error) from exc


def run_subprocess(
    command: list[str],
    *,
    cwd: Path,
    timeout_sec: int,
    timeout_error: str,
    on_progress_frame: Callable[[int], None] | None = None,
    progress_interval_sec: float = 2.0,
    error_factory: Callable[[str], Exception] = ProviderExecutionError,
) -> subprocess.CompletedProcess[str]:
    return run_process(
        command,
        cwd=cwd,
        timeout_sec=timeout_sec,
        shell=False,
        timeout_error=timeout_error,
        on_progress_frame=on_progress_frame,
        progress_interval_sec=progress_interval_sec,
        error_factory=error_factory,
    )


def extract_json_payload(
    text: str,
    *,
    error_factory: Callable[[str], Exception] = ProviderExecutionError,
) -> Any:
    stripped = text.strip()
    if not stripped:
        raise error_factory("Claude returned empty output")
    decoder = json.JSONDecoder()
    for match in re.finditer(r"[{\[]", stripped):
        try:
            payload, end = decoder.raw_decode(stripped[match.start() :])
        except JSONDecodeError:
            continue
        if not stripped[match.start() + end :].strip():
            return payload
    raise error_factory("Claude output did not contain a standalone JSON payload")


def extract_usage(payload: Any) -> dict[str, Any] | None:
    if not isinstance(payload, dict):
        return None
    usage = payload.get("usage")
    model_usage = payload.get("modelUsage")
    total_cost = payload.get("total_cost_usd")
    if usage is None and model_usage is None and total_cost is None:
        return None
    summary: dict[str, Any] = {
        "inputTokens": int(usage.get("input_tokens", 0)) if isinstance(usage, dict) else 0,
        "outputTokens": int(usage.get("output_tokens", 0)) if isinstance(usage, dict) else 0,
        "cacheReadInputTokens": int(usage.get("cache_read_input_tokens", 0)) if isinstance(usage, dict) else 0,
        "cacheCreationInputTokens": int(usage.get("cache_creation_input_tokens", 0))
        if isinstance(usage, dict)
        else 0,
        "serviceTier": usage.get("service_tier") if isinstance(usage, dict) else None,
        "durationMs": payload.get("duration_ms"),
        "durationApiMs": payload.get("duration_api_ms"),
        "numTurns": payload.get("num_turns"),
        "stopReason": payload.get("stop_reason"),
        "isError": payload.get("is_error"),
        "totalCostUsd": float(total_cost) if isinstance(total_cost, (int, float)) else None,
        "modelUsage": model_usage if isinstance(model_usage, dict) else {},
    }
    return summary
