# Handoff

## Resume Order

- Load hot context first.
- Confirm branch state with `git status --short` and backlog state via `TODO.md`.
- Use `docs/benchmarking.md` plus benchmark threshold files as benchmark/process source of truth.
- Load `ai/state/benchmark-state.md` and `ai/core/benchmark-context.md` only when doing benchmark/profiler/threshold work.

## Current Priority

- No active implementation tasks are open right now (`TODO.md` is empty).
- Latest commit on `main` is `b074644` (`2026-03-20`), which updates benchmark classes/docs to execution-only benchmarking methodology.
- Working tree now also includes uncommitted release-coordinate prep for Central namespace verification: `groupId` is switched to `io.github.laughingmancommits` in `pom.xml`, with matching README install snippet and aligned AI repo-purpose metadata.
- Working tree also includes an uncommitted docs refresh pass across `README.md`, `CONTRIBUTING.md`, `RELEASE.md`, and `MIGRATION.md`; `scripts/check-doc-consistency.ps1` passes on that updated set.
- Working tree now also includes uncommitted Central release automation:
  - `pom.xml` has a `release-central` profile (sources, javadocs, gpg signing, central publishing plugin)
  - `.github/workflows/release.yml` publishes on `v*` tags or manual dispatch using Central token + GPG secrets
  - release workflow enforces tag version parity (`vX.Y.Z` vs `pom.xml` version)
  - `scripts/export-release-secrets.ps1` generates GPG export files plus a GitHub secrets template into `target/release-secrets`
  - latest CI deploy failure root cause/fix: Central token env placeholders were unresolved during deploy (`401`); workflow now exports `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD` at job scope and uses `MAVEN_GPG_PASSPHRASE` for gpg signing
- Local validation checkpoint for this session: `mvn -q test` passed on `2026-03-20` with `488` tests.
- Lint baseline checkpoint for this session: `mvn -B -ntp -Plint verify -DskipTests` generated `target/checkstyle-result.xml`; `scripts/check-lint-baseline.ps1` initially reported drift (`report=11513 baseline=11457 new=548 fixed=492`), and after `scripts/check-lint-baseline.ps1 -WriteBaseline` the gate passed with `report=11513 baseline=11513 new=0 fixed=0`.
- Keep current benchmark guardrails from `benchmarks/thresholds.json` (2026-03-19 CI rebaseline) unless new measured evidence requires another systematic rebaseline.

## Next Validation

- After any code changes:
  - run focused regressions for touched areas
  - run `mvn -q test`
- Before quoting fresh JMH results:
  - rebuild runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
- For guardrail updates:
  - run strict checker against `target/benchmarks.json` and `benchmarks/thresholds.json`
  - keep chart guardrail checks aligned with `benchmarks/chart-thresholds.json`
- Refresh warmed `-prof gc` computed-join comparison numbers under execution-only methodology before treating them as active profiling baselines.
- For release automation changes, rerun:
  - `mvn -B -ntp -Prelease-central -DskipTests package`
  - `scripts/check-doc-consistency.ps1`

## Guardrails

- Do not reopen WP19 without a materially different structural idea.
- Do not reopen WP18 based only on cold chart-suite drift; require targeted warm/profile evidence.
- Do not run concurrent Maven builds against the same workspace `target/` directory.
- Remember benchmark suite arg files use regex patterns; newly added benchmark methods may be included implicitly and require matching threshold entries.
- Judge performance changes by absolute `ms/op` and allocation context, not fluent-vs-SQL-like ratio alone.

## Useful Files

- `TODO.md`
- `docs/benchmarking.md`
- `benchmarks/thresholds.json`
- `benchmarks/chart-thresholds.json`
- `scripts/benchmark-suite-main.args`
- `scripts/benchmark-suite-chart.args`
- `scripts/benchmark-suite-hotspots.args`
- `src/main/java/laughing/man/commits/benchmark/PojoLensPipelineJmhBenchmark.java`
- `src/main/java/laughing/man/commits/benchmark/SqlLikePipelineJmhBenchmark.java`
- `src/main/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmark.java`
