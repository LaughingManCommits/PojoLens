# Operator TUI — Screen Reference & Flow Audit

Terminal UI launched via `pojolens-agents operator`.  
Built on Textual.  All screens share the Matrix cyberpunk theme (`_MTX_VARS`).

---

## Screen Inventory

| # | Screen class          | File              | Type        | Entry point        |
|---|-----------------------|-------------------|-------------|--------------------|
| 1 | `HomeScreen`          | _tui_home         | Full        | App root           |
| 2 | `GoalInputScreen`     | _tui_wizard       | Full        | Wizard step 1      |
| 3 | `ClarificationScreen` | _tui_wizard       | Full        | Wizard step 1b     |
| 4 | `EffortSelectScreen`  | _tui_wizard       | Full        | Wizard step 2      |
| 5 | `WorkspaceModeScreen` | _tui_wizard       | Full        | Wizard step 3      |
| 6 | `ProviderSelectScreen`| _tui_wizard       | Full        | Wizard step 4      |
| 7 | `GovernanceScreen`    | _tui_wizard       | Full        | Wizard step 5      |
| 8 | `PlanRunScreen`       | _tui_wizard_run   | Full        | Wizard step 6      |
| 9 | `SavedPlansScreen`    | _tui_plans        | Full        | Home [S]           |
|10 | `PlanDetailsScreen`   | _tui_plans        | Full        | SavedPlans / Runs  |
|11 | `PlanEditorScreen`    | _tui_plans        | Full        | PlanDetails [E]    |
|12 | `RunPlanScreen`       | _tui_validate     | Full        | PlanDetails [A]    |
|13 | `ValidatePlanScreen`  | _tui_validate     | **Modal**   | Home [V]           |
|14 | `ValidateRunScreen`   | _tui_validate     | Full        | ValidatePlan / PlanDetails [V] |
|15 | `RunLedgerScreen`     | _tui_ledger       | Full        | Home [R/L/P]       |
|16 | `RunDetailsScreen`    | _tui_ledger       | Full        | RunLedger [I]      |
|17 | `ResumeRetryScreen`   | _tui_ledger       | Full        | RunLedger [R/Y]    |
|18 | `HitlGateScreen`      | _tui_gate         | Full        | RunLedger [G] / auto-push |
|19 | `DiffReviewScreen`    | _tui_diff         | Full        | RunLedger [P]      |
|20 | `AgentsScreen`        | _tui_inspect      | Full        | Home [A]           |
|21 | `SkillsScreen`        | _tui_inspect      | Full        | Home [K]           |
|22 | `PlanInspectScreen`   | _tui_inspect      | Full        | PlanDetails [T/I/O/P/F] |
|23 | `MemoryToolsScreen`   | _tui_tools        | Full        | Home [M]           |
|24 | `SettingsScreen`      | _tui_tools        | Full        | Home [T]           |
|25 | `EstimateScreen`      | _tui_estimate     | Full        | Home [D]           |
|26 | `EstimateResultScreen`| _tui_estimate     | Full        | EstimateScreen     |

> **26 screens total** — 1 modal, 25 full-screen.  
> Backward-compat aliases (`ExtraToolsScreen`, `ValidationIntentsScreen`, etc.) all resolve to `PlanInspectScreen`.

---

## Top-Level Navigation Map

```
                         POJOLENS OPERATOR CONSOLE
                         ┌─────────────────────────────────────────────────┐
                         │                  HomeScreen                      │
                         │                                                  │
                         │  [N] New Plan       [S] Saved Plans              │
                         │  ─────────────────────────────────               │
                         │  [R] Runs           [L] Ledger                   │
                         │  [V] Validate       [D] Dry Run / Estimate       │
                         │  [P] Promote                                      │
                         │  ─────────────────────────────────               │
                         │  [A] Agents         [K] Skills                   │
                         │  [M] Memory Tools   [T] Settings                 │
                         │  ─────────────────────────────────               │
                         │  [Q] Quit                                        │
                         └──────────────────┬──────────────────────────────┘
                                            │
          ┌─────────────────────────────────┼──────────────────────────────┐
          │                                 │                              │
          ▼         ▼         ▼         ▼   ▼   ▼        ▼        ▼       ▼
       Wizard    Saved     Runs/      Valid  Est   Agents  Skills  Memory  Settings
        Flow     Plans    Ledger     Flow  Flow   Screen  Screen   Tools
       [N]       [S]      [R/L/P]    [V]   [D]    [A]     [K]      [M]     [T]
```

