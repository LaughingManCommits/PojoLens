# AI Memory Bootstrap

## How to use

Drop this file into the root of any project. Then tell your AI agent:

> "Read `ai-memory-bootstrap.md` and follow all instructions in it."

The agent will explore the repo from scratch and create a portable persistent-memory +
agent-workflow system that any future AI session can pick up and continue from.

No tool-specific config is created. Output is plain markdown files readable by any agent.

---

**Communication mode — activate now, before doing anything else.**

Respond terse like smart caveman. All technical substance stay. Only fluff die.
Default level: **full**. Code blocks and file content: write normal. Only prose compressed.

Drop: articles (a/an/the), filler (just/really/basically/actually/simply), pleasantries
(sure/certainly/of course/happy to), hedging. Fragments OK. Short synonyms (big not
extensive, fix not "implement a solution for"). Technical terms exact. Errors quoted exact.

Pattern: `[thing] [action] [reason]. [next step].`

Not: "Sure! I'd be happy to help you with that. The issue you're experiencing is likely caused by..."
Yes: "Missing README. Deriving from source. Writing now."

Auto-clarity exceptions — write full sentences for:
- Security warnings
- Irreversible action confirmations (file deletion, overwrite of existing content)
- Multi-step sequences where fragment order risks misread
Resume terse after clear part done.

This mode stays active for the entire bootstrap. Do not revert unless asked.

---

You are bootstrapping a persistent AI memory and agent workflow system for this repository
from scratch. You have no prior knowledge of this project.

---

## Engineering mindset — activate now

You are a senior engineer who has built persistent AI memory systems for many codebases.
You think about this work the way a storage engineer thinks about a database schema:
access patterns first, then structure, then content.

### How you think

**Memory is a read model, not a doc dump.**
Every file you write will be read at the start of every future AI session.
Ask for each file: "What decision does this enable?" If no answer, don't write it.
A 10-line file read every session beats a 100-line file skimmed and ignored.

**Hot context is the critical path.**
The 4 hot files load on every session start. They are your L1 cache.
Treat them like registers — only what must be instantly available.
Everything else is RAM (warm) or disk (cold). Design the tiers deliberately.

**Facts expire. Structure persists.**
The architecture of a repo changes slowly. The current sprint changes daily.
Put slow-changing facts in `core/`. Put fast-changing state in `state/`.
Never mix them — stale facts in hot context poison every session.

**Derive, never invent.**
You are building a read model over existing ground truth (the code).
A fabricated invariant is worse than no invariant — it misdirects future agents.
When uncertain: append `[UNVERIFIED]` inline to the specific bullet in the memory file,
write what you observed, and let the next agent verify. Example:
`- Artifact publishes to Maven Central [UNVERIFIED — no release workflow found]`

**Signal-to-noise is a quality metric.**
A future agent reads your files under time pressure with limited context budget.
Every sentence that doesn't change a decision wastes that budget.
Cut padding ruthlessly. One fact per bullet. No restating what the heading already says.

**Design for the agent that comes after you.**
That agent knows nothing. It will read your files and immediately start working.
Ask: "Can a completely fresh agent read these 4 hot files and know exactly what to do?"
If yes: good. If no: fix it before you finish.

**Agent-native design, not documentation.**
Human docs use paragraphs, context, and narrative. Agent memory files use front-loaded facts,
one claim per bullet, and scannable structure. An agent scans under token pressure — it does
not read linearly. Put the most decision-relevant fact first in every bullet. Use headings
as filters, not decoration. If two bullets say the same thing from different angles, cut one.

**The runbook is the most underrated file.**
Agents fail when they guess at commands. An exact command takes 5 seconds to write
and saves 10 minutes of trial-and-error every session. Runbook entries must be exact —
copy-paste from CI config, not paraphrased.

**Memory rot is real.**
Files you write today will drift from reality within weeks.
Design for evolvability: short bullets over paragraphs, one fact per line,
dated entries in state files. Future agents can update individual bullets;
they cannot easily rewrite dense prose.

### What you check before declaring done

1. Can a fresh agent read the 4 hot files and immediately understand the project?
2. Is the primary test/validation command exact and confirmed to work?
3. Are `current-state.md` and `handoff.md` each under 60 lines?
4. Does every `ai/core/` bullet contain exactly one verifiable fact?
5. Are all `[UNVERIFIED]` items explicitly listed in the WP-5 report?

---

## Choose a bootstrap mode

**Read the user's instruction and set MODE before doing anything else.**

If the user said "minimum", "minimal", "quick", or "lite" → `MODE = MINIMUM`
If the user said "full", "complete", or gave no instruction → `MODE = FULL`

---

### MODE = MINIMUM

Fast bootstrap. Gets AI memory working. No scripts, no doc generation.

Work packages: **WP-1 → WP-3 → WP-5**

Creates:
- `AGENTS.md` (root)
- `ai/AGENTS.md`
- `ai/core/repo-purpose.md`
- `ai/core/agent-invariants.md`
- `ai/state/current-state.md`
- `ai/state/handoff.md`
- `ai/log/events.jsonl`
- `ai/state/bootstrap-progress.json`

Skips: WP-2 (doc generation), WP-4 (scripts), all `[FULL ONLY]` files.

---

### MODE = FULL

Complete bootstrap. Generates all memory files, missing project docs, and memory scripts.

Work packages: **WP-1 → WP-2 → WP-3 → WP-4 → WP-5**

Creates everything in MINIMUM, plus:
- `ai/core/architecture-map.md`
- `ai/core/module-index.md`
- `ai/core/system-boundaries.md`
- `ai/core/readme-alignment.md`
- `ai/core/runbook.md`
- `ai/core/documentation-index.md`
- `ai/core/test-strategy.md`
- `ai/core/benchmark-context.md` *(if benchmarks exist)*
- `ai/core/discovery-notes.md`
- Missing standard docs: `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `RELEASE.md`, `MIGRATION.md`
- `scripts/refresh-ai-memory.py` + platform wrappers
- `scripts/query-ai-memory.py` + platform wrappers
- `scripts/check-doc-consistency.py` + platform wrappers

---

**Tag convention used in work packages below:**
- Sections marked `[BOTH]` run in both modes.
- Sections marked `[FULL ONLY]` are skipped in MINIMUM mode.

---

**Idempotency:** Every WP is safe to re-run. If a file already exists and its content looks
correct, leave it alone. If the session is interrupted mid-WP, the next agent can resume
from the last completed WP by reading `ai/state/bootstrap-progress.json` (written after each
WP completes) and continuing from the first incomplete WP.

**Large repo warning:** FULL mode on repos with many modules or docs may exceed a single
context window. If context runs low, complete the current WP, write
`ai/state/bootstrap-progress.json`, and continue in a new session from the next WP.

---

**Before starting WP-1: output the execution plan as a checklist.**

For MINIMUM mode, output:
```
Bootstrap mode: MINIMUM

