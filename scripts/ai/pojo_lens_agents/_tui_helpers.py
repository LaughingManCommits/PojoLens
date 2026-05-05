from __future__ import annotations

import datetime
import json
from pathlib import Path
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from rich.text import Text
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    Text = None  # type: ignore[assignment]

try:
    from pojo_lens_agents.wizard import (
        PlanPreview,
        discover_plan_previews,
        discover_saved_plans,
    )
except ImportError:  # pragma: no cover
    PlanPreview = None  # type: ignore[assignment,misc]
    discover_plan_previews = None  # type: ignore[assignment]
    discover_saved_plans = None  # type: ignore[assignment]


def _fmt_tok(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    if n >= 1_000:
        return f"{n / 1_000:.1f}K"
    return str(n)


def _calc_run_duration(manifest_data: dict[str, Any]) -> str:
    """Return HH:MM:SS duration string from manifest events, or empty string."""
    events = manifest_data.get("events") or []
    if not events:
        return ""
    try:
        t0 = datetime.datetime.fromisoformat(str(events[0].get("ts", "")))
        finished = next(
            (e for e in reversed(events) if e.get("phase") == "run-finished"), None
        )
        t1 = (
            datetime.datetime.fromisoformat(str(finished["ts"]))
            if finished and finished.get("ts")
            else datetime.datetime.now(t0.tzinfo)
        )
        secs = int((t1 - t0).total_seconds())
        return f"{secs // 3600:02d}:{(secs % 3600) // 60:02d}:{secs % 60:02d}"
    except Exception:
        return ""


def _status_color(status: str) -> str:
    return {
        "completed":       "#00ff41",
        "failed":          "#ff2244",
        "blocked":         "#ff2244",
        "running":         "#ffaa00",
        "retry":           "#ffaa00",
        "planned":         "#2a5a3a",
        "pending":         "#2a5a3a",
        "aborted":         "#ff2244",
        "budget_exceeded": "#ffaa00",
        "skipped":         "#2a5a3a",
        "injected":        "#00e5ff",
        "saved":           "#00e5ff",
        "stopped":         "#2a5a3a",
        "dry-run":         "#00e5ff",
    }.get(status, "#a0ffa0")


def _load_plan_json(path: str) -> dict[str, Any] | None:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except Exception:
        return None


def _plan_summary_rich(plan_data: dict[str, Any]) -> list[str]:
    """Rich-markup summary lines for a plan JSON dict."""
    lines: list[str] = []
    name   = str(plan_data.get("name") or plan_data.get("planName") or "unnamed")
    goal   = str(plan_data.get("goal") or "").strip()
    tasks  = list(plan_data.get("tasks") or [])
    rp     = plan_data.get("runPolicy") or {}

    lines.append(f"[bold #00e5ff]Plan   :[/] [#a0ffa0]{name}[/]")
    if goal:
        lines.append(f"[bold #00e5ff]Goal   :[/] [dim]{goal[:120]}[/]")
    lines.append(f"[bold #00e5ff]Tasks  :[/] {len(tasks)}")

    agents = sorted({str(t.get("agent") or "") for t in tasks if t.get("agent")})
    if agents:
        lines.append(f"[bold #00e5ff]Agents :[/] {', '.join(agents)}")

    profiles = sorted({str(t.get("modelProfile") or "") for t in tasks if t.get("modelProfile")})
    if profiles:
        lines.append(f"[bold #00e5ff]Profile:[/] {', '.join(profiles)}")

    ws_modes = sorted({str(t.get("workspaceMode") or "") for t in tasks if t.get("workspaceMode")})
    if ws_modes:
        lines.append(f"[bold #00e5ff]WS Mode:[/] {', '.join(ws_modes)}")

    if rp:
        budget = rp.get("runBudgetUsd")
        hitl   = rp.get("hitlMode")
        if budget is not None:
            lines.append(f"[bold #00e5ff]Budget :[/] [#ffaa00]${budget}[/]")
        if hitl:
            lines.append(f"[bold #00e5ff]HITL   :[/] {hitl}")

    if tasks:
        lines.append("")
        lines.append("[bold #00e5ff]═══ Task Graph ═══[/]")
        for t in tasks[:20]:
            tid   = str(t.get("id") or t.get("taskId") or "?")
            title = str(t.get("title") or t.get("name") or tid)
            deps  = list(t.get("dependencies") or [])
            agent = str(t.get("agent") or "-")
            dep_s = f" [dim]← {', '.join(deps)}[/]" if deps else ""
            lines.append(
                f"  [#00ff41]▸[/] [bold #a0ffa0]{tid}[/]: {title[:50]}"
                f" [[dim]{agent}[/]]{dep_s}"
            )
        if len(tasks) > 20:
            lines.append(f"  [dim]... +{len(tasks) - 20} more tasks[/]")

    # Aggregate read / write paths and skills across all tasks
    all_read: list[str] = []
    all_write: list[str] = []
    all_skills: set[str] = set()
    all_hints: list[str] = []
    for t in tasks:
        all_read.extend(list(t.get("readPaths") or []))
        all_write.extend(list(t.get("writePaths") or []))
        all_skills.update(list(t.get("skills") or []))
        for h in list(t.get("validationHints") or []):
            vh = h if isinstance(h, str) else str(h.get("hint") or h.get("description") or h)
            if vh:
                all_hints.append(vh)
    sc_block = plan_data.get("sharedContext") or {}
    all_read.extend(list(sc_block.get("readPaths") or []))

    if all_read:
        unique_read = sorted(set(all_read))
        lines.append("")
        lines.append("[bold #00e5ff]═══ Read Paths ═══[/]")
        for p in unique_read[:12]:
            lines.append(f"  [dim #00e5ff]→[/] {p}")
        if len(unique_read) > 12:
            lines.append(f"  [dim]+{len(unique_read)-12} more[/]")

    if all_write:
        unique_write = sorted(set(all_write))
        lines.append("")
        lines.append("[bold #00e5ff]═══ Write Paths ═══[/]")
        for p in unique_write[:12]:
            lines.append(f"  [#ffaa00]→[/] {p}")
        if len(unique_write) > 12:
            lines.append(f"  [dim]+{len(unique_write)-12} more[/]")

    if all_skills:
        lines.append("")
        lines.append(f"[bold #00e5ff]Skills:[/] {', '.join(sorted(all_skills))}")
        if len(all_skills) > 4:
            lines.append(f"  [#ffaa00]⚠ {len(all_skills)} skills — check stack limit (5 max)[/]")

    if all_hints:
        lines.append("")
        lines.append("[bold #00e5ff]═══ Validation Hints ═══[/]")
        for h in all_hints[:8]:
            lines.append(f"  [#00ff41]✓[/] {h}")
        if len(all_hints) > 8:
            lines.append(f"  [dim]+{len(all_hints)-8} more hints[/]")

    # Full run policy detail
    rp2 = plan_data.get("runPolicy") or {}
    if rp2:
        lines.append("")
        lines.append("[bold #00e5ff]═══ Run Policy ═══[/]")
        for key2, label2 in [
            ("runBudgetUsd",      "Budget USD"),
            ("budgetBehavior",    "Budget Behavior"),
            ("artifactBehavior",  "Artifact Behavior"),
            ("hitlMode",          "HITL Mode"),
            ("followUpBehavior",  "Follow-Up"),
            ("maxTaskStdoutBytes","Max Stdout"),
            ("maxTaskStderrBytes","Max Stderr"),
            ("maxTaskResultBytes","Max Result"),
        ]:
            val2 = rp2.get(key2)
            if val2 is not None:
                lines.append(f"  [#00e5ff]{label2}:[/] {val2}")

    # Protection reminder
    lines.append("")
    lines.append("[dim #2a5a3a]── Protected paths (workers must not edit) ──[/]")
    for pp in ["TODO.md", "ai/state/*", "ai/log/*", "ai/indexes/*"]:
        lines.append(f"  [dim #ff2244]⚠[/] [dim]{pp}[/]")

    return lines


def _collect_previews(
    runtime_root: Path,
    tasks_dir: Path,
) -> list[Any]:
    previews: list[Any] = []
    try:
        previews.extend(discover_plan_previews(tasks_dir))  # type: ignore[misc]
    except Exception:
        pass
    try:
        previews.extend(discover_saved_plans(runtime_root))  # type: ignore[misc]
    except Exception:
        pass
    return previews
