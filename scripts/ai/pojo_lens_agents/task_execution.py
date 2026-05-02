from __future__ import annotations

from pathlib import Path
from typing import Any


def effective_workspace_mode(task: Any, agent: Any) -> str:
    return task.workspace_mode or agent.workspace_mode


def prepare_workspace(
    plan: Any,
    task: Any,
    workspace_mode: str,
    workspace_path: Path,
    runtime_root: Path,
    dependency_records: dict[str, Any],
    *,
    deps: dict[str, Any],
) -> Any:
    _ = runtime_root
    if workspace_mode == "repo":
        return deps["workspace_preparation_result_factory"](deps["root"], [])
    if workspace_path.exists():
        deps["shutil"].rmtree(workspace_path)
    workspace_path.parent.mkdir(parents=True, exist_ok=True)
    action = deps["workspace_prep_action"](task, workspace_mode)
    if action is not None:
        deps["emit_slop_log"](action)
    if workspace_mode == "copy":
        hydration = deps["analyze_copy_hydration_inputs"](plan, task)
        hydration_issues: list[str] = []
        if hydration["missingReadPaths"]:
            hydration_issues.append(
                "copy readPaths are missing from the repo: "
                f"{deps['summarize_paths'](hydration['missingReadPaths'])}"
            )
        if hydration["directoryReadPaths"]:
            hydration_issues.append(
                "copy readPaths must be concrete files, not directories: "
                f"{deps['summarize_paths'](hydration['directoryReadPaths'])}"
            )
        if hydration["oversizedPaths"]:
            hydration_issues.append(
                "copy workspace inputs exceed the hydration size limit: "
                f"{deps['summarize_paths'](hydration['oversizedPaths'])}"
            )
        if hydration_issues:
            raise deps["error_factory"](
                deps["format_issue_block"](f"{task.id}: invalid copy workspace inputs", hydration_issues)
            )
        deps["hydrate_copy_workspace"](workspace_path, hydration["filesToCopy"])
        return deps["workspace_preparation_result_factory"](
            workspace_path,
            deps["materialize_dependency_layers"](
                task,
                dependency_records,
                workspace_root=workspace_path,
            ),
        )
    if workspace_mode == "worktree":
        deps["ensure_clean_for_worktrees"]()
        completed = deps["subprocess"].run(
            ["git", "worktree", "add", "--detach", str(workspace_path), "HEAD"],
            cwd=deps["root"],
            capture_output=True,
            text=True,
            check=False,
        )
        if completed.returncode != 0:
            raise deps["error_factory"](
                f"{task.id}: failed to create worktree: {completed.stderr.strip() or completed.stdout.strip()}"
            )
        return deps["workspace_preparation_result_factory"](
            workspace_path,
            deps["materialize_dependency_layers"](
                task,
                dependency_records,
                workspace_root=workspace_path,
            ),
        )
    raise deps["error_factory"](f"{task.id}: unsupported workspace mode '{workspace_mode}'")


def blocked_record(
    task: Any,
    agent_name: str,
    agent: Any,
    workspace_mode: str,
    *,
    reason: str,
    dependency_records: dict[str, Any] | None = None,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    deps: dict[str, Any],
) -> Any:
    now = deps["iso_now"]()
    validation_resolution = deps["resolve_worker_validation_mode"](
        task,
        agent,
        run_override=worker_validation_mode,
    )
    resolved_effort, effort_source = deps["resolve_effort"](task, agent, run_override=effort_override)
    dependency_materialization_mode = deps["effective_dependency_materialization_mode"](task)
    return deps["task_run_record_factory"](
        id=task.id,
        title=task.title,
        agent=agent_name,
        resolved_skills=deps["effective_task_skills"](task, agent),
        branch_context_id=deps["task_branch_context_id"](task, dependency_records),
        branch_parent_context_ids=deps["task_branch_parent_context_ids"](task, dependency_records),
        status="blocked",
        summary=reason,
        workspace_mode=workspace_mode,
        workspace_path="",
        started_at=now,
        finished_at=now,
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
        write_scope_violations=[],
        validation_commands=[],
        follow_ups=[reason],
        notes=[],
        model=deps["resolved_model"](task, agent),
        model_profile=deps["resolved_model_profile"](task, agent),
        prompt_chars=0,
        prompt_estimated_tokens=0,
        prompt_sections=[],
        prompt_budget=deps["prompt_budget_result_factory"](
            max_chars=deps["resolved_max_prompt_chars"](task, agent),
            max_estimated_tokens=deps["resolved_max_prompt_estimated_tokens"](task, agent),
            exceeded=False,
            violations=[],
        ),
        usage=None,
        return_code=None,
        prompt_path="",
        command_path="",
        stdout_path=None,
        stderr_path=None,
        result_path=None,
        dependency_materialization_mode=dependency_materialization_mode,
        worker_validation_mode=validation_resolution.mode,
        worker_validation_mode_source=validation_resolution.source,
        effort=resolved_effort,
        effort_source=effort_source,
    )


