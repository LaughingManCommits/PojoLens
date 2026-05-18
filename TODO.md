# TODO

## Current Goal

- Remove the in-repo multi-agent and orchestrator stack from PojoLens. That
  code has moved to its own codebase: `neon`.
- Keep PojoLens focused on the Java library, examples, benchmarks, release
  flow, docs, and repo-memory helpers.
- Keep `scripts/ai/refresh-ai-memory.*` and `scripts/ai/query-ai-memory.*`
  only if they are still needed for this repo's memory workflow.

---

## Working Rules

- Do not add new orchestrator features in this repo.
- Prefer deleting stale compatibility layers over preserving the
  `pojolens-agents` surface here.
- Remove code, tests, docs, packaging, and retained runtime data together so
  the repo does not keep broken references.

---

## Removal Backlog

### 1. Package and CLI surface

- [ ] Remove `pyproject.toml` packaging for `pojolens-agents`.
- [ ] Remove `scripts/ai/claude-orchestrator.py`.
- [ ] Remove `scripts/ai/claude-orchestrator.ps1`.
- [ ] Remove `scripts/ai/pojo_lens_agents/**`.
- [ ] Remove `scripts/ai/pojolens_agents.egg-info/**`.

### 2. Tracked control-plane files

- [ ] Remove `ai/orchestrator/**` after any PojoLens-only facts are preserved
  elsewhere.
- [ ] Remove stale orchestrator roadmap/history references from `ai/state/*`
  and other repo-memory files.

### 3. Runtime artifacts and retained data

- [ ] Remove repo-local retained run data under `.claude-orchestrator/**`.
- [ ] Remove orchestrator-generated run data under `runs/**` if it is not
  needed for any remaining PojoLens workflow.

### 4. Tests and Python-only tooling

- [ ] Remove orchestrator-only tests under `scripts/tests/`.
- [ ] Keep or replace only the tests that still cover repo-memory helpers.
- [ ] Re-run the surviving validation set after the removals land.

### 5. Docs and metadata

- [ ] Remove or rewrite orchestrator references in `README.md`,
  `CHANGELOG.md`, `CLAUDE.md`, `MAINTENANCE.md`, `AGENTS.md`, and related docs.
- [ ] Decide how much old orchestrator history should stay in this repo's
  changelog versus moving to `neon`.
- [ ] Remove editor or CI assumptions that still expect the Python package to
  exist.

---

## Keep

- The Java library modules and Maven build.
- Example applications and benchmark tooling.
- Repo-memory workflow files under `ai/core/*`, `ai/state/*`, and the memory
  refresh/query scripts, unless they are explicitly replaced.

---

## Out Of Scope

- No new work on the extracted multi-agent runtime in this repo.
- No attempt to keep `pojolens-agents` backwards compatible here.
- No `neon` feature tracking here beyond the removal work needed for
  PojoLens.

---

## Done Recently

- [x] `2026-05-18`: Replaced the stale WP backlog with a cleanup backlog
  focused on removing the in-repo multi-agent stack after the move to `neon`.
