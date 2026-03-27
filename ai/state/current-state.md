# Current State

## Repository Health

- Multi-module Maven Java 17 library:
  `pojo-lens-parent`,
  `pojo-lens`,
  `pojo-lens-spring-boot-autoconfigure`,
  `pojo-lens-spring-boot-starter`,
  `pojo-lens-benchmarks`.
- Runtime consumer artifact remains `io.github.laughingmancommits:pojo-lens:1.0.0`.
- Release profile and workflows exist for runtime plus Boot artifacts.
- `TODO.md` still exists as the planning file, but the current roadmap is fully complete.

## Current Focus

- No active feature roadmap is queued in `TODO.md`.
- Highest-priority operational work is Maven Central release retry or verification for `v1.0.0`.
- AI memory now uses a tiered hybrid model:
  hot context in `ai/core` and `ai/state`;
  warm validation history in `ai/state/recent-validations.md`;
  recent events in `ai/log/events.jsonl`;
  archived history in `ai/log/archive/*.jsonl`;
  generated navigation in `ai/indexes/*.json`;
  optional `ai/indexes/cold-memory.db` for derived SQLite/FTS cold search.

## Latest Verified State

- `2026-03-26`: full repository regression passed with `mvn -q test`.
- `2026-03-26`: docs consistency passed with `scripts/check-doc-consistency.ps1`.
- `2026-03-26`: starter example Playwright suite passed with
  `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`.
- `2026-03-27`: AI memory compaction and refresh passed with
  `py -3 scripts/refresh-ai-memory.py --compact-log`,
  `py -3 scripts/refresh-ai-memory.py --check`,
  and archive-aware search via `py -3 scripts/query-ai-memory.py "<archive topic>"`.
- `2026-03-26`: example frontend fixes validated:
  salary input browser validation fix,
  local Bootstrap and Chart.js assets,
  Chart.js null-field serialization hardening,
  and user-facing `statsView/chartType` selectors.
- `2026-03-25`: pre-adoption simplification completed:
  `PojoLens` is helper-only and `PojoLensRuntime` is the only public cache-tuning surface.

## Release Status

- Central namespace is verified for `io.github.laughingmancommits`.
- Release workflow publishes:
  `pojo-lens`,
  `pojo-lens-spring-boot-autoconfigure`,
  `pojo-lens-spring-boot-starter`.
- The last publish attempt reached bundle upload but failed signature verification because Central could not find the signer public key.
- The public key was uploaded to `keyserver.ubuntu.com` and `keys.openpgp.org`; retry is the next operational step.

## Open Risks

- Central publish status is not yet reconfirmed after key propagation.
- AI memory becomes stale after repo structure or doc changes unless `scripts/refresh-ai-memory.ps1` is rerun.
- Active AI event history grows noisy unless older entries are compacted into `ai/log/archive/*.jsonl`.

## Next Actions

- Retry the release workflow or a manual release dispatch for `v1.0.0`.
- After structural or doc changes, run:
  `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1`
- Then verify freshness with:
  `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -Check`
- When the active event log grows, compact it with:
  `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -CompactLog`
- Keep `mvn -q test` and `scripts/check-doc-consistency.ps1` green after code or doc changes.
