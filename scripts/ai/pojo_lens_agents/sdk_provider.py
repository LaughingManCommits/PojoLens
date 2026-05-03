"""
Direct Anthropic SDK provider: replaces the claude subprocess for orchestrator
worker execution. Activated when ANTHROPIC_API_KEY is set and
POJO_LENS_PROVIDER is not forced to "subprocess".

Features:
- Agentic tool loop: read_file, write_file, str_replace_based_edit_tool, bash
- Streaming partial text to stderr for interactive runs
- SDK-managed rate-limit handling; exceptions mapped to WP42 retry patterns
- Structured SDK Usage -> existing usage dict shape
"""
from __future__ import annotations

import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

SDK_PROVIDER_DEFAULT_MODEL = "claude-sonnet-4-6"
MAX_TOOL_ITERATIONS = 30
DEFAULT_MAX_TOKENS = 8192
DEFAULT_BASH_TIMEOUT_SEC = 60
MAX_TOOL_OUTPUT_CHARS = 16_000


@dataclass
class SdkProviderResult:
    text: str
    usage: dict[str, Any] | None
    provider_mode: str = "sdk"
    error: str | None = None


# ---------------------------------------------------------------------------
# Workspace tools
# ---------------------------------------------------------------------------

WORKSPACE_TOOLS: list[dict[str, Any]] = [
    {
        "name": "read_file",
        "description": "Read a file from the workspace. Returns the file content.",
        "input_schema": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "File path relative to the workspace root",
                },
            },
            "required": ["path"],
        },
    },
    {
        "name": "write_file",
        "description": "Write content to a file in the workspace. Creates parent directories.",
        "input_schema": {
            "type": "object",
            "properties": {
                "path": {"type": "string", "description": "Path relative to workspace root"},
                "content": {"type": "string", "description": "Full file content to write"},
            },
            "required": ["path", "content"],
        },
    },
    {
        "name": "str_replace_based_edit_tool",
        "description": (
            "Edit a file by replacing an exact occurrence of old_str with new_str. "
            "Fails if old_str is not found or not unique in the file."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "path": {"type": "string"},
                "old_str": {"type": "string", "description": "Exact string to replace (must be unique)"},
                "new_str": {"type": "string", "description": "Replacement string"},
            },
            "required": ["path", "old_str", "new_str"],
        },
    },
    {
        "name": "bash",
        "description": "Run a shell command in the workspace directory. Returns stdout + stderr.",
        "input_schema": {
            "type": "object",
            "properties": {
                "command": {"type": "string", "description": "Shell command to execute"},
            },
            "required": ["command"],
        },
    },
]


def _safe_workspace_path(workspace_root: Path, rel_path: str) -> Path | None:
    """Resolve rel_path under workspace_root; return None on path traversal."""
    try:
        resolved = (workspace_root / rel_path).resolve()
        resolved.relative_to(workspace_root.resolve())
        return resolved
    except (ValueError, RuntimeError):
        return None


def execute_workspace_tool(
    name: str,
    inputs: dict[str, Any],
    *,
    workspace_root: Path,
    bash_timeout_sec: int = DEFAULT_BASH_TIMEOUT_SEC,
) -> str:
    """Execute a single workspace tool call; always returns a string result."""
    if name == "read_file":
        path = _safe_workspace_path(workspace_root, inputs.get("path", ""))
        if path is None:
            return f"Error: path traversal rejected: {inputs.get('path')}"
        if not path.exists():
            return f"Error: file not found: {inputs.get('path')}"
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError as exc:
            return f"Error reading file: {exc}"
        if len(text) > MAX_TOOL_OUTPUT_CHARS:
            text = text[:MAX_TOOL_OUTPUT_CHARS] + f"\n...(truncated, {len(text)} total chars)"
        return text

    if name == "write_file":
        path = _safe_workspace_path(workspace_root, inputs.get("path", ""))
        if path is None:
            return f"Error: path traversal rejected: {inputs.get('path')}"
        content = inputs.get("content", "")
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        except OSError as exc:
            return f"Error writing file: {exc}"
        return f"Wrote {len(content)} chars to {inputs.get('path')}"

    if name == "str_replace_based_edit_tool":
        path = _safe_workspace_path(workspace_root, inputs.get("path", ""))
        if path is None:
            return f"Error: path traversal rejected: {inputs.get('path')}"
        if not path.exists():
            return f"Error: file not found: {inputs.get('path')}"
        old_str = inputs.get("old_str", "")
        new_str = inputs.get("new_str", "")
        try:
            old_content = path.read_text(encoding="utf-8", errors="replace")
        except OSError as exc:
            return f"Error reading file for edit: {exc}"
        count = old_content.count(old_str)
        if count == 0:
            return f"Error: old_str not found in {inputs.get('path')}"
        if count > 1:
            return f"Error: old_str matches {count} locations in {inputs.get('path')}; must be unique"
        try:
            path.write_text(old_content.replace(old_str, new_str, 1), encoding="utf-8")
        except OSError as exc:
            return f"Error writing edited file: {exc}"
        return f"Edited {inputs.get('path')}: replaced 1 occurrence"

    if name == "bash":
        command = inputs.get("command", "")
        try:
            result = subprocess.run(
                command,
                shell=True,
                cwd=workspace_root,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=bash_timeout_sec,
            )
            combined = result.stdout + result.stderr
            if len(combined) > MAX_TOOL_OUTPUT_CHARS:
                combined = combined[:MAX_TOOL_OUTPUT_CHARS] + "\n...(truncated)"
            return combined if combined.strip() else f"(exit {result.returncode})"
        except subprocess.TimeoutExpired:
            return f"Error: command timed out after {bash_timeout_sec}s"
        except OSError as exc:
            return f"Error running command: {exc}"

    return f"Error: unknown tool '{name}'"


