You are improving a Python-based TUI application for a local Claude orchestration system.

The app should provide a modern, polished, Matrix/cyberpunk-inspired terminal interface for creating, reviewing, saving, validating, running, resuming, retrying, and managing AI agent task plans.

The UI must reflect the actual capabilities of the orchestration system described below. Do not design a generic planner. Design a TUI operator console for this specific AI memory + Claude multi-agent orchestrator.

Visual Theme

Use a Matrix-inspired cyberpunk terminal aesthetic:

- Dark terminal background.
- Neon green primary accent.
- Optional cyan, purple, amber, or red secondary accents.
- ASCII art headers and section dividers.
- Matrix-style “digital rain” loading screens where appropriate.
- Cyberpunk dashboard panels.
- Futuristic bordered cards.
- Terminal scan messages during validation, dry-runs, and live execution.
- Clear active, warning, error, and success states.
- Keyboard-first navigation.
- Must remain readable and practical.
- ANSI color support should be optional with graceful fallback.
- Unicode borders/icons may be used, but provide ASCII fallback.

The UI should feel like a hacker-console mission planner, but it must remain a professional operator tool for real work.

Suggested main ASCII title:

    ██████╗ ██╗      █████╗ ███╗   ██╗
    ██╔══██╗██║     ██╔══██╗████╗  ██║
    ██████╔╝██║     ███████║██╔██╗ ██║
    ██╔═══╝ ██║     ██╔══██║██║╚██╗██║
    ██║     ███████╗██║  ██║██║ ╚████║
    ╚═╝     ╚══════╝╚═╝  ╚═╝╚═╝  ╚═══╝

or another compact Matrix/cyberpunk ASCII header.

Primary UX Goal

The TUI should make “Create New Plan with AI” the primary path.

Saved plans, retained runs, resume, retry, validation, ledger summaries, and advanced governance controls should be easily available but secondary.

Main Navigation

The home screen should expose these major sections:

1. Create New Plan
2. Saved Plans
3. Run / Resume / Retry
4. Run Ledger
5. Validate Plan
6. Dry Run / Estimate
7. Review / Promote
8. Settings
9. Exit

The default highlighted action should be “Create New Plan”.

Use visible keyboard hints:

- [N] New Plan
- [S] Saved Plans
- [R] Runs
- [V] Validate
- [D] Dry Run
- [L] Ledger
- [P] Promote
- [Q] Quit
- [Enter] Select
- [Esc] Back

Create New Plan Flow

The new-plan wizard should guide the user through an AI Agent Planner flow.

The planner uses Claude as the underlying LLM.

The user describes what they want to do. The wizard then runs a bounded clarification loop where the planner may ask up to 3 focused questions before generating a plan.

The user must be able to:

- Enter a goal.
- Choose whether to use clarification.
- Skip clarification with a selected existing plan.
- Select or configure Claude model profile.
- Select planning depth / effort.
- Choose workspace strategy.
- Configure max parallelism.
- Configure budget and artifact limits.
- Configure HITL gates.
- Configure follow-up behavior.
- Configure notification behavior.
- Generate a plan.
- Review the staged plan.
- Revise the plan.
- Save the plan.
- Validate the plan.
- Estimate cost.
- Dry-run the plan.
- Start the plan only after explicit approval.

Planner Effort / Model Profile

The UI should support model profile selection based on the orchestration contract:

- simple -> claude-haiku-4-5
- balanced -> claude-sonnet-4-6
- complex -> claude-opus-4-7

The UI should make clear that complex / opus usage is expensive and should be an explicit exception.

The user should be able to configure planner effort such as:

- Low: fast, small plan, minimal agents.
- Medium: balanced, practical plan.
- High: deeper analysis, more validation, higher cost.
- Custom: expose advanced settings.

The UI should prefer the smallest actor set that can finish the work. Analyst and reviewer roles should be optional, not default stages.

For narrow code changes, the default recommendation should usually be:

- one implementer task

or, when risk is higher:

- implementer -> reviewer

Plan Generation Requirements

Generated plans should support the actual task-plan features:

- task id
- title
- agent
- prompt
- dependencies
- sharedContext.readPaths
- task-local readPaths
- task-local writePaths
- constraints
- validation hints
- contextMode
- modelProfile
- workspaceMode
- dependencyMaterialization
- skills
- extraTools
- outputProfile
- maxPromptEstimatedTokens
- maxPromptChars
- structured follow-up behavior
- structured validation intents
- runPolicy

The generated plan should default to:

