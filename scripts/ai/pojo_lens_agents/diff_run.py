from __future__ import annotations

import fnmatch
import io
from pathlib import Path, PurePosixPath
from typing import Any, Callable

try:  # pragma: no cover - optional rich console rendering
    from rich.console import Console
    from rich.text import Text
except ImportError:  # pragma: no cover
    Console = None  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]


def split_csv_values(values: list[str] | None, csv_value: str | None) -> list[str]:
    merged: list[str] = []
    for raw in list(values or []):
        text = str(raw or "").strip()
        if text:
            merged.append(text)
    if csv_value:
        for raw in str(csv_value).split(","):
            text = raw.strip()
            if text:
                merged.append(text)
    deduped: list[str] = []
    seen: set[str] = set()
    for item in merged:
        if item in seen:
            continue
        seen.add(item)
        deduped.append(item)
    return deduped


def path_matches_filters(relative_path: str, path_filters: list[str]) -> bool:
    if not path_filters:
        return True
    normalized = PurePosixPath(str(relative_path).replace("\\", "/")).as_posix()
    path_obj = PurePosixPath(normalized)
    for pattern in path_filters:
        normalized_pattern = PurePosixPath(str(pattern).replace("\\", "/")).as_posix()
        if fnmatch.fnmatch(normalized, normalized_pattern):
            return True
        if path_obj.match(normalized_pattern):
            return True
    return False


def _style_diff_line(line: str) -> Any:
    if Text is None:
        return line
    if line.startswith("+++ ") or line.startswith("--- "):
        return Text(line, style="bold")
    if line.startswith("@@"):
        return Text(line, style="cyan")
    if line.startswith("+"):
        return Text(line, style="green")
    if line.startswith("-"):
        return Text(line, style="red")
    return Text(line, style="dim")


def _render_rich_block(lines: list[str]) -> str:
    if Console is None or Text is None:
        return "\n".join(lines).rstrip() + "\n"
    buffer = io.StringIO()
    console = Console(file=buffer, record=True, force_terminal=True, color_system="truecolor")
    for line in lines:
        console.print(_style_diff_line(line))
    return console.export_text(styles=True)


def build_console_text(
    payload: dict[str, Any],
    *,
    stat_only: bool,
) -> str:
    lines: list[str] = []
    lines.append(
        f"Run {payload.get('runId')}: "
        f"{payload.get('summary', {}).get('changedFileCount', 0)} files changed, "
        f"+{payload.get('summary', {}).get('addedLines', 0)} "
        f"-{payload.get('summary', {}).get('removedLines', 0)}"
    )
    if payload.get("summary", {}).get("taskCount", 0) == 0:
        lines.append("No matching tasks.")
        return "\n".join(lines).rstrip() + "\n"
    for task in payload.get("tasks", []):
        lines.append("")
        lines.append(
            f"[{task.get('id')}] {task.get('title')} | "
            f"{task.get('diffStats', {}).get('filesChanged', 0)} files changed | "
            f"+{task.get('diffStats', {}).get('addedLines', 0)} "
            f"-{task.get('diffStats', {}).get('removedLines', 0)}"
        )
        task_error = str(task.get("error", "") or "").strip()
        if task_error:
            lines.append(f"  error: {task_error}")
            continue
        for file_payload in task.get("files", []):
            if stat_only:
                lines.append(
                    f"  {file_payload.get('path')} | {file_payload.get('status')} | "
                    f"+{file_payload.get('addedLines', 0)} -{file_payload.get('removedLines', 0)}"
                )
                continue
            unified_diff = str(file_payload.get("unifiedDiff", "") or "")
            if unified_diff:
                lines.extend(unified_diff.rstrip("\n").splitlines())
            elif file_payload.get("isBinary"):
                lines.append(f"Binary file changed: {file_payload.get('path')}")
    return _render_rich_block(lines)


def diff_run(
    *,
    manifest_path: Path,
    manifest: dict[str, Any],
    records: list[Any],
    context_lines: int,
    stat_only: bool,
    path_filters: list[str],
    dedupe_strings: Callable[[list[str]], list[str]],
    diff_file_against_workspace_fn: Callable[..., tuple[dict[str, Any], str | None]],
) -> dict[str, Any]:
    task_payloads: list[dict[str, Any]] = []
    total_changed_files = 0
    total_binary_files = 0
    total_added_lines = 0
    total_removed_lines = 0
    missing_workspace_tasks = 0
    filtered_path_count = 0
    for record in records:
        reviewed_paths = dedupe_strings(list(record.actual_files_touched or record.files_touched or []))
        matching_paths = [path for path in reviewed_paths if path_matches_filters(path, path_filters)]
        filtered_path_count += len(matching_paths)
        file_payloads: list[dict[str, Any]] = []
        task_error = ""
        changed_files = 0
        binary_files = 0
        added_lines = 0
        removed_lines = 0
        try:
            for relative_path in matching_paths:
                summary, patch_text = diff_file_against_workspace_fn(
                    record,
                    relative_path,
                    context_lines=context_lines,
                )
                file_payload = dict(summary)
                file_payload["taskId"] = record.id
                if patch_text and not stat_only:
                    file_payload["unifiedDiff"] = patch_text
                if summary["status"] not in {"unchanged", "missing"}:
                    changed_files += 1
                if bool(summary.get("isBinary")):
                    binary_files += 1
                added_lines += int(summary.get("addedLines", 0) or 0)
                removed_lines += int(summary.get("removedLines", 0) or 0)
                file_payloads.append(file_payload)
        except Exception as exc:
            task_error = str(exc)
            missing_workspace_tasks += 1
        total_changed_files += changed_files
        total_binary_files += binary_files
        total_added_lines += added_lines
        total_removed_lines += removed_lines
        task_payloads.append(
            {
                "id": record.id,
                "title": record.title,
                "agent": record.agent,
                "status": record.status,
                "summary": record.summary,
                "workspaceMode": record.workspace_mode,
                "workspacePath": record.workspace_path,
                "filesReported": list(record.files_touched or []),
                "filesObserved": list(record.actual_files_touched or []),
                "selectedPaths": matching_paths,
                "diffStats": {
                    "filesReviewed": len(matching_paths),
                    "filesChanged": changed_files,
                    "binaryFiles": binary_files,
                    "addedLines": added_lines,
                    "removedLines": removed_lines,
                },
                "error": task_error or None,
                "files": file_payloads,
            }
        )
    payload = {
        "runId": manifest.get("runId"),
        "manifestPath": str(manifest_path),
        "runDir": str(manifest_path.parent),
        "stat": bool(stat_only),
        "taskIds": [record.id for record in records],
        "pathFilters": list(path_filters),
        "summary": {
            "taskCount": len(task_payloads),
            "changedTaskCount": sum(1 for task in task_payloads if int(task["diffStats"]["filesChanged"]) > 0),
            "changedFileCount": total_changed_files,
            "binaryFileCount": total_binary_files,
            "addedLines": total_added_lines,
            "removedLines": total_removed_lines,
            "missingWorkspaceTaskCount": missing_workspace_tasks,
            "selectedPathCount": filtered_path_count,
        },
        "tasks": task_payloads,
    }
    payload["_consoleText"] = build_console_text(payload, stat_only=stat_only)
    return payload
