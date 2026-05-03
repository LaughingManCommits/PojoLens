# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-03`: WP68 is queued next at high priority; create one shared Matrix-style Textual theme across `tui_console.py`, `tui_app.py`, and wizard prompts, and remove mojibake-corrupted TUI text.
- `2026-05-03`: WP60 is complete; shared interactive streaming now reaches watch and TUI surfaces; 836 Python tests passed.
- `2026-05-03`: WP50 is complete; proactive TPM/RPM throttling, wizard wiring, and follow-up-budget recompute landed; 799 tests passed.
- `2026-05-03`: WP59 and WP67 are complete; Textual ownership is consolidated and covered by 65 focused `tui_console` tests.
- `2026-05-03`: WP58/WP57/WP55 remain the active operator base: persistent console, retained diff preview, and guided wizard flow.

## Verified
- `2026-05-03`: Full Python suite last recorded at `836` tests after WP60.
- `2026-05-03`: Focused validations passed for WP60, WP50, WP59, WP67, WP58, and WP57.
- `2026-05-03`: `scripts/docs/check-doc-consistency.ps1` passed.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-03`: Roadmap order is WP68 -> WP56 -> WP61 -> WP62 -> WP63 -> WP64 -> WP65 -> WP66 -> WP40 -> Release Gate.
