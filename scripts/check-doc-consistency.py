#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POM = ROOT / "pom.xml"
README = ROOT / "README.md"
CONTRIBUTING = ROOT / "CONTRIBUTING.md"
CHANGELOG = ROOT / "CHANGELOG.md"
MIGRATION = ROOT / "MIGRATION.md"
RELEASE = ROOT / "RELEASE.md"
ENTRY_POINTS = ROOT / "docs/entry-points.md"
USECASES = ROOT / "docs/usecases.md"
REUSABLE_WRAPPERS = ROOT / "docs/reusable-wrappers.md"
REPORTS = ROOT / "docs/reports.md"
CHARTS = ROOT / "docs/charts.md"
COMPUTED_FIELDS = ROOT / "docs/computed-fields.md"
TIME_BUCKETS = ROOT / "docs/time-buckets.md"
TABULAR_SCHEMA = ROOT / "docs/tabular-schema.md"
TELEMETRY = ROOT / "docs/telemetry.md"
CACHING = ROOT / "docs/caching.md"
METAMODEL = ROOT / "docs/metamodel.md"
MODULES = ROOT / "docs/modules.md"
SQL_LIKE = ROOT / "docs/sql-like.md"
BENCHMARKING = ROOT / "docs/benchmarking.md"
BENCHMARK_MAIN_ARGS = ROOT / "scripts/benchmark-suite-main.args"
QUICKSTART_POM = ROOT / "examples/spring-boot-starter-quickstart/pom.xml"
RISK_CONSOLE_POM = ROOT / "examples/spring-boot-starter-risk-console/pom.xml"
TYPED_COMPILER_MAVEN_POM = ROOT / "examples/typed-compiler-maven/pom.xml"
TYPED_COMPILER_GRADLE_JAVA_BUILD = ROOT / "examples/typed-compiler-gradle-java/build.gradle.kts"
TYPED_COMPILER_GRADLE_KOTLIN_BUILD = ROOT / "examples/typed-compiler-gradle-kotlin/build.gradle.kts"


def read_text(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"[doc-check] Missing required file: {path}")
    return path.read_text(encoding="utf-8")


def pom_version() -> str:
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    root = ET.fromstring(read_text(POM))
    version = root.findtext("m:version", namespaces=ns)
    if not version:
        raise SystemExit("[doc-check] Missing pom.xml version")
    return version.strip()


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
    version = pom_version()
    readme = read_text(README)
    contributing = read_text(CONTRIBUTING)
    changelog = read_text(CHANGELOG)
    migration = read_text(MIGRATION)
    release = read_text(RELEASE)
    entry_points = read_text(ENTRY_POINTS)
    usecases = read_text(USECASES)
    reusable_wrappers = read_text(REUSABLE_WRAPPERS)
    reports = read_text(REPORTS)
    charts = read_text(CHARTS)
    computed_fields = read_text(COMPUTED_FIELDS)
    time_buckets = read_text(TIME_BUCKETS)
    tabular_schema = read_text(TABULAR_SCHEMA)
    telemetry = read_text(TELEMETRY)
    caching = read_text(CACHING)
    metamodel = read_text(METAMODEL)
    modules = read_text(MODULES)
    sql_like = read_text(SQL_LIKE)
    benchmarking = read_text(BENCHMARKING)
    benchmark_main_args = read_text(BENCHMARK_MAIN_ARGS)
    quickstart_pom = read_text(QUICKSTART_POM)
    risk_console_pom = read_text(RISK_CONSOLE_POM)
    typed_compiler_maven_pom = read_text(TYPED_COMPILER_MAVEN_POM)
    read_text(TYPED_COMPILER_GRADLE_JAVA_BUILD)
    read_text(TYPED_COMPILER_GRADLE_KOTLIN_BUILD)

    errors: list[str] = []

    require_substring(readme, README, f"<version>{version}</version>", errors)
    require_substring(modules, MODULES, f"<version>{version}</version>", errors)
    require_substring(release, RELEASE, f"Maven version: `{version}`", errors)
    require_substring(release, RELEASE, f"Git tag: `release-{version}`", errors)
    require_substring(changelog, CHANGELOG, f"## [{version}]", errors)
    require_substring(quickstart_pom, QUICKSTART_POM, f"<version>{version}</version>", errors)
    require_substring(risk_console_pom, RISK_CONSOLE_POM, f"<version>{version}</version>", errors)
    require_substring(typed_compiler_maven_pom, TYPED_COMPILER_MAVEN_POM, f"<version>{version}</version>", errors)

    public_entry_docs = (
        (README, readme),
        (MIGRATION, migration),
        (ENTRY_POINTS, entry_points),
        (USECASES, usecases),
        (REUSABLE_WRAPPERS, reusable_wrappers),
        (REPORTS, reports),
        (CHARTS, charts),
        (COMPUTED_FIELDS, computed_fields),
        (TIME_BUCKETS, time_buckets),
        (TABULAR_SCHEMA, tabular_schema),
        (TELEMETRY, telemetry),
        (CACHING, caching),
        (METAMODEL, metamodel),
    )
    internal_api_patterns = (
        r"\bPojoLensCore\b",
        r"\bQueryBuilder\b",
        r"\bFluentQueryDefinition\b",
        r"ReportDefinition\.fluent",
        r"laughing\.man\.commits\.internal",
    )
    for path, doc in public_entry_docs:
        for pattern in internal_api_patterns:
            forbid_regex(doc, path, pattern, errors)

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
