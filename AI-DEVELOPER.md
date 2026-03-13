# AI Developer Workflow

This document explains how developers should use AI agents (e.g., Codex in IntelliJ) with the repository’s persistent AI memory system.

The repository includes an AI context system located in `/ai`.

This system allows agents to understand the project architecture, current work, and repository constraints without rediscovering everything.

The root `AGENTS.md` defines how agents load and maintain repository context.

The system uses a **Hot / Cold Context model** to keep AI interactions fast.

---

# Hot vs Cold Context

## Hot Context (Always Loaded)

These files provide the minimal context needed for most tasks.

ai/core/agent-invariants.md  
ai/core/repo-purpose.md  
ai/state/current-state.md  
ai/state/handoff.md

These files contain:

- repository rules
- project purpose
- current working state
- next steps

They must remain **small and concise**.

---

## Cold Context (Loaded When Needed)

These files provide deeper architectural and discovery information.

They should **not be loaded automatically**.

ai/core/module-index.md  
ai/core/system-boundaries.md  
ai/core/architecture-map.md  
ai/core/readme-alignment.md  
ai/core/documentation-index.md  
ai/core/test-strategy.md  
ai/core/runbook.md  
ai/core/discovery-notes.md

ai/indexes/*  
ai/log/events.jsonl

Load them only when the task requires deeper repository knowledge.

---

# 1. Starting a New AI Session

When opening the repository or starting a new Codex session, load the **hot context** first.

Example prompt:

Follow AGENTS.md and load the default repository context.  
Summarize the repository purpose and current work.

The AI should read:

ai/core/agent-invariants.md  
ai/core/repo-purpose.md  
ai/state/current-state.md  
ai/state/handoff.md

After this step, the agent should understand:

- what the repository does
- the rules it must follow
- the current work in progress
- what should happen next

---

# 2. Starting Development Work

When beginning a task, reference the memory system.

Example prompts:

Follow AGENTS.md and load the default context.  
Help implement the next task listed in current-state.md.

or

Based on ai/state/current-state.md and TODO.md,  
help optimize ReflectionUtil.

This ensures the AI works from **verified repository knowledge**, not assumptions.

---

# 3. Navigating the Repository

If you need to explore code structure, load **module context**.

Example prompt:

Follow AGENTS.md.  
Also load ai/core/module-index.md and relevant files in ai/indexes.  
Help locate where filtering logic is implemented.

Useful navigation files:

ai/core/module-index.md  
ai/indexes/files-index.json  
ai/indexes/symbols-index.json  
ai/indexes/test-index.json

---

# 4. Architecture and Design Work

If making architectural changes, load deeper architecture context.

Example prompt:

Follow AGENTS.md.  
Also load architecture-map.md and system-boundaries.md before analyzing this change.

Relevant files:

ai/core/architecture-map.md  
ai/core/system-boundaries.md  
ai/core/module-index.md

---

# 5. Documentation Changes

If modifying documentation or APIs, verify alignment between code and docs.

Relevant files:

ai/core/readme-alignment.md  
ai/indexes/docs-index.json

Example prompt:

Check whether this code change affects documentation alignment.  
Update ai/core/readme-alignment.md if necessary.

---

# 6. Updating AI Memory After Work

Before finishing a session, update the AI memory.

Example prompt:

Update ai/state/current-state.md and ai/state/handoff.md based on the work completed.  
Log any significant discoveries to ai/log/events.jsonl.

Expected updates:

### current-state.md

Update:

- repository health
- active work areas
- new discoveries
- documentation risks

### handoff.md

Update:

- next tasks
- relevant files to open next
- unresolved questions

---

# 7. Updating Core Memory

Core memory should only change when **durable repository truths change**.

Examples:

ai/core/module-index.md  
ai/core/system-boundaries.md  
ai/core/repo-purpose.md

Example prompt:

If this change affects repository architecture,  
update ai/core/module-index.md accordingly.

Avoid rewriting core files unless necessary.

---

# 8. Regenerating Repository Indexes

If the repository structure changes significantly (new modules or major refactors), regenerate indexes.

Example prompt:

Regenerate repository indexes under ai/indexes.

Indexes include:

ai/indexes/files-index.json  
ai/indexes/symbols-index.json  
ai/indexes/docs-index.json  
ai/indexes/test-index.json  
ai/indexes/config-index.json

Indexes should represent the **current repository structure only**.

---

# 9. Compacting AI Memory

The AI memory system must remain lightweight.

Occasionally run:

Review ai/log/events.jsonl and apply memory compaction rules from ai/AGENTS.md.

This may:

- summarize older logs
- archive historical discoveries
- keep hot context files minimal

---

# 10. Recommended IntelliJ Prompt

Create a reusable prompt called **Load Repo Context**.

Prompt:

Follow AGENTS.md.  
Load the default repository context.

Summarize:

1. repository purpose
2. current state
3. next tasks

Running this prompt at the start of every session allows the AI to behave like a **persistent teammate**.

---

# Key Principles

AI agents must always follow these rules:

- Code and tests override AI memory.
- Documentation must align with implementation.
- AI memory must remain accurate and compact.
- Core knowledge should remain stable across sessions.
- State files should represent the latest verified work.

The `/ai` directory acts as a **persistent repository knowledge base for AI agents**.