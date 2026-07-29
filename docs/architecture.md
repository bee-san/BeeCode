# BeeCode high-level architecture

## Architectural intent

BeeCode is one offline-first study product with two first-class clients.
Android and desktop share Problem semantics, review workflow, FSRS behavior,
achievement rules, persistence contracts, and the optional Leaderboard
protocol. Platform-specific adapters own Python execution, application
lifecycle, packaging, credentials, and any UI component that cannot meet the
shared behavior well.

The central boundary is:

> Local study is authoritative. Social state is an optional projection.

Opening Problems, editing source, running tests, finalizing reviews, scheduling
with FSRS, earning local achievements, inspecting history, and exporting data
must work without an account or network.

Android and desktop implement the same semantics, but v1 keeps independent
local profiles. Moving a profile is a deliberate backup/export-import operation;
the Leaderboard is not live study-history or source-code sync.

## System topology

```mermaid
flowchart TD
    subgraph Client["BeeCode client"]
        App["Shared UI + use cases"]
        Domain["Domain ports + events"]
        Local["SQLite adapter"]
        Platform["Platform adapters"]
    end
    Social["Optional Leaderboard API"]
    App --> Domain
    Local -. "implements" .-> Domain
    Platform -. "implements" .-> Domain
    Platform -. "minimal activity outbox" .-> Social
```

Platform adapters include:

- FSRS mapping to the generic engine;
- desktop and Android Python runners;
- database drivers and secure credential storage;
- clocks, files, notifications, and networking.

The domain does not import Compose, Android, desktop, SQL, HTTP, Python-provider,
or server framework classes.

## Planned repository modules

```text
BeeCode/
├── androidApp/       Android entry point, lifecycle, credentials, Python adapter
├── desktopApp/       Desktop entry point, packaging, process runner
├── shared/           KMP shared UI and application services
├── domain/           Pure models, commands, events, and state machines
├── persistence/      Local schema, repositories, transactions, migrations
├── fsrs-adapter/     BeeCode policy around the external bee-fsrs package
├── python-api/       Platform-neutral execution contracts
├── protocol/         Versioned Leaderboard DTOs
├── server/           Ktor/PostgreSQL modular monolith
├── content/          Problem packs and achievement definitions
└── tools/            Problem generator/compiler/validator
```

The exact number of Gradle modules may be smaller in the first walking
skeleton. The dependency rules are more important than the physical split.

## Recommended client technology direction

- Compose Multiplatform for shared Android/desktop rendering and application
  logic.
- Separate Android application and desktop JVM entry modules.
- A shared KMP domain/application layer with platform adapter interfaces.
- SQLite as local authority, with SQLDelight as the leading shared schema/
  migration candidate.
