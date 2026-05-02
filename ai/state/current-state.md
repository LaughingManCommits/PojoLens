# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-02`: WP48 is complete; pre-flight cost estimation now uses tracked Anthropic pricing plus effort/model heuristics, adds `run --estimate`, emits `costEstimate` in validate/run/manifests, and warns when `runBudgetUsd` is already below the minimum estimate.
- `2026-05-02`: WP45 is complete; OTEL emission now maps retained run spans to OTLP HTTP with `OTEL_EXPORTER_OTLP_ENDPOINT` or `--otel-endpoint`, extra lineage exported as span links, and task cost/model attributes attached on task spans.
- `2026-05-02`: WP47 is complete; HITL gates support runPolicy/CLI activation, batch/on-failure/always modes, manifest-persisted gate events, sentinel or stdin approval, auto-approve testing, and abort blocking.
- `2026-05-02`: WP46 is complete; Pydantic v2 now backs major orchestrator contracts, JSON-boundary validation, `py.typed`, and mypy coverage.
- `2026-05-02`: WP44 async execution, WP43 SDK provider, WP42 task retry, and WP41 atomic writes are complete; preserve async `--max-parallel`, provider fallback, retry metadata, and crash-safe manifests.
- `2026-05-02`: WP39 low-cost profiles, skill routing, file-backed role prompts, and prompt-size guardrails are complete.
- `2026-04-30`: Parallel execution remains required; preserve isolated workspaces and conservative write-scope serialization.

## Verified
- `2026-05-02`: WP48 validations passed: focused `py_compile`, 509 Python tests, example parallel validate, `run --estimate`, example parallel dry-run, and docs consistency check.
- `2026-05-02`: WP45 validations passed: `py_compile -B`, 502 Python tests, example parallel validate/dry-run, retained `export-trace`, docs check, and AI memory refresh/check.
- `2026-05-02`: WP47 validations passed: focused HITL tests, example parallel HITL auto-approved dry-run, full Python suite, docs check, and AI memory refresh/check.
- `2026-05-02`: WP46 validations passed: py_compile over `pojo_lens_agents`, mypy over package, 489 Python tests, example parallel validate/dry-run, docs check, and AI memory refresh/check.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-02`: Roadmap order is WP49 -> WP50 -> WP51 -> WP52 -> WP53 -> WP54 -> WP55 -> WP56 -> WP57 -> WP40 -> deferred WP18 -> Release Gate.
- `2026-05-02`: WP49 is next unless the user chooses WP40 first: Dynamic Plan Mutation.
