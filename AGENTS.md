# Repository Agent Instructions

This repository uses a persistent AI memory system stored under `/ai`.

The `/ai` directory contains durable project context so future agents can understand the repository without rediscovering everything.

Rules for maintaining the memory system are defined in:

`ai/AGENTS.md`

Agents must read this file before modifying `/ai` memory.

---

# Required Context Load Order

Before performing any task, read these files in order:

1. `ai/core/agent-invariants.md`
2. `ai/core/repo-purpose.md`
3. `ai/core/module-index.md`
4. `ai/core/readme-alignment.md`
5. `ai/core/runbook.md`
6. `ai/core/documentation-index.md`
7. `ai/state/current-state.md`
8. `ai/state/handoff.md`

If additional core files exist (for example `system-boundaries.md` or `architecture-map.md`), read them **before the state files**.

Core files describe **stable repository truths**.  
State files describe **the most recent work session**.

---

# Memory Freshness

The file `ai/memory-state.json` records when the repository was last scanned.

If the repository has changed significantly since the recorded commit or scan time, agents should:

- regenerate affected index files in `ai/indexes/`
- update relevant core files if repository truths changed
- update `ai/state/current-state.md`

---

# Trust Order for Facts

When evidence conflicts, use this priority order:

1. runtime code in `src/main/java`
2. build and CI configuration (`pom.xml`, workflows)
3. tests in `src/test/java` and `src/test/resources`
4. repository markdown documentation
5. `/ai` generated memory

If `/ai` memory contradicts code or tests, prefer **code and tests** and update the `/ai` memory.

---

# Repository Operating Rules

Agents must treat this repository as:

**a library artifact (JAR), not a deployable service.**

Key rules:

- Recheck README and documentation claims against **code and tests**.
- Use `TODO.md` for planned work; no separate backlog file exists.
- Ignore `target/` when deriving repository truth except when validating generated artifacts.

---

# Documentation Consistency

When public behavior or documentation changes:

Update:

- `ai/core/readme-alignment.md`
- `ai/indexes/docs-index.json`

This ensures documentation stays aligned with the implementation.

---

# Session Completion Rules

Before ending a task or session:

1. update `ai/state/current-state.md`
2. update `ai/state/handoff.md`
3. append significant events to `ai/log/events.jsonl`
4. regenerate indexes if repository structure changed
5. ensure `/ai` memory still reflects repository truth

If `/ai` memory appears stale or inconsistent with code, update it following the rules in `ai/AGENTS.md`.