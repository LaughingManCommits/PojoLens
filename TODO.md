# TODO

## ReflectionUtil Cleanup

### ~~RU-WP1 — Rename `isPlatformType` to `isUserDefinedType`~~ ✓ DONE
### ~~RU-WP2 — Remove dead `extractQueryFields` method~~ ✓ DONE
### ~~RU-WP3 — Fix `DirectFieldReadPlan` silently skipping `final` fields~~ ✓ DONE
### ~~RU-WP4 — Reduce allocation in `collectFieldGraph` / `appendPath`~~ ✓ DONE
### ~~RU-WP5 — Consistent map type in `buildMutableFieldByNameMap`~~ ✓ DONE

---

## FastArrayQuerySupport Cleanup

### FA-WP1 — Replace stream usage in `canUseFastJoinPath`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:44,155`
- **Problem:** Two `stream().findFirst().orElse(null)` calls on small maps inside a hot gate check. Stream overhead unnecessary.
- **Work:** Replace with direct map key iteration (`map.keySet().iterator().next()`).
- **Risk:** Low — private method, no logic change.

### FA-WP2 — Reuse `visitingComputedNames` set in `compileJoinPlan`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:181,191,324`
- **Problem:** `addFieldReference` called with `new LinkedHashSet<>()` per field for `visitingComputedNames`. Allocates a fresh set on every field reference during plan compilation.
- **Work:** Allocate `visitingComputedNames` once in `compileJoinPlan` and pass it through. Clear between calls if needed.
- **Risk:** Low — compile-time only, well-contained.

### FA-WP3 — Reuse child row buffer in `buildChildIndex`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:423`
- **Problem:** `readFlatRowValues(child, plan.childReadPlan())` allocates a new `Object[]` per child row. Parent side (L76) correctly reuses a buffer — child side doesn't get the same treatment.
- **Work:** Pre-allocate a single `childValues` buffer outside the loop; use the `readFlatRowValues(bean, plan, target, offset)` overload. Copy into a fresh array only when storing into the index.
- **Risk:** Medium — must ensure stored arrays are independent copies, not the reused buffer.

### FA-WP4 — Replace `HashMap` with `LinkedHashMap` in `buildChildIndex`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:418,439,452`
- **Problem:** `buildChildIndex` uses `HashMap` for the hash index, inconsistent with the rest of the codebase which uses `LinkedHashMap`.
- **Work:** Change to `LinkedHashMap`. No behaviour change — this is a lookup structure.
- **Risk:** Low — internal structure, no order contract exposed.

### FA-WP5 — Delete dead 3-arg `orderRows` overload
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:688`
- **Problem:** Private 3-arg `orderRows` overload has no call sites — only the 4-arg version is used.
- **Work:** Delete the 3-arg overload.
- **Risk:** Low — confirmed dead by grep.

### FA-WP6 — Clarify `matchesRuleGroups` AND logic variable names
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:634`
- **Problem:** `andMatched` tracks "any AND rule passed" but the real gate is `andFailed`. The name implies "all AND rules matched" which it does not. Misleading for future maintenance.
- **Work:** Rename `andMatched` → `andAnyPassed`, `andFailed` → `andAnyFailed`. Add a clarifying comment on the final condition.
- **Risk:** Low — rename only, no logic change.
