# TODO

## ReflectionUtil Cleanup

### RU-WP1 — Rename `isPlatformType` to `isUserDefinedType`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/util/ReflectionUtil.java:516`
- **Problem:** Method named `isPlatformType` returns `true` for non-JDK (user-defined) types — name is inverted relative to behaviour.
- **Work:** Rename to `isUserDefinedType`. Update call site in `isTraversableType` (L529). No logic change.
- **Risk:** Low — private method, single call site.

### RU-WP2 — Remove dead `extractQueryFields` method
- **File:** `pojo-lens/src/main/java/laughing/man/commits/util/ReflectionUtil.java:601`
- **Problem:** `extractQueryFields` has no callers anywhere in the codebase.
- **Work:** Delete method. Verify no reflection-based callers in tests.
- **Risk:** Low — confirmed dead by grep.

### RU-WP3 — Fix `DirectFieldReadPlan` silently skipping `final` fields
- **File:** `pojo-lens/src/main/java/laughing/man/commits/util/ReflectionUtil.java:410`
- **Problem:** `compileDirectFieldReadPlan` calls `findMutableField` which filters out `final` fields. POJOs with `final` primitive fields (e.g. `final double price`) are silently absent from chart fast paths — no error, wrong data.
- **Work:** Add a separate field lookup path in `compileDirectFieldReadPlan` that includes `final` non-static fields (read-only use, no write needed). Keep mutable-only path for write plans.
- **Risk:** Medium — need to ensure new read path doesn't bleed into write plan codepath.

### RU-WP4 — Reduce allocation in `collectFieldGraph` / `appendPath`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/util/ReflectionUtil.java:660`
- **Problem:** `appendPath` allocates a new `ArrayList` for every field visited during graph traversal. For a POJO with many fields or nested objects this creates O(n) short-lived lists.
- **Work:** Replace with an array-backed path stack passed by index/size through `collectFieldGraph`. Snapshot to immutable list only when a leaf is found and a `ResolvedFieldPath` is built.
- **Risk:** Medium — recursive traversal change, needs test coverage for nested and cyclic graphs.

### RU-WP5 — Consistent map type in `buildMutableFieldByNameMap`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/util/ReflectionUtil.java:487`
- **Problem:** Uses `HashMap` while all other ordered maps in the class use `LinkedHashMap`. Field iteration order is non-deterministic.
- **Work:** Change to `LinkedHashMap`. No behaviour change expected but field order becomes stable (matches declaration order).
- **Risk:** Low — internal cache, no contract on order exposed externally.