---

## Flow 1 — Wizard (New Plan)

Longest path in the TUI.  6 sequential steps, each can abort back to Home.

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
┌────────────────────────────┐
│  ProviderSelectScreen      │  anthropic-sdk / subprocess / custom
│  (step 4)                  │  Lists registered providers from registry
└────────────────┬───────────┘
                 │ provider
                 ▼
┌────────────────────────────┐
│  GovernanceScreen          │  HITL mode, max_parallel, budget,
│  (step 5)                  │  budget_behavior, follow_up policy
└────────────────┬───────────┘
                 │ governance dict
                 ▼
┌────────────────────────────────────────────────┐
│  PlanRunScreen (step 6)                        │
│  • Calls wizard handler (generates plan)       │
│  • Runs plan execution inline                  │
│  • Streams progress to RichLog                 │
│  [H] → go_home   [Esc] → back                  │
└────────────────────────────────────────────────┘
```

**Abort points:** every screen returns `None` or empty to cancel the wizard.  
**Depth:** 7 screens pushed, 7 pops on Home.

---

## Flow 2 — Saved Plans

```
Home [S]
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
    PlanDetails  PlanDetails  PlanDetails  PlanEditor
    mode=run     mode=view    mode=validate Screen
         │
         └── (same as Details flow below)
```

---

## Flow 3 — Plan Details

Central hub for a single plan.  Reached from SavedPlans or RunLedger.

```
PlanDetailsScreen
│
│  [ PLAN REVIEW ]
│  plan-path: ai/orchestrator/tasks/my-plan.json
│  ┌─────────────────────────────────────────────┐
│  │  Plan   : my-plan                           │
│  │  Goal   : ...                               │
│  │  Tasks  : 4                                 │
│  │  ═══ Task Graph ═══                         │
│  │  ▸ task-1: ...                              │
│  └─────────────────────────────────────────────┘
│  [APPROVE+RUN] [VALIDATE] [DRY RUN] [SAVE COPY] [TOOLS] [FOLLOW-UP] [BACK]
│
├─[A] Approve + Run ──────────────────► RunPlanScreen
│
├─[V] Validate ───────────────────────► ValidateRunScreen
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

## Flow 5 — Validate

```
Home [V]
  │
  ▼  (modal overlay)
┌──────────────────────────────────────┐
│  ValidatePlanScreen  ← ModalScreen   │
│                                      │
│  [ VALIDATE ]  Enter plan file path  │
│  [ai/orchestrator/tasks/my-plan.json]│
│                                      │
│     [VALIDATE →]   [CANCEL]          │
└──────────────────────────────────────┘
         │ path string (or None)
         ▼
┌──────────────────────────────────────────────────────────┐
│  ValidateRunScreen                                       │
│                                                          │
│  [ VALIDATE ]  Scanning plan...                          │
│  → on complete: title updates to ✓ VALID / ✗ ERRORS     │
│                                                          │
│  validates workspace boundaries                          │
│  shows: errors, warnings, cost estimate, token sections  │
│  [BACK]                                                  │
└──────────────────────────────────────────────────────────┘
```

Also reachable from `PlanDetailsScreen [V]` (skips the modal, path already known).

---

## Flow 6 — Run Ledger

`RunLedgerScreen` reused across 3 Home entry points with different labels.

```
Home [R] ──► RunLedgerScreen(mode="runs")
Home [L] ──► RunLedgerScreen(mode="ledger")
Home [P] ──► RunLedgerScreen(mode="promote")

  ┌──────────────────────────────────────────────────────────────────────┐
  │  [ LEDGER ]  Run ledger summary                                      │
  │                                                                      │
  │  Run ID          Plan        Status      Tasks  Cost   Tokens         │
  │  ──────────────────────────────────────────────────────────────────  │
  │  20260504T11...  my-plan     completed   4/4    $0.023 ↓96K ↑6.5K   │
  │  20260504T11...  docs-proof  completed   2/2    $0.012 ↓48K ↑3.2K   │
  │  ...                                                                  │
  │                                                                      │
  │  [INSPECT] [RESUME] [RETRY] [PROMOTE] [GATE [G]] [BACK]             │
  └────────┬──────────┬──────────┬──────────┬──────────┬────────────────┘
           │          │          │          │          │
         [I]        [R]        [Y]        [P]        [G]
           │          │          │          │          │
           ▼          ▼          ▼          ▼          ▼
      RunDetails  ResumeRetry  ResumeRetry  DiffReview  HitlGate
      Screen      mode=resume  mode=retry   Screen      Screen
```

