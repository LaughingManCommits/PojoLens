# Agent Invariants

- Repository type: single-module Maven library that packages as a `jar`, not a deployable service.
- API stability: do not rename public packages, remove exported classes, or change public signatures without explicit versioning intent.
- Source of truth: prefer runtime code, then build and CI config, then tests; documentation and `/ai` must follow them.
- Build integrity: keep the existing Maven and CI flow buildable and compatible with the current packaging.
- Scope: do not add unrelated services, runtime infrastructure, or extra subsystems.