- contextMode = minimal
- workspaceMode = copy
- dependencyMaterialization = summary-only
- followUpBehavior = ignore
- smallest useful task graph
- conservative writePaths
- explicit readPaths
- explicit validation hints

Workspace Mode UI

The wizard should explain and allow selection of workspace modes:

1. copy
    - default
    - isolated sparse filesystem copy
    - seeded only from declared readPaths and existing writePaths
    - safest default

2. worktree
    - detached git worktree rooted at HEAD
    - requires clean repo
    - useful for larger code changes

3. repo
    - live repo root
    - high-risk exception only
    - should require explicit confirmation

The UI should warn when repo mode is selected.

Concurrency UI

The TUI should support parallel execution controls:

- max parallel tasks
- dependency graph preview
- batch shape preview
- dependency depth
- agent counts
- read-only vs write-capable task counts
- warning for overlapping write scopes
- warning when the plan is heavier than necessary

The UI should clearly show that workers editing the same files must not run in parallel.

Run Governance UI

The wizard should expose runPolicy settings:

- runBudgetUsd
- budgetBehavior: warn or stop
- artifactBehavior: warn or stop
- maxTaskStdoutBytes
- maxTaskStderrBytes
- maxTaskResultBytes
- HITL mode
- followUpBehavior
- notification behavior

Budget behavior should be clearly explained:

- warn: continue but show warnings
- stop: block unscheduled later batches once the budget is exceeded

The UI should surface:

- pre-flight cost estimate
- per-task token ranges
- per-task USD ranges
- batch rollups
- concurrency-adjusted wall-clock range
- highest-cost tasks
- aggregate artifact totals
- warning if runBudgetUsd is below the minimum estimated cost

HITL Gate UI

The TUI should support human-in-the-loop gates.

Supported modes:

- none
- batch
- on-failure
- always

The UI should explain:

- always fires before every batch
- batch fires before every batch with pending tasks
- on-failure fires only after a failed batch
- none disables gates

During a live gate, show a cyberpunk approval screen with:

- completed batch summary
- pending tasks
- failures if any
- cost so far
- approve / abort controls
- gate id
- stale sentinel warning if applicable

Actions:

- [A] Approve
- [X] Abort
- [D] Details
- [Esc] Back where safe

Saved Plans Flow

The TUI should include a Saved Plans area.

Saved plans should come from tracked task-plan samples and reusable plans under:

