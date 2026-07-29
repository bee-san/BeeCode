# ADR 0003 — One persistence implementation for desktop and Android

- Status: accepted
- Date: 2026-07-29
- Constrains `docs/architecture.md` ("database drivers" as a platform adapter)

## Context

The architecture listed database drivers among the platform adapters, and named
SQLDelight as the leading candidate for a shared schema with per-platform
drivers. That anticipated Android needing `android.database.sqlite` while desktop
used JDBC — two drivers, one schema, and a conformance risk between them.

Checking the artifact rather than assuming: `org.xerial:sqlite-jdbc` 3.50.1.0
bundles native libraries under `org/sqlite/native/Linux-Android/` for `x86`,
`x86_64`, `arm`, and `aarch64` — every ABI BeeCode targets, including the
`x86_64` emulator and `arm64-v8a` physical-device evidence the plan requires.

## Decision

Desktop and Android use **the same** `persistence` module, the same JDBC driver,
and the same SQL. There is no Android-specific database implementation and no
platform driver abstraction in v1.

## Why this is the right trade

The plan's hardest persistence requirement is not performance, it is that
*desktop and Android agree on review and FSRS semantics* (an explicit M3 gate).
Two drivers make that a property to be tested continuously. One driver makes it
true by construction — there is no second implementation that could drift.

It also removes the SQLDelight dependency and its code generation from the build
for now. The repository contract is hand-written SQL behind Kotlin repositories,
which is more code than SQLDelight would generate but keeps the transaction
semantics that matter — `BEGIN IMMEDIATE`, the schedule-version compare-and-swap,
`synchronous = FULL` — visible and directly testable rather than expressed
through a generator.

## Consequences

- The Android APK carries the JDBC driver's native libraries. This is real APK
  size, and it is the price of semantic identity.
- `android.database.sqlite` conventions (`SQLiteOpenHelper`, cursors, Room) do
  not apply. Android-specific tooling that expects them will not see this
  database.
- Android's default temp directory must be set for the driver to extract its
  native library. The Android entry point does this before opening the database;
  the failure mode otherwise is an `UnsatisfiedLinkError` at first open.
- The exactly-once finalization tests, the migration tests, and the
  replay-equals-incremental test cover both platforms at once, because there is
  only one implementation to cover.

## What would reverse this

- The bundled native library failing on a real `arm64-v8a` device rather than
  only the emulator.
- APK size becoming a genuine constraint, at which point the repository contract
  is already the seam a platform driver would slot behind.
- A need for Android-platform database features — encrypted storage via
  SQLCipher's Android integration, or backup APIs that expect the platform
  database.

The repository interfaces are the abstraction boundary, so reversing this means
adding a second implementation behind them, not reworking callers.
