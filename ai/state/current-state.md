# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-02`: WP46 is complete; Pydantic v2 now backs major orchestrator contracts, JSON-boundary validation, `py.typed`, and mypy coverage.
- `2026-05-02`: WP44 async execution, WP43 SDK provider, WP42 task retry, and WP41 atomic writes are complete; preserve async `--max-parallel`, provider fallback, retry metadata, and crash-safe manifests.
- `2026-05-02`: WP39 low-cost profiles, skill routing, file-backed role prompts, and prompt-size guardrails are complete.
- `2026-04-30`: Parallel execution remains required; preserve isolated workspaces and conservative write-scope serialization.

## Verified
- `2026-05-02`: WP46 validations passed: py_compile over `pojo_lens_agents`, mypy over package, 489 Python tests, example parallel validate/dry-run, docs check, and AI memory refresh/check.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-02`: Roadmap order is WP45 -> WP40 -> deferred WP18 -> Release Gate (WP46 complete).
- `2026-05-02`: WP45 is next unless the user chooses WP40 first: OpenTelemetry Observability (emit standard OTEL spans from existing trace events).
