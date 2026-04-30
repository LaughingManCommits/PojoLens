# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-04-29`: Surface/tooling cleanup complete; first-read docs authoring-first, `PojoLensFiles` owns CSV/TSV/JSON/JSONL.
- `2026-04-29`: WP23/WP24 completed typed joins, aggregates, `HAVING`, subqueries, windows, `QUALIFY`, `JoinBindings`/`DatasetBundle`.
- `2026-04-30`: WP26 completed compiler-time typed fields via `@GeneratePojoLensTypedFields` + `PojoLensTypedFieldsProcessor`.
- `2026-04-30`: WP27 complete: global options (`--verbose`, `--provider-bin`, `--dry-run`, `--json`) on all 12 subcommands; exit codes 0–7; `--max-parallel` in root help; 122 tests pass.
- `2026-04-30`: Orchestration roadmap active: WP29 LangGraph spike, WP30 operator UX.
- `2026-04-30`: Parallel execution required; independent tasks concurrent, write-scope conflicts serialized.
- `2026-04-30`: WP28 complete: runtime layers cover governance, path safety, provider calls/JSON, run manifests, scheduling, and workspace review primitives; 142 script tests pass.
- `2026-04-30`: Script tooling by domain: `scripts/ai/`, `scripts/benchmarks/`, `scripts/docs/`, `scripts/quality/`, `scripts/release/`.
- `2026-04-30`: Examples curated: typed-compiler Maven/Gradle Java/Gradle Kotlin, Spring quickstart, Spring risk console.
- `2026-04-29`: `README.md` route-based with `Quick Integration` → `AGENTS.md` pointer.

## Verified
- `2026-04-29`: WP21-WP25 passed focused coverage, `mvn -B -ntp test`, static-analysis, docs consistency.
- `2026-04-30`: WP26 + examples passed compiler tests, Gradle 9.3.0 build, reactor `mvn -B -ntp test`, lint, static-analysis.
- `2026-04-30`: WP27 passed py_compile, 122 unittest, validate+dry-run CLI smoke, doc-consistency.
- `2026-04-30`: WP28 passed py_compile, 142 unittest, validate+dry-run CLI smoke, doc-consistency.

## Release
- Latest cut is `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-04-30`: Roadmap order is WP29 -> WP30, then deferred WP18 and Release Gate.
- `2026-04-30`: Start WP29 LangGraph spike after preserving current `--max-parallel` and write-scope serialization contracts.
