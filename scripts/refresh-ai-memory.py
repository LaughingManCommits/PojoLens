#!/usr/bin/env python3
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import sqlite3
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
AI_DIR = ROOT / "ai"
INDEX_DIR = AI_DIR / "indexes"
MEMORY_STATE_PATH = AI_DIR / "memory-state.json"
SQLITE_DB_PATH = INDEX_DIR / "cold-memory.db"
REFRESH_CACHE_PATH = INDEX_DIR / "refresh-state.json"
ACTIVE_LOG_PATH = AI_DIR / "log" / "events.jsonl"
LOG_ARCHIVE_DIR = AI_DIR / "log" / "archive"
RECENT_VALIDATIONS_PATH = AI_DIR / "state" / "recent-validations.md"

HOT_CONTEXT_FILES = [
    AI_DIR / "core" / "agent-invariants.md",
    AI_DIR / "core" / "repo-purpose.md",
    AI_DIR / "state" / "current-state.md",
    AI_DIR / "state" / "handoff.md",
]

HOT_CONTEXT_MAX_LINES = 240
HOT_CONTEXT_MAX_BYTES = 24 * 1024
ACTIVE_EVENT_RETENTION = 12
SCHEMA_VERSION = 5
REFRESH_CACHE_SCHEMA_VERSION = 1

HOT_CONTEXT_FILE_SPECS = {
    "ai/state/current-state.md": {
        "maxLines": 50,
        "maxBytes": 3 * 1024,
        "allowedHeadings": ["Repo", "Focus", "Verified", "Release", "Risks", "Next"],
        "requiredHeadings": ["Repo", "Focus", "Verified", "Release", "Risks", "Next"],
        "headingOrder": ["Repo", "Focus", "Verified", "Release", "Risks", "Next"],
    },
    "ai/state/handoff.md": {
        "maxLines": 50,
        "maxBytes": 3 * 1024,
        "allowedHeadings": ["Resume", "Focus", "Facts", "Validate", "Cold Pointers"],
        "requiredHeadings": ["Resume", "Focus", "Facts", "Validate", "Cold Pointers"],
        "headingOrder": ["Resume", "Focus", "Facts", "Validate", "Cold Pointers"],
    },
}

INDEX_OUTPUTS = {
    "docs": INDEX_DIR / "docs-index.json",
    "files": INDEX_DIR / "files-index.json",
    "symbols": INDEX_DIR / "symbols-index.json",
    "test": INDEX_DIR / "test-index.json",
    "config": INDEX_DIR / "config-index.json",
}

ROOT_TEXT_FILES = [
    "AGENTS.md",
    "CONTRIBUTING.md",
    "MAINTENANCE.md",
    "MIGRATION.md",
    "README.md",
    "RELEASE.md",
    "TODO.md",
]

MODULE_SPECS = [
    {
        "path": "pojo-lens",
        "kind": "runtime-module",
        "role": "runtime",
        "published": True,
    },
    {
        "path": "pojo-lens-spring-boot-autoconfigure",
        "kind": "boot-autoconfigure-module",
        "role": "spring-boot-autoconfigure",
        "published": True,
    },
    {
        "path": "pojo-lens-spring-boot-starter",
        "kind": "boot-starter-module",
        "role": "spring-boot-starter",
        "published": True,
    },
    {
        "path": "pojo-lens-benchmarks",
        "kind": "benchmark-module",
        "role": "benchmark-tooling",
        "published": False,
    },
    {
        "path": "examples/spring-boot-starter-basic",
        "kind": "example-module",
        "role": "starter-dashboard-example",
        "published": False,
    },
    {
        "path": "examples/spring-boot-starter-quickstart",
        "kind": "example-module",
        "role": "starter-quickstart-example",
        "published": False,
    },
]

SYMBOL_GROUPS = {
    "facades": [
        "PojoLens",
        "PojoLensCore",
        "PojoLensSql",
        "PojoLensChart",
        "PojoLensRuntime",
        "PojoLensRuntimePreset",
    ],
    "fluent-engine": [
        "QueryBuilder",
        "FilterQueryBuilder",
        "FilterImpl",
        "FastArrayQuerySupport",
        "FastStatsQuerySupport",
        "JoinEngine",
    ],
    "sql-like": [
        "SqlLikeQuery",
        "SqlLikeTemplate",
        "SqlLikeBoundQuery",
        "SqlLikeCursor",
        "SqlLikeParser",
        "SqlLikeValidator",
        "SqlLikeBinder",
        "SqlExpressionEvaluator",
    ],
    "chart-and-stats": [
        "ChartMapper",
        "ChartQueryPreset",
        "ChartQueryPresets",
        "ChartJsAdapter",
        "ChartJsPayload",
        "StatsViewPresets",
        "StatsViewPreset",
        "StatsTable",
        "StatsTablePayload",
        "TabularRows",
    ],
    "ecosystem": [
        "DatasetBundle",
        "ReportDefinition",
        "SnapshotComparison",
        "QueryRegressionFixture",
        "FieldMetamodelGenerator",
        "QueryTelemetryListener",
    ],
    "spring-boot": [
        "PojoLensProperties",
        "MicrometerQueryTelemetryListener",
        "PojoLensSpringBootAutoConfiguration",
        "PojoLensSpringBootStarterMarker",
    ],
    "examples": [
        "BasicExampleApplication",
        "QuickstartExampleApplication",
        "QuickstartEmployeeController",
        "EmployeeDashboardService",
        "EmployeeQueryController",
        "EmployeeStore",
    ],
    "benchmark-tooling": [
        "JmhRunner",
        "BenchmarkThresholdChecker",
        "BenchmarkMetricsPlotGenerator",
    ],
}


