# Operator TUI — Screen Reference & Flow Audit

Terminal UI launched via `pojolens-agents operator`.  
Built on Textual.  All screens share the Matrix cyberpunk theme (`_MTX_VARS`).

---

## Screen Inventory

| # | Screen class            | File              | Type        | Entry point                        |
|---|-------------------------|-------------------|-------------|------------------------------------|
| 1 | `HomeScreen`            | _tui_home         | Full        | App root                           |
| 2 | `GoalInputScreen`       | _tui_wizard       | Full        | Wizard step 1                      |
| 3 | `ClarificationScreen`   | _tui_wizard       | Full        | Wizard step 1b                     |
| 4 | `EffortSelectScreen`    | _tui_wizard       | Full        | Wizard step 2                      |
| 5 | `WorkspaceModeScreen`   | _tui_wizard       | Full        | Wizard step 3                      |
| 6 | `RunConfigScreen`       | _tui_wizard       | Full        | Wizard step 4                      |
| 7 | `PlanRunScreen`         | _tui_wizard_run   | Full        | Wizard step 5 / Plans [R/D]        |
| 8 | `SavedPlansScreen`      | _tui_plans        | Full        | Home [P]                           |
| 9 | `PlanDetailsScreen`     | _tui_plans        | Full        | SavedPlans / Runs                  |
|10 | `PlanEditorScreen`      | _tui_plans        | Full        | PlanDetails [E]                    |
|11 | `ValidatePlanDialog`    | _tui_validate     | **Modal**   | PlanDetails [V]                    |
|13 | `ValidateRunScreen`     | _tui_validate     | Full        | ValidatePlanDialog / PlanDetails   |
|14 | `RunLedgerScreen`       | _tui_ledger       | Full        | Home [R]                           |
|15 | `RunDetailsScreen`      | _tui_ledger       | Full        | RunLedger [OPEN]                   |
|16 | `ResumeRetryScreen`     | _tui_ledger       | Full        | RunLedger [R/Y]                    |
|17 | `HitlGateScreen`        | _tui_gate         | Full        | RunLedger [G] / auto-push          |
|18 | `DiffReviewScreen`      | _tui_diff         | Full        | RunLedger [P]                      |
|19 | `PromoteConfirmDialog`  | _tui_diff         | **Modal**   | DiffReview [P]                     |
|20 | `AgentsScreen`          | _tui_inspect      | Full        | Home [A]                           |
|21 | `AgentEditScreen`       | _tui_inspect      | Full        | AgentsScreen [NEW] / [EDIT]        |
|22 | `SkillsScreen`          | _tui_inspect      | Full        | Home [S]                           |
|23 | `SkillEditScreen`       | _tui_inspect      | Full        | SkillsScreen [NEW] / [EDIT]        |
|24 | `PlanInspectScreen`     | _tui_inspect      | Full        | PlanDetails [T/I/O/P/F]            |
|25 | `MemoryToolsScreen`     | _tui_tools        | Full        | Home [M]                           |
|26 | `SettingsScreen`        | _tui_tools        | Full        | Home [C]                           |
|27 | `EstimateScreen`        | _tui_estimate     | Full        | PlanDetails [D]                    |
|28 | `EstimateResultScreen`  | _tui_estimate     | Full        | EstimateScreen                     |

> **27 screens total** — 2 modals, 25 full-screen.  
> Backward-compat aliases: `RunPlanScreen` (→ `PlanRunScreen`);
> `ProviderSelectScreen`, `GovernanceScreen` (→ `RunConfigScreen`);
> `ValidatePlanScreen` (→ `ValidatePlanDialog`);
> `ExtraToolsScreen`, `ValidationIntentsScreen`, `OutputProfilesScreen`,
> `PromptAccountingScreen`, `FollowUpTaskScreen` (→ `PlanInspectScreen`).

---

## Top-Level Navigation Map

