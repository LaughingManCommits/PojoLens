# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-18`: The extracted local AI runtime now lives in the separate `neon` codebase.
- `2026-05-18`: `TODO.md` was reset from the stale WP roadmap to a cleanup backlog focused on removing extracted local AI assets from PojoLens.
- `2026-05-18`: Completed the extraction cleanup by removing the old Python runtime surface, retained runtime artifacts, and stale references from the surviving repo-memory scripts.

## Verified
- `2026-05-18`: `py -3 -m unittest scripts.tests.test_refresh_ai_memory`, `scripts/docs/check-doc-consistency.ps1`, `scripts/ai/refresh-ai-memory.ps1`, and `scripts/ai/refresh-ai-memory.ps1 -Check` passed after the extraction cleanup and repo-memory refresh.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active extracted-runtime risks remain in the live changelog or hot/warm repo-memory files.

## Next
- `2026-05-18`: The `neon` extraction cleanup is complete; continue with normal PojoLens library work unless deeper archive scrubbing is requested.