**Duration** and **Tokens** columns are populated from manifest scan when inventory handler unavailable (fallback path).

---

## Flow 7 — Diff Review & Promote

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
  └─[PROMOTE] ──► ResumeRetryScreen(mode="promote")
                    │
                    │  [ PROMOTE ]  confirm to proceed
                    │  [CONFIRM PROMOTE]  [BACK]
                    │
                    └──► promote handler ──► result displayed
```

---

## Flow 8 — HITL Gate

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

## Flow 9 — Estimate (Dry Run)

```
Home [D]
  │
  ▼
┌───────────────────────────────────────┐
│  EstimateScreen                       │
│                                       │
│  Mode: [ ] Estimate  [x] Dry Run      │
│  Plan: [_____________________________]│
│                                       │
│  [RUN ESTIMATE]  [BACK]               │
└───────────────────┬───────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────┐
│  EstimateResultScreen                            │
│                                                  │
│  Task estimates, per-model costs,                │
│  total estimated USD, token budget               │
│  [BACK]                                          │
└──────────────────────────────────────────────────┘
```

---

## Flow 10 — Inspect Tools (Agents / Skills)

```
Home [A] ──► AgentsScreen
Home [K] ──► SkillsScreen

  AgentsScreen:
  ┌──────────────────────┬────────────────────────────────────────────┐
  │ Name    Role  Skills │  agent detail                              │
  │ planner plan  3      │  prompt size: 12.4 KB                      │
  │ coder   impl  5      │  ⚠ prompt > 8 KB — may hit context limits  │
  │ ...                  │  tools: read_file, write_file, bash, ...   │
  └──────────────────────┴────────────────────────────────────────────┘

  SkillsScreen:
  ┌─────────────────────┬─────────────────────────────────────────────┐
  │ Name   Size  Status │  skill file content / description           │
  │ java   4.2KB OK     │  ⚠ > 6 KB: review for trim                  │
  │ python 7.1KB WARN   │  ✗ > 8 KB: too large, will be truncated     │
  └─────────────────────┴─────────────────────────────────────────────┘
```

---

## Flow 11 — Memory & Settings

```
Home [M] ──► MemoryToolsScreen
  │
  │  [ MEMORY TOOLS ]
  │  Status: [ RUNNING ] refreshing...  ← live status + button lock
  │  [________________]  ← query input
  │  [R] Refresh   [C] Check   [Q] Query   [BACK]
  │
  │  workers: refresh-ai-memory.ps1 / .py
  │           query-ai-memory.ps1 / .py
  │
  └── logs subprocess output; shows ✓ / ✗ on completion

Home [T] ──► SettingsScreen
  │
  │  ═══ Operator Defaults ═══
  │  Runtime Root / Agents File / Claude Binary / Tasks Dir
  │  ═══ Effort → Model Mapping ═══
  │  ═══ Rate Limits ═══
  │  ═══ Notification Defaults ═══
  │  ═══ Config File ═══
  │  ═══ Providers ═══  (id, class, pricing, TPM/RPM per provider)
