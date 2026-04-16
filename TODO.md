# TODO

## ReflectionUtil Cleanup

### ~~RU-WP1 — Rename `isPlatformType` to `isUserDefinedType`~~ ✓ DONE
### ~~RU-WP2 — Remove dead `extractQueryFields` method~~ ✓ DONE
### ~~RU-WP3 — Fix `DirectFieldReadPlan` silently skipping `final` fields~~ ✓ DONE
### ~~RU-WP4 — Reduce allocation in `collectFieldGraph` / `appendPath`~~ ✓ DONE
### ~~RU-WP5 — Consistent map type in `buildMutableFieldByNameMap`~~ ✓ DONE

---

## FastArrayQuerySupport Cleanup

### ~~FA-WP1 — Replace stream usage in `canUseFastJoinPath`~~ ✓ DONE
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:44,155`
- **Problem:** Two `stream().findFirst().orElse(null)` calls on small maps inside a hot gate check. Stream overhead unnecessary.
- **Work:** Replace with direct map key iteration (`map.keySet().iterator().next()`).
- **Risk:** Low — private method, no logic change.

### ~~FA-WP2 — Reuse `visitingComputedNames` set in `compileJoinPlan`~~ ✓ DONE
- **File:** `pojo-lens/src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java:181,191,324`
- **Problem:** `addFieldReference` called with `new LinkedHashSet<>()` per field for `visitingComputedNames`. Allocates a fresh set on every field reference during plan compilation.
- **Work:** Allocate `visitingComputedNames` once in `compileJoinPlan` and pass it through. Clear between calls if needed.
- **Risk:** Low — compile-time only, well-contained.

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