```
                        POJOLENS OPERATOR CONSOLE
                        ┌────────────────────────────────────────────────┐
                        │                  HomeScreen                     │
                        │                                                 │
                        │  [N] New Plan       [P] Plans                  │
                        │  ─────────────────────────────────              │
                        │  [R] Runs                                       │
                        │  ─────────────────────────────────              │
                        │  [A] Agents         [S] Skills                 │
                        │  [M] Memory Tools   [C] Config                 │
                        │  ─────────────────────────────────              │
                        │  [Q] Quit                                       │
                        └──────────────────┬──────────────────────────────┘
                                           │
         ┌─────────────────────────────────┼──────────────────────────────┐
         │                                 │                              │
         ▼      ▼      ▼       ▼      ▼       ▼      ▼        ▼
       Wizard  Plans  Runs   Agents  Skills  Memory Config
       Flow           Ledger Screen  Screen  Tools  Screen
       [N]     [P]    [R]    [A]     [S]     [M]    [C]
```

---

## Flow 1 — Wizard (New Plan)

5 sequential steps, each can abort back to Home.

```
Home [N]
  │
  ▼
┌──────────────────┐
│  GoalInputScreen │  Type a free-text goal
│  (step 1)        │  Submit → proceed  │  empty → cancel
└────────┬─────────┘
         │ goal string
         ▼
┌──────────────────────┐
│  ClarificationScreen │  AI (haiku) asks up to 3 questions
│  (step 1b)           │  Optional — falls back to static prompts
│                      │  if no AI backend
└──────────┬───────────┘
           │ refined goal
           ▼
┌──────────────────────┐
│  EffortSelectScreen  │  low / medium / high
│  (step 2)            │  → haiku / sonnet / opus
└──────────┬───────────┘
           │ effort
           ▼
┌──────────────────────────┐
│  WorkspaceModeScreen     │  repo / copy / scratch
│  (step 3)                │  Shows isolation warning for copy/scratch
└──────────────┬───────────┘
               │ ws_mode
               ▼
┌──────────────────────────────────────────────────────┐
│  RunConfigScreen (step 4)                            │
│  ── Provider ──────────────────────────────────      │
│  anthropic-sdk / subprocess / custom                 │
│  Lists registered providers; CONFIG DEFAULT = skip   │
│  ── Governance ────────────────────────────────      │
│  HITL mode, max_parallel, budget,                    │
│  budget_behavior, follow_up policy                   │
└──────────────────────────┬───────────────────────────┘
                           │ {provider, hitl, budget, ...}
                           ▼
┌────────────────────────────────────────────────┐
│  PlanRunScreen (step 5, wizard_params={...})   │
│  • Calls wizard handler (generates plan)       │
│  • Live progress bar + manifest polling        │
│  • Streams progress to RichLog                 │
│  [H] → go_home   [Esc] → back                 │
└────────────────────────────────────────────────┘
```

**Abort points:** every screen returns `None` or empty to cancel the wizard.  
**Depth:** 6 screens pushed, 6 pops on Home.

---

## Flow 2 — Plans

```
Home [P]
  │
  ▼
┌──────────────────────────────────────────────────────────┐
│  SavedPlansScreen                                        │
│                                                          │
│  search: [________________]                              │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Name          Goal                Tasks  Source    │  │
│  │ my-plan       Fix the thing       4      tracked   │  │
│  │ ...                                                │  │
│  └────────────────────────────────────────────────────┘  │
│  [◀ prev]  Page 1/3 (1–15 of 42)  [next ▶]              │
│  [RUN] [DETAILS] [VALIDATE] [EDIT] [BACK]                │
└────────┬──────────┬───────────┬───────────┬─────────────┘
         │          │           │           │
       [R]Run    [D]Details  [V]Validate  [E]Edit
         │          │           │           │
         ▼          ▼           ▼           ▼
    RunPlanScreen PlanDetails ValidateRun  PlanEditor
    (direct)      Screen      Screen       Screen
                              (direct)
```

---

## Flow 3 — Plan Details

Central hub for a single plan. Reached from SavedPlans or RunLedger.