```

---

## Home Screen Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ POJOLENS  //  OPERATOR CONSOLE                          MISSION CONTROL  12:34│
├────────────────────────────────────────┬────────────────────────────────────┤
│           nav-panel                    │         dashboard-panel             │
│  ┌──────────────────────────────────┐  │  ┌────────────────────────────────┐ │
│  │  [ascii banner art]              │  │  │ [ DASHBOARD ]                  │ │
│  │  // MISSION CONTROL //           │  │  │ Runs: 6  ✓4  ✗1  1▸            │ │
│  └──────────────────────────────────┘  │  │ Total: $0.035  Tok: ↓1.1K ↑10K │ │
│  [ MAIN NAVIGATION ]                   │  │ ────────────────────────────── │ │
│  [N]  CREATE NEW PLAN  Generate plan   │  │ Run 1 / 6  [latest]            │ │
│  [S]  SAVED PLANS      Browse saved    │  │ ────────────────────────────── │ │
│  ──────────────────────────────────── │  │ Run  : 20260504T11...          │ │
│  [R]  RUNS             Run history     │  │ State: COMPLETED               │ │
│  [L]  LEDGER           Cost summary    │  │ Tasks: ████████████░░░░░░░░    │ │
│  [V]  VALIDATE         Validate plan   │  │        4/4                     │ │
│  [D]  DRY RUN          Cost estimate   │  │ Cost : $0.023                  │ │
│  [P]  PROMOTE          Review diffs    │  │ Tokens: ↓96K in  ↑6.5K out    │ │
│  ──────────────────────────────────── │  │ Time : 00:02:41                │ │
│  [A]  AGENTS           Agent defs      │  │ ────────────────────────────── │ │
│  [K]  SKILLS           Skill registry  │  │ [ RECENT ACTIVITY ]            │ │
│  [M]  MEMORY TOOLS     AI memory       │  │ 11:01:23  ✓ task-1 completed   │ │
│  [T]  SETTINGS         Config          │  │ 11:02:41  ▸ BATCH              │ │
│  ──────────────────────────────────── │  │ 11:03:12  RUN COMPLETED        │ │
│  [Q]  QUIT             Exit            │  │ [STOP] [PAUSE] [DELETE]        │ │
│                                        │  └────────────────────────────────┘ │
├────────────────────────────────────────┴────────────────────────────────────┤
│ ▶ CREATE NEW PLAN  Generate an AI task plan                                  │
│ ↑↓ navigate · Enter/letter select · [N] new  [S] saved  [R] runs ...        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Full Navigation Graph

```
                               ┌─────────┐
                               │  HOME   │
                               └────┬────┘
         ┌──────┬──────┬──────┬─────┼──────┬──────┬──────┬──────┐
         │      │      │      │     │      │      │      │      │
        [N]    [S]  [R/L/P]  [V]   [D]   [A]   [K]   [M]   [T]
         │      │      │      │     │      │      │      │      │
         ▼      ▼      ▼      ▼     ▼      ▼      ▼      ▼      ▼
       Goal  Saved  Ledger  Valid  Est.  Agents Skills Memory  Settings
       Input Plans  Screen  Modal  Screen Screen Screen Tools  Screen
         │      │      │      │     │
         ▼      │      │      ▼     ▼
       Clarif  │      │   ValidRun  EstResult
         │      │      │
         ▼      │      ├──[I]──► RunDetails
       Effort   │      ├──[R]──► ResumeRetry (resume)
         │      │      ├──[Y]──► ResumeRetry (retry)
         ▼      │      ├──[P]──► DiffReview ──► ResumeRetry (promote)
       WsMode   │      └──[G]──► HitlGate
         │      │
         ▼      │
       Provider │
         │      │
         ▼      │         ┌──[A]──► RunPlanScreen
       Govern   │         ├──[V]──► ValidateRunScreen
         │      ▼         ├──[D]──► RunPlanScreen (dry)
         ▼   PlanDetails ─┤──[E]──► PlanEditor
       PlanRun (wizard)   ├──[S]──► (save copy, notify)
                          ├──[T]──► PlanInspect (tools)
                          ├──[I]──► PlanInspect (intents)
                          ├──[O]──► PlanInspect (profiles)
                          ├──[P]──► PlanInspect (prompt)
                          └──[F]──► PlanInspect (followup)
```

---

## Complexity Audit

### Identified complexities and notes

```
COMPLEXITY                          SEVERITY   NOTES
───────────────────────────────────────────────────────────────────────────────
Wizard is 6 steps deep              MEDIUM     Steps 4 (Provider) and 5
                                               (Governance) could merge into
                                               one "Run Config" screen for
                                               shorter flows

RunLedgerScreen has 3 modes         LOW        Same screen, different label
(runs / ledger / promote)                      and hint text.  Works but
                                               HOME has separate [R], [L], [P]
                                               entries for what renders the same
                                               screen — confusing to operators.
                                               Consider one "Runs" screen with
                                               a mode tab inside.

PlanRunScreen vs RunPlanScreen      MEDIUM     PlanRunScreen = wizard execution
(naming confusion)                             RunPlanScreen = saved plan run
                                               Nearly identical purpose, different
                                               constructors.  Merge candidate.

DiffReview → ResumeRetry (promote)  LOW        Two hops for promote.  DiffReview
                                               could inline the confirm dialog
                                               as a modal rather than pushing a
                                               full screen.

SavedPlans → PlanDetails →          LOW        3 levels deep before execution.
PlanInspect                                    Deep but each level adds value.
                                               Acceptable.

ValidatePlanScreen is modal but     LOW        Named inconsistently with the
ValidateRunScreen is full-screen               "Screen" suffix. Modal screens
                                               conventionally use "Dialog" or
                                               "Modal" suffix.