- User's Kotlin FSRS 7 engine extracted with provenance from
  [`bee-san/kanji_anki`](https://github.com/bee-san/kanji_anki) into the
  independently versioned `bee-san/bee-fsrs` GitHub repository/package. BeeCode
  pins its released artifact and wraps it with a BeeCode-owned scheduler
  adapter.
- Platform-neutral `PythonRunner`; out-of-process worker on desktop and an
  isolated Android service/process if the provider spike proves the boundary.
- Ktor/PostgreSQL/Docker Compose/Caddy for the optional self-hosted server.

The researched initial toolchain pins and AGP 9 module constraints are recorded
in [the architecture goals](../goals/01-architecture-foundations.md). They are a
compatibility set to validate in a walking skeleton, not implementation already
performed.

## Problem content architecture

A Problem is source-controlled content:

```text
content/packs/core/problems/two-sum/
├── problem.yaml
├── statement.md
├── starter.py
├── tests.yaml
├── explanation.md
├── reference.py
└── assets/
```

Tooling discovers folders automatically. Adding a Problem never requires a
central Kotlin registry.

Build-time tooling:

1. validates the versioned schema, paths, provenance, assets, limits, codecs,
   and comparator IDs;
2. compiles starter/reference Python;
3. runs the trusted reference against all declared tests;
4. generates one canonical runtime representation and index;
5. produces a deterministic `.beecodepack`;
6. includes revealable `explanation.md` as non-executable content;
7. proves `reference.py` and tooling-only files are absent.

Client packs contain data only. They select trusted built-in codecs/comparators
by versioned ID and cannot introduce arbitrary executable judge logic.

## Core domain entities

| Entity | Responsibility |
|---|---|
| `ProblemDefinition` | Immutable compiled Problem content and revision/hash. |
| `SolutionDraft` | Local source, starter base, edit revision, and reveal state. |
| `ExecutionRun` | Bounded local attempt and finite typed result. |
| `ReviewSession` | Explicit started/working/passed/revealed/finalized lifecycle. |
| `ProblemReviewFinalized` | Every finalized outcome, selected run/source, rating, and schedule audit. |
| `ProblemSchedule` | Materialized FSRS state and next due decision. |
| `ProblemSolved` | Derived once only from an eligible unaided passing session. |
| `AchievementAward` | Immutable, idempotent local accomplishment. |
| `SocialOutboxEvent` | Minimal eventually delivered activity metadata. |

Stable IDs include Problem/revision, execution run, review session, domain
event, achievement definition, account, device, and Leaderboard.

## Review transaction

```mermaid
sequenceDiagram
    participant UI as BeeCode UI
    participant Run as Python runner
    participant App as Review use case
    participant FSRS as FSRS adapter
    participant DB as Local database
    UI->>Run: Execute immutable source snapshot
    Run-->>UI: Typed test result
    UI->>App: Finalize selected run/source + rating
    App->>DB: Begin write; read schedule + version
    DB-->>App: Authoritative previous state
    App->>FSRS: Previous state + elapsed days + rating
    FSRS-->>App: Next state + interval
    App->>DB: CAS review + schedule + events (+ outbox if social enabled)
    DB-->>UI: Existing or new finalized outcome
```

The final database transaction:

1. checks the `reviewSessionId` has not finalized;
2. verifies the selected run/source snapshot and authoritative Problem schedule
   projection version;
3. calculates the fast pure FSRS transition inside the write transaction;
4. appends `ProblemReviewFinalized` and `ProblemSolved` when eligible;
5. persists the full recorded FSRS transition/current schedule;
6. inserts a minimal social event into the outbox when already account-linked;
7. marks the session finalized.

It commits all core effects or none. A retry returns the existing result; a
different stale session receives a schedule-version conflict. Achievement
projection runs immediately after commit in a separate idempotent transaction
and catches up after restart, so reducer failure cannot block study. A test run
alone never mutates FSRS, achievements, or Leaderboards.

## FSRS boundary

The user's versioned `bee-fsrs` package owns FSRS 7 memory mathematics:

- initial state;
- retrievability;
- next difficulty/stability;
- next interval;
- complete review calculation.

BeeCode owns:

- which ratings are allowed from pass/fail/reveal evidence;
- review-session idempotency;
- due-queue ordering and daily limits;
- parameter/version migration;
- persistence, clocks, explanation, and history.

Every schedule transition records the FSRS algorithm ID, `bee-fsrs` package
version/checksum and source commit, previous-state hash, elapsed/rating inputs,
immutable 21-value parameter set/hash, resulting memory state, interval, and due
decision.
Operational rebuild folds those recorded outputs; historical recomputation is
an integrity check only while the exact old implementation remains available.
Golden vectors run through both platform compositions before an upgrade can
change schedules. v1 has no separate minute-based learning ladder.

## Python execution boundary

Both platforms implement:

```text
RunRequest
- run and Problem revision IDs
- immutable source
- trusted entry point, codecs, comparators, and tests
- timeout/output/resource limits
- contract and harness versions

RunResult
- finite outcome kind
- bounded per-test results and diagnostics
- timing/truncation/resource flags
- runner and Python versions
```

Desktop direction:

- `UI → supervisor control channel → disposable learner CPython child`;
- framed structured control protocol separate from learner stdout/stderr and
  without shell interpolation;
- fresh constrained temporary workspace;
- clean environment, deadline, output/resource caps, process-tree cleanup;
- OS-specific containment with a visible capability level.

Android direction:

- test a pinned embedded runtime in ordinary remote and isolated processes;
- target no network permission or access to main-process credentials/storage,
  but treat both as unproven until the isolation spike passes;
- UI-enforced deadline and worker termination/recreation;
- `arm64-v8a` physical device and `x86_64` emulator evidence.

The Android spike is a go/no-go gate: if the provider cannot operate behind an
honest isolation boundary, follow the explicit fallback ladder: isolated
service, separate signature-bound no-permission runner APK, same-UID crash
isolation labelled “trusted code only”, or reject Android execution until an
acceptable boundary exists. It must test provider extraction/import behavior,
Binder pipes/limits, GIL-bound termination, filesystem/socket/Java/keystore/
environment access, and Logcat leakage. Do not call same-process execution a
sandbox.

## Achievement architecture

Canonical events feed deterministic, versioned reducers. Definitions are data,
but evaluator kinds are trusted code. Progress is rebuildable; awards are
immutable/idempotent.

5am Club is normative:

- source: eligible finalized `ProblemSolved`;
- official suite passed; no packaged explanation/solution or prior-source
  reveal;
- local window `[00:00, 06:00)`;
- seven distinct consecutive local dates;
- one qualifying contribution per date;
- first qualifying event starts an epoch with locked `streakZoneId`;
- every event in that epoch is converted from canonical UTC using the locked
  zone; after a missing locked-zone date, a new qualifying event starts an epoch
  with the then-current profile zone;
- progress is recomputed from ordered qualifying UTC events, so late events do
  not corrupt an increment-only counter;
- UTC instant, profile zone, and derived local audit values remain auditable;
- immediate local award; independent friendly-trust server acceptance for a
  public title.

## Leaderboard architecture

Private custom Leaderboards are the planned social model. The service owns
accounts, membership/invites, accepted social activity, aggregate ranks, and
server-accepted awards.

The client uploads each account-global idempotent activity event once, without a
board ID. Network delivery is at-least-once; a durable ingestion ledger and
server unique constraints produce one social effect. Board queries include only
events in the current membership episode: no pre-join backfill, leaving hides
the row, and rejoining starts a new board-local score. Reviews from before
account linking are not backfilled in v1.

Rank periods derive dates server-side from UTC using the board's immutable v1
timezone/week start. Rows show:

`rank · avatar · display name + equipped title · Problems · streak`

The server is a modular monolith:

```text
Caddy/TLS → Ktor API → PostgreSQL
```

It needs no Redis, broker, object store, WebSockets, or microservices in v1.

## Security and privacy boundary

The Leaderboard never receives:

- learner source or draft history;
- stdout/stderr or expected/actual values;
- detailed test/run transcript;
- FSRS state, rating, interval, due date, or parameters;
- local backup/database content.

Runner workers never receive:

- account/refresh tokens;
- Leaderboard URL/configuration;
- local database path;
- arbitrary host environment or solution history.

Backups may contain source, so export UI and optional encryption must treat them
as sensitive. Logs and support bundles are allowlisted/redacted and previewed.

## Highest-risk decision gates

1. Android Python provider works behind a killable isolation boundary.
2. Each supported desktop OS has an honestly testable containment level.
3. The editor is usable with Android IMEs and desktop keyboard/accessibility.
4. Local database transaction and migration behavior survives process failure.
5. Problem packs are deterministic and exclude reference source.
6. FSRS golden vectors agree across targets.
7. 5am Club passes timezone/DST/boundary/replay tests.
8. Duplicate offline social uploads count once and contain no sensitive fields.
9. Self-host deployment restores from backup on a clean environment.

The full acceptance criteria, dependencies, risks, and tests are in
[`goals/`](../goals/README.md); the realistic first-year cut and sequencing are
in [the year-one execution plan](../goals/YEAR-ONE.md).
