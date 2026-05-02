from __future__ import annotations

import json
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, TextIO

APPROVE_VALUES = {"approve", "approved", "continue", "c", "yes", "y"}
ABORT_VALUES = {"abort", "aborted", "reject", "rejected", "stop", "no", "n"}


@dataclass(frozen=True)
class HitlPolicy:
    enabled: bool
    mode: str
    auto_approve: bool = False


@dataclass(frozen=True)
class HitlGateContext:
    gate_id: str
    mode: str
    batch_index: int
    completed_batch_task_ids: list[str]
    failed_task_ids: list[str]
    pending_task_ids: list[str]
    run_dir: Path
    dry_run: bool


@dataclass(frozen=True)
class HitlDecision:
    approved: bool
    action: str
    reason: str
    source: str
    sentinel_path: str


def resolve_hitl_policy(
    run_policy: Any,
    *,
    hitl_override: bool = False,
    hitl_mode_override: str | None = None,
    hitl_auto_approve: bool = False,
    default_cli_mode: str = "batch",
) -> HitlPolicy:
    policy_enabled = bool(getattr(run_policy, "hitl", False))
    policy_mode = str(getattr(run_policy, "hitl_mode", "none") or "none")
    if hitl_override:
        policy_enabled = True
        policy_mode = hitl_mode_override or default_cli_mode
    elif hitl_mode_override:
        policy_enabled = True
        policy_mode = hitl_mode_override
    if not policy_enabled:
        policy_mode = "none"
    elif policy_mode == "none":
        policy_mode = default_cli_mode
    return HitlPolicy(
        enabled=policy_enabled,
        mode=policy_mode,
        auto_approve=bool(hitl_auto_approve),
    )


def should_trigger_hitl_gate(
    policy: HitlPolicy,
    *,
    batch_index: int,
    failed_task_ids: list[str],
) -> bool:
    if not policy.enabled or policy.mode == "none":
        return False
    if policy.mode == "batch":
        return True
    if policy.mode == "on-failure":
        return bool(failed_task_ids)
    if policy.mode == "always":
        return batch_index == 1
    return False


def write_hitl_sentinel(context: HitlGateContext, *, write_text: Callable[[Path, str], None] | None = None) -> Path:
    sentinel_path = context.run_dir / "hitl-gate.lock"
    payload = {
        "gateId": context.gate_id,
        "mode": context.mode,
        "batchIndex": context.batch_index,
        "completedBatchTaskIds": context.completed_batch_task_ids,
        "failedTaskIds": context.failed_task_ids,
        "pendingTaskIds": context.pending_task_ids,
        "instructions": (
            "Edit this file to contain 'approve' or 'abort'. "
            "Interactive runs can also answer the terminal prompt."
        ),
    }
    text = json.dumps(payload, indent=2)
    if write_text is not None:
        write_text(sentinel_path, text)
    else:
        sentinel_path.write_text(text, encoding="utf-8")
    return sentinel_path


def _sentinel_action(sentinel_path: Path) -> str | None:
    try:
        text = sentinel_path.read_text(encoding="utf-8").strip().lower()
    except OSError:
        return None
    if text in APPROVE_VALUES:
        return "approve"
    if text in ABORT_VALUES:
        return "abort"
    return None


def wait_for_hitl_decision(
    context: HitlGateContext,
    *,
    auto_approve: bool = False,
    input_stream: TextIO | None = None,
    output_stream: TextIO | None = None,
    poll_interval_sec: float = 1.0,
    write_text: Callable[[Path, str], None] | None = None,
    sleep: Callable[[float], None] = time.sleep,
) -> HitlDecision:
    sentinel_path = write_hitl_sentinel(context, write_text=write_text)
    sentinel = str(sentinel_path)
    if auto_approve:
        return HitlDecision(
            approved=True,
            action="approve",
            reason="HITL gate auto-approved by operator flag.",
            source="auto",
            sentinel_path=sentinel,
        )

    input_stream = input_stream or sys.stdin
    output_stream = output_stream or sys.stderr
    if input_stream.isatty():
        print(
            (
                f"[HITL] Gate {context.gate_id} after batch {context.batch_index}. "
                f"Tasks: {', '.join(context.completed_batch_task_ids) or '(none)'}. "
                "Type 'approve' to continue or 'abort' to stop."
            ),
            file=output_stream,
        )
        while True:
            response = input_stream.readline()
            if response == "":
                break
            normalized = response.strip().lower()
            if normalized in APPROVE_VALUES:
                return HitlDecision(True, "approve", "Operator approved continuation.", "stdin", sentinel)
            if normalized in ABORT_VALUES:
                return HitlDecision(False, "abort", "Operator aborted continuation.", "stdin", sentinel)
            print("[HITL] Expected 'approve' or 'abort'.", file=output_stream)

    print(
        (
            f"[HITL] Waiting for {sentinel_path}. "
            "Replace its contents with 'approve' or 'abort'."
        ),
        file=output_stream,
    )
    while True:
        action = _sentinel_action(sentinel_path)
        if action == "approve":
            return HitlDecision(True, "approve", "Operator approved via sentinel file.", "sentinel", sentinel)
        if action == "abort":
            return HitlDecision(False, "abort", "Operator aborted via sentinel file.", "sentinel", sentinel)
        sleep(poll_interval_sec)

