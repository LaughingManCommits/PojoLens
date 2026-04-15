# TODO

## ReflectionUtil Cleanup

### ~~RU-WP1 — Rename `isPlatformType` to `isUserDefinedType`~~ ✓ DONE
### ~~RU-WP2 — Remove dead `extractQueryFields` method~~ ✓ DONE
### ~~RU-WP3 — Fix `DirectFieldReadPlan` silently skipping `final` fields~~ ✓ DONE
### ~~RU-WP5 — Consistent map type in `buildMutableFieldByNameMap`~~ ✓ DONE

### RU-WP4 — Reduce allocation in `collectFieldGraph` / `appendPath`
- **File:** `pojo-lens/src/main/java/laughing/man/commits/util/ReflectionUtil.java:660`
- **Problem:** `appendPath` allocates a new `ArrayList` for every field visited during graph traversal. For a POJO with many fields or nested objects this creates O(n) short-lived lists.
- **Work:** Replace with an array-backed path stack passed by index/size through `collectFieldGraph`. Snapshot to immutable list only when a leaf is found and a `ResolvedFieldPath` is built.
- **Risk:** Medium — recursive traversal change, needs test coverage for nested and cyclic graphs.
