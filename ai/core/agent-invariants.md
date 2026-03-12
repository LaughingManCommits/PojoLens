# Agent Invariants

These rules must never be violated by automated changes.

If a task conflicts with an invariant, the agent must stop and explain the conflict.

---

## Repository Type

This repository produces a **library JAR**, not a deployable service.

Agents must not:

- add runtime service infrastructure
- introduce service startup logic
- convert the project into an application

---

## Public API Stability

Public APIs exposed by this library must remain stable.

Agents must not:

- rename public packages
- remove exported classes
- change method signatures without explicit version bump

---

## Source of Truth

Implementation truth comes from:

1. runtime code
2. build configuration
3. tests

Documentation must align with these sources.

---

## Build Integrity

The project must remain buildable using the existing build system.

Agents must not:

- introduce incompatible build plugins
- break CI configuration
- change artifact packaging type

---

## Repository Scope

This repository represents **a single library module**.

Agents must not introduce unrelated subsystems or services.