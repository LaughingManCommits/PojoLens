# Current State

## Repository Health

- Repository is a single-module Maven Java library (`jar`) on Java 17.
- Coordinates are `io.github.laughingmancommits:pojo-lens:1.0.0`.
- `pom.xml` includes `release-central` profile for Maven Central publishing.
- CI workflows present: `.github/workflows/ci.yml` and `.github/workflows/release.yml`.
- `TODO.md` tracks active high-value work; pagination spike is in progress.

## Latest Validation

- `2026-03-20`: `mvn -q test` passed after pagination changes (`OFFSET` support + docs/tests updates).
- `2026-03-20`: focused suite passed (`SqlLikeDocsExamplesTest`, `SqlLikeParserTest`, `SqlLikeMappingParityTest`, `ExplainToolingTest`, `PublicApiCoverageTest`).
- `2026-03-20`: `scripts/check-doc-consistency.ps1` passed.
- `2026-03-20`: lint baseline script currently reports baseline drift (`new=1549`, `fixed=5417`) and needs intentional baseline refresh strategy before treating as a gate.

## Release Status

- Central namespace is verified for `io.github.laughingmancommits`.
- Release workflow supports tag-triggered (`v*`) and manual publish.
- Most recent Central publish reached bundle upload but failed signature verification because Central could not find the signer public key.
- Public key was then uploaded to `keyserver.ubuntu.com` and `keys.openpgp.org`; publish should be retried after keyserver propagation.

## Active Work

- Pagination spike 1 progressed:
  - Fluent + SQL-like `OFFSET` implemented.
  - Explain payloads include `offset`.
  - SQL-like grammar supports `OFFSET` and `LIMIT ... OFFSET`.
  - Docs/tests include offset and keyset/cursor query patterns.
- Remaining in spike 1: first-class keyset/cursor API primitives (beyond documented query patterns).

## Next Actions

- Complete keyset/cursor API design + implementation for spike 1.
- Decide lint baseline policy (refresh baseline vs reduce inherited violations).
- Retry release workflow for `v1.0.0` (or manual dispatch) and confirm Central publish status.
