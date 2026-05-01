from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any


def planner_output_path(
    name: str,
    explicit_path: str,
    *,
    default_tasks_dir: Path,
    slugify,
) -> Path:
    if explicit_path:
        return Path(explicit_path).resolve()
    filename = f"generated-{slugify(name)}.json"
    return (default_tasks_dir / filename).resolve()


def planner_prompt(
    goal: str,
    name: str,
    files: list[str],
    constraints: list[str],
    validation: list[str],
    agents: dict[str, Any],
    *,
    planner_task_id: str,
    default_prompt_item_char_limit: int,
    format_bullet_list,
    prompt_section_factory,
    render_prompt,
) -> Any:
    agent_catalog, agent_count, agent_catalog_truncated = format_bullet_list(
        [
            f"`{agent.name}`: {agent.description}"
            for agent in agents.values()
            if agent.name != planner_task_id
        ],
        empty_line="- none supplied",
        max_chars=default_prompt_item_char_limit,
    )
    file_lines, file_count, files_truncated = format_bullet_list(
        files,
        empty_line="- none supplied",
        code_format=True,
    )
    constraint_lines, constraint_count, constraints_truncated = format_bullet_list(
        constraints,
        empty_line="- keep tasks bounded and concrete",
    )
    validation_lines, validation_count, validation_truncated = format_bullet_list(
        validation,
        empty_line="- none supplied",
        code_format=True,
    )
    requirement_lines, requirement_count, requirements_truncated = format_bullet_list(
        [
            "Return schema-valid JSON only. No narration or markdown fences.",
            "Create 1 to 6 tasks.",
            "Prefer the smallest actor set that can finish the work; do not add analyst or reviewer hops by default.",
            "For narrow code changes, prefer a single implementer or implementer->reviewer path. Use a separate analyst only when implementation uncertainty is high, and use a reviewer only when independent review materially lowers risk or the operator asked for it.",
            'Use `workspaceMode="copy"` for most tasks.',
            'Use `workspaceMode="worktree"` only when isolated git metadata matters.',
            'Avoid `workspaceMode="repo"` unless direct in-place execution is essential.',
            'Use `contextMode="minimal"` unless a task really needs the full shared context.',
            '`modelProfile="simple"` fits narrow lookups, doc summaries, and other cheap read-only tasks.',
            '`modelProfile="balanced"` fits planning, coding, analysis, and most review tasks.',
            '`modelProfile="complex"` is the exceptional path; use it only when `simple` or `balanced` are likely insufficient for architecture or unusually deep multi-step reasoning.',
            "Live worker validation suggestions are structured-intent-only; do not plan around raw worker `validationCommands`.",
            "Use `sharedContext.readPaths` for cross-task context, task `readPaths` for task-local context, and task `writePaths` for allowed edits.",
            "Keep `readPaths` repo-relative and concrete. In copy mode they must point at existing files, not directories.",
            "Keep `writePaths` conservative and only as wide as the task needs. Use directory scopes only when multiple sibling edits are intentional.",
            "Use `dependencyMaterialization=\"apply-reviewed\"` only for downstream tasks that truly need reviewed upstream code state inside their workspace; otherwise leave the default summary-only path.",
            "Minimize per-task context and validation hints.",
            "Do not assign `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*` edits to workers.",
            "Put cross-task setup in `sharedContext`.",
            "Add `runPolicy` only when run-level cost or artifact governance is important for the plan.",
            "Use `dependsOn` only when one task genuinely needs another task's output.",
            "Omit speculative tasks when evidence is insufficient.",
        ],
        empty_line="- none",
        max_items=22,
    )
    return render_prompt(
        [
            prompt_section_factory(
                name="planner_role",
                heading="Planner role",
                body="Plan a bounded Claude worker DAG for this repository.",
            ),
            prompt_section_factory(name="goal", heading="Goal", body=goal),
            prompt_section_factory(name="plan_name", heading="Plan name", body=name),
            prompt_section_factory(
                name="available_agents",
                heading="Available worker agents",
                body=agent_catalog,
                item_count=agent_count,
                truncated=agent_catalog_truncated,
            ),
            prompt_section_factory(
                name="file_hints",
                heading="Read hints",
                body=file_lines,
                item_count=file_count,
                truncated=files_truncated,
            ),
            prompt_section_factory(
                name="constraints",
                heading="Constraints",
                body=constraint_lines,
                item_count=constraint_count,
                truncated=constraints_truncated,
            ),
            prompt_section_factory(
                name="validation_hints",
                heading="Validation hints",
                body=validation_lines,
                item_count=validation_count,
                truncated=validation_truncated,
            ),
            prompt_section_factory(
                name="requirements",
                heading="Planning requirements",
                body=requirement_lines,
                item_count=requirement_count,
                truncated=requirements_truncated,
            ),
        ]
    )


