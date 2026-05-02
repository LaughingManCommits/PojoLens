#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from typing import Any, Callable

from pojo_lens_agents.orchestrator_contracts import (
    EXIT_BLOCKED,
    EXIT_CRASH,
    EXIT_ERROR,
    EXIT_SUCCESS,
    EXIT_UNSAFE_PROMOTION,
    EXIT_WORKER_FAILURE,
    OrchestratorError,
    PromotionBlockedError,
)


def print_payload(payload: dict[str, Any], *, as_json: bool) -> None:
    if as_json:
        print(json.dumps(payload))
    else:
        print(json.dumps(payload, indent=2))


def _worker_run_exit_code(status_counts: dict[str, int]) -> int:
    if status_counts.get("failed", 0) > 0:
        return EXIT_WORKER_FAILURE
    if status_counts.get("blocked", 0) > 0:
        return EXIT_BLOCKED
    return EXIT_SUCCESS


def dispatch_main(args: Any, handlers: dict[str, Callable[[Any], dict[str, Any]]]) -> int:
    try:
        handler = handlers.get(str(args.command))
        if handler is None:
            raise OrchestratorError(f"Unknown command '{args.command}'")
        payload = handler(args)
        print_payload(payload, as_json=bool(getattr(args, "json", False)))
        if args.command in {"run", "resume", "retry", "wizard"}:
            return _worker_run_exit_code(payload.get("statusCounts", {}))
        return EXIT_SUCCESS
    except PromotionBlockedError as exc:
        print(f"[claude-orchestrator] {exc}", file=sys.stderr)
        return EXIT_UNSAFE_PROMOTION
    except OrchestratorError as exc:
        print(f"[claude-orchestrator] {exc}", file=sys.stderr)
        return EXIT_ERROR
    except Exception as exc:
        print(f"[claude-orchestrator] unexpected error: {exc}", file=sys.stderr)
        return EXIT_CRASH
