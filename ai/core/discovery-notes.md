# Discovery Notes

- Verified: `/ai` existed only as the bootstrap prompt before this memory scaffold was created.
- Verified: The repository is small enough that core code, tests, CI, and all markdown docs were scanned directly during bootstrap.
- Verified: `TODO.md` is the main active-planning file; no separate roadmap/backlog document was found.
- Verified: No `TODO`/`FIXME`/`HACK`/`XXX` markers were found across `src/main/java`, `src/test/java`, and the main docs outside the dedicated `TODO.md`.
- Verified: `target/` already existed and contains generated outputs; it should not be treated as the primary source of truth.
- Verified: The project currently builds and tests successfully on this workspace using Java `17.0.10` and Maven `3.8.1`.
- Inferred: Product docs are maintained alongside tests, but release/process docs lag behind feature work more often than code-backed reference docs do.
- Unknown: No publication workflow or release automation beyond Git tags/checklists was found.
