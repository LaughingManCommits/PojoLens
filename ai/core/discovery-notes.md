# Discovery Notes

- `TODO.md` remains the backlog document even when the current roadmap has no open work packages.
- Runtime sources live under module-local `pojo-lens/src/...`; benchmark sources live under `pojo-lens-benchmarks/src/...`.
- Derived AI navigation lives under `ai/indexes/*.json`; optional cold search lives in `ai/indexes/cold-memory.db` and must never replace Markdown truth.
- AI event history is tiered: keep recent discoveries in `ai/log/events.jsonl` and archive older significant events under `ai/log/archive/*.jsonl`.
- `target/` contains generated outputs and is not source of truth.
- Maven Central publishing is implemented via `release-central` profile in `pom.xml` and `.github/workflows/release.yml`.
- Benchmark runner usage should resolve `target/*-benchmarks.jar` dynamically instead of hardcoding a versioned filename.
- The Claude orchestrator runtime is repo-local under `.claude-orchestrator/`; copy-mode workspaces must exclude that tree so parallel or overlapping runs do not copy runtime artifacts back into worker sandboxes.