```
PlanDetailsScreen
│
│  [ PLAN REVIEW ]
│  plan-path: ai/orchestrator/tasks/my-plan.json
│  ┌──────────────────────────────────────────────┐
│  │  Plan   : my-plan                            │
│  │  Goal   : ...                                │
│  │  Tasks  : 4                                  │
│  │  ═══ Task Graph ═══                          │
│  │  ▸ task-1: ...                               │
│  └──────────────────────────────────────────────┘
│  [APPROVE+RUN] [VALIDATE] [DRY RUN] [SAVE COPY] [TOOLS] [FOLLOW-UP] [BACK]
│
├─[A] Approve + Run ──────────────────► RunPlanScreen
│
├─[V] Validate ───────────────────────► ValidatePlanDialog (modal) → ValidateRunScreen
│
├─[D] Dry Run ────────────────────────► RunPlanScreen (dry_run=True)
│
├─[S] Save Copy ──────────────────────► (copies to runtime_root/saved-plans/, notify)
│
├─[T] Tools ──────────────────────────► PlanInspectScreen (mode=tools)
├─[I] Intents ────────────────────────► PlanInspectScreen (mode=intents)
├─[O] Profiles ───────────────────────► PlanInspectScreen (mode=profiles)
├─[P] Prompt ─────────────────────────► PlanInspectScreen (mode=prompt)
├─[F] Follow-up ──────────────────────► PlanInspectScreen (mode=followup, run_dir=<latest>)
│
└─[Esc] Back
```

---

## Flow 4 — Plan Inspect (Tabbed)

`PlanInspectScreen` consolidates 5 former individual screens into one.

```
PlanInspectScreen
│
│  [ PLAN INSPECTOR ]   ai/orchestrator/tasks/my-plan.json
│
│  [T] TOOLS  [I] INTENTS  [O] PROFILES  [P] PROMPT  [F] FOLLOW-UP
│  ──────────────────────────────────────────────────────────────────
│
│  TOOLS mode:
│  ┌─────────────────────────┬──────────────────────────────────────┐
│  │ tool-name  kind  source │  detail / warnings (RichLog)         │
│  │ ...                     │  ⚠ base-tool collision               │
│  │                         │  ⚠ path traversal risk               │
│  └─────────────────────────┴──────────────────────────────────────┘
│
│  INTENTS mode:     repo-script vs tool intents, legacy commands
│  PROFILES mode:    default vs lean per task, lean hints
│  PROMPT mode:      section-level token breakdown, 80K/120K thresholds
│  FOLLOW-UP mode:   plan followUpTasks + live injection/skipped events
│                    (run_dir required for live events)
│
│  [BACK]
```

---

## Flow 5 — Run Ledger

```
Home [R] ──► RunLedgerScreen(mode="runs")

  ┌──────────────────────────────────────────────────────────────────────┐
  │  [ RUNS ]  Retained run history                                      │
  │                                                                      │
  │  Run ID          Plan        Status      Tasks  Cost   Tokens        │
  │  ──────────────────────────────────────────────────────────────────  │
  │  20260504T11...  my-plan     completed   4/4    $0.023 ↓96K ↑6.5K   │
  │  20260504T11...  docs-proof  failed      1/2    $0.012 ↓48K ↑3.2K   │
  │  ...                                                                 │
  │                                                                      │
  │  [OPEN] [RESUME] [RETRY] [PROMOTE] [GATE] [LEDGER] [BACK]           │
  └────────┬──────────┬──────────┬──────────┬──────────┬────────────────┘
           │          │          │          │          │
         [OPEN]     [R]        [Y]        [P]        [G]
           │          │          │          │          │
           ▼          ▼          ▼          ▼          ▼
      RunDetails  ResumeRetry  ResumeRetry  DiffReview  HitlGate
      Screen      mode=resume  mode=retry   Screen      Screen

  [LEDGER] — switches title to "[ LEDGER ]  Run ledger summary" (same data, tab toggle)
```

### RunDetailsScreen — Button State Rules

Buttons start **disabled** and are enabled based on the manifest-derived run state:

| Button  | Enabled when        |
|---------|---------------------|
| RESUME  | `paused` or `unknown` |
| RETRY   | `failed` or `completed` |
| PROMOTE | `completed` only      |
| OPEN    | always (plan inspect) |

State is derived by scanning manifest events + task-level `status` fields:
- `run-finished` present + all task statuses OK → `completed`
- `run-finished` present + any task `failed` → `failed`
- `run-finished` present + any task `blocked` → `blocked`
- `run-start` present, no `run-finished` → `running`
- otherwise → `unknown`

**Duration** and **Tokens** columns populated from manifest scan (fallback path when inventory handler unavailable).

---

## Flow 6 — Diff Review & Promote

```
RunLedger [P] ──► DiffReviewScreen
  │
  │  [ DIFF REVIEW ]   run-dir: .claude-orchestrator/runs/<run-id>
  │  +42 insertions  -8 deletions  across 3 file(s)
  │
  │  ┌───────────────────────┬──────────────────────────────────────────┐
  │  │ File              +  -│  diff content (git-style colored)        │
  │  │ src/Foo.java     42  8│  ─ old line (red)                        │
  │  │ README.md         3  1│  + new line (green)                      │
  │  │ pom.xml           1  0│  word-level highlights for changed tokens │
  │  └───────────────────────┴──────────────────────────────────────────┘
  │
  │  [PROMOTE] [ALL FILES] [EXPORT PATCH] [COORD VALIDATE] [BACK]
  │
  └─[PROMOTE] ──► PromoteConfirmDialog  ← ModalScreen
                    │
                    │  [ PROMOTE ]  Apply workspace changes to the repo?
                    │  [Y] CONFIRM PROMOTE    [N] CANCEL
                    │
                    └─[Y]──► _do_promote worker (inline, logs to diff-log)
```

---

## Flow 7 — HITL Gate

Two entry points: operator-triggered from Ledger, or auto-pushed during a live run.

```
RunLedger [G] ──► HitlGateScreen(run_ref=run_dir)
                      OR
OperatorApp.wait_for_hitl_decision() ──► auto-push HitlGateScreen

  ┌───────────────────────────────────────────────────────────────────┐
  │  [ HITL GATE ]  ⚠  HUMAN APPROVAL REQUIRED  ⚠                   │
  │  Gate ID: batch-2  ·  Run: .claude-orchestrator/runs/...          │
  │                                                                   │
  │  ┌───────────────────────────────┐  ┌─────────────────────────┐  │
  │  │  COMPLETED BATCH SUMMARY      │  │  PENDING TASKS          │  │
  │  │  ✓ task-1: completed $0.012   │  │  task-3: pending        │  │
  │  │  ✓ task-2: completed $0.008   │  │  task-4: pending        │  │
  │  └───────────────────────────────┘  └─────────────────────────┘  │
  │  Cost so far: $0.020  ·  Stale sentinel: OK                       │
  │                                                                   │
  │  [A] APPROVE    [X] ABORT    [BACK]                               │
  └───────────────────────────────────────────────────────────────────┘

  [A] ──► approve handler ──► run continues
  [X] ──► abort handler   ──► run stops
  [Esc] ──► sentinel polling resumes (operator defers decision)
```

---

## Flow 8 — Agents

