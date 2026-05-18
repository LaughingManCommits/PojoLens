# Recent Validations

- `2026-05-18`: `scripts/docs/check-doc-consistency.ps1` and `py -3 scripts/docs/check-doc-consistency.py` passed after updating consumer install docs to `2026.05.18.1353` and teaching the doc checker to validate published-version docs separately from the checked-in POM-backed example builds.
- `2026-05-18`: `mvn -B -ntp test`, `scripts/docs/check-doc-consistency.ps1`, `scripts/ai/refresh-ai-memory.ps1`, and `scripts/ai/refresh-ai-memory.ps1 -Check` passed after wiring release wait mode through Maven properties, backfilling `release-2026.05.18.1353`, and updating release docs/state.
- `2026-05-18`: `py -3 -m unittest scripts.tests.test_refresh_ai_memory`, `scripts/docs/check-doc-consistency.ps1`, `scripts/ai/refresh-ai-memory.ps1`, and `scripts/ai/refresh-ai-memory.ps1 -Check` passed after the `neon` extraction cleanup, live-history scrub, and repo-memory refresh.
