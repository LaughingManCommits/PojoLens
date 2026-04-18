# TODO

## DOC-WP1: Fix stale version references

**Priority:** High
**Files:** `docs/public-api-stability.md`, `ai/core/repo-purpose.md`, `docs/benchmarking.md`

Tasks:
- [ ] `public-api-stability.md`: Remove or rewrite "pre-first-release" conditional sections - the first public `release-*` tag has already shipped. Replace with present-tense post-release policy throughout.
- [ ] `repo-purpose.md`: Update version string after next release is cut; consider referencing pom.xml as source of truth rather than embedding the version in prose.
- [ ] `benchmarking.md`: Replace all hardcoded `2026.03.28.1919` jar filenames (~15 occurrences) with a variable or glob pattern (e.g. `target/*-benchmarks.jar`), consistent with the "do not hardcode a specific dated release filename" guidance already in that doc.

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP2: Fix entry-point omissions in summary sections

**Priority:** High
**Files:** `README.md`, `docs/usecases.md`, `docs/modules.md`

Tasks:
- [ ] `README.md` - `API Entry Points` section: add `PojoLensCsv` entry (currently listed in "Pick A Path" table but absent from the summary).
- [ ] `docs/usecases.md` - Section 7 "Default Calls": add `PojoLensNatural` default call (present in Section 1 path-selection table but absent from the defaults summary).
- [ ] `docs/modules.md` - "Public Runtime Layering" section: add `PojoLensNatural` entry (the third first-class query entry point is missing from this list alongside `PojoLensCore` and `PojoLensSql`).

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP3: Remove process text from user-facing docs

**Priority:** Medium
**Files:** `docs/reusable-wrappers.md`, `docs/product-surface.md`

Tasks:
- [ ] `reusable-wrappers.md` - "Overlap And Disposition" section: remove the final two sentences ("No wrapper is a current deprecation candidate. Further wrapper reduction remains a pre-first-release product decision, not a compatibility constraint.") - these are internal planning notes, not user guidance.
- [ ] `product-surface.md` - "Follow-On Work" section: remove entirely, or replace with a single sentence pointing to `public-api-stability.md` for stability guarantees.

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP4: Align product family names

**Priority:** Medium
**Files:** `README.md`, `docs/product-surface.md`

The README "Product Shape" section uses different family names than the canonical `product-surface.md`:

| README label | Canonical (`product-surface.md`) |
|---|---|
| `Workflow helpers` | `Workflow helper` |
| `Runtime integration` | `Integration` |
| `Advanced and tooling` | `Advanced` + `Tooling` (two families) |

Tasks:
- [ ] Decide canonical names and update the non-canonical file to match. Preferred: keep `product-surface.md` as the authority and update README to match.

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP5: Fix caching.md broken sentence and maxWeight clarity

**Priority:** Medium
**File:** `docs/caching.md`

Tasks:
- [ ] Fix broken sentence at lines 3-5: move the two bullet-list items (`SQL-like parse cache`, `stats-plan cache`) to immediately follow the colon, before the "This is an advanced policy-tuning surface." line.
- [ ] Clarify `maxWeight=0` inline comment in the defaults table: change `(disabled)` to `(count-based eviction via maxEntries)` to avoid implying eviction is off.

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP6: Consolidate natural.md Non-goals / Limitations overlap

**Priority:** Medium
**File:** `docs/natural.md`

Tasks:
- [ ] Remove "free-form SQL window grammar beyond the supported natural window phrases" from the "Non-goals" section - it duplicates what is already covered in "Current Limitations". Keep it only in Limitations as a technical scope boundary.
- [ ] Verify remaining Non-goals are design-intent statements, not technical scope gaps (those belong in Limitations).

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP7: Add natural query diagnostics pointer to advanced-features.md

**Priority:** Medium
**File:** `docs/advanced-features.md`

Tasks:
- [ ] In the "Diagnostics And Guardrails" section, add a bullet for natural query `explain()` pointing to `natural.md`. Currently the section only references `sql-like.md` for explain and lint mode, leaving natural query diagnostics undiscoverable from this guide.

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP8: Add cross-references to thin docs

**Priority:** Low
**Files:** `docs/snapshot-comparison.md`, `docs/regression-fixtures.md`, `docs/tabular-schema.md`

Tasks:
- [ ] `snapshot-comparison.md`: add `## See Also` section pointing to `reports.md`, `charts.md`, `entry-points.md`.
- [ ] `regression-fixtures.md`: add `## See Also` section pointing to `entry-points.md`, `sql-like.md`, `reports.md`.
- [ ] `tabular-schema.md`: add `## See Also` section pointing to `reports.md`, `stats-presets.md`, `entry-points.md`.

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP9: Minor README fixes

**Priority:** Low
**File:** `README.md`

Tasks:
- [ ] Fix double "and" in Capability Snapshot -> Workflow helpers bullet: "tree row shaping, and tabular schema metadata" -> "tree row shaping, and tabular schema metadata" (remove one "and").
- [ ] Clarify `StatsViewPreset / StatsTable` row in "Pick A Path" table: note that `StatsTablePayload` is the projection-free dashboard variant, separate from the typed `StatsTable<T>`.

Validate: `scripts/check-doc-consistency.ps1`

---

## DOC-WP10: Add docs/ landing file

**Priority:** Low
**File:** `docs/README.md` (new)

Tasks:
- [ ] Create a thin `docs/README.md` that orients users who land directly in the `docs/` folder on GitHub. One sentence pointing to the root README's Documentation Map section is sufficient.

Validate: `scripts/check-doc-consistency.ps1`
