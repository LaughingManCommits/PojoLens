from __future__ import annotations

import os
import shlex
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any


def truncate_command_text(text: str, max_chars: int) -> tuple[str, bool]:
    stripped = text.strip()
    if len(stripped) <= max_chars:
        return stripped, False
    if max_chars <= 3:
        return stripped[:max_chars], True
    return stripped[: max_chars - 3].rstrip() + "...", True


def strip_optional_quotes(token: str) -> str:
    if len(token) >= 2 and token[0] == token[-1] and token[0] in {"'", '"'}:
        return token[1:-1]
    return token


def contains_unquoted_shell_operator(text: str) -> bool:
    in_single = False
    in_double = False
    index = 0
    while index < len(text):
        char = text[index]
        if char == "'" and not in_double:
            in_single = not in_single
            index += 1
            continue
        if char == '"' and not in_single:
            in_double = not in_double
            index += 1
            continue
        if not in_single and not in_double:
            if text.startswith(("&&", "||", ">>"), index):
                return True
            if char in {"|", "&", ";", "<", ">"}:
                return True
        index += 1
    return False


def normalize_worker_text_list(
    payload: Any,
    *,
    key: str,
    max_items: int,
    max_chars: int,
    compact: bool,
    truncate_text,
    error_factory,
) -> tuple[list[str], bool]:
    if payload is None:
        return [], False
    if not isinstance(payload, list):
        raise error_factory(f"Claude JSON output field '{key}' must be an array or null")
    normalized: list[str] = []
    for item in payload:
        if not isinstance(item, str):
            raise error_factory(f"Claude JSON output field '{key}' must contain only strings")
        text = item.strip()
        if not text:
            continue
        if compact:
            text, _ = truncate_text(text, max_chars)
        else:
            text, _ = truncate_command_text(text, max_chars)
        normalized.append(text)
        if len(normalized) >= max_items:
            break
    return list(dict.fromkeys(normalized)), True


def normalize_worker_files_touched(
    payload: Any,
    *,
    normalize_relative_path,
    error_factory,
) -> tuple[list[str], bool]:
    if payload is None:
        return [], False
    if not isinstance(payload, list):
        raise error_factory("Claude JSON output field 'filesTouched' must be an array or null")
    normalized: list[str] = []
    for item in payload:
        if not isinstance(item, str):
            raise error_factory("Claude JSON output field 'filesTouched' must contain only strings")
        text = item.strip()
        if not text:
            continue
        normalized.append(normalize_relative_path(text, location="worker result filesTouched"))
    return list(dict.fromkeys(normalized)), True


def normalized_worker_unknown_fields(payload: Any, *, worker_unknownable_fields: list[str]) -> list[str]:
    if not isinstance(payload, list):
        return []
    return [field_name for field_name in worker_unknownable_fields if field_name in payload]


def worker_field_unknown(record: Any, field_name: str) -> bool:
    return field_name in record.unknown_fields


