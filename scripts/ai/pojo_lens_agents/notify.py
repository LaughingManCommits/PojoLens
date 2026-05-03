from __future__ import annotations

import json
import urllib.request
from typing import Any, Callable


def build_notification_payload(run_payload: dict[str, Any]) -> dict[str, Any]:
    run_id = run_payload.get("runId") or ""
    status_counts = run_payload.get("statusCounts") or {}
    usage_totals = run_payload.get("usageTotals") or {}

    completed = int(status_counts.get("completed", 0) or 0)
    failed = int(status_counts.get("failed", 0) or 0)
    blocked = int(status_counts.get("blocked", 0) or 0)

    if failed:
        overall = "failed"
    elif blocked:
        overall = "blocked"
    else:
        overall = "completed"

    summary_parts = [f"{completed} completed"]
    if failed:
        summary_parts.append(f"{failed} failed")
    if blocked:
        summary_parts.append(f"{blocked} blocked")
    summary = ", ".join(summary_parts)

    result: dict[str, Any] = {
        "runId": run_id,
        "status": overall,
        "summary": summary,
        "taskCounts": {"completed": completed, "failed": failed, "blocked": blocked},
    }

    total_cost = usage_totals.get("totalCostUsd")
    if total_cost is not None:
        result["totalCostUsd"] = float(total_cost)

    return result


def notify_desktop(
    payload: dict[str, Any],
    *,
    _notify_fn: Callable[..., None] | None = None,
) -> None:
    status = payload.get("status", "finished")
    title = f"Run {status.title()}"
    message = payload.get("summary", "Run complete")
    run_id = payload.get("runId", "")
    if run_id:
        message = f"{message} [{run_id[:8]}]"

    if _notify_fn is not None:
        _notify_fn(title=title, message=message)
        return

    try:
        from plyer import notification  # type: ignore[import]
        notification.notify(title=title, message=message, app_name="pojolens-agents", timeout=10)
    except Exception:
        pass


def notify_webhook(
    url: str,
    payload: dict[str, Any],
    *,
    _urlopen_fn: Callable[..., Any] | None = None,
) -> list[str]:
    errors: list[str] = []
    try:
        body = json.dumps(payload).encode()
        req = urllib.request.Request(
            url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        open_fn = _urlopen_fn or urllib.request.urlopen
        with open_fn(req, timeout=15) as resp:
            resp.read()
    except Exception as exc:
        errors.append(f"webhook: {exc}")
    return errors


def notify_slack(
    url: str,
    payload: dict[str, Any],
    *,
    _urlopen_fn: Callable[..., Any] | None = None,
) -> list[str]:
    errors: list[str] = []
    status = payload.get("status", "completed")
    color = "#2eb67d" if status == "completed" else "#e01e5a"
    summary = payload.get("summary", "Run complete")
    run_id = payload.get("runId", "")

    title = f"Run {status.title()}"
    if run_id:
        title += f"  [{run_id[:8]}]"

    fields = []
    task_counts = payload.get("taskCounts") or {}
    for label, key in [("Completed", "completed"), ("Failed", "failed"), ("Blocked", "blocked")]:
        val = task_counts.get(key, 0)
        if val or key == "completed":
            fields.append({"type": "mrkdwn", "text": f"*{label}:* {val}"})
    if "totalCostUsd" in payload:
        fields.append({"type": "mrkdwn", "text": f"*Cost:* ${payload['totalCostUsd']:.4f}"})

    blocks: list[dict[str, Any]] = [
        {"type": "header", "text": {"type": "plain_text", "text": title}},
        {"type": "section", "text": {"type": "mrkdwn", "text": summary}},
    ]
    if fields:
        blocks.append({"type": "section", "fields": fields})

    slack_body: dict[str, Any] = {
        "blocks": blocks,
        "attachments": [{"color": color, "fallback": summary}],
    }

    try:
        body = json.dumps(slack_body).encode()
        req = urllib.request.Request(
            url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        open_fn = _urlopen_fn or urllib.request.urlopen
        with open_fn(req, timeout=15) as resp:
            resp.read()
    except Exception as exc:
        errors.append(f"slack: {exc}")
    return errors


def dispatch_notifications(
    run_payload: dict[str, Any],
    config: dict[str, Any],
    *,
    force_desktop: bool = False,
    _desktop_fn: Callable[[dict[str, Any]], None] | None = None,
    _webhook_fn: Callable[[str, dict[str, Any]], list[str]] | None = None,
    _slack_fn: Callable[[str, dict[str, Any]], list[str]] | None = None,
) -> list[str]:
    notification_payload = build_notification_payload(run_payload)
    status = notification_payload.get("status", "completed")

    notify_on_raw = config.get("notify_on")
    notify_on: list[str] = notify_on_raw if notify_on_raw is not None else ["always"]
    should_notify = (
        "always" in notify_on
        or (status == "completed" and "success" in notify_on)
        or (status != "completed" and "failure" in notify_on)
    )
    if not should_notify:
        return []

    errors: list[str] = []

    if force_desktop or config.get("desktop"):
        fn = _desktop_fn if _desktop_fn is not None else notify_desktop
        try:
            fn(notification_payload)
        except Exception as exc:
            errors.append(f"desktop: {exc}")

    webhook_url = config.get("webhook_url", "")
    if webhook_url:
        fn_w = _webhook_fn if _webhook_fn is not None else lambda url, p: notify_webhook(url, p)
        errors.extend(fn_w(webhook_url, notification_payload))

    slack_url = config.get("slack_webhook_url", "")
    if slack_url:
        fn_s = _slack_fn if _slack_fn is not None else lambda url, p: notify_slack(url, p)
        errors.extend(fn_s(slack_url, notification_payload))

    return errors