HitlGateScreen reachable only       LOW        Auto-push works during live runs.
from [G] in ledger or auto-push                Operator-triggered path added
(previously no manual path)                    (WP80). OK now.

PlanInspectScreen replaces 5        RESOLVED   Was 5 separate screens.  Now one
individual inspect screens                     tabbed screen with backward-compat
                                               aliases.  Correct direction.

ProviderSelectScreen never          INFO       Provider dropdown populated from
dismisses None (always returns                 registry.  If registry empty the
a value)                                       screen still returns something.
                                               Not a bug, just worth noting.
───────────────────────────────────────────────────────────────────────────────
```

### Suggested simplifications (non-urgent)

```
1. MERGE ProviderSelectScreen into GovernanceScreen
   ─────────────────────────────────────────────────
   Wizard step 4 (provider) feeds directly into step 5 (governance).
   Both are config choices.  Combining them saves one push/pop cycle
   and makes the wizard 5 steps instead of 6.

   BEFORE:  ...WorkspaceMode → ProviderSelect → Governance → PlanRun
   AFTER:   ...WorkspaceMode → RunConfigScreen → PlanRun

2. MERGE [R] / [L] into one Runs screen with internal tabs
   ──────────────────────────────────────────────────────────
   Home currently has both [R] Runs and [L] Ledger pointing to the
   same RunLedgerScreen with different mode labels.  Consolidate to
   a single [R] entry with a tab bar inside the screen.

   Frees up [L] keybinding for something else (e.g. live run log).

3. INLINE promote confirm in DiffReviewScreen
   ─────────────────────────────────────────────
   DiffReview [PROMOTE] currently pushes ResumeRetryScreen(mode=promote)
   which requires a second CONFIRM press.  A small ModalScreen confirm
   dialog ("Promote this run? [Y/N]") achieves the same safety with
   one fewer full-screen push.

4. RENAME ValidatePlanScreen → ValidatePlanDialog (or ModalScreen suffix)
   ────────────────────────────────────────────────────────────────────────
   It is a ModalScreen.  Naming it "Screen" is misleading.  Low priority
   but helps future developers understand the type immediately.
```

---

## Key Bindings Quick Reference

```
HOME
  [N] New Plan        [S] Saved Plans     [R] Runs
  [L] Ledger          [V] Validate        [D] Dry Run
  [P] Promote         [A] Agents          [K] Skills
  [M] Memory Tools    [T] Settings        [Q] Quit
  [↑↓] Navigate menu  [Enter] Activate

WIZARD (each step)
  [Esc]  Cancel / back to previous step
  [Enter] Submit / proceed

SAVED PLANS
  [R] Run   [D] Details  [V] Validate  [E] Edit
  [◀▶] Paginate          [Esc] Back

PLAN DETAILS
  [A] Approve+Run  [V] Validate  [D] Dry Run  [S] Save
  [T] Tools        [I] Intents   [O] Profiles [P] Prompt
  [F] Follow-up    [Esc] Back

PLAN INSPECT
  [T] Tools   [I] Intents   [O] Profiles   [P] Prompt   [F] Follow-up
  [Esc] Back

RUN LEDGER
  [I] Inspect   [R] Resume   [Y] Retry   [P] Promote
  [G] HITL Gate              [Esc] Back

DIFF REVIEW
  [P] Promote   [Esc] Back

RUN PLAN / PLAN RUN
  [H] Home / Dashboard   [Esc] Back

HITL GATE
  [A] Approve   [X] Abort   [Esc] Back (defers decision)

MEMORY TOOLS
  [R] Refresh   [C] Check   [Q] Query   [Esc] Back
```

---

## Screen Ownership Map (by file)

```
_tui_home.py         HomeScreen
_tui_wizard.py       GoalInputScreen, ClarificationScreen,
                     EffortSelectScreen, WorkspaceModeScreen,
                     ProviderSelectScreen, GovernanceScreen
_tui_wizard_run.py   PlanRunScreen
_tui_plans.py        SavedPlansScreen, PlanDetailsScreen, PlanEditorScreen
_tui_validate.py     ValidatePlanScreen (modal), ValidateRunScreen, RunPlanScreen
_tui_ledger.py       RunLedgerScreen, RunDetailsScreen, ResumeRetryScreen
_tui_gate.py         HitlGateScreen
_tui_diff.py         DiffReviewScreen
_tui_inspect.py      AgentsScreen, SkillsScreen, PlanInspectScreen
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