def analyze_validation_command(
    command_text: str,
    *,
    validation_allowed_executables: set[str],
    validation_allowed_script_suffixes: set[str],
    validation_allowed_relative_prefixes: tuple[str, ...],
    validation_intent_factory,
    normalize_relative_path,
    error_factory,
    asdict,
) -> dict[str, Any]:
    stripped = command_text.strip()
    if not stripped:
        return {"accepted": False, "reason": "empty command", "entrypoint": None, "intent": None}
    if "\n" in stripped or "\r" in stripped:
        return {
            "accepted": False,
            "reason": "multi-line commands are not allowed",
            "entrypoint": None,
            "intent": None,
        }
    if contains_unquoted_shell_operator(stripped):
        return {
            "accepted": False,
            "reason": "shell composition is not allowed; use a direct tool or script invocation",
            "entrypoint": None,
            "intent": None,
        }
    try:
        tokens = shlex.split(stripped, posix=False)
    except ValueError as exc:
        return {
            "accepted": False,
            "reason": f"unable to parse command text: {exc}",
            "entrypoint": None,
            "intent": None,
        }
    if not tokens:
        return {"accepted": False, "reason": "empty command", "entrypoint": None, "intent": None}
    cleaned_tokens = [strip_optional_quotes(token) for token in tokens]
    entrypoint = cleaned_tokens[0]
    args = [token for token in cleaned_tokens[1:] if token]
    entry_path = Path(entrypoint)
    if entry_path.is_absolute():
        suffix = entry_path.suffix.lower()
        if suffix == ".exe" or entry_path.name.lower() in validation_allowed_executables:
            return {
                "accepted": True,
                "reason": None,
                "entrypoint": entrypoint,
                "intent": asdict(validation_intent_factory(kind="tool", entrypoint=entrypoint, args=args)),
            }
        return {
            "accepted": False,
            "reason": "absolute entrypoint is not an approved executable",
            "entrypoint": entrypoint,
            "intent": None,
        }
    lowered_entrypoint = entrypoint.lower()
    if lowered_entrypoint in validation_allowed_executables:
        return {
            "accepted": True,
            "reason": None,
            "entrypoint": entrypoint,
            "intent": asdict(validation_intent_factory(kind="tool", entrypoint=entrypoint, args=args)),
        }
    try:
        normalized = normalize_relative_path(entrypoint, location="validation command entrypoint")
    except error_factory as exc:
        return {
            "accepted": False,
            "reason": str(exc),
            "entrypoint": entrypoint,
            "intent": None,
        }
    suffix = Path(normalized).suffix.lower()
    if suffix in validation_allowed_script_suffixes and (
        normalized in {"mvnw", "mvnw.cmd"} or normalized.startswith(validation_allowed_relative_prefixes)
    ):
        return {
            "accepted": True,
            "reason": None,
            "entrypoint": normalized,
            "intent": asdict(validation_intent_factory(kind="repo-script", entrypoint=normalized, args=args)),
        }
    return {
        "accepted": False,
        "reason": "entrypoint is not an approved repo script or executable",
        "entrypoint": entrypoint,
        "intent": None,
    }


def coerce_validation_intent_payload(
    payload: Any,
    *,
    location: str,
    validation_intent_kinds: set[str],
    max_worker_validation_intent_arg_chars: int,
    validation_intent_factory,
    normalize_relative_path,
    truncate_text,
    error_factory,
) -> Any:
    if not isinstance(payload, dict):
        raise error_factory(f"{location}: expected validation intent object")
    extra_keys = sorted(set(payload) - {"kind", "entrypoint", "args"})
    if extra_keys:
        raise error_factory(f"{location}: unsupported validation intent fields {extra_keys}")
    kind = payload.get("kind")
    if not isinstance(kind, str) or kind not in validation_intent_kinds:
        raise error_factory(f"{location}: field 'kind' must be one of {sorted(validation_intent_kinds)}")
    entrypoint_value = payload.get("entrypoint")
    if not isinstance(entrypoint_value, str) or not entrypoint_value.strip():
        raise error_factory(f"{location}: field 'entrypoint' must be a non-empty string")
    cleaned_entrypoint = strip_optional_quotes(entrypoint_value.strip())
    if kind == "repo-script":
        cleaned_entrypoint = normalize_relative_path(cleaned_entrypoint, location=f"{location}:entrypoint")
    args_payload = payload.get("args", [])
    if args_payload is None:
        args_payload = []
    if not isinstance(args_payload, list):
        raise error_factory(f"{location}: field 'args' must be an array when supplied")
    args: list[str] = []
    for index, item in enumerate(args_payload, start=1):
        if not isinstance(item, str):
            raise error_factory(f"{location}: field 'args[{index}]' must be a string")
        text = strip_optional_quotes(item.strip())
        if not text:
            continue
        text, _ = truncate_text(text, max_worker_validation_intent_arg_chars)
        args.append(text)
    return validation_intent_factory(kind=kind, entrypoint=cleaned_entrypoint, args=args)