def planned_record(
    task: Any,
    agent_name: str,
    agent: Any,
    workspace_mode: str,
    workspace_path: str,
    *,
    summary: str,
    dependency_records: dict[str, Any] | None = None,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    deps: dict[str, Any],
) -> Any:
    now = deps["iso_now"]()
    validation_resolution = deps["resolve_worker_validation_mode"](
        task,
        agent,
        run_override=worker_validation_mode,
    )
    resolved_effort, effort_source = deps["resolve_effort"](task, agent, run_override=effort_override)
    dependency_materialization_mode = deps["effective_dependency_materialization_mode"](task)
    return deps["task_run_record_factory"](
        id=task.id,
        title=task.title,
        agent=agent_name,
        resolved_skills=deps["effective_task_skills"](task, agent),
        branch_context_id=deps["task_branch_context_id"](task, dependency_records),
        branch_parent_context_ids=deps["task_branch_parent_context_ids"](task, dependency_records),
        status="planned",
        summary=summary,
        workspace_mode=workspace_mode,
        workspace_path=workspace_path,
        started_at=now,
        finished_at=now,
        files_touched=[],
        actual_files_touched=[],
        protected_path_violations=[],
        write_scope_violations=[],
        validation_commands=[],
        follow_ups=[],
        notes=[],
        model=deps["resolved_model"](task, agent),
        model_profile=deps["resolved_model_profile"](task, agent),
        prompt_chars=0,
        prompt_estimated_tokens=0,
        prompt_sections=[],
        prompt_budget=deps["prompt_budget_result_factory"](
            max_chars=None,
            max_estimated_tokens=None,
            exceeded=False,
            violations=[],
        ),
        usage=None,
        return_code=None,
        prompt_path="",
        command_path="",
        stdout_path=None,
        stderr_path=None,
        result_path=None,
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=[],
        worker_validation_mode=validation_resolution.mode,
        worker_validation_mode_source=validation_resolution.source,
        effort=resolved_effort,
        effort_source=effort_source,
    )


