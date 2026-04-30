# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-04-29`: Surface/tooling cleanup is complete: first-read docs are authoring-first, `PojoLensFiles` owns CSV/TSV/JSON/JSONL, and tooling is split between `metamodel` generation and `tooling` validation.
- `2026-04-29`: WP23/WP24 completed typed joins, grouped aggregates, grouped `HAVING`, bounded subqueries, rank windows, aggregate window frames, `QUALIFY`, and `JoinBindings` / `DatasetBundle` reuse.
- `2026-04-30`: WP26 completed compiler-time typed field generation through `@GeneratePojoLensTypedFields` and `PojoLensTypedFieldsProcessor`, with no AST rewriting and `FieldMetamodelGenerator` retained as fallback.
- `2026-04-30`: Example inventory is now curated to typed compiler Maven, typed compiler Gradle Java, typed compiler Gradle Kotlin, Spring quickstart, and Spring risk console.
- `2026-04-30`: Spring risk-console Query Studio now consumes compiler-generated `TransactionRecordTypedFields` as a realistic integration proof.
- `2026-04-29`: `README.md` is route-based with a top `Quick Integration` section that points AI agents to `AGENTS.md`.

## Verified
- `2026-04-29`: WP21-WP25 passed focused coverage, `mvn -B -ntp test`, static-analysis where applicable, and docs consistency.
- `2026-04-30`: WP26 and curated examples passed focused compiler/public-API tests, Maven/Gradle/Kotlin example tests using a temporary Gradle 9.3.0 distribution under `target/tools`, full reactor `mvn -B -ntp test`, docs consistency, lint, static-analysis, jar descriptor inspection, and `git diff --check`.

## Release
- Latest cut is `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-04-30`: Roadmap order is WP18 -> Release Gate.
- `2026-04-30`: Next roadmap item is WP18 JDK 25 Runtime Knob Evaluation.
