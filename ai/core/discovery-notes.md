# Discovery Notes

- `TODO.md` is the only active backlog-style document in the repository.
- No separate roadmap, ADR directory, or design-notes folder is present.
- `target/` contains generated outputs and should not be treated as repository truth.
- No `distributionManagement` block or publication workflow was found.
- Benchmark runner usage depends on resolving the generated `target/*-benchmarks.jar` path instead of hardcoding a versioned filename.
