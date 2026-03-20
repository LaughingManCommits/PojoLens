# Discovery Notes

- `TODO.md` is the backlog-style document and is currently empty.
- No separate roadmap, ADR directory, or design-notes folder is present.
- `target/` contains generated outputs and is not source of truth.
- Maven Central publishing is implemented via `release-central` profile in `pom.xml` and `.github/workflows/release.yml`.
- Benchmark runner usage should resolve `target/*-benchmarks.jar` dynamically instead of hardcoding a versioned filename.