def make_execute_record(
    task: Any,
    agent: Any,
    workspace_mode: str,
    prepared_workspace: Path,
    started_at: str,
    model_name: str | None,
    model_profile: str | None,
    prompt_chars: int,
    prompt_estimated_tokens: int,
    prompt_render: Any,
    prompt_budget: Any,
    prompt_path: Path,
    command_path: Path,
    dependency_materialization_mode: str,
    prepared_dependency_layers: list[Any],
    dependency_records: dict[str, Any],
    effort: str | None,
    effort_source: str,
    effective_validation_mode: str,
    validation_resolution: Any,
    *,
    status: str,
    summary: str,
    files_touched: list[str] = (),
    validation_intents: list[Any] = (),
    validation_commands: list[str] = (),
    follow_ups: list[str] = (),
    notes: list[str] = (),
    reviewer_findings: list[Any] = (),
    usage: dict[str, Any] | None = None,
    return_code: int | None = None,
    stdout_path: str | None = None,
    stderr_path: str | None = None,
    result_path: str | None = None,
    stdout_bytes: int = 0,
    stderr_bytes: int = 0,
    result_bytes: int = 0,
    unknown_fields: list[str] = (),
    deps: dict[str, Any],
) -> Any:
    return deps["task_run_record_factory"](
        id=task.id,
        title=task.title,
        agent=task.agent,
        resolved_skills=deps["effective_task_skills"](task, agent),
        branch_context_id=deps["task_branch_context_id"](task, dependency_records),
        branch_parent_context_ids=deps["task_branch_parent_context_ids"](task, dependency_records),
        status=status,
        summary=summary,
        workspace_mode=workspace_mode,
        workspace_path=str(prepared_workspace),
        started_at=started_at,
        finished_at=deps["iso_now"](),
        files_touched=list(files_touched),
        actual_files_touched=[],
        protected_path_violations=[],
        write_scope_violations=[],
        validation_intents=list(validation_intents),
        validation_commands=list(validation_commands),
        follow_ups=list(follow_ups),
        notes=list(notes),
        reviewer_findings=list(reviewer_findings),
        model=model_name,
        model_profile=model_profile,
        prompt_chars=prompt_chars,
        prompt_estimated_tokens=prompt_estimated_tokens,
        prompt_sections=prompt_render.sections,
        prompt_budget=prompt_budget,
        usage=usage,
        return_code=return_code,
        prompt_path=str(prompt_path),
        command_path=str(command_path),
        stdout_path=stdout_path,
        stderr_path=stderr_path,
        result_path=result_path,
        stdout_bytes=stdout_bytes,
        stderr_bytes=stderr_bytes,
        result_bytes=result_bytes,
        unknown_fields=list(unknown_fields),
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=prepared_dependency_layers,
        worker_validation_mode=effective_validation_mode,
        worker_validation_mode_source=validation_resolution.source,
        effort=effort,
        effort_source=effort_source,
    )