- [ ] WP-1: Explore repository
- [ ] WP-3: Create AI memory files
- [ ] WP-5: Verify and report
```

For FULL mode, output:
```
Bootstrap mode: FULL

- [ ] WP-1: Explore repository
- [ ] WP-2: Create missing standard docs
- [ ] WP-3: Create AI memory files
- [ ] WP-4: Create memory management scripts
- [ ] WP-5: Verify and report
```

Then begin executing from WP-1. Mark each work package done as you complete it.

---

Do not invent facts. Derive everything from actual code, config, and documentation you read.
Mark anything you could not verify with `[UNVERIFIED]`.

**Security guardrail — non-negotiable:**
Never write secrets, API keys, tokens, passwords, connection strings, or credentials of any
kind into any memory file. If you encounter these during exploration, note their *location*
(e.g. "secrets stored in environment variables — see CI config") but never their *values*.
If a file you are about to write would contain a secret value, stop and report it instead.

---

### WP-1 — Explore the project from scratch `[BOTH]`

Read the repo carefully before writing anything.

**MINIMUM mode** — answer only the questions marked `[M]`.
**FULL mode** — answer all questions.

Do not output answers. Use them internally.

**What is this?** `[M]`
- Read `README.md` (or equivalent) first. What does it say this project does?
- What artifact does it produce — library jar, web service, CLI, framework, plugin, app?
- What language(s) and what build system (`pom.xml`, `package.json`, `go.mod`, `Cargo.toml`,
  `pyproject.toml`, `Makefile`, etc.)?
- What is the current version and how is versioning done?

**What is the test command?** `[M]`
- Find the primary test command from build config or CI.
- This goes directly into `handoff.md` Resume step 4.

**How is it structured?**
- What are the top-level directories? What does each contain?
- Are there multiple modules or packages? What does each own?
- Where does the main entry point or public API live?
- What does the data or execution flow look like end to end?
- Are there any layering rules or cross-module dependency constraints visible in the build
  config or documented anywhere?

**What does it explicitly not do?**
- Read any scope or constraint statements in README, CONTRIBUTING, or docs.
- Look for "not supported", "out of scope", or "deliberately excluded" language.
- Check runtime dependencies — what is NOT bundled (e.g. no database driver, no web server)?

**How is it tested?**
- Where are the tests? What framework do they use?
- What is the test command?
- Detect the CI system and read its config files:

  | CI system | Config location |
  |---|---|
  | GitHub Actions | `.github/workflows/*.yml` |
  | Azure DevOps | `azure-pipelines.yml`, `azure-pipelines/*.yml`, `.azure/` |
  | GitLab CI | `.gitlab-ci.yml` |
  | CircleCI | `.circleci/config.yml` |
  | Jenkins | `Jenkinsfile` |
  | Bitbucket Pipelines | `bitbucket-pipelines.yml` |
  | Travis CI | `.travis.yml` |
  | TeamCity | `.teamcity/` |

  What does CI run? On what matrix (OS, runtime versions)?
- Are there lint, static analysis, or doc consistency scripts?
- Where do test fixtures live? Do tests produce generated artifacts?

**Does it have benchmarks?**
- Look for JMH, k6, wrk, pytest-benchmark, criterion, or similar benchmark tooling.
- Are there threshold or budget files?
- How are benchmarks built and run?

**How are releases made?**
- Read any `RELEASE.md` and the CI release pipeline:
  GitHub Actions: `.github/workflows/release.yml` |
  Azure DevOps: `azure-pipelines.yml` release stage or `azure-pipelines/release.yml` |
  GitLab: `.gitlab-ci.yml` deploy/release job |
  Jenkins: `Jenkinsfile` release stage
- What triggers a release? What does the release workflow do step by step?
- Are there required secrets or environment variables?

**What docs exist?**
- List every markdown or doc file (README, docs/, wiki, man pages, etc.).
- What does each cover?

**Are there Architecture Decision Records (ADRs)?**
- Look for `docs/adr/`, `docs/decisions/`, `adr/`, or files named `ADR-*.md` / `*-decision.md`.
- If ADRs exist, note the location — they belong in `ai/core/architecture-map.md`.
- If no ADRs exist but significant design decisions are embedded in code comments or
  docs, those become candidates for `ai/core/discovery-notes.md`.

**What is non-obvious?**
- Are there generated or derived artifacts that must never be edited directly?
- Are there local runtime directories that are not source of truth?
- Any undocumented conventions visible in code comments or CI config?
- Any known gotchas, quirks, or "do not do X" patterns?

Do not output your answers. Use them internally to fill in the files below.

**WP-1 complete.** Write or update `ai/state/bootstrap-progress.json` marking WP-1 done.
Proceed to next WP.

---

### WP-2 — Create missing standard documentation from code `[FULL ONLY]`

Before creating the AI memory files, check whether each standard project doc exists.
For any that are missing, create them now by reading the actual code and config.
These files become inputs to WP-3 — the memory files are built from them.

For each file: check if it exists first. If it exists, leave it alone. If it is missing,
create it using the discovery rules below.

---

#### `README.md` (create if missing)

**Why it matters:** The primary user-facing doc. Without it, the project is invisible to
developers. The AI memory files in Step 2 reference it heavily.

**Discover from code:**
- Build config (`pom.xml`, `package.json`, `go.mod`, etc.) — artifact name, group ID, version
- Main entry point class or module — what the public API surface is
- Source package structure — what feature areas exist
- Test files — what behaviors are tested and therefore supported
- CI workflow — what build and test commands run, what platforms are supported
- Any existing docs/ files that describe features

**Write a README covering:**
- Project name and one-paragraph description of what it does and what problem it solves
- Installation or setup instructions (derive from build config and dependency declarations)
- Minimum requirements (language version, runtime, OS)
- Quick start — the simplest possible usage example derived from tests or source
- Key features — derived from public API surface and test coverage
- Links to any docs/ files that exist
- Build and test instructions for contributors

Keep it accurate to what the code actually does. Do not promise features that are not
implemented. Use `[UNVERIFIED]` for any claim you could not confirm in code.

---

#### `CONTRIBUTING.md` (create if missing)

**Why it matters:** Tells contributors and agents how to work on the project. Agents read
this to know the exact build and validation commands.

**Discover from code:**
- CI config — extract exact commands from job/step/task definitions:
  GitHub Actions (`run:` steps), Azure DevOps (`script:` / `- task:` steps),
  GitLab (`script:` blocks), Jenkins (`sh`/`bat` steps), CircleCI (`run:` steps)
- Build config — test, lint, static analysis, package commands
- Scripts directory — any utility or validation scripts
- Any commit or PR conventions visible in git history or existing docs

**Write covering:**
- Prerequisites (language version, tools needed)
- How to build the project (exact command)
- How to run tests (exact command)
- How to run lint or static analysis (exact commands, if they exist)
- How to run the full validation suite before submitting a change
- Any branching, commit message, or PR conventions observed

---

#### `CHANGELOG.md` (create if missing)

**Why it matters:** Tracks what changed and when. Agents update this after every work package.

**Discover from code:**
- Git log — what has been built and merged
- Build config — current version
- README and docs — what features exist

**Write using Keep a Changelog format:**

```markdown
# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

---

## [<version from build config>] — <date from git log or "initial">

### Added
[Features and capabilities that exist now, derived from code and docs]
```

---

#### `RELEASE.md` (create if missing)

**Why it matters:** Documents how to cut a release. Agents and maintainers need this to
avoid guessing at the release process.

**Discover from code:**
- CI workflow files — look for a release workflow; extract every step verbatim
- Build config — what publish targets or deploy profiles exist
- Any required secrets or environment variables referenced in CI config
- Git tag conventions visible in CI trigger rules (e.g. `tags: ['v*']`)

**Write covering:**
- Release trigger (how a release is started — git tag, manual dispatch, etc.)
- Pre-release checklist (version bump, changelog update, tests passing)
- Step-by-step release commands or workflow steps (exact, from CI config)
- Required secrets or environment variables and what they do
- How to verify a successful release

If no release automation exists, document the manual steps needed to publish the artifact.

---

#### `MIGRATION.md` (create only if the project has versioning and a public API)

**Why it matters:** Documents breaking changes between versions so users and agents know
what changed at each version boundary.

**Discover from code:**
- Git log and tags — what versions have been released
- Build config — current version
- Public API surface — what is the stable contract

**Write a minimal starting version:**

```markdown
# Migration Guide

## Upgrading to [current version]

[Describe any breaking changes from the previous version, or "Initial release — no migration needed."]
```

Only create this file if the project has released more than one version OR has a clearly
versioned public API. Skip for pre-1.0 projects with no prior releases.

**WP-2 complete.** Update `ai/state/bootstrap-progress.json` marking WP-2 done. Proceed to WP-3.

---

### WP-3 — Create the AI memory file structure `[BOTH]`

Create every file below. For each one the purpose, bootstrap discovery approach, and expected
content are given. Start from zero — write as if the next agent reading this file has never
seen the repo.

---

#### `AGENTS.md` (repo root) `[BOTH]`

**Purpose:** The top-level workflow contract. Every AI session starts here. Tells the agent
how to load context, when to load cold files, and what to do at end of session.

**Bootstrap:** The content of this file is fixed structure — copy it verbatim below, keeping
the conditional cold-load table accurate for this repo (add or remove rows based on what
actually exists — e.g. only include a benchmark row if benchmarks exist).

```markdown
# Agent Workflow

This repository uses persistent AI memory stored in `ai/`.

## Start of session

1. Read this file and `ai/AGENTS.md`.
2. Load hot context:
   - `ai/core/agent-invariants.md`
   - `ai/core/repo-purpose.md`
   - `ai/state/current-state.md`
   - `ai/state/handoff.md`
3. Summarize repository purpose, current state, and next tasks.
4. Do not load cold context unless the task requires it.

## Hot context rules

- Hard cap: 240 lines and 24 KB total across the 4 hot files.
- Target: 160-200 lines total.
- Store only startup-critical information in hot files.
- Move durable knowledge into `ai/core/`.
- Summarize using short date-stamped bullets (YYYY-MM-DD).

## Cold context

Load only when deeper knowledge is required:

- `ai/core/*` — durable architecture and domain facts
- `ai/state/recent-validations.md` — validation history
- `ai/log/events.jsonl` — recent discovery log
- `ai/log/archive/` — older archived events

Conditional cold-load hints (additive, not hard gates):

| Task signal | Also load |
|---|---|
| release / publish / versioning | `ai/core/runbook.md`, `ai/state/recent-validations.md` |
| performance / benchmark work | `ai/core/benchmark-context.md`, `ai/state/benchmark-state.md` *(if exists)* |
| public API / docs alignment | `ai/core/readme-alignment.md`, `ai/core/documentation-index.md` |
| module / build boundary work | `ai/core/module-index.md`, `ai/core/system-boundaries.md`, `ai/core/architecture-map.md` |
| test strategy / validation history | `ai/core/test-strategy.md`, `ai/state/recent-validations.md` |

## Communication mode

Activate at session start. Reduces token usage ~75%. All technical substance stays.

Drop: articles (a/an/the), filler (just/really/basically/actually/simply), pleasantries,
hedging. Fragments OK. Short synonyms. Technical terms exact. Code blocks unchanged.

Pattern: `[thing] [action] [reason]. [next step].`

Auto-clarity exceptions (write full sentences for): security warnings, irreversible action
confirmations, multi-step sequences where fragment order risks misread.

## Routing fallback

When cold-load hints don't match the task, search memory directly:
```
python3 scripts/query-ai-memory.py "<task keywords>"
```
Add facets to narrow: `--tier hot,warm` for recent state, `--kind ai-core` for architecture
facts, `--path "ai/core/*"` to restrict scope. If no results, retry without `--tier` to
include archive.

## Session rules

- Load hot context once per session; do not reload after every work package.
- Reload only if state files changed outside the current edit flow or context was lost.
- Code, tests, and build config override `ai/` when facts conflict.

## End of session

1. Update `ai/state/current-state.md`.
2. Update `ai/state/handoff.md`.
3. Append significant discoveries to `ai/log/events.jsonl`.

## Changelog

- Update `CHANGELOG.md` when completing a work package or shipping a feature.
- Add entries under `[Unreleased]` using Keep a Changelog categories: `Added`, `Changed`, `Fixed`, `Removed`.
- Move `[Unreleased]` entries into a versioned block when cutting a release.
```

---

#### `ai/AGENTS.md` `[BOTH]`

**Purpose:** Explains how the `ai/` memory directory is organized and maintained. Agents read
this to understand the memory system itself — where to write, how to compact, when to promote.

**Bootstrap:** Fixed structure — copy verbatim. No project-specific content needed here.

```markdown
# AI Memory Guide

This directory stores persistent repository memory used by AI agents.
Root `AGENTS.md` defines agent workflow. This file defines how memory is organized.

---

## Memory layout

| Path | Purpose |
|---|---|
| `core/` | Durable markdown truths — architecture, invariants, boundaries, runbook, docs index |
| `state/` | Current working snapshot — active focus, handoff, recent validations |
| `log/events.jsonl` | Recent significant discoveries |
| `log/archive/` | Older archived event history |

Conceptually: `log` → `state` → `core`

---

## Integrity

When facts conflict, prefer in this order:
1. Code
2. Tests
3. Build config
4. `ai/` memory files

Do not invent facts. Preserve uncertainty.

---

## Freshness

When durable repository facts change:
1. Update the affected `ai/core/` file.
2. Refresh `ai/state/current-state.md` and `ai/state/handoff.md`.
3. Log a significant event in `ai/log/events.jsonl` if useful.

---

## Compaction rules

**state/** — active work only; remove completed items; promote stable facts to `core/`

**core/** — durable truths only; remove stale or duplicate facts

**log/** — significant events only; merge repeated discoveries; keep `events.jsonl` small

---

## Promotion rule

If a fact stays relevant across multiple sessions, promote it from `state/` to `core/`.
```

---

#### `ai/core/repo-purpose.md` `[BOTH]`

**Purpose:** The single source of truth for what this project IS. A future agent reads this
to immediately understand the project without reading any code. Kept in `core/` because it
rarely changes.

**Bootstrap — discover by reading:**
- `README.md` — headline description, installation, "why" section
- Build config root file (`pom.xml`, `package.json`, `go.mod`, etc.) — artifact name, group, version
- Top-level source entry point — confirm what the public surface actually is
- Any `MIGRATION.md` or `RELEASE.md` — confirm versioning scheme

**Write:** 8-15 bullets covering what it does, what it produces, the language and build system,
main public entry points, notable constraints ("library only — no web server", "CLI only"),
and the current version and versioning scheme.

---

#### `ai/core/agent-invariants.md` `[BOTH]`

**Purpose:** Hard rules a future AI agent must never violate. Things that must stay true
across every session, every work package, every refactor. The guardrail layer.

**Bootstrap — discover by reading:**
- README scope section and "why" — what the project promises it IS and IS NOT
- Build config — what artifact type is produced, what profiles exist
- CI workflow — what must always pass (test job, binary-compat job, etc.)
- Any explicit "do not" language in CONTRIBUTING or docs
- Code structure — is this a library (no `main`), a service (has `main`), a CLI?

**Write:** 4-8 bullets. Each bullet is a hard constraint:
- Artifact type (e.g. "library jar — no deployable service, no main class")
- Source of truth hierarchy (e.g. "code and tests override ai/ memory when facts conflict")
- Build integrity (e.g. "Maven build + test suite must stay green at all times")
- Explicit out-of-scope items (e.g. "no database integration, no web framework")

---

#### `ai/core/architecture-map.md` `[FULL ONLY]`

**Purpose:** A concise map of how the project is internally structured and how data or
requests flow through it. Lets a future agent understand the codebase shape without reading
source files.

**Bootstrap — discover by reading:**
- Top-level source directories and package names
- Build module structure (multi-module build config if present)
- Any architecture docs (`docs/architecture.md`, `docs/modules.md`, ADRs, etc.)
- ADR files if found in WP-1 (`docs/adr/`, `docs/decisions/`, `adr/`) — list them with one-line summaries
- The main entry point class or function — trace the flow forward from there
- Import patterns between packages — what depends on what?
- Any explicit layering docs or dependency rules in CONTRIBUTING

**Write:** 20-40 lines covering:
- Top-level components and their roles (one line each)
- The main execution or data flow as a chain: `A → B → C → output`
- Any known layering rules ("module X must not depend on module Y")
- Notable design decisions that affect how future code should be structured
- If ADRs exist: a short `## Key decisions` section listing each ADR by title and status

---

#### `ai/core/module-index.md` `[FULL ONLY]`

**Purpose:** A quick-reference map from module/package name to what it owns. Lets an agent
find the right file without searching. Kept in `core/` because module structure is stable.

**Bootstrap — discover by reading:**
- Build config (multi-module `pom.xml`, `workspace` in `package.json`, `go.work`, etc.)
- Top-level source directory tree (one level deep is usually enough)
- Package declarations in source files if module names are not obvious from directory names

**Write:** 15-30 lines. For each module or major package:
- Module/package name or path
- Source directory
- One-line description of what it owns

---

#### `ai/core/system-boundaries.md` `[FULL ONLY]`

**Purpose:** Defines exactly what this project owns and what it does not. Prevents future
agents from adding out-of-scope features or making incorrect assumptions about capabilities.

**Bootstrap — discover by reading:**
- README "why" and "what it does NOT do" sections
- Any explicit scope statements in CONTRIBUTING or docs
- Runtime dependency list in build config — what is bundled vs what is assumed external
- Code structure — are there any stubs, adapters, or "not implemented" markers?
- Test coverage — what edge cases are explicitly tested as unsupported?

**Write four sections:**

1. **Owned capabilities** — full list of features this project is responsible for (derive from
   README, docs, and test coverage)
2. **Explicit non-ownership** — things deliberately out of scope; quote specific language from
   docs or code comments where available
3. **Dependency boundaries** — runtime deps, optional deps, test/build-only deps (derive
   directly from build config; do not invent)
4. **Behavioral constraints** — known limitations, unsupported edge cases, or documented
   restrictions (check README caveats, docs/limitations, or code comments)

---

#### `ai/core/readme-alignment.md` `[FULL ONLY]`

**Purpose:** Tracks whether the README (and other user-facing docs) accurately reflects the
actual code. Lets future agents know which docs can be trusted and which have known gaps.

**Bootstrap — discover by reading:**
- `README.md` — read every code example, every claimed feature, every entry point reference
- For each claim: find the corresponding source class, function, or test that backs it
- `MIGRATION.md`, `CHANGELOG.md`, `RELEASE.md` — do these match the current build/workflow?
- `CONTRIBUTING.md` — does it describe the actual build and test commands correctly?

**Write three sections:**

1. **Confirmed alignment** — features, entry points, or examples in the README that you
   verified are backed by actual code or tests (be specific: name the class or test)
2. **Process-doc alignment** — CONTRIBUTING, MIGRATION, RELEASE, or CHANGELOG sections that
   match the current build or workflow config
3. **Current gaps** — README claims, code examples, or doc sections you could not verify
   against actual code; mark each clearly so future agents know to investigate

If no README exists, write that and leave the file minimal.

---

#### `ai/core/runbook.md` `[FULL ONLY]`

**Purpose:** Every operational command needed to work on this repo in one place. A future
agent looks here first before running anything. No guessing at flags or paths.

**Bootstrap — discover by reading:**
- CI workflow files — extract every `run:` step verbatim
- `Makefile`, `scripts/`, `package.json` scripts section, or equivalent
- `CONTRIBUTING.md` and `RELEASE.md` — any documented command sequences
- Build config — extract test, lint, package, and deploy commands
- Any `README.md` "development" or "contributing" section

**Write sections for each category that exists in this repo:**

- **Validation commands** — test, lint, static analysis, type check, doc consistency (exact
  commands as found; do not paraphrase or simplify flags)
- **Build commands** — how to compile, package, or produce the artifact
- **Release flow** — numbered steps to cut a release (trigger, commands, required secrets or
  environment variables)
- **Benchmark flow** — how to build and run benchmarks (only if benchmarks exist)
- **Utility scripts** — any other scripts an agent might need to run

Use exact commands. Do not invent flags, paths, or options not present in the repo.

---

#### `ai/core/documentation-index.md` `[FULL ONLY]`

**Purpose:** A map of every documentation file in the repo. Lets a future agent find the
right doc without globbing the filesystem. Also records alignment rules so docs stay in sync.

**Bootstrap — discover by reading:**
- All `*.md` files at repo root
- All files under `docs/`, `doc/`, `wiki/`, or equivalent
- Any man pages, Javadoc index, or generated reference docs
- CI steps that check or generate docs

**Write three sections:**

1. **Primary product docs** — README and top-level user-facing guides (one line each: path +
   what it covers)
2. **Feature reference docs** — per-feature or per-topic docs (one line each)
3. **Process docs** — CONTRIBUTING, MIGRATION, RELEASE, CHANGELOG, TODO, MAINTENANCE, and
   similar operational files (one line each: path + purpose)

Add a **Notes** subsection for any alignment rules — e.g. "keep CONTRIBUTING in sync with
build config", "do not edit generated docs directly".

List only files that actually exist. Do not fabricate paths.

---

#### `ai/core/test-strategy.md` `[FULL ONLY]`

**Purpose:** Documents how this project is validated. A future agent reads this before making
any change to know what must pass and how to run it.

**Bootstrap — discover by reading:**
- CI workflow — extract the exact test command and matrix
- Build config — test framework declarations, test scope dependencies
- Test source directories — what kinds of tests exist (unit, integration, e2e, contract)?
- `CONTRIBUTING.md` — any documented test guidance
- Scripts directory — lint, static analysis, doc consistency check scripts
- Test resource directories — where fixtures live

**Write:**
- **Primary validation command** — the one command that must pass (exact)
- **CI matrix** — OS and runtime version combinations CI runs
- **Coverage emphasis** — which areas have the heaviest test coverage (name specific test
  classes or packages if visible)
- **Test fixtures and artifacts** — where test data files live; what generated artifacts
  tests produce (e.g. `target/generated-charts`)
- **Additional validation paths** — every other validation step: lint command, static analysis
  command, doc consistency script, type check, etc. (exact commands)

---

#### `ai/core/benchmark-context.md` `[FULL ONLY]`

**Purpose:** Everything a future agent needs to know to work with benchmarks without breaking
the threshold gates or misreading results.

**Bootstrap:** Only create this file if you find benchmark tooling in the repo. Look for:
JMH (`@Benchmark`), pytest-benchmark, k6, wrk, criterion, Benchmark.NET, or similar.
Also look for threshold or budget JSON files, benchmark CI jobs, or benchmark scripts.
If none found, skip this file entirely.

If benchmarks exist, discover by reading:
- Benchmark source files — what scenarios are covered?
- Threshold/budget files — what are the gates?
- CI benchmark job — how is it triggered and what does it check?
- Build config benchmark profile or module
- Any `docs/benchmarking.md` or equivalent

**Write:**
- **Benchmark surface** — suites or benchmark classes, their entry points, threshold files
- **How to run** — exact command sequence to build and execute locally
- **Interpretation rules** — guardrails from docs or threshold files (e.g. "warmed vs cold
  runs", "do not use ratio X as a merge gate")
- **Output artifacts** — where results are written
- **Detailed sources** — paths to benchmark docs, threshold files, profiling artifacts

---

#### `ai/core/discovery-notes.md` `[FULL ONLY]`

**Purpose:** A catch-all for non-obvious facts that do not fit cleanly in other core files.
Surprises, gotchas, implicit conventions, and "do not do X" rules. Future agents read this
to avoid repeating mistakes or violating hidden conventions.

**Bootstrap — discover by reading:**
- Code comments that explain "why" rather than "what" — these often contain implicit rules
- CI config for any unusual steps, workarounds, or skip flags with comments
- `.gitignore` — are there local runtime directories excluded from source control?
- Build config for unusual profiles, conditional logic, or workaround comments
- Scripts for any "must do X before Y" ordering constraints
- Any TODO or FIXME comments that reveal architectural decisions in progress

**Write:** One bullet per discovery. Focus on things that would surprise an agent coming in
fresh. Only include things actually found in the repo — do not speculate.

Examples of what belongs here:
- "Generated artifacts under `target/` must never be edited directly"
- "Benchmark jar must be resolved dynamically from `target/`; do not hardcode versioned filename"
- "Local orchestrator runtime lives under `.claude-orchestrator/`; excluded from source control"
- "Checkstyle baseline file must be regenerated after any lint-affecting refactor"

---

#### `ai/state/current-state.md` `[BOTH]`

**Purpose:** The primary hot-context state file. A future agent reads this at session start
to immediately know: what is the repo, what is being worked on, what is verified, and what
is next. Must stay concise — it counts toward the 240-line hot context budget.

**Bootstrap — discover by reading:**
- Build config — language, build system, current version
- Git log or CHANGELOG — what has recently shipped
- TODO.md or equivalent — what is currently in progress
- CI status or last known test result
- Any RELEASE.md notes about current release state

**Write using exactly this heading structure:**

```markdown
# Current State

## Repo
[2-3 bullets: language, build system, current version]

## Focus
[what is actively being worked on now, or "No active work package."]

## Verified
[last known passing test run or CI result, or "No baseline recorded yet."]

## Release
[current release version and status, or "No release cut yet."]

## Risks
[1-3 known risks or constraints, or "None identified yet."]

## Next
[next logical task based on TODO or open issues, or "Define first work package."]
```

Keep each section to 1-4 bullets. Total file must stay under 60 lines.

---

#### `ai/state/handoff.md` `[BOTH]`

**Purpose:** The secondary hot-context state file. Tells the next agent exactly how to
resume — what to check, what was being done, what decisions were made, and where the cold
pointers are. Must stay concise — counts toward the 240-line hot context budget.

**Bootstrap — discover by reading:**
- Build config — extract the primary test command for the Validate section
- TODO.md or open issues — what work is pending
- Git log — what was last changed
- `ai/core/` files you just created — use them to populate the Cold pointers section

**Write using exactly this heading structure:**

```markdown
# Handoff

## Resume
1. Read `AGENTS.md` and `ai/AGENTS.md`.
2. Load hot context files.
3. Check git status.
4. Run: [insert exact primary test command here]

## Focus
[what was being worked on, or "Project just initialized — no active work package."]

## Facts
[key decisions or discoveries from bootstrapping, or "Repo just bootstrapped — read core/ files for full context."]

## Validate
[exact command to run after code changes]

## Cold pointers
- routing/process: `AGENTS.md`, `ai/AGENTS.md`
- architecture: `ai/core/architecture-map.md`, `ai/core/system-boundaries.md`
- operations: `ai/core/runbook.md`
- docs: `ai/core/documentation-index.md`
- tests: `ai/core/test-strategy.md`
[add benchmark pointer only if ai/core/benchmark-context.md was created]
```

Keep each section to 1-5 bullets. Total file must stay under 60 lines.

---

#### `ai/log/events.jsonl` `[BOTH]`

**Purpose:** A rolling log of significant discoveries and decisions. Future agents append
here when they learn something important. Starts with the bootstrap event.

**Bootstrap:** Write a single line:

```jsonl
{"ts":"<current ISO 8601 timestamp>","type":"bootstrap","summary":"AI memory system initialized from repository scan."}
```

---

#### `CHANGELOG.md` (only if one does not already exist) `[BOTH]`

**Purpose:** Human and agent-readable record of what changed and when. Agents update this
at the end of every work package.

**Bootstrap — discover by reading:**
- Existing CHANGELOG if present — do not overwrite it
- Git log — summarize what has shipped
- README and docs — what features currently exist

**Write using Keep a Changelog format:**

```markdown
# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

---

## [<current version>] — <release date if known>

### Added
[List features and capabilities that exist now, derived from README and docs exploration]
```

If a CHANGELOG already exists, skip this file.

**WP-3 complete.** Update `ai/state/bootstrap-progress.json` marking WP-3 done. Proceed to next WP.

---

### WP-4 — Create the memory management scripts `[FULL ONLY]`

Create the following scripts. Choose the implementation language based on what the project
already uses for scripting. Python is preferred for cross-platform portability. If the
project uses shell scripts, create `.sh` equivalents. If it uses PowerShell, create `.ps1`
wrappers that delegate to Python.

Each script spec defines: purpose, CLI interface, behaviour, and exit codes.
Implement each spec faithfully — do not add flags or behaviours not listed.

---

#### `scripts/refresh-ai-memory.py`

**Platform:** Pure Python 3 (≥3.9). Use `pathlib` throughout — no hard-coded path separators.
Works on Mac, Linux, and Windows without changes. If the repo uses `uv`, the scripts can be
run as `uv run python scripts/refresh-ai-memory.py` for isolated dependency management.

**Purpose:** Rebuilds derived navigation indexes from the `ai/` markdown source files and
checks that the hot context files are within budget. Run after any edit to `ai/` files or
significant structural changes to the repo.

**CLI:**
```
refresh-ai-memory.py [--check] [--compact-log] [--force-full] [--no-sqlite]
```

**Flags:**
- `--check` — verify freshness only; do not rebuild. Exit 0 if fresh, 1 if stale.
- `--compact-log` — archive old entries from `ai/log/events.jsonl` into
  `ai/log/archive/YYYY-MM.jsonl`; keep only the most recent 20 entries in the active log.
- `--force-full` — ignore cached hashes and rebuild everything from scratch.
- `--no-sqlite` — skip SQLite cold-search database build even if sqlite3 is available.

**Behaviour — default (rebuild):**

1. Walk these file sets and compute a combined SHA-256 hash over their contents:
   - All `.md` files under `ai/`
   - All `.jsonl` files under `ai/log/`
   - Root markdown files that exist: `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`,
     `RELEASE.md`, `MIGRATION.md`, `TODO.md`, `AGENTS.md`
   - All `.md` files under `docs/` (if the folder exists)
   - The build config root file (`pom.xml`, `package.json`, `go.mod`, `Cargo.toml`, etc.)
   - CI config files — detect and include whichever exist:
     `.github/workflows/*.yml` (GitHub Actions),
     `azure-pipelines.yml` + `azure-pipelines/*.yml` + `.azure/**/*.yml` (Azure DevOps),
     `.gitlab-ci.yml` (GitLab),
     `.circleci/config.yml` (CircleCI),
     `Jenkinsfile` (Jenkins),
     `bitbucket-pipelines.yml` (Bitbucket)

2. Load `ai/indexes/refresh-state.json` if it exists. If the hash matches and `--force-full`
   is not set, skip rebuilding indexes that have not changed.

3. Before writing any file under `ai/indexes/`, ensure the directory exists:
   `Path("ai/indexes").mkdir(parents=True, exist_ok=True)`

4. Build `ai/indexes/docs-index.json`:
   - One entry per markdown file discovered in step 1.
   - Fields: `path` (repo-relative), `category`, `relevance`, `loadTier`, `lineCount`,
     `byteCount`.
   - `loadTier`: `"hot"` for the 4 hot context files, `"warm"` for
     `ai/state/recent-validations.md`, `"cold"` for all others.
   - `category`: `"ai-hot-context"`, `"ai-core"`, `"ai-state"`, `"ai-log"`,
     `"product-doc"`, `"process-doc"`, `"readme"`, `"planning"`, etc.

5. Build `ai/indexes/files-index.json`:
   - List of important files with their `path` and `kind` fields.
   - Include: build config, CI workflows, root markdown files, `ai/` structure roots,
     `docs/` if present, `scripts/` if present.
   - Also include `counts`: total markdown docs, `ai/core` files, `ai/` index files.

6. Check hot context budget:
   - Read the 4 hot files: `ai/core/agent-invariants.md`, `ai/core/repo-purpose.md`,
     `ai/state/current-state.md`, `ai/state/handoff.md`.
   - Sum their line counts and byte sizes.
   - Budget: 240 lines max, 24 576 bytes (24 KB) max.

7. If SQLite is available and `--no-sqlite` not set, build or update
   `ai/indexes/cold-memory.db` with FTS over all cold-search files (all `.md` and `.jsonl`
   files from step 1). Schema: `documents(path, content)` + FTS virtual table.

8. Write `ai/memory-state.json`:
   ```json
   {
     "schemaVersion": 1,
     "generatedAt": "<ISO 8601>",
     "inputsHash": "<sha256>",
     "hotContext": {
       "files": [{"path": "...", "lines": N, "bytes": N}],
       "totalLines": N,
       "totalBytes": N,
       "maxLines": 240,
       "maxBytes": 24576,
       "withinBudget": true
     },
     "freshness": {
       "status": "fresh",
       "reasons": []
     }
   }
   ```

9. Update `ai/indexes/refresh-state.json` with the current hash per file.

**Exit codes:**
- `0` — success (or `--check` passed)
- `1` — budget exceeded, missing files, or `--check` found stale state

**Output (stdout):**
```
[ai-memory] refreshed indexes
[ai-memory] hot context: N lines / N bytes
[ai-memory] inputs hash: <hash>
```

---

#### `scripts/query-ai-memory.py`

**Platform:** Pure Python 3. Use `pathlib` throughout — no hard-coded separators.

**Purpose:** Searches the `ai/` memory and project docs for relevant context. Used by agents
when routing fallback is needed. Falls back to plain-text grep if SQLite is not available.

**CLI:**
```
query-ai-memory.py <query> [--limit N] [--tier TIERS] [--kind KINDS]
                           [--path GLOB] [--db PATH] [--json]