```
Home [A] ──► AgentsScreen

  ┌─────────────────────────────────────────────────────────────────┐
  │  [ AGENTS ]  Available agent definitions                        │
  │  ag-path: ai/orchestrator/agents.json                           │
  │  ┌───────────────────────────────────────────────────────────┐  │
  │  │ Name            Model     Skills  Effort  Workspace       │  │
  │  │ planner         power     3       high    repo            │  │
  │  │ coder           balanced  5       medium  copy            │  │
  │  │ docs-writer     simple    1       low     scratch         │  │
  │  │ reviewer        balanced  2       medium  repo            │  │
  │  │ test-runner     simple    0       low     copy            │  │
  │  └───────────────────────────────────────────────────────────┘  │
  │  [◀ PREV]  Page 1/2  (8 agents)  [NEXT ▶]                      │
  │  ─────────────────────────────────────────────────────────────  │
  │  ┌───────────────────────────────────────────────────────────┐  │
  │  │ planner  (detail RichLog)                                 │  │
  │  │   Model: power   Effort: high   Workspace: repo           │  │
  │  │   Prompt: agents/planner/prompt.md  ✓ 3.2KB               │  │
  │  │   Skills: caveman, java, python                           │  │
  │  └───────────────────────────────────────────────────────────┘  │
  │  [NEW]  [EDIT]  [BACK]                                          │
  └─────────────────────────────────────────────────────────────────┘

  [NEW]  ──► AgentEditScreen (is_new=True)
  [EDIT] ──► AgentEditScreen (is_new=False, current agent)
```

### AgentEditScreen

```
AgentEditScreen
│  [ NEW AGENT ] / [ EDIT AGENT ]
│
│  name            [___________________]
│  description     ┌─────────────────────────────────────┐  (TextArea)
│                  │ What this agent does...              │
│                  └─────────────────────────────────────┘
│  promptFile      [agents/my-agent/prompt.md_____________]
│  modelProfile    [▼ power           ]
│  effort          [▼ high            ]
│  workspaceMode   [▼ repo            ]
│  contextMode     [▼ full            ]
│  permissionMode  [▼ dontAsk         ]
│  outputProfile   [▼ standard        ]
│  skills          ┌─────────────────────────────────────┐  (SelectionList)
│  (space=toggle)  │ ✓ caveman                           │
│                  │ ✓ java                              │
│                  │   python                            │
│                  └─────────────────────────────────────┘
│  allowedTools    ┌─────────────────────────────────────┐  (SelectionList)
│  (space=toggle)  │ ✓ Read                              │
│                  │ ✓ Write                             │
│                  │   Bash                              │
│                  └─────────────────────────────────────┘
│  maxPromptEstimatedTokens  [1600___]
│  timeoutSec                [1800___]
│
│  [SAVE]  [CANCEL]

SAVE writes agents.json (key = name).
Renames key if name changed on edit.
```

---

## Flow 9 — Skills

```
Home [S] ──► SkillsScreen

  ┌─────────────────────────────────────────────────────────────────┐
  │  [ SKILLS ]  Skill registry                                     │
  │  sk-path: ai/orchestrator/skills/registry.json                  │
  │  ┌───────────────────────────────────────────────────────────┐  │
  │  │ Skill           Size     State   Description              │  │
  │  │ caveman         1.2KB    OK      Terse output mode        │  │
  │  │ java            4.1KB    OK      Java 17 development      │  │
  │  │ python          3.8KB    OK      Python 3.10+ patterns    │  │
  │  │ router          0.9KB    OK      Skill routing table      │  │
  │  │ textual-tui     5.9KB    WARN    Textual TUI patterns     │  │
  │  └───────────────────────────────────────────────────────────┘  │
  │  [◀ PREV]  Page 1/3  (12 skills)  [NEXT ▶]                     │
  │  ─────────────────────────────────────────────────────────────  │
  │  ┌───────────────────────────────────────────────────────────┐  │
  │  │ caveman  (detail RichLog)                                 │  │
  │  │   Prompt File: caveman/SKILL.md  ✓ 1.2KB                 │  │
  │  │   ── Content preview ──                                   │  │
  │  │   # Caveman mode...                                       │  │
  │  └───────────────────────────────────────────────────────────┘  │
  │  [NEW]  [EDIT]  [BACK]                                          │
  └─────────────────────────────────────────────────────────────────┘

  File size thresholds: > 6KB → WARN (#ffaa00), > 8KB → ERR (#ff2244)
  promptFile resolved relative to registry.json dir (not CWD).

  [NEW]  ──► SkillEditScreen (is_new=True)
  [EDIT] ──► SkillEditScreen (is_new=False, current skill)
```

### SkillEditScreen

