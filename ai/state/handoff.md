# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow the dependency-ordered roadmap in `TODO.md`: WP27.
4. Treat `Release Gate` as last and cut from `RELEASE.md` when requested.

## Focus
- `2026-04-30`: Typed DSL/compiler roadmap is complete through WP26, including typed joins, aggregates, `HAVING`, windows, bounded subqueries, `QUALIFY`, and compiler-generated typed fields.
- `2026-04-30`: `TODO.md` now tracks orchestration-tooling work packages: WP27 CLI productization, WP28 runtime layering, WP29 LangGraph execution spike, and WP30 run visibility/operator UX.
- `2026-04-30`: Parallel agent execution is required for independent tasks; keep `--max-parallel`, ready batches, isolated workspaces, and conservative write-scope serialization intact.
- `2026-04-30`: WP27 started with a `pojolens-agents` console entrypoint that bootstraps the existing script via `--repo-root`, `POJOLENS_REPO_ROOT`, or cwd.
- `2026-04-30`: AI tooling implementations now live under `scripts/ai/`; root AI script shims were removed, leaving `pojolens-agents` and direct `scripts/ai/*` paths as canonical.
- `2026-04-30`: Script tooling now uses domain folders: `scripts/benchmarks/`, `scripts/docs/`, `scripts/quality/`, and `scripts/release/`.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-30`: `TODO.md` order is WP27, WP28, WP29, WP30, then deferred WP18 and Release Gate.
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
