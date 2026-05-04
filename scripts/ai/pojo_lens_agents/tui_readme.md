# Operator TUI — Screen Reference & Flow Audit

Terminal UI launched via `pojolens-agents operator`.  
Built on Textual.  All screens share the Matrix cyberpunk theme (`_MTX_VARS`).

---

## Screen Inventory

| # | Screen class            | File              | Type        | Entry point        |
|---|-------------------------|-------------------|-------------|--------------------|
| 1 | `HomeScreen`            | _tui_home         | Full        | App root           |
| 2 | `GoalInputScreen`       | _tui_wizard       | Full        | Wizard step 1      |
| 3 | `ClarificationScreen`   | _tui_wizard       | Full        | Wizard step 1b     |
| 4 | `EffortSelectScreen`    | _tui_wizard       | Full        | Wizard step 2      |
| 5 | `WorkspaceModeScreen`   | _tui_wizard       | Full        | Wizard step 3      |
| 6 | `RunConfigScreen`       | _tui_wizard       | Full        | Wizard step 4      |
| 7 | `PlanRunScreen`         | _tui_wizard_run   | Full        | Wizard step 5      |
| 8 | `SavedPlansScreen`      | _tui_plans        | Full        | Home [S]           |
| 9 | `PlanDetailsScreen`     | _tui_plans        | Full        | SavedPlans / Runs  |
|10 | `PlanEditorScreen`      | _tui_plans        | Full        | PlanDetails [E]    |
|11 | `RunPlanScreen`         | _tui_validate     | Full        | PlanDetails [A]    |
|12 | `ValidatePlanDialog`    | _tui_validate     | **Modal**   | Home [V]           |
|13 | `ValidateRunScreen`     | _tui_validate     | Full        | ValidatePlan / PlanDetails [V] |
|14 | `RunLedgerScreen`       | _tui_ledger       | Full        | Home [R/P]         |
|15 | `RunDetailsScreen`      | _tui_ledger       | Full        | RunLedger [I]      |
|16 | `ResumeRetryScreen`     | _tui_ledger       | Full        | RunLedger [R/Y]    |
|17 | `HitlGateScreen`        | _tui_gate         | Full        | RunLedger [G] / auto-push |
|18 | `DiffReviewScreen`      | _tui_diff         | Full        | RunLedger [P]      |
|19 | `PromoteConfirmDialog`  | _tui_diff         | **Modal**   | DiffReview [P]     |
|20 | `AgentsScreen`          | _tui_inspect      | Full        | Home [A]           |
|21 | `SkillsScreen`          | _tui_inspect      | Full        | Home [K]           |
|22 | `PlanInspectScreen`     | _tui_inspect      | Full        | PlanDetails [T/I/O/P/F] |
|23 | `MemoryToolsScreen`     | _tui_tools        | Full        | Home [M]           |
|24 | `SettingsScreen`        | _tui_tools        | Full        | Home [T]           |
|25 | `EstimateScreen`        | _tui_estimate     | Full        | Home [D]           |
|26 | `EstimateResultScreen`  | _tui_estimate     | Full        | EstimateScreen     |

> **26 screens total** — 2 modals, 24 full-screen.  
> Backward-compat aliases: `ProviderSelectScreen`, `GovernanceScreen` (both → `RunConfigScreen`); `ValidatePlanScreen` (→ `ValidatePlanDialog`); `ExtraToolsScreen`, `ValidationIntentsScreen`, etc. (→ `PlanInspectScreen`).

---

## Top-Level Navigation Map