```
SkillEditScreen
│  [ NEW SKILL ] / [ EDIT SKILL ]
│
│  name (key in registry.json)  [___________________]
│  description                  ┌─────────────────────────────────────┐  (TextArea)
│                               │ What this skill does...             │
│                               └─────────────────────────────────────┘
│  promptFile                   [my-skill/SKILL.md___________________]
│  tags (comma-separated)       [java, testing______________________]
│  prompt content               ┌─────────────────────────────────────┐  (TextArea, markdown)
│                               │ # My Skill                          │
│                               │ ...                                 │
│                               └─────────────────────────────────────┘
│
│  [SAVE]  [CANCEL]

SAVE writes:
  1. prompt file at (registry.json dir / promptFile)  — creates parent dirs
  2. registry.json entry  { promptFile, description?, tags? }
Renames key if name changed on edit.
```

---

## Flow 10 — Memory Tools

```
Home [M] ──► MemoryToolsScreen
  │
  │  [ MEMORY TOOLS ]  AI Memory Maintenance
  │  Hot context: ai/core/agent-invariants.md · ai/core/repo-purpose.md
  │               ai/state/current-state.md · ai/state/handoff.md
  │  Status: [ RUNNING ] refreshing...  ← live status + button lock
  │  [________________]  ← query input
  │  [REFRESH] [CHECK] [QUERY] [BACK]
  │
  │  workers run:
  │    refresh-ai-memory.ps1 / .py   (platform-aware)
  │    query-ai-memory.ps1 / .py
  │
  └── logs subprocess output; shows ✓ / ✗ on completion
```

---

## Flow 11 — Config / Settings

```
Home [C] ──► SettingsScreen
  │
  │  [ SETTINGS ]
  │  config-path: pojolens-agents.toml  (will be created if absent)
  │
  │  ── TOKENS / API KEYS ────────────────────────────────────────────
  │  ANTHROPIC_API_KEY  (stored in .env, never in TOML)
  │  [●●●●●●●●●●●●●●●●●●●●●●●●●]  password field
  │  Current status: ● SET  env/.env
  │
  │  POJO_LENS_PROVIDER
  │  [▼ auto                   ]  auto / sdk / subprocess
  │
  │  ── DEFAULTS ─────────────────────────────────────────────────────
  │  runtime_root           [.claude-orchestrator_____________]
  │  claude_bin             [claude___________________________]
  │  max_parallel           [4_______]
  │  continue_on_error      [▼ false  ]
  │  worker_validation_mode [▼ intents-only ]
  │
  │  ── NOTIFICATIONS ─────────────────────────────────────────────────
  │  desktop        [▼ true  ]
  │  notify_on      [▼ always]
  │  webhook_url    [https://...]
  │  slack_webhook_url [https://hooks.slack.com/...]
  │
  │  ── WORKSPACE ──────────────────────────────────────────────────────
  │  strategy  [▼ repo    ]
  │  root       [.workspaces_________________]
  │
  │  [SAVE]  [BACK]

SAVE writes:
  1. .env  ← ANTHROPIC_API_KEY, POJO_LENS_PROVIDER  (API key preserved if left blank)
  2. pojolens-agents.toml  ← [defaults], [notifications], [workspace]

claude-orchestrator.ps1 sources .env from repo root on startup.
```

---

## Home Screen Layout

