# AI Developer Workflow

This document describes how developers should use AI agents (e.g., Codex in IntelliJ) with the repository's persistent AI memory system.

The repository includes an AI context system located in `/ai`.  
This system allows agents to understand the project architecture, current work, and repository constraints without rediscovering everything.

The root `AGENTS.md` defines the rules agents must follow.

---

# 1. Starting a New AI Session

When opening the repository or starting a new Codex session in IntelliJ, load the repository context first.

Prompt example:

Follow AGENTS.md and load the repository context files in the required order.
Summarize the repository purpose, architecture, and current work.

This loads:

ai/core/agent-invariants.md
ai/core/repo-purpose.md
ai/core/module-index.md
ai/core/readme-alignment.md
ai/core/runbook.md
ai/core/documentation-index.md
ai/state/current-state.md
ai/state/handoff.md

After this step, the agent should understand:

- repository purpose
- architecture and modules
- current work and open tasks

---

# 2. Starting Development Work

When beginning a task, reference the existing memory system.

Example prompts:

Follow AGENTS.md and load repository context.
Help implement the WP5 selective materialization improvements referenced in current-state.md.

or

Based on ai/state/current-state.md and TODO.md,
help optimize ReflectionUtil.

This ensures the AI works from actual repository knowledge rather than assumptions.

---

# 3. Navigating the Repository

Use the memory indexes and module descriptions to locate code.

Example prompt:

Using ai/core/module-index.md and ai/indexes/symbols-index.json,
locate where filtering logic is implemented.

The following files are useful for navigation:

ai/core/module-index.md
ai/indexes/files-index.json
ai/indexes/symbols-index.json
ai/indexes/test-index.json

---

# 4. Updating Documentation Alignment

When behavior or APIs change, verify documentation alignment.

If documentation becomes outdated update:

ai/core/readme-alignment.md
ai/indexes/docs-index.json

Example prompt:

Check whether this code change affects documentation alignment.
If so, update ai/core/readme-alignment.md.

---

# 5. Updating AI Memory After Work

Before finishing a session, update the AI memory.

Prompt example:

Update ai/state/current-state.md and ai/state/handoff.md
based on the work completed in this session.
Log significant events to ai/log/events.jsonl.

Expected updates:

current-state.md
- repository health
- active work areas
- new discoveries
- documentation risks

handoff.md
- next work tasks
- relevant files to open next
- unresolved questions

---

# 6. Updating Core Memory

Core memory should change only when durable repository truths change.

Examples:

ai/core/module-index.md
ai/core/system-boundaries.md
ai/core/repo-purpose.md

Prompt example:

If this change affects repository architecture,
update ai/core/module-index.md accordingly.

---

# 7. Regenerating Indexes

If repository structure changes significantly (new modules or refactors), regenerate indexes.

Prompt example:

Regenerate repository indexes under ai/indexes.

Indexes:

ai/indexes/files-index.json
ai/indexes/symbols-index.json
ai/indexes/docs-index.json
ai/indexes/test-index.json
ai/indexes/config-index.json

---

# 8. Compacting AI Memory

AI memory should remain concise and fast to load.

Occasionally run:

Review ai/log/events.jsonl and apply memory compaction rules from ai/AGENTS.md.

This may:

- summarize older logs
- archive historical details
- keep current knowledge concise

---

# 9. Recommended IntelliJ Prompt

Create a reusable prompt called **Load Repo Context**.

Prompt:

Follow AGENTS.md.
Load repository context files in the required order.

Summarize:
1. repository purpose
2. architecture
3. current state
4. next tasks

Running this prompt at the start of every session allows the AI agent to behave like a persistent teammate.

---

# 10. Key Principles

Agents should always follow these rules:

- Code and tests override AI memory.
- Documentation must align with implementation.
- AI memory must remain accurate and compact.
- Core knowledge should be stable across sessions.
- State files should represent the latest verified work.

The `/ai` directory acts as a persistent knowledge base for the repository.