def normalize_worker_findings(
    payload: Any,
    *,
    max_items: int,
    max_chars: int,
    reviewer_finding_severities: frozenset,
    reviewer_finding_factory,
    truncate_text,
    error_factory,
) -> list[Any]:
    if payload is None:
        return []
    if not isinstance(payload, list):
        raise error_factory("Claude JSON output field 'findings' must be an array or null")
    findings: list[Any] = []
    for index, item in enumerate(payload, start=1):
        if not isinstance(item, dict):
            raise error_factory(f"Claude JSON output field 'findings[{index}]' must be an object")
        severity = item.get("severity")
        if not isinstance(severity, str) or severity not in reviewer_finding_severities:
            raise error_factory(
                f"Claude JSON output field 'findings[{index}].severity' must be one of "
                f"{sorted(reviewer_finding_severities)}"
            )
        message = item.get("message")
        if not isinstance(message, str) or not message.strip():
            raise error_factory(
                f"Claude JSON output field 'findings[{index}].message' must be a non-empty string"
            )
        message_text, _ = truncate_text(message.strip(), max_chars)
        findings.append(reviewer_finding_factory(severity=severity, message=message_text))
        if len(findings) >= max_items:
            break
    return findings


def normalize_worker_validation_intents(
    payload: Any,
    *,
    max_worker_validation_intents: int,
    coerce_validation_intent_payload,
    error_factory,
) -> list[Any]:
    if payload is None:
        return []
    if not isinstance(payload, list):
        raise error_factory("Claude JSON output field 'validationIntents' must be an array")
    intents: list[Any] = []
    seen: set[tuple[str, str, tuple[str, ...]]] = set()
    for index, item in enumerate(payload, start=1):
        intent = coerce_validation_intent_payload(
            item,
            location=f"Claude JSON output field 'validationIntents[{index}]'",
        )
        key = (intent.kind, intent.entrypoint, tuple(intent.args))
        if key in seen:
            continue
        seen.add(key)
        intents.append(intent)
        if len(intents) >= max_worker_validation_intents:
            break
    return intents


def validation_intent_policy(
    intent: Any,
    *,
    validation_allowed_executables: set[str],
    validation_allowed_script_suffixes: set[str],
    validation_allowed_relative_prefixes: tuple[str, ...],
) -> dict[str, Any]:
    if intent.kind == "repo-script":
        suffix = Path(intent.entrypoint).suffix.lower()
        if suffix in validation_allowed_script_suffixes and (
            intent.entrypoint in {"mvnw", "mvnw.cmd"} or intent.entrypoint.startswith(validation_allowed_relative_prefixes)
        ):
            return {"accepted": True, "reason": None, "entrypoint": intent.entrypoint}
        return {
            "accepted": False,
            "reason": "repo-script intents must point at an approved repo-local script or wrapper",
            "entrypoint": intent.entrypoint,
        }
    entrypoint = intent.entrypoint
    entry_path = Path(entrypoint)
    if entry_path.is_absolute():
        suffix = entry_path.suffix.lower()
        if suffix == ".exe" or entry_path.name.lower() in validation_allowed_executables:
            return {"accepted": True, "reason": None, "entrypoint": entrypoint}
        return {
            "accepted": False,
            "reason": "absolute tool entrypoint is not an approved executable",
            "entrypoint": entrypoint,
        }
    lowered_entrypoint = entrypoint.lower()
    if lowered_entrypoint in validation_allowed_executables:
        return {"accepted": True, "reason": None, "entrypoint": entrypoint}
    return {
        "accepted": False,
        "reason": "tool intent entrypoint is not an approved executable",
        "entrypoint": entrypoint,
    }


def render_command_tokens(tokens: list[str]) -> str:
    if os.name == "nt":
        return subprocess.list2cmdline(tokens)
    return shlex.join(tokens)


def validation_intent_command_text(intent: Any) -> str:
    return render_command_tokens([intent.entrypoint, *intent.args])


def validation_intent_execution_tokens(intent: Any, *, root: Path) -> list[str]:
    if intent.kind == "tool":
        resolved_entrypoint = shutil.which(intent.entrypoint) or intent.entrypoint
        return [resolved_entrypoint, *intent.args]
    absolute_path = str((root / intent.entrypoint).resolve())
    suffix = Path(intent.entrypoint).suffix.lower()
    if suffix == ".ps1":
        powershell_bin = shutil.which("pwsh") or shutil.which("powershell") or "powershell"
        return [powershell_bin, "-File", absolute_path, *intent.args]
    if suffix == ".py":
        return [sys.executable, absolute_path, *intent.args]
    if suffix in {".cmd", ".bat"}:
        return ["cmd.exe", "/c", absolute_path, *intent.args]
    if suffix == ".sh":
        return ["bash", absolute_path, *intent.args]
    return [absolute_path, *intent.args]


