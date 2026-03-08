#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
MIGRATION = ROOT / "MIGRATION.md"
RELEASE = ROOT / "RELEASE.md"


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


def main() -> int:
    readme = read_text(README)
    migration = read_text(MIGRATION)
    release = read_text(RELEASE)

    errors: list[str] = []

    # HAVING boolean support invariants.
    require_regex(readme, README, r"HAVING.*AND.*/.OR|HAVING.*AND.*OR", errors)
    require_regex(migration, MIGRATION, r"HAVING.*AND.*/.OR|HAVING.*AND.*OR", errors)
    require_regex(release, RELEASE, r"HAVING.*Boolean support:.*AND.*OR", errors)

    # Chart type/policy invariants.
    for chart_type in ("BAR", "LINE", "PIE", "AREA", "SCATTER"):
        require_substring(readme, README, f"- `{chart_type}`", errors)
        require_substring(migration, MIGRATION, f"`{chart_type}`", errors)
    require_regex(readme, README, r"does not ship chart rendering|will not implement native image/chart rendering", errors)
    require_regex(migration, MIGRATION, r"will not implement native image/chart rendering|not implement native image/chart rendering", errors)
    require_regex(readme, README, r"percentStacked.*requires.*stacked=true", errors)
    require_regex(migration, MIGRATION, r"percentStacked.*requires.*stacked=true", errors)

    # Benchmark command coverage invariants.
require_substring(release, RELEASE, "SqlLikePipelineJmhBenchmark.parseAndFilterBooleanDepth", errors)
require_substring(release, RELEASE, "SqlLikePipelineJmhBenchmark.parseAndFilterHavingComputed", errors)
require_substring(release, RELEASE, "BenchmarkMetricsPlotGenerator", errors)
require_substring(readme, README, "BenchmarkMetricsPlotGenerator", errors)
require_substring(readme, README, "benchmarks/chart-thresholds.json", errors)
require_regex(release, RELEASE, r"BenchmarkThresholdChecker.*--strict", errors)
require_regex(readme, README, r"BenchmarkThresholdChecker.*--strict", errors)
require_regex(readme, README, r"CI benchmark/cache/chart gates", errors)

    if errors:
        print("[doc-check] FAILED")
        for err in errors:
            print(f"- {err}")
        return 1

    print("[doc-check] OK: README/MIGRATION/RELEASE invariants satisfied.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