- ai/orchestrator/tasks/*.json

The user should be able to:

- view saved plans
- search/filter plans
- open a plan
- inspect topology
- inspect agents
- inspect model profiles
- inspect readPaths/writePaths
- inspect runPolicy
- inspect validation hints
- edit a plan
- duplicate a plan
- delete a plan where safe
- validate a plan
- estimate a plan
- dry-run a plan
- start a plan

Plan list should show:

- plan name
- short description
- number of tasks
- agents used
- model profile summary
- workspace mode summary
- estimated cost range if available
- last modified time
- warnings count

Empty state should use themed ASCII art:

    [ construct scan complete ]
    No saved plans found.
    Create a new plan to enter the system.

Plan Review Screen

After generation or opening a saved plan, show a staged plan summary.

Display:

- goal
- plan name
- task graph
- tasks grouped by batch
- agents
- model profiles
- workspace modes
- read paths
- write paths
- dependency materialization
- skills
- extra tools
- validation hints
- run policy
- cost estimate
- topology warnings
- prompt size warnings
- protected path warnings
- docs consistency warnings

Actions:

- [A] Approve and Run
- [S] Save
- [E] Edit
- [R] Revise with Claude
- [V] Validate
- [D] Dry Run
- [C] Cost Estimate
- [Q] Cancel

Do not allow accidental execution. Starting a run must require explicit approval.

Validation Screen

The UI should support plan validation before execution.

Validation should surface:

- schema errors
- missing readPaths
- missing writePaths
- directory readPaths that are invalid for copy mode
- oversized inputs
- protected path violations
- write scope conflicts
- prompt size warnings
- skill count warnings
- role prompt size warnings
- skill file size warnings
- model profile warnings
- accidental complex / opus usage
- reviewer materialization warnings
- docs-only validation omissions
- topology warnings
- budget warnings
- worker validation mode details
- resolved task skills
- resolved extra tools
- resolved output profiles

The validation output should be displayed in grouped panels:

- Critical Errors
- Warnings
- Cost / Token Estimate
- Topology
- Workspace Safety
- Prompt Accounting
- Skills
- Validation Hints

Dry Run / Estimate

The TUI should support:

- run --estimate
- run --dry-run

Estimate mode should show cost and token ranges without creating a retained run.

Dry-run mode should reuse prompt assembly estimates and show a tighter estimate.

The UI should display:

- per-task estimate
- per-batch estimate
- total estimate
- wall-clock range
- prompt size
- resolved model/profile
- warning for expensive tasks
- max parallel effect

Live Run Dashboard

The app may expose a Textual-based live dashboard for:

- run
- resume
- retry

The live dashboard should be driven by the retained run-event stream and task-started updates.

It should show:

- run id
- selected plan
- lifecycle state
- current batch
- task status
- task model
- task cost
- elapsed time
- active stderr tail
- stdout/stderr/result artifact limits
- event trace
- HITL approve/abort controls
- rate limiter status
- budget status
- notification status

Task statuses should be visually distinct:

- pending
- running
- completed
- failed
- blocked
- skipped
- injected
- budget_exceeded
- aborted

Use cyberpunk-style log lines such as:

- [construct] assembling prompt matrix...
- [signal] validating workspace boundaries...
- [operator] HITL gate armed...
- [trace] worker event received...
- [budget] cost threshold approaching...
- [exit] run finished...

Resume / Retry UI

The TUI should expose retained run lifecycle helpers.

Users should be able to:

- list retained runs
- inspect run status
- resume unfinished or missing tasks
- retry failed or blocked tasks into a new run
- narrow resume/retry to selected tasks
- view run manifest summary
- view selected-plan snapshot
- view injected follow-up tasks
- view approval checkpoints
- view validation checkpoints
- view promotion checkpoints

Resume should make clear:

- same-run resume preserves completed task records
- same-run resume reloads selected-plan.json
- copy/worktree tasks may rebuild fresh workspaces
- resume is run continuity, not partial sandbox continuity

Run Ledger Screen

The TUI should support a run-ledger summary view.

Features:

- list past runs
- filter by plan name
- filter by date
- filter by status
- filter by count
- show compact status
- show resume candidate
- show coordinator validation state
- show prompt/cost summary
- show topology summary
- show output profile counts
- show verbose/noisy tasks
- show event trace rollup
- show branch lineage rollup

The ledger should support both human-readable and JSON-friendly output modes.

Review / Promote UI

The TUI should provide a review surface for retained runs.

It should show:

- workspace diffs
- task/path filtering
- stat mode
- changed file ownership
- protected path violations
- docs/text guardrails
- mojibake or encoding corruption warnings
- non-ASCII introduction warnings
- dependency materialization info
- applied dependency layers
- promotion readiness
- coordinator validation status

Actions:

- view diff
- export unified patch
- promote selected changes
- reject selected changes
- run coordinator validation
- return to run summary

Promotion must refuse:

- workspaceMode = repo task changes
- protected-path violations
- conflicting changed-file ownership
- path traversal outside repo root

A coding run with promotion should not be considered complete until repo-scope coordinator validation is recorded and passing.

Memory Tools UI

The app should expose AI memory maintenance commands.

Relevant commands:

- refresh AI memory
- refresh AI memory check
- query AI memory
- benchmark AI memory

The UI should understand the memory model:

Hot context:

- ai/core/agent-invariants.md
- ai/core/repo-purpose.md
- ai/state/current-state.md
- ai/state/handoff.md

Warm state:

- ai/state/recent-validations.md

Cold context:

- ai/core/*
- ai/state/*
- ai/log/*
- ai/indexes/*

The UI should remind the user that after tracked AI memory changes, they should run:

- scripts/ai/refresh-ai-memory.ps1
- scripts/ai/refresh-ai-memory.ps1 -Check

Settings Screen

The settings screen should expose configuration for:

- Claude model/profile defaults
- planning effort default
- max parallel default
- default workspace mode
- default contextMode
- default outputProfile
- default run budget
- default HITL mode
- default follow-up behavior
- TPM limit
- RPM limit
- notification settings
- ANSI / Unicode fallback
- theme intensity
- plain REPL fallback
- JSON-friendly output preference

Rate Limiting UI

Expose proactive rate limiting controls:

- --tpm-limit
- --rpm-limit
- ANTHROPIC_TPM_LIMIT
- ANTHROPIC_RPM_LIMIT

The live dashboard should show when dispatch is waiting due to rate limits.

Notifications UI

Expose opt-in run completion notifications:

- desktop
- webhook
- Slack

Support:

- notify_on success
- notify_on failure
- notify_on always
- --notify
- --no-notify

Suppress notifications for:

- dry-run
- estimate

Skills UI

The TUI should show resolved skills per task.

It should validate against:

- ai/orchestrator/skills/registry.json

It should warn when:

- a task resolves more than 4 skills
- total stack exceeds 5 skills
- skill files exceed warning or failure size thresholds
- unknown skills are referenced

The UI should support task-local skills, agent default skills, and inferred skills.

Agent UI

The TUI should show available agents from:

- ai/orchestrator/agents.json

Agent details should include:

- role
- model/profile default
- skills
- extra tools
- output profile
- prompt file path
- prompt size
- validation mode
- warning if prompt exceeds 6 KB
- failure if prompt exceeds 8 KB

Extra Tools UI

The UI should display resolved extra tools from agent and task definitions.

For each tool show:

- name
- description
- kind
- timeout
- source: agent or task

Warn when:

- tool name collides with base tools
- template contains invalid path traversal
- task-level tools override agent tools

Base tools are:

- read_file
- write_file
- str_replace_based_edit_tool
- bash
- write_shared_context

Validation Intents UI

The UI should support structured validation intents.

Supported kinds:

- repo-script
- tool

The UI should reject or warn on legacy raw validationCommands in live worker paths.

Show validation intents separately from legacy suggestions.

Follow-Up Task UI

The UI should support structured followUpTasks when followUpBehavior = inject.

Show:

- emitted follow-up tasks
- conditionField
- conditionValue
- injection accepted/skipped/rejected
- task-injected events
- task-injection-skipped events

The UI should clearly distinguish:

- human-readable followUps
- structured followUpTasks

Output Profiles

The UI should show output profiles.

Supported profile:

- default
- lean

Lean should be recommended for cheap docs/read-only proof tasks that need tighter worker JSON and lower review overhead.

Prompt Accounting

The UI should expose prompt-size estimates before live execution:

- prompt_chars
- prompt_estimated_tokens
- section-level prompt accounting
- maxPromptEstimatedTokens
- maxPromptChars
- actual usage/cost when available

Warn or block when oversized prompts would fail locally.

Plain REPL Fallback

The operator surface may expose a persistent interactive console session:

- console command
- Textual TUI by default
- --no-tui plain REPL fallback

The app should preserve JSON-friendly fallback paths and avoid contaminating machine-readable stdout. Interactive progress should go to stderr only.

Python Implementation Guidance

Use Python.

Prefer Textual and Rich for the TUI if appropriate.

Structure the app into reusable screens/components:

- HomeScreen
- CreatePlanScreen
- ClarificationScreen
- EffortSelectionScreen
- ModelProfileScreen
- GovernanceSettingsScreen
- WorkspaceModeScreen
- PlanGenerationScreen
- PlanReviewScreen
- SavedPlansScreen
- PlanEditorScreen
- ValidationScreen
- EstimateScreen
- DryRunScreen
- LiveRunDashboard
- HitlGateScreen
- RunLedgerScreen
- RunDetailsScreen
- ResumeRetryScreen
- DiffReviewScreen
- PromoteScreen
- MemoryToolsScreen
- SettingsScreen

Keep concerns separated:

- TUI rendering
- keyboard navigation
- plan generation
- plan validation
- run execution
- run ledger access
- saved plan storage
- Claude provider integration
- memory refresh/query tooling
- workspace review/promotion
- notifications
- rate limiting

The TUI should call the existing CLI/orchestrator functionality rather than duplicating orchestration logic.

Important Safety and Contract Rules

The UI must respect worker protection rules:

- workers must not edit TODO.md
- workers must not edit ai/state/*
- workers must not edit ai/log/*
- workers must not edit ai/indexes/*
- workers may edit ai/orchestrator/** only when assigned
- planner should prefer copy workspaces
- planner should declare concrete readPaths and writePaths
- coordinator must detect touched files outside writePaths
- coordinator must detect protected path violations

The UI should make these rules visible during validation and review.

Final Result

The final result should be a modern Matrix/cyberpunk Python TUI operator console for the local Claude orchestration system.

It should let the user:

- create AI-generated plans
- clarify goals with Claude
- select effort/model profile
- configure governance
- save plans
- edit plans
- delete plans
- validate plans
- estimate cost
- dry-run plans
- start approved runs
- monitor live runs
- approve or abort HITL gates
- resume runs
- retry failed work
- inspect retained runs
- summarize the run ledger
- review diffs
- promote changes
- run coordinator validation
- manage memory refresh/query workflows
- inspect skills, agents, tools, topology, prompts, costs, and warnings



The UI should make advanced orchestration power accessible without overwhelming the user. Keep the primary path simple, but allow advanced users to drill into every important contract surface.