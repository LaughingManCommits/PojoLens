#!/usr/bin/env python3
from __future__ import annotations

import argparse
import fnmatch
import json
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REFRESH_SCRIPT = ROOT / "scripts" / "refresh-ai-memory.py"
QUERY_SCRIPT = ROOT / "scripts" / "query-ai-memory.py"
DEFAULT_REPORT_PATH = ROOT / "ai" / "indexes" / "memory-benchmark.json"
JSON_INDEX_COUNTS_PATTERN = re.compile(r"json indexes rebuilt:\s*(\d+)\s*/\s*reused:\s*(\d+)")
SQLITE_COUNTS_PATTERN = re.compile(
    r"sqlite cold search built:\s+\d+\s+docs\s+/\s+\d+\s+sections\s+\(fts=(?:True|False),\s+updated=(\d+),\s+reused=(\d+),\s+removed=(\d+)\)"
)

BENCHMARK_CASES = [
    {
        "id": "release_retry",
        "query": "release retry",
        "expectedTop1": ["ai/state/current-state.md", "ai/state/handoff.md"],
        "expectedTop3": ["ai/state/current-state.md", "ai/state/handoff.md", "ai/core/runbook.md", "RELEASE.md"],
    },
    {
        "id": "recent_validations",
        "query": "DashboardPlaywrightE2eTest",
        "expectedTop1": ["ai/state/recent-validations.md"],
        "expectedTop3": ["ai/state/recent-validations.md", "ai/state/handoff.md", "ai/state/current-state.md"],
    },
    {
        "id": "archive_history",
        "query": "single-join fast-path",
        "pathGlobs": ["ai/log/archive/*"],
        "tiers": ["cold,archive"],
        "expectedTop1": ["ai/log/archive/*-summary.md"],
        "expectedTop3": ["ai/log/archive/*-summary.md", "ai/log/archive/*.jsonl"],
    },
    {
        "id": "module_routing",
        "query": "starter example",
        "expectedTop1": ["ai/state/handoff.md", "ai/state/current-state.md", "ai/core/module-index.md"],
        "expectedTop3": ["ai/state/handoff.md", "ai/state/current-state.md", "ai/core/module-index.md"],
    },
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Benchmark AI memory refresh, query latency, and result quality.")
    parser.add_argument("--report", default="", help="Optional JSON report path.")
    parser.add_argument("--query-iterations", type=int, default=3, help="Number of query timing iterations per benchmark case.")
    return parser.parse_args()


def run_command(arguments: list[str]) -> dict[str, object]:
    started = time.perf_counter()
    completed = subprocess.run(
        arguments,
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    duration_ms = round((time.perf_counter() - started) * 1000, 3)
    return {
        "command": arguments,
        "exitCode": completed.returncode,
        "durationMs": duration_ms,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
    }


def parse_refresh_stats(output: str) -> dict[str, object]:
    payload: dict[str, object] = {}
    json_match = JSON_INDEX_COUNTS_PATTERN.search(output)
    if json_match:
        payload["jsonIndexes"] = {
            "rebuilt": int(json_match.group(1)),
            "reused": int(json_match.group(2)),
        }
    sqlite_match = SQLITE_COUNTS_PATTERN.search(output)
    if sqlite_match:
        payload["sqlite"] = {
            "updatedFiles": int(sqlite_match.group(1)),
            "reusedFiles": int(sqlite_match.group(2)),
            "removedFiles": int(sqlite_match.group(3)),
        }
    return payload


def matches_any(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(path, pattern) for pattern in patterns)


def benchmark_case(case: dict[str, object], iterations: int) -> dict[str, object]:
    timings: list[float] = []
    results: list[dict[str, object]] = []
    for _ in range(iterations):
        arguments = [sys.executable, str(QUERY_SCRIPT), str(case["query"]), "--json"]
        for path_glob in case.get("pathGlobs", []):
            arguments.extend(["--path", str(path_glob)])
        for tier in case.get("tiers", []):
            arguments.extend(["--tier", str(tier)])
        for kind in case.get("kinds", []):
            arguments.extend(["--kind", str(kind)])
        outcome = run_command(arguments)
        if outcome["exitCode"] != 0:
            raise RuntimeError(
                f"Query benchmark failed for {case['id']}: {outcome['stdout'] or outcome['stderr']}"
            )
        timings.append(float(outcome["durationMs"]))
        results = json.loads(str(outcome["stdout"]))

    top1_patterns = [str(pattern) for pattern in case.get("expectedTop1", [])]
    top3_patterns = [str(pattern) for pattern in case.get("expectedTop3", top1_patterns)]
    top1_hit = bool(results) and matches_any(str(results[0]["path"]), top1_patterns)
    top3_hit = any(matches_any(str(item["path"]), top3_patterns) for item in results[:3])
    return {
        "id": case["id"],
        "query": case["query"],
        "pathGlobs": case.get("pathGlobs", []),
        "top1Hit": top1_hit,
        "top3Hit": top3_hit,
        "timingsMs": timings,
        "avgQueryMs": round(statistics.mean(timings), 3),
        "bestQueryMs": round(min(timings), 3),
        "worstQueryMs": round(max(timings), 3),
        "topResults": results[:3],
    }


def main() -> int:
    args = parse_args()

    full_refresh = run_command([sys.executable, str(REFRESH_SCRIPT), "--force-full"])
    if full_refresh["exitCode"] != 0:
        print(full_refresh["stdout"] or full_refresh["stderr"])
        return int(full_refresh["exitCode"])

    incremental_refresh = run_command([sys.executable, str(REFRESH_SCRIPT)])
    if incremental_refresh["exitCode"] != 0:
        print(incremental_refresh["stdout"] or incremental_refresh["stderr"])
        return int(incremental_refresh["exitCode"])

    check = run_command([sys.executable, str(REFRESH_SCRIPT), "--check"])
    if check["exitCode"] != 0:
        print(check["stdout"] or check["stderr"])
        return int(check["exitCode"])

    case_results = [benchmark_case(case, args.query_iterations) for case in BENCHMARK_CASES]
    top1_rate = round(sum(1 for case in case_results if case["top1Hit"]) / len(case_results), 3)
    top3_rate = round(sum(1 for case in case_results if case["top3Hit"]) / len(case_results), 3)
    avg_query_ms = round(statistics.mean(case["avgQueryMs"] for case in case_results), 3)

    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "refresh": {
            "full": {
                "durationMs": full_refresh["durationMs"],
                "exitCode": full_refresh["exitCode"],
                **parse_refresh_stats(str(full_refresh["stdout"])),
            },
            "incremental": {
                "durationMs": incremental_refresh["durationMs"],
                "exitCode": incremental_refresh["exitCode"],
                **parse_refresh_stats(str(incremental_refresh["stdout"])),
            },
        },
        "check": {
            "durationMs": check["durationMs"],
            "exitCode": check["exitCode"],
        },
        "queries": {
            "iterations": args.query_iterations,
            "avgQueryMs": avg_query_ms,
            "top1Rate": top1_rate,
            "top3Rate": top3_rate,
            "cases": case_results,
        },
    }

    if args.report:
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print("[ai-memory-benchmark] OK")
    print(f"- full refresh: {report['refresh']['full']['durationMs']} ms")
    print(f"- incremental refresh: {report['refresh']['incremental']['durationMs']} ms")
    print(f"- check: {report['check']['durationMs']} ms")
    if "jsonIndexes" in report["refresh"]["incremental"]:
        json_indexes = report["refresh"]["incremental"]["jsonIndexes"]
        print(
            f"- incremental json reuse: rebuilt={json_indexes['rebuilt']} reused={json_indexes['reused']}"
        )
    if "sqlite" in report["refresh"]["incremental"]:
        sqlite = report["refresh"]["incremental"]["sqlite"]
        print(
            f"- incremental sqlite reuse: updated={sqlite['updatedFiles']} "
            f"reused={sqlite['reusedFiles']} removed={sqlite['removedFiles']}"
        )
    print(f"- avg query: {avg_query_ms} ms")
    print(f"- top1 rate: {top1_rate}")
    print(f"- top3 rate: {top3_rate}")
    for case in case_results:
        print(
            f"- {case['id']}: avg={case['avgQueryMs']} ms "
            f"top1={case['top1Hit']} top3={case['top3Hit']}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
