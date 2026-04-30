# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow the dependency-ordered roadmap in `TODO.md`: WP29.
4. Treat `Release Gate` as last and cut from `RELEASE.md` when requested.

## Focus
- `2026-04-30`: Typed DSL/compiler roadmap is complete through WP26, including typed joins, aggregates, `HAVING`, windows, bounded subqueries, `QUALIFY`, and compiler-generated typed fields.
- `2026-04-30`: `TODO.md` tracks orchestration work: WP29 LangGraph spike, WP30 operator UX.
- `2026-04-30`: Parallel agent execution required; keep `--max-parallel`, ready batches, isolated workspaces, conservative write-scope serialization.
- `2026-04-30`: WP27 complete: global options (`--verbose`, `--provider-bin`/`--claude-bin`, `--dry-run`, `--json`) consistent across all 12 subcommands; exit codes 0–7 with `PromotionBlockedError` (6) and `ValidationError` (3) subclasses; `--max-parallel` in root help; 122 tests pass.
- `2026-04-30`: AI tooling canonical: `pojolens-agents` + `scripts/ai/*`; domain folders: `scripts/benchmarks/`, `scripts/docs/`, `scripts/quality/`, `scripts/release/`.
- `2026-04-30`: WP28 complete: `pojo_lens_agents` owns scheduling, provider subprocess/JSON handling, path safety, run-store manifest helpers, workspace review/hydration primitives, and run-governance checks; script unittest count is now 142.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-30`: `TODO.md` order is WP29, WP30, then deferred WP18 and Release Gate.
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).
- `2026-04-30`: WP26 emits ordinary generated Java with `PLM-AP-*` diagnostics, service-loader and Gradle incremental descriptors, and no Lombok-style AST transformation.
- `2026-04-30`: Kotlin/JVM support is through kapt over `@JvmField var` field models; Kotlin property/data-class-native generation remains deferred.
- `2026-04-30`: Curated example projects are `examples/typed-compiler-maven`, `examples/typed-compiler-gradle-java`, `examples/typed-compiler-gradle-kotlin`, `examples/spring-boot-starter-quickstart`, and `examples/spring-boot-starter-risk-console`; the redundant Spring basic example was removed.
- `2026-04-29`: `README.md` stays route-based and now includes a top `Quick Integration` section that points AI agents to `AGENTS.md`; do not reintroduce separate product-taxonomy sections there.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `README.md`, `docs/**` for docs work
- `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md` for release and benchmarks
