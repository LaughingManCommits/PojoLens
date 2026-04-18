#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTRIBUTING = ROOT / "CONTRIBUTING.md"
MIGRATION = ROOT / "MIGRATION.md"
RELEASE = ROOT / "RELEASE.md"
SQL_LIKE = ROOT / "docs/sql-like.md"
BENCHMARKING = ROOT / "docs/benchmarking.md"
BENCHMARK_MAIN_ARGS = ROOT / "scripts/benchmark-suite-main.args"


def read_text(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"[doc-check] Missing required file: {path}")
    return path.read_text(encoding="utf-8")


def require_substring(doc: str, path: Path, needle: str, errors: list[str]) -> None:
    if needle not in doc:
        errors.append(f"{path.name}: missing required text: {needle}")


def require_regex(doc: str, path: Path, pattern: str, errors: list[str]) -> None:
    if re.search(pattern, doc, flags=re.IGNORECASE | re.MULTILINE | re.DOTALL) is None:
        errors.append(f"{path.name}: missing required pattern: {pattern}")


def forbid_regex(doc: str, path: Path, pattern: str, errors: list[str]) -> None:
    if re.search(pattern, doc, flags=re.IGNORECASE | re.MULTILINE | re.DOTALL) is not None:
        errors.append(f"{path.name}: contains forbidden pattern: {pattern}")


def main() -> int:
    contributing = read_text(CONTRIBUTING)
    migration = read_text(MIGRATION)
    release = read_text(RELEASE)
    sql_like = read_text(SQL_LIKE)
    benchmarking = read_text(BENCHMARKING)
    benchmark_main_args = read_text(BENCHMARK_MAIN_ARGS)

    errors: list[str] = []

    require_regex(contributing, CONTRIBUTING, r"BENCHMARK_JAR=.*\*-benchmarks\.jar", errors)
    require_regex(release, RELEASE, r"target/\*-benchmarks\.jar", errors)
    forbid_regex(contributing, CONTRIBUTING, r"pojo-lens-\d+(?:\.\d+)+-benchmarks\.jar", errors)
    forbid_regex(release, RELEASE, r"Release\s+\d+\.\d+\.\d+|v\d+\.\d+\.\d+", errors)
    forbid_regex(release, RELEASE, r"pojo-lens-\d+(?:\.\d+)+-benchmarks\.jar", errors)

    require_regex(migration, MIGRATION, r"supports uncorrelated .*WHERE \.\.\. IN \(select \.\.\.\).*subqueries", errors)
    require_regex(migration, MIGRATION, r"supports bounded uncorrelated .*EXISTS \(select", errors)
    require_regex(migration, MIGRATION, r"chained joins are supported", errors)
    require_regex(release, RELEASE, r"supports uncorrelated\s+`WHERE \.\.\. IN \(select \.\.\.\)` subqueries", errors)
    require_regex(release, RELEASE, r"bounded uncorrelated\s+`WHERE \[NOT\] EXISTS \(select \.\.\.\)` subqueries", errors)
    require_regex(release, RELEASE, r"chained joins are supported", errors)
    forbid_regex(migration, MIGRATION, r"does not support subqueries or multi-join SQL plans", errors)
    forbid_regex(migration, MIGRATION, r"`EXISTS`, scalar, and broad nested SQL subquery plans are still unsupported", errors)
    forbid_regex(release, RELEASE, r"unsupported:\s*subqueries,\s*multi-join SQL plans", errors)
    forbid_regex(release, RELEASE, r"correlated, `EXISTS`, scalar, and broad nested SQL subqueries remain unsupported", errors)

    require_substring(sql_like, SQL_LIKE, "SQL-like subqueries support uncorrelated `WHERE <field> IN (select ...)`", errors)
    require_substring(sql_like, SQL_LIKE, "and `WHERE [NOT] EXISTS (select ...)` predicates.", errors)
    require_substring(sql_like, SQL_LIKE, "chained joins are supported when each `JOIN ... ON ...` references the current plan or qualifies the source explicitly", errors)

    require_substring(benchmark_main_args, BENCHMARK_MAIN_ARGS, "PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField", errors)
    require_substring(benchmarking, BENCHMARKING, "PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField", errors)
    require_substring(benchmarking, BENCHMARKING, "BenchmarkThresholdChecker", errors)
    require_substring(benchmarking, BENCHMARKING, "benchmarks/chart-thresholds.json", errors)
    require_regex(benchmarking, BENCHMARKING, r"BENCHMARK_JAR=.*target/\*-benchmarks\.jar", errors)
    forbid_regex(benchmarking, BENCHMARKING, r"target/pojo-lens-\d+(?:\.\d+)+-benchmarks\.jar", errors)

    if errors:
        print("[doc-check] FAILED")
        for err in errors:
            print(f"- {err}")
        return 1

    print("[doc-check] OK: documentation invariants satisfied.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
