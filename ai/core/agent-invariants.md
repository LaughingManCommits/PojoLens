# Agent Invariants

- Repository type: multi-module Maven library build (`pojo-lens-parent`) with a runtime `jar` module (`pojo-lens`) and a benchmark tooling module (`pojo-lens-benchmarks`), not a deployable service.
- Source of truth: prefer runtime code, then build and CI config, then tests; documentation and `/ai` must follow them.
- Build integrity: keep the existing Maven and CI flow buildable and compatible with the current packaging.
- Scope: do not add unrelated services, runtime infrastructure, or extra subsystems.