def _build_extra_tool_schema(tool: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": tool["name"],
        "description": tool["description"],
        "input_schema": {
            "type": "object",
            "properties": {
                "args": {"type": "string", "description": "Arguments to pass to the tool"},
            },
            "required": ["args"],
        },
    }


def execute_extra_tool(
    name: str,
    inputs: dict[str, Any],
    *,
    extra_tools_by_name: dict[str, dict[str, Any]],
    workspace_root: Path,
) -> str:
    tool_def = extra_tools_by_name.get(name)
    if tool_def is None:
        return f"Error: unknown extra tool '{name}'"
    template = str(tool_def.get("template", ""))
    args = str(inputs.get("args", ""))
    timeout_sec = int(tool_def.get("timeout_sec", 30))
    kind = str(tool_def.get("kind", ""))
    if kind == "shell":
        try:
            command = template.format(args=args)
        except KeyError:
            command = f"{template} {args}"
    elif kind == "script":
        command = f"{template} {args}"
    else:
        return f"Error: unknown extra tool kind '{kind}'"
    try:
        result = subprocess.run(
            command,
            shell=True,
            cwd=workspace_root,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_sec,
        )
        combined = result.stdout + result.stderr
        if len(combined) > MAX_TOOL_OUTPUT_CHARS:
            combined = combined[:MAX_TOOL_OUTPUT_CHARS] + "\n...(truncated)"
        return combined if combined.strip() else f"(exit {result.returncode})"
    except subprocess.TimeoutExpired:
        return f"Error: extra tool '{name}' timed out after {timeout_sec}s"
    except OSError as exc:
        return f"Error running extra tool '{name}': {exc}"


# ---------------------------------------------------------------------------
# Usage mapping
# ---------------------------------------------------------------------------

def map_sdk_usage(sdk_usage: Any) -> dict[str, Any] | None:
    """Map an anthropic SDK Usage object to the existing orchestrator usage shape."""
    if sdk_usage is None:
        return None
    return {
        "inputTokens": getattr(sdk_usage, "input_tokens", 0) or 0,
        "outputTokens": getattr(sdk_usage, "output_tokens", 0) or 0,
        "cacheReadInputTokens": getattr(sdk_usage, "cache_read_input_tokens", 0) or 0,
        "cacheCreationInputTokens": getattr(sdk_usage, "cache_creation_input_tokens", 0) or 0,
        "serviceTier": None,
        "durationMs": None,
        "durationApiMs": None,
        "numTurns": None,
        "stopReason": None,
        "isError": False,
        "totalCostUsd": None,
        "modelUsage": {},
    }


def _accumulate_usage(
    total: dict[str, Any] | None,
    sdk_usage: Any,
) -> dict[str, Any] | None:
    """Add sdk_usage into a running total; returns updated total."""
    mapped = map_sdk_usage(sdk_usage)
    if mapped is None:
        return total
    if total is None:
        return dict(mapped)
    total["inputTokens"] += mapped["inputTokens"]
    total["outputTokens"] += mapped["outputTokens"]
    total["cacheReadInputTokens"] += mapped["cacheReadInputTokens"]
    total["cacheCreationInputTokens"] += mapped["cacheCreationInputTokens"]
    return total


# ---------------------------------------------------------------------------
# Provider mode detection
# ---------------------------------------------------------------------------

def sdk_available() -> bool:
    """Return True if the anthropic package is importable."""
    try:
        import anthropic  # noqa: F401
        return True
    except ImportError:
        return False


def detect_provider_mode() -> str:
    """
    Return 'sdk' or 'subprocess'.

    Priority:
      1. POJO_LENS_PROVIDER env var ('sdk' or 'subprocess')
      2. Auto: sdk when anthropic package is importable and ANTHROPIC_API_KEY is set
      3. Fall back to subprocess
    """
    explicit = os.environ.get("POJO_LENS_PROVIDER", "").strip().lower()
    if explicit in ("subprocess", "claude"):
        return "subprocess"
    if explicit == "sdk":
        return "sdk"
    if sdk_available() and os.environ.get("ANTHROPIC_API_KEY"):
        return "sdk"
    return "subprocess"


# ---------------------------------------------------------------------------
# Core agentic loop
# ---------------------------------------------------------------------------