def validation_command_policy(command_text: str, *, analyze_validation_command) -> dict[str, Any]:
    analysis = analyze_validation_command(command_text)
    return {
        "accepted": bool(analysis.get("accepted", False)),
        "reason": analysis.get("reason"),
        "entrypoint": analysis.get("entrypoint"),
        "intent": analysis.get("intent"),
    }


def coerce_worker_result(
    payload: Any,
    *,
    worker_validation_mode: str,
    worker_statuses: set[str],
    max_worker_summary_chars: int,
    max_worker_validation_commands: int,
    max_worker_validation_command_chars: int,
    max_worker_follow_ups: int,
    max_worker_follow_up_chars: int,
    max_worker_notes: int,
    max_worker_note_chars: int,
    normalize_worker_validation_mode,
    normalize_worker_files_touched,
    normalize_worker_text_list,
    normalize_worker_validation_intents,
    normalize_worker_findings,
    truncate_text,
    asdict,
    error_factory,
) -> dict[str, Any]:
    worker_validation_mode = normalize_worker_validation_mode(
        worker_validation_mode,
        location="Claude JSON output",
    )
    _ = worker_validation_mode
    required = {"status", "summary", "filesTouched", "validationIntents", "followUps", "notes"}
    if isinstance(payload, dict) and not required.issubset(payload):
        for key in ("result", "data", "response", "structured_output"):
            nested = payload.get(key)
            if isinstance(nested, dict) and required.issubset(nested):
                payload = nested
                break
        else:
            raise error_factory("Claude JSON output did not match the expected worker schema")
    if not isinstance(payload, dict):
        raise error_factory("Claude JSON output did not match the expected worker schema")
    status = payload.get("status")
    if not isinstance(status, str) or status not in worker_statuses:
        raise error_factory(f"Claude JSON output field 'status' must be one of {sorted(worker_statuses)}")
    summary_value = payload.get("summary")
    if not isinstance(summary_value, str) or not summary_value.strip():
        raise error_factory("Claude JSON output field 'summary' must be a non-empty string")
    summary, _ = truncate_text(summary_value, max_worker_summary_chars)
    unknown_fields: list[str] = []
    normalized_files_touched, files_touched_known = normalize_worker_files_touched(payload.get("filesTouched"))
    if not files_touched_known:
        unknown_fields.append("filesTouched")
    validation_commands, _ = normalize_worker_text_list(
        payload.get("validationCommands", []),
        key="validationCommands",
        max_items=max_worker_validation_commands,
        max_chars=max_worker_validation_command_chars,
        compact=False,
    )
    if validation_commands:
        raise error_factory(
            "Claude JSON output must not include raw validationCommands items; use structured validationIntents instead"
        )
    follow_ups, follow_ups_known = normalize_worker_text_list(
        payload.get("followUps"),
        key="followUps",
        max_items=max_worker_follow_ups,
        max_chars=max_worker_follow_up_chars,
        compact=True,
    )
    if not follow_ups_known:
        unknown_fields.append("followUps")
    notes, notes_known = normalize_worker_text_list(
        payload.get("notes"),
        key="notes",
        max_items=max_worker_notes,
        max_chars=max_worker_note_chars,
        compact=True,
    )
    if not notes_known:
        unknown_fields.append("notes")
    validation_intents = normalize_worker_validation_intents(payload.get("validationIntents"))
    findings = normalize_worker_findings(payload.get("findings"))
    return {
        "status": status,
        "summary": summary,
        "filesTouched": normalized_files_touched,
        "validationIntents": [asdict(intent) for intent in validation_intents],
        "validationCommands": validation_commands,
        "followUps": follow_ups,
        "notes": notes,
        "unknownFields": unknown_fields,
        "findings": [asdict(f) for f in findings],
    }