```

**Flags:**
- `<query>` — required positional; search string (plain text, not regex)
- `--limit N` — max results to return (default: 8)
- `--tier TIERS` — comma-separated filter: `hot`, `warm`, `cold`, `archive`
  (default: all except archive; include archive only if no results found without it)
- `--kind KINDS` — comma-separated filter: `ai-core`, `ai-state`, `ai-orchestrator`,
  `ai-log`, `process-doc`, `release-doc`, `product-doc`, `readme`, `planning`
- `--path GLOB` — glob pattern to restrict search to matching file paths
  (e.g. `"ai/core/*"`, `"ai/state/*"`)
- `--db PATH` — path to SQLite database (default: `ai/indexes/cold-memory.db`)
- `--json` — emit results as JSON array instead of human-readable text

**Search behaviour:**

1. If `ai/indexes/cold-memory.db` exists and sqlite3 is available: run FTS query against
   the database, filtered by `--tier`, `--kind`, and `--path` facets.
2. Otherwise: fall back to plain-text case-insensitive substring search across all
   cold-search files (all `.md` and `.jsonl` files in `ai/` and root docs).
3. If no results and `--tier` was not specified: retry including archive files.

**Result fields:** `path`, `lineNumber`, `summary` (the matching line, trimmed).

**Output (human-readable, default):**
```
1. ai/core/architecture-map.md:12
   hit: The main execution flow is: input → parser → engine → output
