# Handoff

## Resume Order

1. Load hot context files.
2. Confirm workspace status with `git status --short`.
3. Check backlog status in `TODO.md`.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific tasks.

## Current Focus

- No open backlog items.
- Primary pending operational task is Maven Central release completion.
- Release pipeline and docs are in place; remaining issue is signer-key discovery/propagation on Central keyservers.

## Next Validation

- After any code change: run focused tests, then `mvn -q test`.
- For docs/process edits: run `scripts/check-doc-consistency.ps1`.
- For release-path changes: run `mvn -B -ntp -Prelease-central -DskipTests package`.

## Release Retry Checklist

- Confirm GitHub secrets exist: `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.
- Ensure release tag matches `pom.xml` version (`vX.Y.Z` vs `project.version`).
- Trigger `.github/workflows/release.yml` via tag push or `workflow_dispatch`.
- If signature lookup still fails, wait and retry after keyserver propagation.