def execute_task(
    run_dir: Path,
    runtime_root: Path,
    workspaces_dir: Path,
    plan: Any,
    agents: dict[str, Any],
    task: Any,
    dependency_records: dict[str, Any],
    *,
    claude_bin: str,
    agents_json: str,
    dry_run: bool,
    worker_validation_mode: str | None = None,
    effort_override: str | None = None,
    deps: dict[str, Any],
) -> Any:
    agent = agents[task.agent]
    validation_resolution = deps["resolve_worker_validation_mode"](
        task,
        agent,
        run_override=worker_validation_mode,
    )
    effective_validation_mode = validation_resolution.mode
    resolved_effort, effort_source = deps["resolve_effort"](
        task,
        agent,
        run_override=effort_override,
    )
    dependency_materialization_mode = deps["effective_dependency_materialization_mode"](task)
    model_name = deps["resolved_model"](task, agent)
    model_profile = deps["resolved_model_profile"](task, agent)
    workspace_mode = deps["effective_workspace_mode"](task, agent)
    declared_write_scope = deps["effective_task_write_scope"](task)
    workspace_path = workspaces_dir / task.id
    task_dir = run_dir / "tasks" / task.id
    task_dir.mkdir(parents=True, exist_ok=True)
    prepared_workspace = deps["root"] if workspace_mode == "repo" else workspace_path
    prepared_dependency_layers: list[Any] = []
    if not dry_run:
        preparation = deps["prepare_workspace"](
            plan,
            task,
            workspace_mode,
            workspace_path,
            runtime_root,
            dependency_records,
        )
        prepared_workspace = preparation.workspace_path
        prepared_dependency_layers = preparation.dependency_layers_applied
    prompt_render = deps["worker_prompt"](
        plan,
        task,
        agent,
        workspace_mode,
        prepared_workspace,
        deps["dependency_summary"](dependency_records, task),
        dependency_materialization_mode=dependency_materialization_mode,
        dependency_layers_applied=prepared_dependency_layers,
        dry_run=dry_run,
        worker_validation_mode=effective_validation_mode,
    )
    prompt = prompt_render.text
    prompt_chars = prompt_render.chars
    prompt_estimated_tokens = prompt_render.estimated_tokens
    prompt_budget = deps["evaluate_prompt_budget"](
        prompt_chars=prompt_chars,
        prompt_estimated_tokens=prompt_estimated_tokens,
        max_chars=deps["resolved_max_prompt_chars"](task, agent),
        max_estimated_tokens=deps["resolved_max_prompt_estimated_tokens"](task, agent),
    )
    task_allowed, task_disallowed = deps["effective_tool_lists"](task, agent)
    command = deps["claude_command"](
        claude_bin,
        agents_json,
        task.agent,
        prompt,
        deps["task_output_schema_json"](effective_validation_mode),
        model=model_name,
        effort=resolved_effort,
        permission_mode=task.permission_mode or agent.permission_mode,
        allowed_tools=task_allowed,
        disallowed_tools=task_disallowed,
        max_budget_usd=task.max_budget_usd if task.max_budget_usd is not None else agent.max_budget_usd,
    )
    prompt_path = task_dir / "prompt.txt"
    command_path = task_dir / "command.json"
    deps["write_text"](prompt_path, prompt)
    deps["write_json"](command_path, {"cwd": str(prepared_workspace), "command": command})
    started_at = deps["iso_now"]()
    result_path = task_dir / "result.json"
    make_record = lambda **kwargs: make_execute_record(  # noqa: E731
        task,
        agent,
        workspace_mode,
        prepared_workspace,
        started_at,
        model_name,
        model_profile,
        prompt_chars,
        prompt_estimated_tokens,
        prompt_render,
        prompt_budget,
        prompt_path,
        command_path,
        dependency_materialization_mode,
        prepared_dependency_layers,
        dependency_records,
        resolved_effort,
        effort_source,
        effective_validation_mode,
        validation_resolution,
        deps=deps,
        **kwargs,
    )
    if prompt_budget.exceeded:
        record = make_record(
            status="failed",
            summary=deps["prompt_budget_failure_summary"](prompt_budget),
            follow_ups=["Reduce the task prompt scope, dependency summary, or validation hints."],
        )
        deps["write_json"](result_path, deps["asdict"](record))
        return record
    if dry_run:
        record = make_record(status="planned", summary="Dry run only; Claude was not invoked.")
        deps["write_json"](result_path, deps["asdict"](record))
        return record
    stdout_path = task_dir / "stdout.json"
    stderr_path = task_dir / "stderr.txt"
    worker_result_path = task_dir / "worker-result.json"
    return_code: int | None = None
    usage: dict[str, Any] | None = None
    workspace_snapshot_before = deps["snapshot_workspace_files"](prepared_workspace)
    repo_snapshot_before = deps["snapshot_workspace_files"](deps["root"]) if workspace_mode != "repo" else {}
    actual_changed_files: list[str] = []
    changed_repo_files: list[str] = []
    try:
        completed = deps["run_subprocess"](
            command,
            cwd=prepared_workspace,
            timeout_sec=task.timeout_sec or agent.timeout_sec,
            progress_action=deps["task_wait_action"](task),
        )
        return_code = completed.returncode
        deps["write_text"](stdout_path, completed.stdout)
        deps["write_text"](stderr_path, completed.stderr)
        actual_changed_files = deps["diff_workspace_snapshots"](
            workspace_snapshot_before,
            deps["snapshot_workspace_files"](prepared_workspace),
        )
        if workspace_mode != "repo":
            changed_repo_files = deps["diff_workspace_snapshots"](
                repo_snapshot_before,
                deps["snapshot_workspace_files"](deps["root"]),
            )
        raw_payload: Any | None = None
        try:
            raw_payload = deps["extract_json_payload"](completed.stdout)
            usage = deps["extract_usage"](raw_payload)
        except deps["error_factory"]:
            raw_payload = None
        if completed.returncode != 0:
            record = make_record(
                status="failed",
                summary=completed.stderr.strip() or completed.stdout.strip() or "Claude failed",
                follow_ups=["Inspect stderr/stdout artifacts for details."],
                usage=usage,
                return_code=return_code,
                stdout_path=str(stdout_path),
                stderr_path=str(stderr_path),
                stdout_bytes=deps["artifact_file_size"](stdout_path),
                stderr_bytes=deps["artifact_file_size"](stderr_path),
            )
            deps["apply_workspace_audit"](
                record,
                reported_files=[],
                actual_files=actual_changed_files,
                declared_write_scope=declared_write_scope,
            )
            deps["apply_repository_isolation_audit"](
                record,
                workspace_mode=workspace_mode,
                changed_repo_files=changed_repo_files,
            )
            deps["write_json"](result_path, deps["asdict"](record))
            return record
        if raw_payload is None:
            raise deps["error_factory"]("Claude output did not contain a standalone JSON payload")
        payload = deps["coerce_worker_result"](
            raw_payload,
            worker_validation_mode=effective_validation_mode,
        )
        deps["write_json"](worker_result_path, payload)
        record = make_record(
            status=str(payload["status"]),
            summary=str(payload["summary"]),
            files_touched=[str(item) for item in payload["filesTouched"]],
            validation_intents=[
                deps["coerce_validation_intent_payload"](
                    item,
                    location=f"worker result {task.id}:validationIntents",
                )
                for item in payload.get("validationIntents", [])
            ],
            validation_commands=[str(item) for item in payload["validationCommands"]],
            follow_ups=[str(item) for item in payload["followUps"]],
            notes=[str(item) for item in payload["notes"]],
            reviewer_findings=[
                deps["reviewer_finding_factory"](
                    severity=str(item.get("severity", "")),
                    message=str(item.get("message", "")),
                )
                for item in payload.get("findings", []) or []
                if isinstance(item, dict)
            ],
            usage=usage,
            return_code=return_code,
            stdout_path=str(stdout_path),
            stderr_path=str(stderr_path),
            result_path=str(worker_result_path),
            stdout_bytes=deps["artifact_file_size"](stdout_path),
            stderr_bytes=deps["artifact_file_size"](stderr_path),
            result_bytes=deps["artifact_file_size"](worker_result_path),
            unknown_fields=[str(item) for item in payload.get("unknownFields", [])],
        )
        deps["apply_workspace_audit"](
            record,
            reported_files=[str(item) for item in payload["filesTouched"]],
            actual_files=actual_changed_files,
            declared_write_scope=declared_write_scope,
        )
        deps["apply_repository_isolation_audit"](
            record,
            workspace_mode=workspace_mode,
            changed_repo_files=changed_repo_files,
        )
        deps["write_json"](result_path, deps["asdict"](record))
        return record
    except deps["error_factory"] as exc:
        deps["write_text"](stderr_path, str(exc))
        actual_changed_files = deps["diff_workspace_snapshots"](
            workspace_snapshot_before,
            deps["snapshot_workspace_files"](prepared_workspace),
        )
        if workspace_mode != "repo":
            changed_repo_files = deps["diff_workspace_snapshots"](
                repo_snapshot_before,
                deps["snapshot_workspace_files"](deps["root"]),
            )
        record = make_record(
            status="failed",
            summary=str(exc),
            follow_ups=["Retry after fixing the worker failure."],
            usage=usage,
            return_code=return_code,
            stdout_path=str(stdout_path) if stdout_path.exists() else None,
            stderr_path=str(stderr_path),
            stdout_bytes=deps["artifact_file_size"](stdout_path) if stdout_path.exists() else 0,
            stderr_bytes=deps["artifact_file_size"](stderr_path),
        )
        deps["apply_workspace_audit"](
            record,
            reported_files=[],
            actual_files=actual_changed_files,
            declared_write_scope=declared_write_scope,
        )
        deps["apply_repository_isolation_audit"](
            record,
            workspace_mode=workspace_mode,
            changed_repo_files=changed_repo_files,
        )
        deps["write_json"](result_path, deps["asdict"](record))
        return record
