# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow the dependency-ordered roadmap in `TODO.md`: WP18.
4. Treat `Release Gate` as last and cut from `RELEASE.md` when requested.

## Focus
- `2026-04-29`: WP23/WP24 are complete; `TypedQuery` now covers joins, grouped aggregates, `HAVING`, bounded subqueries, windows, frames, `QUALIFY`, and `JoinBindings` / `DatasetBundle` reuse.
- `2026-04-30`: WP26 is complete; `@GeneratePojoLensTypedFields` plus `PojoLensTypedFieldsProcessor` emit compiler-time typed constants and keep `FieldMetamodelGenerator.generateTyped(...)` as fallback.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-30`: `TODO.md` order is WP18, then Release Gate.
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).
- `2026-04-30`: WP26 outcome: annotation processing emits ordinary generated Java source with `PLM-AP-*` compiler diagnostics for invalid targets, duplicate targets, graph-depth failures, and source-write failures; there is no Lombok-style AST transformation.
- `2026-04-30`: WP26 now publishes `META-INF/services/javax.annotation.processing.Processor` and `META-INF/gradle/incremental.annotation.processors`; the annotation is `CLASS` retention so Gradle incremental APT can treat the processor as isolating.
- `2026-04-30`: Kotlin/JVM support is through kapt over `@JvmField var` field models; Kotlin property/data-class-native generation remains deferred pending KSP or property-metadata design.
- `2026-04-29`: `README.md` stays route-based and now includes a top `Quick Integration` section that points AI agents to `AGENTS.md`; do not reintroduce separate product-taxonomy sections there.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `README.md`, `docs/**` for docs work
- `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md` for release and benchmarks
