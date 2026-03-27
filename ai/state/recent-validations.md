# Recent Validations

- `2026-03-26`: `mvn -q test` passed after the dashboard-simplification and starter-example hardening work.
- `2026-03-26`: `scripts/check-doc-consistency.ps1` passed after the starter example and docs refresh.
- `2026-03-26`: `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test` passed after the dashboard UI, chart, and form-validation fixes.
- `2026-03-27`: `py -3 scripts/refresh-ai-memory.py --compact-log` passed and compacted AI event history to `12` active events plus `73` archived events in `ai/log/archive/2026-03.jsonl`.
- `2026-03-27`: `py -3 scripts/refresh-ai-memory.py --check` passed with hot context reduced to `91` lines / `5550` bytes and SQLite cold search built.
- `2026-03-27`: `py -3 scripts/query-ai-memory.py "<archive topic>"` and the PowerShell wrapper both returned archive-aware cold-search results, confirming the archived event log is searchable.