```
┌────────────────────────────────────────────────────────────────────────────┐
│ POJOLENS  //  OPERATOR CONSOLE                        MISSION CONTROL  12:34│
├───────────────────────────────────┬────────────────────────────────────────┤
│           nav-panel               │         dashboard-panel                 │
│  ┌─────────────────────────────┐  │  ┌────────────────────────────────┐    │
│  │  [ascii banner art]         │  │  │ [ DASHBOARD ]                  │    │
│  │  // MISSION CONTROL //      │  │  │ Runs: 6  ✓4  ✗1  1▸           │    │
│  └─────────────────────────────┘  │  │ Total: $0.035  Tok: ↓1.1K ↑10K│    │
│  [ MAIN NAVIGATION ]              │  │ ─────────────────────────────  │    │
│  [N]  NEW PLAN    Generate plan   │  │ Run 1 / 6  [◀] [▶]            │    │
│  [P]  PLANS       Browse saved    │  │ ─────────────────────────────  │    │
│  ─────────────────────────────── │  │ Run  : 20260504T11...          │    │
│  [R]  RUNS        Run history     │  │ State: COMPLETED               │    │
│  ─────────────────────────────── │  │ Tasks: ████████████░░░░░░░░    │    │
│  [A]  AGENTS      Agent defs      │  │        4/4                     │    │
│  [S]  SKILLS      Skill registry  │  │ Cost : $0.023                  │    │
│  [M]  MEMORY TOOLS  AI memory     │  │ Tokens: ↓96K in  ↑6.5K out    │    │
│  [C]  CONFIG      Config & tokens │  │ Time : 00:02:41                │    │
│  ─────────────────────────────── │  │ ─────────────────────────────  │    │
│  [Q]  QUIT        Exit            │  │ [ RECENT ACTIVITY ]            │    │
│                                   │  │ 11:01:23  ✓ task-1 completed   │    │
│                                   │  │ 11:03:12  RUN COMPLETED        │    │
│                                   │  │ [OPEN] [STOP] [PAUSE] [DELETE] │    │
│                                   │  └────────────────────────────────┘    │
├───────────────────────────────────┴────────────────────────────────────────┤
│ ▶ NEW PLAN  Generate an AI task plan                                        │
│ ↑↓ navigate · Enter/letter select · [N][P][R][A][S][M][C][Q]               │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Full Navigation Graph

```
                              ┌─────────┐
                              │  HOME   │
                              └────┬────┘
        ┌──────┬──────┬──────┬─────┼──────┬──────┬──────┐
        │      │      │      │     │      │      │      │
       [N]    [P]    [R]    [A]   [S]   [M]   [C]   [Q]
        │      │      │      │     │      │      │
        ▼      ▼      ▼      ▼     ▼      ▼      ▼
      Goal  Plans  Runs/  Agents Skills Memory Config
      Input        Ledger Screen Screen  Tools  Screen
        │      │      │      │     │
        ▼      │      │      │     │
      Clarif   │      │      ├──[NEW]──► AgentEditScreen
        │      │      │      └──[EDIT]─► AgentEditScreen
        ▼      │      │
      Effort   │      │            ├──[NEW]──► SkillEditScreen
        │      │      │            └──[EDIT]─► SkillEditScreen
        ▼      │      │
      WsMode   │      ├──[OPEN]──► RunDetails
        │      │      ├──[R]────► ResumeRetry (resume)
        ▼      │      ├──[Y]────► ResumeRetry (retry)
     RunConfig │      ├──[P]────► DiffReview ──► PromoteConfirmDialog (modal)
        │      │      ├──[L]────► (tab: title → LEDGER view, same data)
        ▼      │      └──[G]────► HitlGate
      PlanRun  │
               └─► SavedPlans ──[R]──► RunPlanScreen (direct)
                     │        ──[V]──► ValidateRunScreen (direct)
                     │        ──[E]──► PlanEditor (direct)
                     │
                     └──[D]──► PlanDetails ─┬──[A]──► RunPlanScreen
                                            ├──[V]──► ValidateRunScreen
                                            ├──[D]──► RunPlanScreen (dry)
                                            ├──[S]──► (save copy, notify)
                                            ├──[T]──► PlanInspect (tools)
                                            ├──[I]──► PlanInspect (intents)
                                            ├──[O]──► PlanInspect (profiles)
                                            ├──[P]──► PlanInspect (prompt)
                                            └──[F]──► PlanInspect (followup)
```

---

## Key Bindings Quick Reference

```
HOME
  [N] New Plan     [P] Plans       [R] Runs
  [A] Agents       [S] Skills      [M] Memory Tools
  [C] Config       [Q] Quit
  [↑↓] Navigate    [Enter] Activate

WIZARD (each step)
  [Esc]   Cancel / back to previous step
  [Enter] Submit / proceed

