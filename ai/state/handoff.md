# Handoff

## Resume Order

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; there is no active engineering roadmap currently.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Current Focus

- Release retry or release verification is the main pending repo task.
- Starter example and dashboard simplification work are complete and validated.
- AI memory is tiered hybrid:
  hot context under `ai/core` and `ai/state`;
  warm validation history in `ai/state/recent-validations.md`;
  recent events in `ai/log/events.jsonl`;
  archived history in `ai/log/archive/*.jsonl`;
  generated navigation under `ai/indexes/*.json`;
  optional cold search in `ai/indexes/cold-memory.db`.

## Key Verified Facts

- `PojoLens` is helper-only; `PojoLensRuntime` owns public cache tuning.
- The repo is module-local:
  runtime code lives in `pojo-lens/src/...`,
  benchmark code lives in `pojo-lens-benchmarks/src/...`.
- The starter example at `examples/spring-boot-starter-basic` is the current reference app and has Java Playwright E2E coverage.
- Recent verified baseline:
  `mvn -q test`
  `scripts/check-doc-consistency.ps1`
  `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
- AI memory maintenance baseline:
  `py -3 scripts/refresh-ai-memory.py --compact-log`
  `py -3 scripts/refresh-ai-memory.py --check`
  `py -3 scripts/query-ai-memory.py "<archive topic>"`

## Next Validation

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes:
  `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1`
  `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -Check`
- After the active AI event log grows:
  `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -CompactLog`
- For release-path changes:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Release Retry Checklist

- Confirm GitHub secrets:
  `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`
- Ensure the release tag matches `pom.xml` version.
- Trigger `.github/workflows/release.yml` by tag push or `workflow_dispatch`.
- Publish command:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests deploy`
- If signature lookup still fails, wait and retry after keyserver propagation.

## Cold Context Pointers

- repo structure: `ai/core/module-index.md`, `ai/core/architecture-map.md`
- docs and process: `ai/core/documentation-index.md`, `ai/core/runbook.md`
- recent validation details: `ai/state/recent-validations.md`
- benchmarks only when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`