def coerce_plan_result(payload: Any, *, error_factory) -> dict[str, Any]:
    required = {"version", "name", "goal", "sharedContext", "tasks"}
    if isinstance(payload, dict) and required.issubset(payload):
        return payload
    if isinstance(payload, dict):
        for key in ("result", "data", "response"):
            nested = payload.get(key)
            if isinstance(nested, dict) and required.issubset(nested):
                return nested
    raise error_factory("Claude JSON output did not match the expected plan schema")


def plan_with_claude(args: argparse.Namespace, *, deps: dict[str, Any]) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    agents = deps["load_agents"](agents_path)
    if args.planner_agent not in agents:
        raise deps["error_factory"](f"Unknown planner agent '{args.planner_agent}'")
    if not args.dry_run:
        deps["ensure_claude_available"](args.claude_bin)
    prompt_render = planner_prompt(
        args.goal,
        args.name,
        args.files,
        args.constraints,
        args.validation,
        agents,
        planner_task_id=deps["planner_task_id"],
        default_prompt_item_char_limit=deps["default_prompt_item_char_limit"],
        format_bullet_list=deps["format_bullet_list"],
        prompt_section_factory=deps["prompt_section_factory"],
        render_prompt=deps["render_prompt"],
    )
    agent = agents[args.planner_agent]
    planner_effort = deps["normalize_effort_override"](
        getattr(args, "effort", None),
        location="planner effort override",
    ) or agent.effort
    prompt_budget = deps["evaluate_prompt_budget"](
        prompt_chars=prompt_render.chars,
        prompt_estimated_tokens=prompt_render.estimated_tokens,
        max_chars=agent.max_prompt_chars,
        max_estimated_tokens=agent.max_prompt_estimated_tokens,
    )
    planner_model = agent.model or (
        deps["model_profile_to_model"][agent.model_profile] if agent.model_profile else None
    )
    agents_json = deps["agent_payload_for_claude"](agents, selected_names=[args.planner_agent])
    output_path = planner_output_path(
        args.name,
        args.out,
        default_tasks_dir=deps["default_tasks_dir"],
        slugify=deps["slugify"],
    )
    command = deps["claude_command"](
        args.claude_bin,
        agents_json,
        args.planner_agent,
        prompt_render.text,
        deps["plan_output_schema_json"](),
        model=planner_model,
        effort=planner_effort,
        permission_mode=agent.permission_mode,
        allowed_tools=agent.allowed_tools,
        disallowed_tools=agent.disallowed_tools,
        max_budget_usd=agent.max_budget_usd,
    )
    payload: dict[str, Any] = {
        "name": args.name,
        "goal": args.goal,
        "plannerAgent": args.planner_agent,
        "agentsPath": str(agents_path),
        "outputPath": str(output_path),
        "prompt": prompt_render.text,
        "promptChars": prompt_render.chars,
        "promptEstimatedTokens": prompt_render.estimated_tokens,
        "promptSections": [deps["asdict"](section) for section in prompt_render.sections],
        "promptBudget": deps["asdict"](prompt_budget),
        "model": planner_model,
        "modelProfile": agent.model_profile,
        "effort": planner_effort,
        "outputPath": str(output_path),
        "dryRun": bool(args.dry_run),
        "command": command,
    }
    if prompt_budget.exceeded:
        payload["status"] = "blocked"
        payload["summary"] = deps["prompt_budget_failure_summary"](prompt_budget)
        return payload
    if args.dry_run:
        payload["status"] = "dry-run"
        payload["summary"] = "Planner prompt and command prepared"
        return payload
    completed = deps["run_subprocess"](
        command,
        cwd=deps["root"],
        timeout_sec=agent.timeout_sec,
        progress_action=deps["planner_wait_action"](args.planner_agent),
    )
    raw_payload = deps["extract_json_payload"](completed.stdout)
    plan_payload = coerce_plan_result(raw_payload, error_factory=deps["error_factory"])
    deps["write_json"](output_path, plan_payload)
    payload.update(
        {
            "status": "written",
            "summary": "Planner output written",
            "taskPlanPath": str(output_path),
        }
    )
    usage = deps["extract_usage"](raw_payload)
    if usage is not None:
        payload["usage"] = usage
    return payload