PLANS (SavedPlansScreen)
  [R] Run   [D] Details  [V] Validate  [E] Edit
  [◀▶] Paginate           [Esc] Back

PLAN DETAILS
  [A] Approve+Run  [V] Validate  [D] Dry Run  [S] Save
  [T] Tools        [I] Intents   [O] Profiles [P] Prompt
  [F] Follow-up    [Esc] Back

PLAN INSPECT
  [T] Tools  [I] Intents  [O] Profiles  [P] Prompt  [F] Follow-up
  [Esc] Back

RUN LEDGER
  [I] Open/Inspect  [R] Resume  [Y] Retry  [P] Promote
  [G] HITL Gate     [L] Ledger tab          [Esc] Back

AGENTS SCREEN
  [Esc] Back  (mouse/keyboard row selection)
  [NEW] Create agent   [EDIT] Edit selected   [BACK]
  [◀ PREV] / [NEXT ▶] Paginate (5 per page)

SKILLS SCREEN
  [Esc] Back
  [NEW] Create skill   [EDIT] Edit selected   [BACK]
  [◀ PREV] / [NEXT ▶] Paginate (5 per page)

AGENT EDIT / SKILL EDIT
  [Space] Toggle selection in SelectionList
  [SAVE] Write JSON + prompt file   [CANCEL / Esc] Discard

DIFF REVIEW
  [P] Promote (→ modal confirm)   [E] Export Patch   [C] Coord. Val.   [Esc] Back

PROMOTE CONFIRM DIALOG
  [Y] Confirm   [N] Cancel   [Esc] Cancel

PLANRUNSCREEN  (wizard run + saved plan run)
  [H] Home / Dashboard   [Esc] Back

HITL GATE
  [A] Approve   [X] Abort   [Esc] Back (defers decision)

MEMORY TOOLS
  [R] Refresh   [C] Check   [Q] Query   [Esc] Back

CONFIG
  [SAVE] Write TOML + .env   [BACK / Esc] Discard
```

---

## Screen Ownership Map (by file)

```
_tui_home.py         HomeScreen
_tui_wizard.py       GoalInputScreen, ClarificationScreen,
                     EffortSelectScreen, WorkspaceModeScreen,
                     RunConfigScreen
                     (+ legacy aliases: ProviderSelectScreen, GovernanceScreen)
_tui_wizard_run.py   PlanRunScreen  (wizard + plan run, merged)
                     (+ alias exported: RunPlanScreen → PlanRunScreen)
_tui_plans.py        SavedPlansScreen, PlanDetailsScreen, PlanEditorScreen
_tui_validate.py     ValidatePlanDialog (modal), ValidateRunScreen
                     (+ aliases: ValidatePlanScreen → ValidatePlanDialog,
                      RunPlanScreen re-exported from _tui_wizard_run)
_tui_ledger.py       RunLedgerScreen, RunDetailsScreen, ResumeRetryScreen
_tui_gate.py         HitlGateScreen
_tui_diff.py         DiffReviewScreen, PromoteConfirmDialog (modal)
_tui_inspect.py      AgentsScreen, AgentEditScreen,
                     SkillsScreen, SkillEditScreen,
                     PlanInspectScreen
                     (+ aliases: ExtraToolsScreen, ValidationIntentsScreen,
                      OutputProfilesScreen, PromptAccountingScreen,
                      FollowUpTaskScreen)
_tui_tools.py        MemoryToolsScreen, SettingsScreen
_tui_estimate.py     EstimateScreen, EstimateResultScreen
_tui_dashboard.py    DashboardWidget  (widget, not a screen)
_tui_theme.py        _MTX_VARS, CSS constants, banner art, option lists
_tui_helpers.py      _status_color, _load_plan_json, _plan_summary_rich,
                     _collect_previews, _fmt_tok, _calc_run_duration
tui_operator.py      OperatorApp, _ErrorLog, run_operator_tui()
                     (re-exports all screen classes)
tui_operator.tcss    All Textual CSS for operator screens
```