```
                         POJOLENS OPERATOR CONSOLE
                         ┌─────────────────────────────────────────────────┐
                         │                  HomeScreen                      │
                         │                                                  │
                         │  [N] New Plan       [S] Saved Plans              │
                         │  ─────────────────────────────────               │
                         │  [R] Runs           [V] Validate                 │
                         │  [D] Dry Run / Estimate                          │
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
          ▼         ▼         ▼      ▼     ▼      ▼        ▼        ▼       ▼
       Wizard    Saved    Runs/   Valid   Est   Agents  Skills  Memory  Settings
        Flow     Plans   Ledger   Flow  Flow   Screen  Screen   Tools
       [N]       [S]     [R/P]    [V]   [D]    [A]     [K]      [M]     [T]
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
┌────────────────────────────────────────────────────┐
│  RunConfigScreen (step 4)                          │
│  ── Provider ──────────────────────────────────    │
│  anthropic-sdk / subprocess / custom               │
│  Lists registered providers; CONFIG DEFAULT = skip  │
│  ── Governance ────────────────────────────────    │
│  HITL mode, max_parallel, budget,                  │
│  budget_behavior, follow_up policy                 │
└──────────────────────────┬─────────────────────────┘
                           │ {provider, hitl, budget, ...}
                           ▼
┌────────────────────────────────────────────────┐
│  PlanRunScreen (step 5)                        │
│  • Calls wizard handler (generates plan)       │
│  • Runs plan execution inline                  │
│  • Streams progress to RichLog                 │
│  [H] → go_home   [Esc] → back                  │
└────────────────────────────────────────────────┘
```

**Abort points:** every screen returns `None` or empty to cancel the wizard.  
**Depth:** 6 screens pushed, 6 pops on Home.

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
│  ValidatePlanDialog  ← ModalScreen   │
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

`RunLedgerScreen` has two Home entry points.  `[L]` is now an internal tab
that switches the screen title between "RUNS" and "LEDGER" views.

```
Home [R] ──► RunLedgerScreen(mode="runs")
Home [P] ──► RunLedgerScreen(mode="promote")

  ┌──────────────────────────────────────────────────────────────────────┐
  │  [ RUNS ]  Retained run history                                      │
  │                                                                      │
  │  Run ID          Plan        Status      Tasks  Cost   Tokens         │
  │  ──────────────────────────────────────────────────────────────────  │
  │  20260504T11...  my-plan     completed   4/4    $0.023 ↓96K ↑6.5K   │
  │  20260504T11...  docs-proof  completed   2/2    $0.012 ↓48K ↑3.2K   │
  │  ...                                                                  │
  │                                                                      │
  │  [INSPECT] [RESUME] [RETRY] [PROMOTE] [GATE [G]] [LEDGER [L]] [BACK]│
  └────────┬──────────┬──────────┬──────────┬──────────┬────────────────┘
           │          │          │          │          │
         [I]        [R]        [Y]        [P]        [G]
           │          │          │          │          │
           ▼          ▼          ▼          ▼          ▼
      RunDetails  ResumeRetry  ResumeRetry  DiffReview  HitlGate
      Screen      mode=resume  mode=retry   Screen      Screen

  [L] — switches title to "[ LEDGER ]  Run ledger summary" (same data, cosmetic tab)
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
  └─[PROMOTE] ──► PromoteConfirmDialog  ← ModalScreen
                    │
                    │  [ PROMOTE ]  Apply workspace changes to the repo?
                    │  [Y] CONFIRM PROMOTE    [N] CANCEL
                    │
                    └─[Y]──► _do_promote worker (inline, logs to diff-log)
                             ──► promote handler ──► result in diff-log
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
│  [V]  VALIDATE         Validate plan   │  │ Tasks: ████████████░░░░░░░░    │ │
│  [D]  DRY RUN          Cost estimate   │  │        4/4                     │ │
│  [P]  PROMOTE          Review diffs    │  │ Cost : $0.023                  │ │
│  ──────────────────────────────────── │  │ Tokens: ↓96K in  ↑6.5K out    │ │
│  [A]  AGENTS           Agent defs      │  │ Time : 00:02:41                │ │
│  [K]  SKILLS           Skill registry  │  │ ────────────────────────────── │ │
│  [M]  MEMORY TOOLS     AI memory       │  │ [ RECENT ACTIVITY ]            │ │
│  [T]  SETTINGS         Config          │  │ 11:01:23  ✓ task-1 completed   │ │
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
        [N]    [S]   [R/P]  [V]   [D]   [A]   [K]   [M]   [T]
         │      │      │      │     │      │      │      │      │
         ▼      ▼      ▼      ▼     ▼      ▼      ▼      ▼      ▼
       Goal  Saved  Runs/  Valid  Est.  Agents Skills Memory  Settings
       Input Plans  Ledger Modal  Screen Screen Screen Tools  Screen
         │      │      │      │     │
         ▼      │      │      ▼     ▼
       Clarif  │      │   ValidRun  EstResult
         │      │      │
         ▼      │      ├──[I]──► RunDetails
       Effort   │      ├──[R]──► ResumeRetry (resume)
         │      │      ├──[Y]──► ResumeRetry (retry)
         ▼      │      ├──[P]──► DiffReview ──► PromoteConfirmDialog (modal)
       WsMode   │      ├──[L]──► (tab: title → LEDGER view, same data)
         │      │      └──[G]──► HitlGate
         ▼      │
      RunConfig │         ┌──[A]──► RunPlanScreen
         │      ▼         ├──[V]──► ValidateRunScreen
         ▼   PlanDetails ─┤──[D]──► RunPlanScreen (dry)
       PlanRun (wizard)   ├──[E]──► PlanEditor
                          ├──[S]──► (save copy, notify)
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
Wizard steps merged                 RESOLVED   ProviderSelectScreen +
(was 6 steps, now 5)                           GovernanceScreen merged into
                                               RunConfigScreen (step 4/5).
                                               Wizard depth: 6 screens.

[L] Ledger moved inside             RESOLVED   [L] Ledger removed from Home
RunLedgerScreen                                menu. RunLedgerScreen now has
                                               a [L] tab button + binding that
                                               switches the title in-place.
                                               [L] keybinding freed at Home.

DiffReview promote inlined          RESOLVED   DiffReview [P] now opens
                                               PromoteConfirmDialog (modal)
                                               inline.  ResumeRetryScreen is
                                               no longer used for promote.

ValidatePlanScreen renamed          RESOLVED   Class is now ValidatePlanDialog.
                                               ValidatePlanScreen kept as a
                                               backward-compat alias.  CSS
                                               selector updated accordingly.

PlanRunScreen vs RunPlanScreen      MEDIUM     PlanRunScreen = wizard execution
(naming confusion)                             RunPlanScreen = saved plan run
                                               Nearly identical purpose, different
                                               constructors.  Merge candidate
                                               for a future pass.

SavedPlans → PlanDetails →          LOW        3 levels deep before execution.
PlanInspect                                    Deep but each level adds value.
                                               Acceptable.

HitlGateScreen reachable only       LOW        Auto-push works during live runs.
from [G] in ledger or auto-push                Operator-triggered path added
(previously no manual path)                    (WP80). OK now.

PlanInspectScreen replaces 5        RESOLVED   Was 5 separate screens.  Now one
individual inspect screens                     tabbed screen with backward-compat
                                               aliases.  Correct direction.

ProviderSelectScreen/               INFO       Both kept as backward-compat
GovernanceScreen kept as aliases               aliases pointing to RunConfigScreen.
                                               Not a bug; useful for any code
                                               that already imports these names.
───────────────────────────────────────────────────────────────────────────────
```