def run_sdk_provider(
    system_prompt: str,
    user_prompt: str,
    *,
    model: str | None = None,
    workspace_root: Path,
    timeout_sec: int = 1800,
    stream_to_stderr: bool = False,
    on_partial_text: Callable[[str], None] | None = None,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    bash_timeout_sec: int = DEFAULT_BASH_TIMEOUT_SEC,
    on_progress: Callable[[int], None] | None = None,
    extra_tools: list[dict[str, Any]] | None = None,
) -> SdkProviderResult:
    """
    Execute a worker task via the Anthropic SDK with workspace tool use.

    Runs a bounded agentic loop: each iteration either executes tool calls
    (read/write/edit/bash in workspace_root) or returns a final text response.
    The final text response is expected to be JSON matching the worker schema.

    Returns SdkProviderResult; never raises anthropic.* exceptions. Errors are
    encoded in SdkProviderResult.error using the exception type name so WP42
    retry_policy can classify them from record.summary.
    """
    try:
        import anthropic
    except ImportError:
        return SdkProviderResult(
            text="",
            usage=None,
            error="ImportError: anthropic package not installed; run: pip install 'pojolens-agents[sdk]'",
        )

    effective_model = model or SDK_PROVIDER_DEFAULT_MODEL
    client = anthropic.Anthropic(timeout=float(timeout_sec))
    messages: list[dict[str, Any]] = [{"role": "user", "content": user_prompt}]
    total_usage: dict[str, Any] | None = None
    _extra_list = extra_tools or []
    _extra_by_name: dict[str, dict[str, Any]] = {t["name"]: t for t in _extra_list}
    effective_tools = list(WORKSPACE_TOOLS) + [_build_extra_tool_schema(t) for t in _extra_list]

    try:
        for iteration in range(MAX_TOOL_ITERATIONS + 1):
            if on_progress is not None:
                on_progress(iteration)

            _use_stream = on_partial_text is not None or (stream_to_stderr and sys.stderr.isatty())
            if _use_stream:
                with client.messages.stream(
                    model=effective_model,
                    system=system_prompt,
                    messages=messages,
                    tools=effective_tools,
                    max_tokens=max_tokens,
                ) as stream:
                    for text_delta in stream.text_stream:
                        if on_partial_text is not None:
                            on_partial_text(text_delta)
                        elif stream_to_stderr:
                            sys.stderr.write(text_delta)
                            sys.stderr.flush()
                    response = stream.get_final_message()
            else:
                response = client.messages.create(
                    model=effective_model,
                    system=system_prompt,
                    messages=messages,
                    tools=effective_tools,
                    max_tokens=max_tokens,
                )

            total_usage = _accumulate_usage(total_usage, getattr(response, "usage", None))

            text_parts = [b.text for b in response.content if b.type == "text"]
            response_text = "".join(text_parts)

            if response.stop_reason in ("end_turn", "stop_sequence", None):
                if total_usage is not None:
                    total_usage["stopReason"] = response.stop_reason
                    total_usage["numTurns"] = iteration + 1
                return SdkProviderResult(text=response_text, usage=total_usage)

            if response.stop_reason == "max_tokens":
                if total_usage is not None:
                    total_usage["stopReason"] = "max_tokens"
                    total_usage["numTurns"] = iteration + 1
                return SdkProviderResult(
                    text=response_text,
                    usage=total_usage,
                    error=f"APITimeoutError: reached max_tokens limit ({max_tokens}); response may be incomplete",
                )

            if response.stop_reason == "tool_use":
                tool_use_blocks = [b for b in response.content if b.type == "tool_use"]
                if not tool_use_blocks:
                    return SdkProviderResult(text=response_text, usage=total_usage)
                if on_partial_text is not None:
                    for block in tool_use_blocks:
                        on_partial_text(f"\n[tool: {block.name}]\n")
                tool_results: list[dict[str, Any]] = []
                for block in tool_use_blocks:
                    block_inputs = dict(block.input) if block.input else {}
                    if block.name in _extra_by_name:
                        tool_output = execute_extra_tool(
                            block.name,
                            block_inputs,
                            extra_tools_by_name=_extra_by_name,
                            workspace_root=workspace_root,
                        )
                    else:
                        tool_output = execute_workspace_tool(
                            block.name,
                            block_inputs,
                            workspace_root=workspace_root,
                            bash_timeout_sec=bash_timeout_sec,
                        )
                    tool_results.append(
                        {
                            "type": "tool_result",
                            "tool_use_id": block.id,
                            "content": tool_output,
                        }
                    )
                messages.append({"role": "assistant", "content": list(response.content)})
                messages.append({"role": "user", "content": tool_results})
                continue

            # Unexpected stop_reason — treat as permanent failure
            return SdkProviderResult(
                text=response_text,
                usage=total_usage,
                error=f"InvalidRequestError: unexpected stop_reason {response.stop_reason!r}",
            )

        return SdkProviderResult(
            text="",
            usage=total_usage,
            error=f"APITimeoutError: exceeded max tool iterations ({MAX_TOOL_ITERATIONS})",
        )

    except Exception as exc:
        type_name = type(exc).__name__
        msg = str(exc)
        error_text = f"{type_name}: {msg}" if msg else type_name
        return SdkProviderResult(text="", usage=total_usage, error=error_text)
