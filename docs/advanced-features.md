# Advanced Features Guide

This page collects the public PojoLens features that are useful after the core
query path is already in place.

Start with these first:

- [README.md](../README.md)
- [entry-points.md](entry-points.md)
- [usecases.md](usecases.md)
- [reusable-wrappers.md](reusable-wrappers.md)

Those documents cover the default adoption path.
The features below are optional follow-on surfaces for runtime policy,
diagnostics, testing, integration, and build-time tooling.

## Runtime Integration And Policy

Use these when query behavior or wiring needs to vary by environment, tenant, or
application framework:

- `PojoLensRuntime` and runtime presets:
  [entry-points.md](entry-points.md), [sql-like.md](sql-like.md)
- Spring Boot starter/autoconfigure modules:
  [modules.md](modules.md)
- Cache policy tuning:
  [caching.md](caching.md)
- Telemetry hooks:
  [telemetry.md](telemetry.md)

## Diagnostics And Guardrails

Use these when you need operational visibility or stricter query hygiene:

- `explain()` and SQL-like diagnostics:
  [sql-like.md](sql-like.md)
- Lint mode and strict parameter typing:
  [sql-like.md](sql-like.md)
- Benchmark and threshold tooling:
  [benchmarking.md](benchmarking.md)

## Regression And Snapshot Tooling

Use these when you need safety rails around changing query behavior:

- Query regression fixtures and fluent/SQL parity helpers:
  [regression-fixtures.md](regression-fixtures.md)
- Snapshot comparison and delta-row analysis:
  [snapshot-comparison.md](snapshot-comparison.md)

## Authoring And Build-Time Tooling

Use these when you want stronger typed authoring support or build-time
verification:

- Field metamodel generation:
  [metamodel.md](metamodel.md)
- Benchmark runner and threshold checks:
  [benchmarking.md](benchmarking.md)

## Stability Note

Advanced does not mean internal.
Some of these surfaces are public and stable, but they are still not the default
first-read product story.

Compatibility expectations for stable versus advanced APIs are defined in
[public-api-stability.md](public-api-stability.md).