---

## Key Bindings Quick Reference

```
HOME
  [N] New Plan        [S] Saved Plans     [R] Runs
  [V] Validate        [D] Dry Run         [P] Promote
  [A] Agents          [K] Skills          [M] Memory Tools
  [T] Settings        [Q] Quit
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
  [G] HITL Gate [L] Ledger tab           [Esc] Back

DIFF REVIEW
  [P] Promote (→ modal confirm)   [E] Export Patch   [C] Coord. Val.   [Esc] Back

PROMOTE CONFIRM DIALOG
  [Y] Confirm   [N] Cancel   [Esc] Cancel

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
                     RunConfigScreen
                     (+ legacy aliases: ProviderSelectScreen, GovernanceScreen)
_tui_wizard_run.py   PlanRunScreen
_tui_plans.py        SavedPlansScreen, PlanDetailsScreen, PlanEditorScreen
_tui_validate.py     ValidatePlanDialog (modal), ValidateRunScreen, RunPlanScreen
                     (+ alias: ValidatePlanScreen → ValidatePlanDialog)
_tui_ledger.py       RunLedgerScreen, RunDetailsScreen, ResumeRetryScreen
_tui_gate.py         HitlGateScreen
_tui_diff.py         DiffReviewScreen, PromoteConfirmDialog (modal)
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