def rel_path(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_json(path: Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def hash_for_path_hashes(path_hashes: dict[str, str]) -> str:
    digest = hashlib.sha256()
    for relative_path, file_hash in sorted(path_hashes.items()):
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        digest.update(file_hash.encode("utf-8"))
        digest.update(b"\0")
    return digest.hexdigest()


def load_refresh_cache() -> dict[str, object]:
    if not REFRESH_CACHE_PATH.exists():
        return {
            "schemaVersion": REFRESH_CACHE_SCHEMA_VERSION,
            "indexes": {},
        }
    try:
        payload = json.loads(read_text(REFRESH_CACHE_PATH))
    except json.JSONDecodeError:
        return {
            "schemaVersion": REFRESH_CACHE_SCHEMA_VERSION,
            "indexes": {},
        }
    if payload.get("schemaVersion") != REFRESH_CACHE_SCHEMA_VERSION:
        return {
            "schemaVersion": REFRESH_CACHE_SCHEMA_VERSION,
            "indexes": {},
        }
    indexes = payload.get("indexes")
    if not isinstance(indexes, dict):
        indexes = {}
    return {
        "schemaVersion": REFRESH_CACHE_SCHEMA_VERSION,
        "indexes": indexes,
    }


def write_refresh_cache(generated_at: str, cache_payload: dict[str, object]) -> None:
    write_json(
        REFRESH_CACHE_PATH,
        {
            "schemaVersion": REFRESH_CACHE_SCHEMA_VERSION,
            "generatedAt": generated_at,
            "indexes": cache_payload,
        },
    )


def markdown_heading_titles(text: str, level: int = 2) -> list[str]:
    pattern = re.compile(rf"^{'#' * level}\s+(.+?)\s*$", re.MULTILINE)
    return [match.group(1).strip() for match in pattern.finditer(text)]


def repo_files_for_patterns(patterns: list[str]) -> list[Path]:
    files: set[Path] = set()
    for pattern in patterns:
        files.update(path for path in ROOT.glob(pattern) if path.is_file())
    return sorted(files)


def collect_markdown_files() -> list[Path]:
    files = repo_files_for_patterns(ROOT_TEXT_FILES)
    files.extend(path for path in (ROOT / "docs").rglob("*.md") if path.is_file())
    files.extend(path for path in AI_DIR.rglob("*.md") if path.is_file())
    return sorted(set(files))


def collect_event_log_files() -> list[Path]:
    files: list[Path] = []
    if ACTIVE_LOG_PATH.exists():
        files.append(ACTIVE_LOG_PATH)
    if LOG_ARCHIVE_DIR.exists():
        files.extend(path for path in LOG_ARCHIVE_DIR.rglob("*.jsonl") if path.is_file())
    return sorted(set(files), key=lambda path: (0 if path == ACTIVE_LOG_PATH else 1, rel_path(path)))


def collect_cold_search_files() -> list[Path]:
    files = set(collect_markdown_files())
    files.update(collect_event_log_files())
    return sorted(files)


def collect_java_files(*relative_roots: str) -> list[Path]:
    files: list[Path] = []
    for root in relative_roots:
        base = ROOT / root
        if not base.exists():
            continue
        files.extend(path for path in base.rglob("*.java") if path.is_file())
    return sorted(files)


def collect_hash_inputs() -> list[Path]:
    files = set(collect_markdown_files())
    files.update(repo_files_for_patterns(
        [
            "pom.xml",
            "pojo-lens/pom.xml",
            "pojo-lens-benchmarks/pom.xml",
            "pojo-lens-spring-boot-autoconfigure/pom.xml",
            "pojo-lens-spring-boot-starter/pom.xml",
            ".github/workflows/*.yml",
            "scripts/refresh-ai-memory.py",
            "scripts/refresh-ai-memory.ps1",
            "scripts/query-ai-memory.py",
            "scripts/query-ai-memory.ps1",
            "scripts/benchmark-ai-memory.py",
            "scripts/benchmark-ai-memory.ps1",
        ]
    ))
    files.update(collect_java_files(
        "pojo-lens/src/main/java",
        "pojo-lens/src/test/java",
        "pojo-lens-benchmarks/src/main/java",
        "pojo-lens-benchmarks/src/test/java",
        "pojo-lens-spring-boot-autoconfigure/src/main/java",
        "pojo-lens-spring-boot-autoconfigure/src/test/java",
        "pojo-lens-spring-boot-starter/src/main/java",
        "pojo-lens-spring-boot-starter/src/test/java",
        "examples/spring-boot-starter-basic/src/main/java",
        "examples/spring-boot-starter-basic/src/test/java",
        "examples/spring-boot-starter-quickstart/src/main/java",
        "examples/spring-boot-starter-quickstart/src/test/java",
    ))
    files.add(AI_DIR / "AGENTS.md")
    files.update(collect_event_log_files())
    return sorted(files)


def load_event_entries(paths: list[Path] | None = None) -> list[dict[str, object]]:
    entries: list[dict[str, object]] = []
    seen: set[tuple[str, str, str]] = set()
    for path in paths or collect_event_log_files():
        for line_number, line in enumerate(read_text(path).splitlines(), start=1):
            if not line.strip():
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            key = (
                str(payload.get("ts", "")),
                str(payload.get("type", "")),
                str(payload.get("summary", "")),
            )
            if key in seen:
                continue
            seen.add(key)
            entries.append(
                {
                    "path": path,
                    "lineNumber": line_number,
                    "payload": payload,
                }
            )
    return sorted(
        entries,
        key=lambda entry: (
            str(entry["payload"].get("ts", "")),
            str(entry["payload"].get("type", "")),
            str(entry["payload"].get("summary", "")),
        ),
    )


def write_jsonl(path: Path, payloads: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = "\n".join(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        for payload in payloads
    )
    if text:
        text += "\n"
    path.write_text(text, encoding="utf-8")


def event_month_key(payload: dict[str, object]) -> str:
    ts = str(payload.get("ts", ""))
    if re.match(r"^\d{4}-\d{2}", ts):
        return ts[:7]
    return "unknown"


def archive_summary_path(month_key: str) -> Path:
    return LOG_ARCHIVE_DIR / f"{month_key}-summary.md"


def summarize_archive_month(month_key: str, payloads: list[dict[str, object]]) -> str:
    type_counts: dict[str, int] = defaultdict(int)
    for payload in payloads:
        type_counts[str(payload.get("type", "event"))] += 1
    sorted_types = sorted(type_counts.items(), key=lambda item: (-item[1], item[0]))
    lines = [
        f"# {month_key} Archive Summary",
        "",
        f"- entries: {len(payloads)}",
    ]
    if payloads:
        lines.append(f"- range: {payloads[0].get('ts', 'unknown')} to {payloads[-1].get('ts', 'unknown')}")
    if sorted_types:
        lines.append(
            "- event types: "
            + ", ".join(f"{event_type}={count}" for event_type, count in sorted_types)
        )
    lines.extend(
        [
            "",
            "## Event Snapshots",
        ]
    )
    for payload in payloads:
        ts = str(payload.get("ts", "unknown"))
        summary = str(payload.get("summary", "")).strip()
        if summary:
            lines.append(f"- `{ts}`: {summary}")
    lines.append("")
    return "\n".join(lines)


def generate_archive_summaries(archived_by_month: dict[str, list[dict[str, object]]]) -> list[str]:
    summary_paths: list[str] = []
    retained = set()
    for month_key, payloads in sorted(archived_by_month.items()):
        summary_path = archive_summary_path(month_key)
        summary_path.parent.mkdir(parents=True, exist_ok=True)
        summary_path.write_text(summarize_archive_month(month_key, payloads), encoding="utf-8")
        retained.add(summary_path)
        summary_paths.append(rel_path(summary_path))
    if LOG_ARCHIVE_DIR.exists():
        for existing in LOG_ARCHIVE_DIR.glob("*-summary.md"):
            if existing not in retained:
                existing.unlink()
    return summary_paths


def sync_archive_summaries_from_disk() -> list[str]:
    archived_by_month: dict[str, list[dict[str, object]]] = defaultdict(list)
    if LOG_ARCHIVE_DIR.exists():
        for archive_path in sorted(LOG_ARCHIVE_DIR.glob("*.jsonl")):
            for line in read_text(archive_path).splitlines():
                if not line.strip():
                    continue
                try:
                    payload = json.loads(line)
                except json.JSONDecodeError:
                    continue
                archived_by_month[event_month_key(payload)].append(payload)
    return generate_archive_summaries(archived_by_month)


def count_event_entries(path: Path) -> int:
    return sum(1 for line in read_text(path).splitlines() if line.strip())


def compact_event_logs(retain_recent: int = ACTIVE_EVENT_RETENTION) -> dict[str, object]:
    entries = load_event_entries()
    if not entries:
        write_jsonl(ACTIVE_LOG_PATH, [])
        return {
            "activePath": rel_path(ACTIVE_LOG_PATH),
            "activeEntries": 0,
            "archivedEntries": 0,
            "archiveFiles": [],
            "summaryFiles": [],
            "retention": retain_recent,
        }

    recent_entries = entries[-retain_recent:] if retain_recent > 0 else []
    archived_entries = entries[:-retain_recent] if retain_recent > 0 else entries
    archived_by_month: dict[str, list[dict[str, object]]] = defaultdict(list)
    for entry in archived_entries:
        payload = entry["payload"]
        if isinstance(payload, dict):
            archived_by_month[event_month_key(payload)].append(payload)

    retained_archive_paths: set[Path] = set()
    if archived_by_month:
        LOG_ARCHIVE_DIR.mkdir(parents=True, exist_ok=True)
    for month_key, payloads in sorted(archived_by_month.items()):
        archive_path = LOG_ARCHIVE_DIR / f"{month_key}.jsonl"
        write_jsonl(archive_path, payloads)
        retained_archive_paths.add(archive_path)
    if LOG_ARCHIVE_DIR.exists():
        for existing in LOG_ARCHIVE_DIR.glob("*.jsonl"):
            if existing not in retained_archive_paths:
                existing.unlink()
    summary_files = generate_archive_summaries(archived_by_month)

    active_payloads = [
        entry["payload"]
        for entry in recent_entries
        if isinstance(entry["payload"], dict)
    ]
    write_jsonl(ACTIVE_LOG_PATH, active_payloads)
    return {
        "activePath": rel_path(ACTIVE_LOG_PATH),
        "activeEntries": len(active_payloads),
        "archivedEntries": len(archived_entries),
        "archiveFiles": [rel_path(path) for path in sorted(retained_archive_paths)],
        "summaryFiles": summary_files,
        "retention": retain_recent,
    }


def event_log_stats() -> dict[str, object]:
    archive_paths = sorted(path for path in LOG_ARCHIVE_DIR.glob("*.jsonl") if path.is_file()) if LOG_ARCHIVE_DIR.exists() else []
    summary_paths = sorted(path for path in LOG_ARCHIVE_DIR.glob("*-summary.md") if path.is_file()) if LOG_ARCHIVE_DIR.exists() else []
    active_entries = count_event_entries(ACTIVE_LOG_PATH) if ACTIVE_LOG_PATH.exists() else 0
    active_bytes = ACTIVE_LOG_PATH.stat().st_size if ACTIVE_LOG_PATH.exists() else 0
    archives = [
        {
            "path": rel_path(path),
            "entries": count_event_entries(path),
            "bytes": path.stat().st_size,
        }
        for path in archive_paths
    ]
    return {
        "activeLog": {
            "path": rel_path(ACTIVE_LOG_PATH),
            "entries": active_entries,
            "bytes": active_bytes,
        },
        "archiveFiles": archives,
        "summaryFiles": [rel_path(path) for path in summary_paths],
        "retention": ACTIVE_EVENT_RETENTION,
        "totalEntries": active_entries + sum(item["entries"] for item in archives),
    }


def load_tier_priority(load_tier: str | None) -> int:
    return {
        "hot": 0,
        "warm": 1,
        "cold": 2,
        "archive": 3,
    }.get(load_tier or "", 2)


def sha256_for_files(paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in paths:
        digest.update(rel_path(path).encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def now_iso() -> str:
    return subprocess.run(
        [sys.executable, "-c", "from datetime import datetime; print(datetime.now().astimezone().isoformat())"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def git_output(*args: str) -> str | None:
    try:
        completed = subprocess.run(
            ["git", *args],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    return completed.stdout.strip()


def hot_context_stats() -> dict[str, object]:
    file_stats = []
    total_lines = 0
    total_bytes = 0
    shape_violations: list[str] = []
    for path in HOT_CONTEXT_FILES:
        text = read_text(path)
        line_count = len(text.splitlines())
        byte_count = len(text.encode("utf-8"))
        total_lines += line_count
        total_bytes += byte_count
        relative_path = rel_path(path)
        file_state = {
            "path": relative_path,
            "lines": line_count,
            "bytes": byte_count,
        }
        spec = HOT_CONTEXT_FILE_SPECS.get(relative_path)
        if spec:
            headings = markdown_heading_titles(text, level=2)
            allowed_headings = spec["allowedHeadings"]
            required_headings = spec["requiredHeadings"]
            expected_headings = spec.get("headingOrder", [])
            missing_headings = [heading for heading in required_headings if heading not in headings]
            extra_headings = [heading for heading in headings if heading not in allowed_headings]
            heading_order_matches = not expected_headings or headings == expected_headings
            within_shape = (
                line_count <= spec["maxLines"]
                and byte_count <= spec["maxBytes"]
                and not missing_headings
                and not extra_headings
                and heading_order_matches
            )
            file_state.update(
                {
                    "maxLines": spec["maxLines"],
                    "maxBytes": spec["maxBytes"],
                    "allowedHeadings": allowed_headings,
                    "requiredHeadings": required_headings,
                    "expectedHeadings": expected_headings,
                    "headings": headings,
                    "missingHeadings": missing_headings,
                    "extraHeadings": extra_headings,
                    "headingOrderMatches": heading_order_matches,
                    "withinShape": within_shape,
                }
            )
            if line_count > spec["maxLines"]:
                shape_violations.append(f"{relative_path}:lines")
            if byte_count > spec["maxBytes"]:
                shape_violations.append(f"{relative_path}:bytes")
            if missing_headings:
                shape_violations.append(f"{relative_path}:missing-headings")
            if extra_headings:
                shape_violations.append(f"{relative_path}:extra-headings")
            if not heading_order_matches:
                shape_violations.append(f"{relative_path}:heading-order")
        file_stats.append(file_state)
    return {
        "files": file_stats,
        "totalLines": total_lines,
        "totalBytes": total_bytes,
        "maxLines": HOT_CONTEXT_MAX_LINES,
        "maxBytes": HOT_CONTEXT_MAX_BYTES,
        "withinBudget": total_lines <= HOT_CONTEXT_MAX_LINES and total_bytes <= HOT_CONTEXT_MAX_BYTES,
        "shapeViolations": shape_violations,
        "shapeValid": not shape_violations,
    }


def path_exists(relative_path: str) -> bool:
    if "*" in relative_path or relative_path.startswith("target/"):
        return True
    return (ROOT / relative_path).exists()


def collect_path_values(node: object) -> list[str]:
    values: list[str] = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "path" and isinstance(value, str):
                values.append(value)
            else:
                values.extend(collect_path_values(value))
    elif isinstance(node, list):
        for item in node:
            values.extend(collect_path_values(item))
    return values


def parse_top_level_type(path: Path) -> str:
    pattern = re.compile(r"\b(class|interface|enum|record)\s+([A-Za-z0-9_]+)")
    for line in read_text(path).splitlines():
        match = pattern.search(line)
        if match:
            return match.group(1)
    return "class"


def find_java_symbol(symbol_name: str) -> Path | None:
    candidates = sorted(ROOT.rglob(f"{symbol_name}.java"))
    preferred = [path for path in candidates if "src/main/java" in path.as_posix()]
    pool = preferred or candidates
    for path in pool:
        parts = set(path.parts)
        if "target" in parts or ".git" in parts or ".idea" in parts:
            continue
        return path
    return None


def load_xml(path: Path) -> ET.Element:
    return ET.fromstring(read_text(path))


def find_text(element: ET.Element, expression: str) -> str | None:
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    value = element.findtext(expression, namespaces=namespace)
    if value is None:
        return None
    stripped = value.strip()
    return stripped or None


def parse_pom(path: Path) -> dict[str, object]:
    root = load_xml(path)
    group_id = find_text(root, "m:groupId") or find_text(root, "m:parent/m:groupId")
    version = find_text(root, "m:version") or find_text(root, "m:parent/m:version")
    artifact_id = find_text(root, "m:artifactId")
    packaging = find_text(root, "m:packaging") or "jar"
    modules = [
        module.text.strip()
        for module in root.findall("m:modules/m:module", {"m": "http://maven.apache.org/POM/4.0.0"})
        if module.text and module.text.strip()
    ]
    profiles = [
        profile.findtext("m:id", namespaces={"m": "http://maven.apache.org/POM/4.0.0"}).strip()
        for profile in root.findall("m:profiles/m:profile", {"m": "http://maven.apache.org/POM/4.0.0"})
        if profile.findtext("m:id", namespaces={"m": "http://maven.apache.org/POM/4.0.0"})
    ]
    return {
        "path": rel_path(path),
        "groupId": group_id,
        "artifactId": artifact_id,
        "version": version,
        "packaging": packaging,
        "modules": modules,
        "profiles": profiles,
        "javaRelease": find_text(root, "m:properties/m:maven.compiler.release"),
        "springBootVersion": find_text(root, "m:properties/m:spring.boot.version"),
    }


def workflow_jobs(path: Path) -> list[str]:
    jobs: list[str] = []
    in_jobs = False
    for line in read_text(path).splitlines():
        if not in_jobs:
            if line.strip() == "jobs:":
                in_jobs = True
            continue
        if line and not line.startswith(" "):
            break
        match = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if match:
            jobs.append(match.group(1))
    return jobs


def workflow_java_versions(path: Path) -> list[int]:
    text = read_text(path)
    match = re.search(r"java:\s*\[([^\]]+)\]", text)
    if not match:
        return []
    versions: list[int] = []
    for token in match.group(1).split(","):
        token = token.strip().strip("'\"")
        if token.isdigit():
            versions.append(int(token))
    return versions


def release_modules_from_workflow(path: Path) -> list[str]:
    text = read_text(path)
    match = re.search(r"-pl\s+([^\s]+)\s+-am\s+-Prelease-central", text)
    if not match:
        return []
    return [module.strip() for module in match.group(1).split(",") if module.strip()]


def count_files(base: Path, pattern: str) -> int:
    if not base.exists():
        return 0
    return sum(1 for _ in base.rglob(pattern))


def doc_category(relative_path: str) -> tuple[str, str, str | None]:
    hot_paths = {rel_path(path) for path in HOT_CONTEXT_FILES}
    high_product_docs = {
        "docs/entry-points.md",
        "docs/reusable-wrappers.md",
        "docs/usecases.md",
        "docs/advanced-features.md",
        "docs/sql-like.md",
        "docs/charts.md",
    }
    if relative_path == "AGENTS.md":
        return ("agent-guide", "high", None)
    if relative_path == "ai/AGENTS.md":
        return ("ai-memory-guide", "high", "cold")
    if relative_path in hot_paths:
        return ("ai-hot-context", "high", "hot")
    if relative_path == rel_path(RECENT_VALIDATIONS_PATH):
        return ("ai-validation-history", "high", "warm")
    if relative_path.startswith("ai/log/archive/") and relative_path.endswith("-summary.md"):
        return ("ai-archive-summary", "medium", "cold")
    if relative_path == "ai/state/benchmark-state.md":
        return ("ai-benchmark-state", "medium", "cold")
    if relative_path.startswith("ai/core/"):
        return ("ai-core", "medium", "cold")
    if relative_path.startswith("ai/state/"):
        return ("ai-state", "medium", "cold")
    if relative_path == "README.md":
        return ("readme", "high", None)
    if relative_path == "TODO.md":
        return ("planning", "high", None)
    if relative_path == "CONTRIBUTING.md":
        return ("process-doc", "high", None)
    if relative_path == "RELEASE.md":
        return ("release-doc", "high", None)
    if relative_path == "MIGRATION.md":
        return ("process-doc", "medium", None)
    if relative_path == "MAINTENANCE.md":
        return ("memory-maintenance", "medium", None)
    if relative_path.startswith("docs/"):
        relevance = "high" if relative_path in high_product_docs else "medium"
        return ("product-doc", relevance, None)
    if relative_path.endswith(".md"):
        return ("document", "medium", None)
    return ("artifact", "low", None)


def build_docs_index(cold_search_paths: set[str], generated_at: str) -> dict[str, object]:
    documents = []
    for path in collect_markdown_files():
        relative_path = rel_path(path)
        category, relevance, load_tier = doc_category(relative_path)
        text = read_text(path)
        documents.append(
            {
                "path": relative_path,
                "category": category,
                "relevance": relevance,
                "loadTier": load_tier,
                "lineCount": len(text.splitlines()),
                "byteCount": len(text.encode("utf-8")),
                "coldSearchEligible": relative_path in cold_search_paths,
            }
        )
    return {
        "generatedAt": generated_at,
        "documents": documents,
    }


def build_files_index(generated_at: str) -> dict[str, object]:
    module_roots = []
    for spec in MODULE_SPECS:
        module_path = ROOT / spec["path"]
        source_root = module_path / "src" / "main" / "java"
        test_root = module_path / "src" / "test" / "java"
        resource_root = module_path / "src" / "main" / "resources"
        module_roots.append(
            {
                "path": spec["path"],
                "kind": spec["kind"],
                "role": spec["role"],
                "published": spec["published"],
                "sourceRoot": rel_path(source_root) if source_root.exists() else None,
                "testRoot": rel_path(test_root) if test_root.exists() else None,
                "resourceRoot": rel_path(resource_root) if resource_root.exists() else None,
                "mainJavaFiles": count_files(source_root, "*.java"),
                "testJavaFiles": count_files(test_root, "*.java"),
                "resourceFiles": sum(1 for path in resource_root.rglob("*") if path.is_file()) if resource_root.exists() else 0,
            }
        )

    main_java_files = collect_java_files(
        "pojo-lens/src/main/java",
        "pojo-lens-benchmarks/src/main/java",
        "pojo-lens-spring-boot-autoconfigure/src/main/java",
        "pojo-lens-spring-boot-starter/src/main/java",
        "examples/spring-boot-starter-basic/src/main/java",
        "examples/spring-boot-starter-quickstart/src/main/java",
    )
    test_java_files = collect_java_files(
        "pojo-lens/src/test/java",
        "pojo-lens-benchmarks/src/test/java",
        "pojo-lens-spring-boot-autoconfigure/src/test/java",
        "pojo-lens-spring-boot-starter/src/test/java",
        "examples/spring-boot-starter-basic/src/test/java",
        "examples/spring-boot-starter-quickstart/src/test/java",
    )

    important_files = [
        {"path": "pom.xml", "kind": "build"},
        {"path": "pojo-lens/pom.xml", "kind": "module-build"},
        {"path": "pojo-lens-spring-boot-autoconfigure/pom.xml", "kind": "module-build"},
        {"path": "pojo-lens-spring-boot-starter/pom.xml", "kind": "module-build"},
        {"path": "pojo-lens-benchmarks/pom.xml", "kind": "module-build"},
        {"path": ".github/workflows/ci.yml", "kind": "ci"},
        {"path": ".github/workflows/release.yml", "kind": "release-ci"},
        {"path": "README.md", "kind": "product-doc"},
        {"path": "CONTRIBUTING.md", "kind": "process-doc"},
        {"path": "RELEASE.md", "kind": "process-doc"},
        {"path": "TODO.md", "kind": "planning"},
        {"path": "MAINTENANCE.md", "kind": "memory-maintenance"},
        {"path": "ai/state/recent-validations.md", "kind": "ai-warm-state"},
        {"path": "scripts/refresh-ai-memory.py", "kind": "memory-script"},
        {"path": "scripts/query-ai-memory.py", "kind": "memory-script"},
        {"path": "scripts/benchmark-ai-memory.py", "kind": "memory-script"},
        {"path": "scripts/check-doc-consistency.ps1", "kind": "validation-script"},
        {"path": "scripts/check-lint-baseline.ps1", "kind": "validation-script"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/PojoLens.java", "kind": "public-entry"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/PojoLensCore.java", "kind": "public-entry"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/PojoLensSql.java", "kind": "public-entry"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/PojoLensRuntime.java", "kind": "public-entry"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java", "kind": "engine"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/filter/FilterImpl.java", "kind": "engine"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/sqllike/SqlLikeQuery.java", "kind": "engine"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/sqllike/parser/SqlLikeParser.java", "kind": "engine"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/chart/ChartQueryPreset.java", "kind": "feature"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/stats/StatsViewPresets.java", "kind": "feature"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/report/ReportDefinition.java", "kind": "feature"},
        {"path": "pojo-lens/src/main/java/laughing/man/commits/chartjs/ChartJsAdapter.java", "kind": "feature"},
        {"path": "examples/spring-boot-starter-basic/src/main/java/laughing/man/commits/examples/spring/boot/basic/EmployeeDashboardService.java", "kind": "example"},
        {"path": "examples/spring-boot-starter-quickstart/src/main/java/laughing/man/commits/examples/spring/boot/quickstart/QuickstartEmployeeController.java", "kind": "example"},
    ]

    return {
        "generatedAt": generated_at,
        "notes": "Generated navigation anchors for the current multi-module layout. Markdown remains the source of truth; target/ outputs are excluded.",
        "counts": {
            "modules": len(MODULE_SPECS),
            "mainJavaFiles": len(main_java_files),
            "testJavaFiles": len(test_java_files),
            "markdownDocs": len(collect_markdown_files()),
            "aiCoreFiles": sum(1 for _ in (AI_DIR / "core").glob("*.md")),
            "aiIndexFiles": sum(1 for _ in INDEX_DIR.glob("*.json")),
        },
        "roots": [
            {"path": ".github/workflows", "kind": "ci"},
            {"path": "ai/core", "kind": "ai-core"},
            {"path": "ai/state", "kind": "ai-state"},
            {"path": "ai/indexes", "kind": "ai-indexes"},
            {"path": "ai/log", "kind": "ai-log"},
            {"path": "ai/log/archive", "kind": "ai-log-archive"},
            {"path": "benchmarks", "kind": "benchmark-config"},
            {"path": "docs", "kind": "documentation"},
            {"path": "scripts", "kind": "repo-scripts"},
            {"path": "pojo-lens/src/main/java/laughing/man/commits", "kind": "runtime-source-root"},
            {"path": "pojo-lens/src/test/java/laughing/man/commits", "kind": "runtime-test-root"},
            {"path": "pojo-lens-benchmarks/src/main/java/laughing/man/commits/benchmark", "kind": "benchmark-source-root"},
            {"path": "examples/spring-boot-starter-basic", "kind": "example"},
            {"path": "examples/spring-boot-starter-quickstart", "kind": "example"},
        ],
        "moduleRoots": module_roots,
        "importantFiles": [entry for entry in important_files if path_exists(entry["path"])],
    }


def build_symbols_index(generated_at: str) -> dict[str, object]:
    groups = []
    for component, symbols in SYMBOL_GROUPS.items():
        resolved = []
        for symbol in symbols:
            path = find_java_symbol(symbol)
            if path is None:
                continue
            resolved.append(
                {
                    "name": symbol,
                    "kind": parse_top_level_type(path),
                    "path": rel_path(path),
                }
            )
        if resolved:
            groups.append({"component": component, "symbols": resolved})
    return {
        "generatedAt": generated_at,
        "groups": groups,
    }


def classify_test(relative_path: str) -> str:
    name = Path(relative_path).name
    if relative_path.startswith("examples/"):
        return "starter-example"
    if "benchmark" in relative_path or "Benchmark" in name:
        return "benchmark-tooling"
    if "publicapi/" in relative_path or name.startswith(("PublicApi", "PublicSurface", "StablePublicApi")):
        return "public-surface"
    if name.endswith("DocsExamplesTest.java"):
        return "docs-examples"
    if "sqllike/" in relative_path or name.startswith("SqlLike"):
        return "sql-like"
    if "Cache" in name or "Runtime" in name or "AutoConfiguration" in name or "StarterSmoke" in name:
        return "runtime-and-cache"
    if any(token in name for token in ("Chart", "Report", "Stats", "Snapshot", "RegressionFixture")) or "/chart/" in relative_path:
        return "charts-stats-reports"
    return "fluent-and-query-engine"


def build_test_index(generated_at: str) -> dict[str, object]:
    test_roots = []
    for root in [
        "pojo-lens/src/test/java",
        "pojo-lens/src/test/resources/fixtures",
        "pojo-lens-benchmarks/src/test/java",
        "pojo-lens-benchmarks/src/test/resources/fixtures",
        "pojo-lens-spring-boot-autoconfigure/src/test/java",
        "pojo-lens-spring-boot-starter/src/test/java",
        "examples/spring-boot-starter-basic/src/test/java",
        "examples/spring-boot-starter-quickstart/src/test/java",
    ]:
        if (ROOT / root).exists():
            test_roots.append(root)

    test_files = collect_java_files(
        "pojo-lens/src/test/java",
        "pojo-lens-benchmarks/src/test/java",
        "pojo-lens-spring-boot-autoconfigure/src/test/java",
        "pojo-lens-spring-boot-starter/src/test/java",
        "examples/spring-boot-starter-basic/src/test/java",
        "examples/spring-boot-starter-quickstart/src/test/java",
    )
    categories: dict[str, list[str]] = {}
    for path in test_files:
        relative_path = rel_path(path)
        category = classify_test(relative_path)
        categories.setdefault(category, []).append(relative_path)

    module_counts = []
    for spec in MODULE_SPECS:
        test_root = ROOT / spec["path"] / "src" / "test" / "java"
        if test_root.exists():
            module_counts.append(
                {
                    "path": spec["path"],
                    "testJavaFiles": count_files(test_root, "*.java"),
                }
            )

    return {
        "generatedAt": generated_at,
        "testRoots": test_roots,
        "counts": {
            "testJavaFiles": len(test_files),
            "testClasses": len(test_files),
        },
        "modules": module_counts,
        "suites": [
            {
                "category": category,
                "files": sorted(paths),
            }
            for category, paths in sorted(categories.items())
        ],
    }


def build_config_index(generated_at: str) -> dict[str, object]:
    root_pom = parse_pom(ROOT / "pom.xml")
    module_poms = [parse_pom(ROOT / f"{module['path']}/pom.xml") for module in MODULE_SPECS if (ROOT / module["path"] / "pom.xml").exists()]
    ci_path = ROOT / ".github" / "workflows" / "ci.yml"
    release_path = ROOT / ".github" / "workflows" / "release.yml"
    return {
        "generatedAt": generated_at,
        "build": {
            "path": "pom.xml",
            "groupId": root_pom["groupId"],
            "artifactId": root_pom["artifactId"],
            "version": root_pom["version"],
            "packaging": root_pom["packaging"],
            "javaRelease": int(root_pom["javaRelease"]) if root_pom["javaRelease"] else None,
            "springBootVersion": root_pom["springBootVersion"],
            "modules": [
                {
                    "path": pom["path"],
                    "artifactId": pom["artifactId"],
                    "packaging": pom["packaging"],
                    "role": next(spec["role"] for spec in MODULE_SPECS if f"{spec['path']}/pom.xml" == pom["path"]),
                    "published": next(spec["published"] for spec in MODULE_SPECS if f"{spec['path']}/pom.xml" == pom["path"]),
                }
                for pom in module_poms
            ],
            "profiles": sorted(set(root_pom["profiles"])),
        },
        "ci": {
            "workflows": [
                {
                    "path": ".github/workflows/ci.yml",
                    "jobs": workflow_jobs(ci_path),
                    "testJavaVersions": workflow_java_versions(ci_path),
                },
                {
                    "path": ".github/workflows/release.yml",
                    "jobs": workflow_jobs(release_path),
                    "triggerTags": ["v*"],
                    "manualDispatch": True,
                    "releaseModules": release_modules_from_workflow(release_path),
                },
            ]
        },
        "aiMemory": {
            "sourceOfTruth": [
                "ai/core/*.md",
                "ai/state/*.md",
                "ai/log/events.jsonl",
                "ai/log/archive/*.jsonl",
            ],
            "hotContext": [rel_path(path) for path in HOT_CONTEXT_FILES],
            "warmContext": [rel_path(RECENT_VALIDATIONS_PATH)],
            "generatedIndexes": [
                "ai/indexes/files-index.json",
                "ai/indexes/docs-index.json",
                "ai/indexes/symbols-index.json",
                "ai/indexes/test-index.json",
                "ai/indexes/config-index.json",
                "ai/indexes/refresh-state.json",
            ],
            "optionalColdSearchDb": "ai/indexes/cold-memory.db",
            "refreshCommand": "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1",
            "checkCommand": "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -Check",
            "compactCommand": "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -CompactLog",
            "fullRefreshCommand": "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -ForceFull",
            "searchCommand": "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/query-ai-memory.ps1 -Query <text>",
            "benchmarkCommand": "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/benchmark-ai-memory.ps1",
            "eventRetention": {
                "activeLog": "ai/log/events.jsonl",
                "archivePattern": "ai/log/archive/*.jsonl",
                "recentEntries": ACTIVE_EVENT_RETENTION,
            },
        },
        "validationScripts": [
            "scripts/check-doc-consistency.ps1",
            "scripts/check-doc-consistency.py",
            "scripts/check-lint-baseline.ps1",
            "scripts/refresh-ai-memory.ps1",
            "scripts/refresh-ai-memory.py",
        ],
        "memoryScripts": [
            "scripts/query-ai-memory.ps1",
            "scripts/query-ai-memory.py",
            "scripts/benchmark-ai-memory.ps1",
            "scripts/benchmark-ai-memory.py",
        ],
        "releaseScripts": [
            "scripts/export-release-secrets.ps1",
        ],
        "benchmarkConfigs": [
            "benchmarks/thresholds.json",
            "benchmarks/chart-thresholds.json",
            "scripts/benchmark-suite-main.args",
            "scripts/benchmark-suite-chart.args",
            "scripts/benchmark-suite-hotspots.args",
            "scripts/benchmark-suite-baseline.args",
            "scripts/benchmark-suite-cache.args",
            "scripts/benchmark-suite-indexes.args",
            "scripts/benchmark-suite-streaming.args",
            "scripts/benchmark-suite-window.args",
        ],
    }


def split_markdown_sections(path: Path, text: str, source_kind: str, load_tier: str | None) -> list[dict[str, object]]:
    sections: list[dict[str, object]] = []
    current_title = path.name
    current_lines: list[str] = []
    line_start = 1
    current_line_number = 1
    for line in text.splitlines():
        heading_match = re.match(r"^(#+)\s+(.+?)\s*$", line)
        if heading_match:
            if current_lines:
                sections.append(
                    {
                        "path": rel_path(path),
                        "title": current_title,
                        "sourceKind": source_kind,
                        "loadTier": load_tier or "",
                        "priority": load_tier_priority(load_tier),
                        "sortTs": "",
                        "lineStart": line_start,
                        "lineEnd": current_line_number - 1,
                        "content": "\n".join(current_lines).strip(),
                    }
                )
            current_title = heading_match.group(2)
            current_lines = [line]
            line_start = current_line_number
        else:
            current_lines.append(line)
        current_line_number += 1
    if current_lines:
        sections.append(
            {
                "path": rel_path(path),
                "title": current_title,
                "sourceKind": source_kind,
                "loadTier": load_tier or "",
                "priority": load_tier_priority(load_tier),
                "sortTs": "",
                "lineStart": line_start,
                "lineEnd": current_line_number - 1,
                "content": "\n".join(current_lines).strip(),
            }
        )
    return [section for section in sections if section["content"]]


def split_event_log_sections(path: Path) -> list[dict[str, object]]:
    sections = []
    relative_path = rel_path(path)
    is_archive = relative_path.startswith("ai/log/archive/")
    load_tier = "archive" if is_archive else "warm"
    source_kind = "ai-log-archive" if is_archive else "ai-log"
    for line_number, line in enumerate(read_text(path).splitlines(), start=1):
        if not line.strip():
            continue
        try:
            payload = json.loads(line)
        except json.JSONDecodeError:
            continue
        title = f"{payload.get('type', 'event')} {payload.get('ts', '')}".strip()
        summary = str(payload.get("summary", "")).strip()
        event_type = str(payload.get("type", "")).strip()
        content_parts = [part for part in [event_type, summary] if part]
        sections.append(
            {
                "path": relative_path,
                "title": title,
                "sourceKind": source_kind,
                "loadTier": load_tier,
                "priority": load_tier_priority(load_tier),
                "sortTs": str(payload.get("ts", "")),
                "lineStart": line_number,
                "lineEnd": line_number,
                "content": "\n".join(content_parts),
            }
        )
    return sections


def build_cold_search_sections() -> list[dict[str, object]]:
    sections: list[dict[str, object]] = []
    for path in collect_cold_search_files():
        sections.extend(build_sections_for_path(path))
    return sections


def build_sections_for_path(path: Path) -> list[dict[str, object]]:
    relative_path = rel_path(path)
    if relative_path.endswith(".md"):
        category, _, load_tier = doc_category(relative_path)
        return split_markdown_sections(path, read_text(path), category, load_tier)
    if relative_path.startswith("ai/log/") and relative_path.endswith(".jsonl"):
        return split_event_log_sections(path)
    return []


def section_id_for(section: dict[str, object]) -> str:
    digest = hashlib.sha256()
    for value in [
        str(section["path"]),
        str(section["title"]),
        str(section["sourceKind"]),
        str(section["loadTier"]),
        str(section["sortTs"]),
        str(section["lineStart"]),
        str(section["lineEnd"]),
        str(section["content"]),
    ]:
        digest.update(value.encode("utf-8"))
        digest.update(b"\0")
    return digest.hexdigest()


def index_input_paths(index_name: str) -> list[Path]:
    if index_name == "docs":
        return sorted(set(collect_markdown_files() + [Path(__file__).resolve()]))
    if index_name == "files":
        return sorted(
            set(
                collect_markdown_files()
                + repo_files_for_patterns(
                    [
                        "pom.xml",
                        "pojo-lens/pom.xml",
                        "pojo-lens-benchmarks/pom.xml",
                        "pojo-lens-spring-boot-autoconfigure/pom.xml",
                        "pojo-lens-spring-boot-starter/pom.xml",
                        ".github/workflows/*.yml",
                        "scripts/refresh-ai-memory.py",
                        "scripts/refresh-ai-memory.ps1",
                        "scripts/benchmark-ai-memory.py",
                        "scripts/benchmark-ai-memory.ps1",
                    ]
                )
                + collect_java_files(
                    "pojo-lens/src/main/java",
                    "pojo-lens/src/test/java",
                    "pojo-lens-benchmarks/src/main/java",
                    "pojo-lens-benchmarks/src/test/java",
                    "pojo-lens-spring-boot-autoconfigure/src/main/java",
                    "pojo-lens-spring-boot-autoconfigure/src/test/java",
                    "pojo-lens-spring-boot-starter/src/main/java",
                    "pojo-lens-spring-boot-starter/src/test/java",
                    "examples/spring-boot-starter-basic/src/main/java",
                    "examples/spring-boot-starter-basic/src/test/java",
                    "examples/spring-boot-starter-quickstart/src/main/java",
                    "examples/spring-boot-starter-quickstart/src/test/java",
                )
            )
        )
    if index_name == "symbols":
        return sorted(
            set(
                collect_java_files(
                    "pojo-lens/src/main/java",
                    "pojo-lens-benchmarks/src/main/java",
                    "pojo-lens-spring-boot-autoconfigure/src/main/java",
                    "pojo-lens-spring-boot-starter/src/main/java",
                    "examples/spring-boot-starter-basic/src/main/java",
                    "examples/spring-boot-starter-quickstart/src/main/java",
                )
                + [Path(__file__).resolve()]
            )
        )
    if index_name == "test":
        return sorted(
            set(
                collect_java_files(
                    "pojo-lens/src/test/java",
                    "pojo-lens-benchmarks/src/test/java",
                    "pojo-lens-spring-boot-autoconfigure/src/test/java",
                    "pojo-lens-spring-boot-starter/src/test/java",
                    "examples/spring-boot-starter-basic/src/test/java",
                    "examples/spring-boot-starter-quickstart/src/test/java",
                )
                + [Path(__file__).resolve()]
            )
        )
    if index_name == "config":
        return sorted(
            set(
                repo_files_for_patterns(
                    [
                        "pom.xml",
                        "pojo-lens/pom.xml",
                        "pojo-lens-benchmarks/pom.xml",
                        "pojo-lens-spring-boot-autoconfigure/pom.xml",
                        "pojo-lens-spring-boot-starter/pom.xml",
                        ".github/workflows/*.yml",
                        "scripts/refresh-ai-memory.py",
                        "scripts/refresh-ai-memory.ps1",
                        "scripts/query-ai-memory.py",
                        "scripts/query-ai-memory.ps1",
                        "scripts/benchmark-ai-memory.py",
                        "scripts/benchmark-ai-memory.ps1",
                    ]
                )
            )
        )
    raise ValueError(f"Unknown index name: {index_name}")


def build_or_reuse_json_index(
    index_name: str,
    generated_at: str,
    refresh_cache: dict[str, object],
    builder: Callable[[str], dict[str, object]],
) -> tuple[dict[str, object], dict[str, object], dict[str, object]]:
    input_paths = index_input_paths(index_name)
    path_hashes = {rel_path(path): file_sha256(path) for path in input_paths}
    input_hash = hash_for_path_hashes(path_hashes)
    output_path = INDEX_OUTPUTS[index_name]
    cached_entry = refresh_cache.get(index_name, {})
    if (
        output_path.exists()
        and cached_entry.get("inputHash") == input_hash
        and cached_entry.get("outputPath") == rel_path(output_path)
    ):
        try:
            payload = json.loads(read_text(output_path))
            return (
                payload,
                {
                    "path": rel_path(output_path),
                    "status": "reused",
                    "inputHash": input_hash,
                    "inputFiles": len(path_hashes),
                },
                {
                    "outputPath": rel_path(output_path),
                    "inputHash": input_hash,
                    "pathHashes": path_hashes,
                },
            )
        except json.JSONDecodeError:
            pass

    payload = builder(generated_at)
    write_json(output_path, payload)
    return (
        payload,
        {
            "path": rel_path(output_path),
            "status": "rebuilt",
            "inputHash": input_hash,
            "inputFiles": len(path_hashes),
        },
        {
            "outputPath": rel_path(output_path),
            "inputHash": input_hash,
            "pathHashes": path_hashes,
        },
    )


def sqlite_table_exists(cursor: sqlite3.Cursor, name: str) -> bool:
    row = cursor.execute(
        "SELECT name FROM sqlite_master WHERE type IN ('table', 'view') AND name = ?",
        (name,),
    ).fetchone()
    return row is not None


def sqlite_table_columns(cursor: sqlite3.Cursor, table_name: str) -> set[str]:
    if not sqlite_table_exists(cursor, table_name):
        return set()
    rows = cursor.execute(f"PRAGMA table_info({table_name})").fetchall()
    return {str(row[1]) for row in rows}


def reset_sqlite_schema(cursor: sqlite3.Cursor) -> None:
    cursor.executescript(
        """
        DROP TABLE IF EXISTS metadata;
        DROP TABLE IF EXISTS source_files;
        DROP TABLE IF EXISTS documents;
        DROP TABLE IF EXISTS document_fts;
        """
    )


def ensure_sqlite_schema(cursor: sqlite3.Cursor, force_full: bool) -> bool:
    required_document_columns = {
        "section_id",
        "source_path",
        "path",
        "title",
        "source_kind",
        "load_tier",
        "priority",
        "sort_ts",
        "line_start",
        "line_end",
        "content",
    }
    reset_required = force_full or not required_document_columns.issubset(sqlite_table_columns(cursor, "documents"))
    if reset_required:
        reset_sqlite_schema(cursor)

    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS source_files (
            path TEXT PRIMARY KEY,
            file_hash TEXT NOT NULL,
            section_count INTEGER NOT NULL,
            updated_at TEXT NOT NULL
        )
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS documents (
            section_id TEXT PRIMARY KEY,
            source_path TEXT NOT NULL,
            path TEXT NOT NULL,
            title TEXT NOT NULL,
            source_kind TEXT NOT NULL,
            load_tier TEXT NOT NULL,
            priority INTEGER NOT NULL,
            sort_ts TEXT NOT NULL,
            line_start INTEGER NOT NULL,
            line_end INTEGER NOT NULL,
            content TEXT NOT NULL
        )
        """
    )
    cursor.execute("CREATE INDEX IF NOT EXISTS documents_path_idx ON documents(path)")
    cursor.execute("CREATE INDEX IF NOT EXISTS documents_source_idx ON documents(source_path)")
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS documents_rank_idx ON documents(priority, sort_ts DESC, path, line_start)"
    )

    fts_enabled = sqlite_table_exists(cursor, "document_fts")
    if not fts_enabled:
        try:
            cursor.execute(
                """
                CREATE VIRTUAL TABLE document_fts
                USING fts5(
                    section_id UNINDEXED,
                    source_path UNINDEXED,
                    path,
                    title,
                    source_kind,
                    load_tier,
                    content
                )
                """
            )
            fts_enabled = True
        except sqlite3.Error:
            fts_enabled = False
    return fts_enabled


def create_sqlite_db(
    sqlite_path: Path,
    generated_at: str,
    inputs_hash: str,
    require_sqlite: bool,
    force_full: bool,
) -> dict[str, object]:
    sqlite_path.parent.mkdir(parents=True, exist_ok=True)
    cold_paths = collect_cold_search_files()
    file_hashes = {rel_path(path): file_sha256(path) for path in cold_paths}
    try:
        connection = sqlite3.connect(sqlite_path)
    except sqlite3.Error as exc:
        if require_sqlite:
            raise
        return {
            "status": "unavailable",
            "path": rel_path(sqlite_path),
            "ftsEnabled": False,
            "documents": 0,
            "sections": 0,
            "updatedFiles": 0,
            "reusedFiles": 0,
            "removedFiles": 0,
            "error": str(exc),
        }

    try:
        cursor = connection.cursor()
        fts_enabled = ensure_sqlite_schema(cursor, force_full=force_full)
        existing_hashes = {
            str(path): str(file_hash)
            for path, file_hash in cursor.execute("SELECT path, file_hash FROM source_files")
        }

        current_paths = set(file_hashes)
        existing_paths = set(existing_hashes)
        removed_paths = sorted(existing_paths - current_paths)
        changed_paths = sorted(
            relative_path
            for relative_path, file_hash in file_hashes.items()
            if force_full or existing_hashes.get(relative_path) != file_hash
        )
        reused_files = len(current_paths) - len(changed_paths)

        for relative_path in removed_paths:
            cursor.execute("DELETE FROM documents WHERE source_path = ?", (relative_path,))
            if fts_enabled:
                cursor.execute("DELETE FROM document_fts WHERE source_path = ?", (relative_path,))
            cursor.execute("DELETE FROM source_files WHERE path = ?", (relative_path,))

        for relative_path in changed_paths:
            source_path = ROOT / relative_path
            sections = build_sections_for_path(source_path)
            current_section_ids = []
            fts_rows = []
            for section in sections:
                section_id = section_id_for(section)
                current_section_ids.append(section_id)
                cursor.execute(
                    """
                    INSERT INTO documents(
                        section_id,
                        source_path,
                        path,
                        title,
                        source_kind,
                        load_tier,
                        priority,
                        sort_ts,
                        line_start,
                        line_end,
                        content
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(section_id) DO UPDATE SET
                        source_path = excluded.source_path,
                        path = excluded.path,
                        title = excluded.title,
                        source_kind = excluded.source_kind,
                        load_tier = excluded.load_tier,
                        priority = excluded.priority,
                        sort_ts = excluded.sort_ts,
                        line_start = excluded.line_start,
                        line_end = excluded.line_end,
                        content = excluded.content
                    """,
                    (
                        section_id,
                        relative_path,
                        section["path"],
                        section["title"],
                        section["sourceKind"],
                        section["loadTier"],
                        section["priority"],
                        section["sortTs"],
                        section["lineStart"],
                        section["lineEnd"],
                        section["content"],
                    ),
                )
                if fts_enabled:
                    fts_rows.append(
                        (
                            section_id,
                            relative_path,
                            section["path"],
                            section["title"],
                            section["sourceKind"],
                            section["loadTier"],
                            section["content"],
                        )
                    )

            if current_section_ids:
                placeholders = ",".join("?" for _ in current_section_ids)
                cursor.execute(
                    f"DELETE FROM documents WHERE source_path = ? AND section_id NOT IN ({placeholders})",
                    [relative_path, *current_section_ids],
                )
            else:
                cursor.execute("DELETE FROM documents WHERE source_path = ?", (relative_path,))

            if fts_enabled:
                cursor.execute("DELETE FROM document_fts WHERE source_path = ?", (relative_path,))
                cursor.executemany(
                    """
                    INSERT INTO document_fts(section_id, source_path, path, title, source_kind, load_tier, content)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    fts_rows,
                )

            cursor.execute(
                """
                INSERT INTO source_files(path, file_hash, section_count, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(path) DO UPDATE SET
                    file_hash = excluded.file_hash,
                    section_count = excluded.section_count,
                    updated_at = excluded.updated_at
                """,
                (
                    relative_path,
                    file_hashes[relative_path],
                    len(current_section_ids),
                    generated_at,
                ),
            )

        cursor.execute("DELETE FROM metadata")
        metadata = {
            "generatedAt": generated_at,
            "inputsHash": inputs_hash,
            "ftsEnabled": "true" if fts_enabled else "false",
            "incremental": "true",
        }
        for key, value in metadata.items():
            cursor.execute("INSERT INTO metadata(key, value) VALUES (?, ?)", (key, value))
        connection.commit()

        document_count = cursor.execute("SELECT COUNT(DISTINCT path) FROM documents").fetchone()[0]
        section_count = cursor.execute("SELECT COUNT(*) FROM documents").fetchone()[0]
    finally:
        connection.close()

    return {
        "status": "built",
        "path": rel_path(sqlite_path),
        "ftsEnabled": fts_enabled,
        "documents": int(document_count),
        "sections": int(section_count),
        "updatedFiles": len(changed_paths),
        "reusedFiles": reused_files,
        "removedFiles": len(removed_paths),
        "mode": "full" if force_full else "incremental",
    }


def build_memory_state(
    generated_at: str,
    inputs_hash: str,
    hot_stats: dict[str, object],
    json_index_states: list[dict[str, object]],
    sqlite_state: dict[str, object],
    path_check_errors: list[str],
) -> dict[str, object]:
    branch = git_output("branch", "--show-current")
    head_commit = git_output("rev-parse", "HEAD")
    dirty = bool(git_output("status", "--short"))
    event_state = event_log_stats()
    freshness_reasons = []
    if not hot_stats["withinBudget"]:
        freshness_reasons.append("hot-context-over-budget")
    if not hot_stats.get("shapeValid", True):
        freshness_reasons.append("hot-context-shape-invalid")
    if path_check_errors:
        freshness_reasons.append("indexed-paths-missing")
    if sqlite_state.get("status") == "unavailable":
        freshness_reasons.append("sqlite-unavailable")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": generated_at,
        "git": {
            "headCommit": head_commit,
            "branch": branch,
            "dirty": dirty,
        },
        "inputsHash": inputs_hash,
        "hotContext": hot_stats,
        "eventLog": event_state,
        "derivedArtifacts": {
            "refreshCache": rel_path(REFRESH_CACHE_PATH),
            "jsonIndexes": json_index_states,
            "sqlite": sqlite_state,
        },
        "freshness": {
            "status": "fresh" if not freshness_reasons else "warning",
            "reasons": freshness_reasons,
            "missingPaths": path_check_errors,
        },
    }


def validate_generated_paths(index_payloads: list[dict[str, object]]) -> list[str]:
    missing = []
    seen = set()
    for payload in index_payloads:
        for relative_path in collect_path_values(payload):
            if relative_path in seen or "*" in relative_path or relative_path.startswith("target/"):
                continue
            seen.add(relative_path)
            if not path_exists(relative_path):
                missing.append(relative_path)
    return sorted(missing)


def run_refresh(no_sqlite: bool, require_sqlite: bool, compact_log: bool, force_full: bool) -> int:
    compaction_state = compact_event_logs() if compact_log else None
    if compaction_state is None:
        sync_archive_summaries_from_disk()
    generated_at = now_iso()
    cold_search_paths = {rel_path(path) for path in collect_cold_search_files()}
    inputs_hash = sha256_for_files(collect_hash_inputs())
    refresh_cache = {} if force_full else load_refresh_cache().get("indexes", {})

    docs_index, docs_state, docs_cache = build_or_reuse_json_index(
        "docs",
        generated_at,
        refresh_cache,
        lambda ts: build_docs_index(cold_search_paths, ts),
    )
    files_index, files_state, files_cache = build_or_reuse_json_index(
        "files",
        generated_at,
        refresh_cache,
        build_files_index,
    )
    symbols_index, symbols_state, symbols_cache = build_or_reuse_json_index(
        "symbols",
        generated_at,
        refresh_cache,
        build_symbols_index,
    )
    test_index, test_state, test_cache = build_or_reuse_json_index(
        "test",
        generated_at,
        refresh_cache,
        build_test_index,
    )
    config_index, config_state, config_cache = build_or_reuse_json_index(
        "config",
        generated_at,
        refresh_cache,
        build_config_index,
    )
    json_index_states = [docs_state, files_state, symbols_state, test_state, config_state]

    missing_paths = validate_generated_paths(
        [docs_index, files_index, symbols_index, test_index, config_index]
    )
    hot_stats = hot_context_stats()
    sqlite_state = {
        "status": "skipped",
        "path": rel_path(SQLITE_DB_PATH),
        "ftsEnabled": False,
        "documents": 0,
        "sections": 0,
    }
    if not no_sqlite:
        sqlite_state = create_sqlite_db(
            SQLITE_DB_PATH,
            generated_at,
            inputs_hash,
            require_sqlite,
            force_full=force_full,
        )

    memory_state = build_memory_state(
        generated_at,
        inputs_hash,
        hot_stats,
        json_index_states,
        sqlite_state,
        missing_paths,
    )

    write_refresh_cache(
        generated_at,
        {
            "docs": docs_cache,
            "files": files_cache,
            "symbols": symbols_cache,
            "test": test_cache,
            "config": config_cache,
        },
    )
    write_json(MEMORY_STATE_PATH, memory_state)

    print("[ai-memory] refreshed markdown truth indexes")
    if compaction_state is not None:
        print(
            "[ai-memory] compacted event log: "
            f"{compaction_state['activeEntries']} active / "
            f"{compaction_state['archivedEntries']} archived across "
            f"{len(compaction_state['archiveFiles'])} archive files"
        )
    print(f"[ai-memory] hot context: {hot_stats['totalLines']} lines / {hot_stats['totalBytes']} bytes")
    print(f"[ai-memory] inputs hash: {inputs_hash}")
    rebuilt_indexes = [state["path"] for state in json_index_states if state["status"] == "rebuilt"]
    reused_indexes = [state["path"] for state in json_index_states if state["status"] == "reused"]
    print(f"[ai-memory] json indexes rebuilt: {len(rebuilt_indexes)} / reused: {len(reused_indexes)}")
    if missing_paths:
        print("[ai-memory] missing indexed paths detected:")
        for path in missing_paths:
            print(f"- {path}")
        return 1
    if not hot_stats["withinBudget"] or not hot_stats.get("shapeValid", True):
        print("[ai-memory] hot context exceeds budget or shape rules")
        return 1
    if sqlite_state.get("status") == "unavailable":
        print(f"[ai-memory] sqlite unavailable: {sqlite_state.get('error', 'unknown error')}")
        return 1 if require_sqlite else 0
    if sqlite_state.get("status") == "built":
        print(
            "[ai-memory] sqlite cold search built: "
            f"{sqlite_state['documents']} docs / {sqlite_state['sections']} sections "
            f"(fts={sqlite_state['ftsEnabled']}, "
            f"updated={sqlite_state.get('updatedFiles', 0)}, "
            f"reused={sqlite_state.get('reusedFiles', 0)}, "
            f"removed={sqlite_state.get('removedFiles', 0)})"
        )
    return 0


def run_check() -> int:
    if not MEMORY_STATE_PATH.exists():
        print("[ai-memory] missing ai/memory-state.json")
        return 1
    try:
        memory_state = json.loads(read_text(MEMORY_STATE_PATH))
    except json.JSONDecodeError as exc:
        print(f"[ai-memory] invalid ai/memory-state.json: {exc}")
        return 1

    reasons = []
    expected_hash = sha256_for_files(collect_hash_inputs())
    stored_hash = memory_state.get("inputsHash")
    if stored_hash != expected_hash:
        reasons.append("inputs-hash-mismatch")

    if memory_state.get("schemaVersion") != SCHEMA_VERSION:
        reasons.append("schema-version-mismatch")

    hot_stats = hot_context_stats()
    if not hot_stats["withinBudget"]:
        reasons.append("hot-context-over-budget")
    if not hot_stats.get("shapeValid", True):
        reasons.append("hot-context-shape-invalid")

    indexed_paths = []
    for index_name in [
        "files-index.json",
        "docs-index.json",
        "symbols-index.json",
        "test-index.json",
        "config-index.json",
    ]:
        index_path = INDEX_DIR / index_name
        if not index_path.exists():
            reasons.append(f"missing-{index_name}")
            continue
        try:
            indexed_paths.extend(collect_path_values(json.loads(read_text(index_path))))
        except json.JSONDecodeError:
            reasons.append(f"invalid-{index_name}")

    missing_paths = sorted(
        {
            path
            for path in indexed_paths
            if "*" not in path and not path.startswith("target/") and not path_exists(path)
        }
    )
    if missing_paths:
        reasons.append("indexed-paths-missing")

    if not REFRESH_CACHE_PATH.exists():
        reasons.append("missing-refresh-cache")

    sqlite_state = memory_state.get("derivedArtifacts", {}).get("sqlite", {})
    if sqlite_state.get("status") == "built" and not SQLITE_DB_PATH.exists():
        reasons.append("missing-sqlite-db")

    if reasons:
        print("[ai-memory] STALE")
        for reason in reasons:
            print(f"- {reason}")
        for path in missing_paths:
            print(f"- missing path: {path}")
        print(f"- hot context: {hot_stats['totalLines']} lines / {hot_stats['totalBytes']} bytes")
        return 1

    print("[ai-memory] OK")
    print(f"- inputs hash: {stored_hash}")
    print(f"- hot context: {hot_stats['totalLines']} lines / {hot_stats['totalBytes']} bytes")
    print(f"- sqlite: {sqlite_state.get('status', 'unknown')}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Refresh or check hybrid AI memory artifacts.")
    parser.add_argument("--check", action="store_true", help="Only verify freshness and budgets.")
    parser.add_argument("--no-sqlite", action="store_true", help="Skip the optional SQLite cold-search database.")
    parser.add_argument("--require-sqlite", action="store_true", help="Fail if SQLite/FTS cold search cannot be built.")
    parser.add_argument("--compact-log", action="store_true", help="Archive older event-log entries and keep only recent active history.")
    parser.add_argument("--force-full", action="store_true", help="Rebuild all derived indexes and the SQLite database from scratch.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.check:
        return run_check()
    return run_refresh(
        no_sqlite=args.no_sqlite,
        require_sqlite=args.require_sqlite,
        compact_log=args.compact_log,
        force_full=args.force_full,
    )


if __name__ == "__main__":
    sys.exit(main())
