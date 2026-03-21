# Handoff

## Resume Order

1. Load hot context files.
2. Confirm workspace status with `git status --short`.
3. Check backlog status in `TODO.md`.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific tasks.

## Current Focus

- Spike 1 (pagination) is active:
  - `OFFSET` is implemented in fluent + SQL-like flows.
  - SQL-like named parameters are supported for `LIMIT/OFFSET` with integer/non-negative validation.
  - keyset/cursor usage is documented and tested as query patterns.
  - first-class keyset/cursor API primitives are still pending.
- Maven Central release completion remains pending operational work.

## Next Validation

- After any code change: run focused tests, then `mvn -q test`.
- For docs/process edits: run `scripts/check-doc-consistency.ps1`.
- For release-path changes: run `mvn -B -ntp -Prelease-central -DskipTests package`.
- Lint note: `scripts/check-lint-baseline.ps1` currently reports large baseline drift and is not a clean gate without baseline maintenance.

## Release Retry Checklist

- Confirm GitHub secrets exist: `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.
- Ensure release tag matches `pom.xml` version (`vX.Y.Z` vs `project.version`).
- Trigger `.github/workflows/release.yml` via tag push or `workflow_dispatch`.
- If signature lookup still fails, wait and retry after keyserver propagation.

## Pagination Follow-Up Checklist

- Define public keyset/cursor API surface (builder helpers and cursor token contract).
- Add deterministic tie-breaker contract docs (null handling, sort-field requirements).
- Add parity + edge-case tests on tie-heavy datasets.