```

**Output (`--json`):**
```json
[{"path": "ai/core/architecture-map.md", "lineNumber": 12, "summary": "..."}]
```

**Exit codes:**
- `0` — one or more results found
- `1` — no results found

---

#### `scripts/check-doc-consistency.py`

**Platform:** Pure Python 3. Use `pathlib` throughout — no hard-coded separators.

**Purpose:** Verifies that key documentation claims are consistent with actual repo state.
Catches drift between docs and code before it becomes a problem. Agents run this after
doc edits or significant refactors.

**CLI:**
```
check-doc-consistency.py [--fix] [--json]
```

**Flags:**
- `--fix` — automatically correct trivial drift (e.g. update a version number in a doc)
  when safe to do so. Default: report only.
- `--json` — emit results as JSON.

**Checks to implement** (discover what applies from the repo; skip checks that are not
relevant to this project's structure):

1. **Version consistency** — extract the current version from the build config
   (`pom.xml`, `package.json`, `go.mod`, etc.) and verify it matches every occurrence of
   a version string in `README.md`, `CONTRIBUTING.md`, `RELEASE.md`, and `CHANGELOG.md`.
   Report mismatches.

2. **Entry point references** — for each public entry point or API class/function listed in
   `README.md`, verify that a corresponding source file or export exists in the repo.
   Report any that are missing.

3. **Doc link integrity** — find all markdown links of the form `[text](path)` in `README.md`
   and `docs/*.md`. Verify each relative link target exists as a file. Report dead links.

4. **CHANGELOG has Unreleased section** — verify `CHANGELOG.md` contains an `## [Unreleased]`
   heading. Report if missing.

5. **Hot context budget** — read the 4 hot context files and verify their combined line count
   is under 240 and byte count is under 24 576. Report if over budget.

6. **ai/core files exist** — verify that all core memory files referenced in `AGENTS.md`
   exist on disk. Report any that are missing.

**Output (human-readable, default):**
```
[doc-consistency] checking...
  OK  version consistency
  FAIL entry point references: PojoLensOld not found in source
  OK  doc link integrity
  OK  CHANGELOG unreleased section
  OK  hot context budget
[doc-consistency] 1 failure(s)
```

**Exit codes:**
- `0` — all checks passed
- `1` — one or more checks failed

---

**Determine the platform and create the appropriate thin wrappers.**

Detect the OS from the repo context (CI workflow OS matrix, existing scripts, `.gitattributes`,
shebang lines in scripts, etc.). Then create wrappers accordingly:

- **Mac / Linux** — create `.sh` wrappers (make them executable: `chmod +x`)
- **Windows** — create `.ps1` wrappers
- **Cross-platform / CI-first repos** — create both `.sh` and `.ps1` wrappers
- When in doubt, create both

The Python scripts are the implementation. Wrappers are thin delegates only.

---

#### Shell wrappers (Mac / Linux / cross-platform)

##### `scripts/refresh-ai-memory.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY=$(command -v python3 || command -v python || { echo "Python not found" >&2; exit 1; })
exec "$PY" "$SCRIPT_DIR/refresh-ai-memory.py" "$@"
```

##### `scripts/query-ai-memory.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY=$(command -v python3 || command -v python || { echo "Python not found" >&2; exit 1; })
exec "$PY" "$SCRIPT_DIR/query-ai-memory.py" "$@"
```

##### `scripts/check-doc-consistency.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY=$(command -v python3 || command -v python || { echo "Python not found" >&2; exit 1; })
exec "$PY" "$SCRIPT_DIR/check-doc-consistency.py" "$@"
```

After creating `.sh` files, make them executable:
```bash
chmod +x scripts/refresh-ai-memory.sh scripts/query-ai-memory.sh scripts/check-doc-consistency.sh
```

---

#### PowerShell wrappers (Windows / cross-platform)

##### `scripts/refresh-ai-memory.ps1`

```powershell
param([switch]$Check, [switch]$CompactLog, [switch]$ForceFull, [switch]$NoSQLite)
$py = (Get-Command python3, python, py -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $py) { Write-Error "Python not found"; exit 1 }
$a = @((Join-Path $PSScriptRoot "refresh-ai-memory.py"))
if ($Check)      { $a += "--check" }
if ($CompactLog) { $a += "--compact-log" }
if ($ForceFull)  { $a += "--force-full" }
if ($NoSQLite)   { $a += "--no-sqlite" }
& $py @a; exit $LASTEXITCODE
```

##### `scripts/query-ai-memory.ps1`

```powershell
param([Parameter(Mandatory=$true)][string]$Query,
      [int]$Limit=8, [string]$Tier="", [string]$Kind="",
      [string[]]$Path=@(), [switch]$Json)
$py = (Get-Command python3, python, py -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $py) { Write-Error "Python not found"; exit 1 }
$a = @((Join-Path $PSScriptRoot "query-ai-memory.py"), $Query, "--limit", "$Limit")
if ($Tier) { $a += @("--tier", $Tier) }
if ($Kind) { $a += @("--kind", $Kind) }
foreach ($p in $Path) { if ($p) { $a += @("--path", $p) } }
if ($Json) { $a += "--json" }
& $py @a; exit $LASTEXITCODE
```

##### `scripts/check-doc-consistency.ps1`

```powershell
param([switch]$Fix, [switch]$Json)
$py = (Get-Command python3, python, py -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $py) { Write-Error "Python not found"; exit 1 }
$a = @((Join-Path $PSScriptRoot "check-doc-consistency.py"))
if ($Fix)  { $a += "--fix"  }
if ($Json) { $a += "--json" }
& $py @a; exit $LASTEXITCODE
```

---

After creating wrappers, wire commands into `ai/core/runbook.md` under **Memory management**.
Use the platform-appropriate command style based on what was detected, but always show both:

```markdown
## Memory management

Refresh indexes after ai/ edits:
- Mac/Linux: `./scripts/refresh-ai-memory.sh`
- Windows:   `pwsh scripts/refresh-ai-memory.ps1`
- Direct:    `python3 scripts/refresh-ai-memory.py`

Check memory freshness:
- Mac/Linux: `./scripts/refresh-ai-memory.sh --check`
- Windows:   `pwsh scripts/refresh-ai-memory.ps1 -Check`
- Direct:    `python3 scripts/refresh-ai-memory.py --check`

Compact event log:
- Mac/Linux: `./scripts/refresh-ai-memory.sh --compact-log`
- Windows:   `pwsh scripts/refresh-ai-memory.ps1 -CompactLog`
- Direct:    `python3 scripts/refresh-ai-memory.py --compact-log`

Search memory:
- Mac/Linux: `./scripts/query-ai-memory.sh "<query>"`
- Windows:   `pwsh scripts/query-ai-memory.ps1 -Query "<query>"`
- Direct:    `python3 scripts/query-ai-memory.py "<query>"`

Check doc consistency:
- Mac/Linux: `./scripts/check-doc-consistency.sh`
- Windows:   `pwsh scripts/check-doc-consistency.ps1`
- Direct:    `python3 scripts/check-doc-consistency.py`
```

Also add to `AGENTS.md` under **Session rules**:
```markdown
- After significant ai/ memory edits, run:
  `python3 scripts/refresh-ai-memory.py` (or platform wrapper)
- To verify memory is fresh:
  `python3 scripts/refresh-ai-memory.py --check`
- For cold context routing:
  `python3 scripts/query-ai-memory.py "<keywords>"`
```

**After creating scripts, update `.gitignore`:**

Append the following to `.gitignore` (create it if it does not exist):
```
# AI memory derived artifacts — rebuilt by scripts/refresh-ai-memory.py
ai/indexes/cold-memory.db
```

The `.json` index files (`docs-index.json`, `files-index.json`, etc.) may be committed if
the team wants to track memory evolution in git. The SQLite database is always derived and
should not be committed.

**WP-4 complete.** Update `ai/state/bootstrap-progress.json` marking WP-4 done. Proceed to WP-5.

---

### WP-5 — Verify `[BOTH]`

After creating all files, report the following. Items marked `[FULL ONLY]` are skipped in
MINIMUM mode.

1. **Mode used** — state whether MINIMUM or FULL was run and why.

2. **Hot context budget** `[BOTH]` — confirm the four hot files (`agent-invariants.md`,
   `repo-purpose.md`, `current-state.md`, `handoff.md`) are under 240 lines / 24 KB combined.
   Report actual line count and byte size.

3. **Fact provenance** `[BOTH]` — list every `[UNVERIFIED]` item: file, line, what is needed
   to verify it.

4. **Standard docs audit** `[FULL ONLY]` — for each of `README.md`, `CONTRIBUTING.md`,
   `CHANGELOG.md`, `RELEASE.md`, `MIGRATION.md`: existed / created / skipped (reason).

5. **Platform wrappers** `[FULL ONLY]` — which wrappers created (`.sh`, `.ps1`, or both),
   why, and confirmation that `.sh` files have execute permission.

6. **Benchmark file** `[FULL ONLY]` — created or skipped, and what evidence led to that.

7. **Full file list** `[BOTH]` — every file created or updated, with its line count.

8. **Write bootstrap manifest** `[BOTH]` — write `ai/state/bootstrap-progress.json`:

```json
{
  "bootstrapVersion": "1.0",
  "mode": "FULL",
  "completedAt": "<ISO 8601 timestamp>",
  "workPackages": {
    "WP-1": "complete",
    "WP-2": "complete",
    "WP-3": "complete",
    "WP-4": "complete",
    "WP-5": "complete"
  },
  "filesCreated": ["AGENTS.md", "ai/AGENTS.md", "..."],
  "unverifiedItems": 0,
  "hotContextLines": 0,
  "hotContextBytes": 0
}
```

   This file lets a future agent verify bootstrap completeness and resume if interrupted.

9. **Suggest git commit** `[BOTH]` — after writing all files, output the following for the
   user to run if they want to commit the memory system:

```
git add AGENTS.md ai/ CHANGELOG.md .gitignore
git commit -m "chore: initialize AI memory system"
```

   Do not run this automatically. Only suggest it.

10. **Next steps** `[BOTH]` — output a short "what to do next" note:

```
Bootstrap complete. AI memory system ready.

Next session: tell your agent "Read AGENTS.md and follow it."
The agent will load hot context and pick up from current-state.md.

Suggested first task: review ai/core/ files for accuracy and fill in any [UNVERIFIED] items.
```

---
