# Target 11: quality and testing

This target makes correctness, resilience, performance, and compatibility
measurable properties. BeeCode's hardest bugs cross boundaries—run result to
review, review to FSRS, review to achievement/outbox—so tests must exercise both
pure rules and real integrations.

## Test layers

| Layer | Principal purpose | Typical speed/cadence |
|---|---|---|
| Pure domain | State machines, dates, ratings, ranking, reducers | Very fast; every change |
| Contract | Runner/scheduler/repository/clock/network adapter semantics | Fast; every change |
| Persistence integration | Transactions, constraints, migrations, replay | Moderate; every change |
| Content verification | Schemas, references, deterministic pack, leakage | Moderate; content changes |
| Runner conformance | Python outcomes, limits, failure recovery | Moderate; every runner change |
| Server integration | Real PostgreSQL auth, authorization, idempotency | Moderate; server changes |
| UI component | States, semantics, focus, screenshots | Targeted; every change |
| Platform/instrumented | Android lifecycle/worker; desktop packaging | Slower; presubmit/nightly |
| End to end | A few critical user journeys across boundaries | Slowest; milestone/release |

Coverage is evaluated by critical behavior and failure scenarios, not one line
percentage.

## QLT-001 — Map every invariant to evidence

- **State:** proposed
- **Outcome:** each critical product invariant names its owner and test layer.
- **Deliverables:** traceability matrix from goals/ADRs to test suites, manual
  checks, benchmarks, or operation drills.
- **Acceptance:**
  - Exactly-once review, runner timeout, FSRS vectors, reference exclusion,
    5am Club, outbox/server dedupe, source non-upload, backup/restore, and
    authorization each have explicit evidence.
  - Manual-only evidence has reason and cadence.
  - Retired behavior removes/updates obsolete tests deliberately.
- **Evidence:** matrix reviewed at milestone gates.
- **Dependencies:** all target acceptance criteria.
- **Risks:** tests existing without proving the intended rule.
- **Non-goals:** mapping every cosmetic line of code.

## QLT-002 — Build deterministic fixtures

- **State:** proposed
- **Outcome:** test failures reproduce with controlled clocks, identities,
  randomness, content, and databases.
- **Deliverables:** fake clock, seeded ID/random providers, timezone corpus,
  FSRS vectors, Problem packs, review histories, server accounts/boards, and
  historical database/backup fixtures.
- **Acceptance:**
  - Repeated suite execution yields stable results.
  - Random/property failures print and preserve seed/counterexample.
  - Fixtures distinguish valid edge data from intentionally corrupt data.
  - Real personal source/history is never committed as a fixture.
- **Evidence:** repeat/stress run.
- **Dependencies:** ARCH-004.
- **Risks:** overmocked fixtures unlike real encodings/databases.
- **Non-goals:** eliminating every nondeterministic platform UI behavior.

## QLT-003 — Maintain a supported platform matrix

- **State:** proposed
- **Outcome:** “supported” means evidence exists for declared desktop and
  Android environments.
- **Deliverables:** OS/API/architecture/runtime/device matrix, presubmit subset,
  nightly/release expansion, owners, and retirement policy.
- **Acceptance:**
  - Every stable target has compile, smoke, install/upgrade, and runner evidence.
  - Android includes accelerated emulator and physical device.
  - Desktop includes declared Python/runtime packaging behavior.
  - A missing required environment blocks the claim instead of being recorded
    as pass.
- **Evidence:** machine-readable matrix attached to release.
- **Dependencies:** DSK-001, AND-001.
- **Risks:** unmaintainable combinatorial matrix.
- **Non-goals:** supporting environments not listed.

## QLT-004 — Set and calibrate performance budgets

- **State:** proposed
- **Outcome:** daily interaction stays responsive and storage/resource growth is
  bounded.
- **Deliverables:** named baseline hardware, benchmark harness, p50/p95 budgets,
  regression threshold, trend archive, and exception process.
- **Initial hypotheses to calibrate:**
  - editor input response below 50 ms p95;
  - due-queue query below 100 ms for 10,000 Problems;
  - final review transaction below 150 ms excluding Python execution;
  - desktop worker overhead below 500 ms warm / 1.5 s cold;
  - Android worker overhead below 1 s warm / 3 s cold;
  - cancellation cleanup within two seconds after grace period;
  - Leaderboard read below 300 ms p95 at expected small-community load;
  - no unbounded stdout, logs, outbox, cache, or run-history growth.
- **Acceptance:**
  - Every number names hardware, OS/API, build type, dataset, and sample method.
  - Main UI thread does not wait on Python/database/network work.
  - Regressions beyond threshold block or receive explicit time-bounded review.
  - Budgets are revised from evidence, not quietly ignored.
- **Evidence:** milestone benchmark report.
- **Dependencies:** RUN-011, AND-009, DATA-010.
- **Risks:** false precision before prototypes.
- **Non-goals:** optimizing synthetic scores that do not affect use.

## QLT-005 — Prove FSRS and review conformance

- **State:** proposed
- **Outcome:** scheduler math and review policy cannot drift across targets or
  refactors.
- **Deliverables:** engine golden vectors, adapter contract, state-machine
  cases, random review histories, migration simulations, and cross-target suite.
- **Acceptance:**
  - Every rating, new/review/same-day/lapse/long interval, and bounds are covered.
  - Android/desktop adapter outputs match within justified tolerance.
  - Random history rebuild equals incremental state.
  - Duplicate/concurrent finalization cannot create second transition.
- **Evidence:** frozen vector/version report.
- **Dependencies:** SRS-008, SRS-011.
- **Risks:** tests only confirming the same faulty implementation.
- **Non-goals:** reproducing all upstream application policy.

## QLT-006 — Prove content quality gates

- **State:** proposed
- **Outcome:** invalid/inconsistent/leaking Problems cannot enter a release pack.
- **Deliverables:** schema mutation suite, Python compile/reference run, codec/
  comparator properties, path/render security cases, deterministic build, and
  archive denylist.
- **Acceptance:**
  - Each validator failure class has a negative fixture.
  - Reference mutation testing or equivalent catches ineffective test suites at
    a chosen threshold.
  - Repeated pack builds are deterministic under policy.
  - `reference.py`, caches, secrets, and tooling fixtures are absent.
- **Evidence:** content release report.
- **Dependencies:** PROB-006 through PROB-008.
- **Risks:** green reference test despite poor coverage.
- **Non-goals:** automatically proving educational quality.

## QLT-007 — Run cross-platform Python conformance and adversarial tests

- **State:** proposed
- **Outcome:** semantic outcomes and recovery behavior match while platform
  capability differences remain visible.
- **Deliverables:** solution corpus for pass/wrong/syntax/runtime/contract/
  timeout/cancel/resource/worker failure, state-contamination tests, and
  capability attack suite.
- **Acceptance:**
  - Desktop and Android agree on semantic result for every supported fixture.
  - Infinite loop, recursion, output flood, huge value, process/network/path/
    environment attempts finish within declared behavior.
  - Repeated runs do not share unexpected module/global state.
  - Worker failure never emits finalized review.
- **Evidence:** matrix report by runtime/provider/version.
- **Dependencies:** RUN-004 through RUN-012.
- **Risks:** capability differences hidden by overly abstract assertions.
- **Non-goals:** equal low-level traceback text on every platform.

## QLT-008 — Test reliability through fault injection

- **State:** proposed
- **Outcome:** failures at transaction/protocol/lifecycle/storage/network stages
  have known safe outcomes.
- **Deliverables:** failure injection hooks and scenarios for process death,
  worker death, full disk, corrupt DB, cancelled run, server outage, partial
  batch, token expiry, migration interruption, and clock change.
- **Acceptance:**
  - Each failure maps to expected durable state and recovery action.
  - No scenario loses committed source/review or duplicates side effects.
  - Repeated recovery is idempotent.
  - Safe mode never mutates canonical data before validation/backup.
- **Evidence:** automated suite plus a release chaos script.
- **Dependencies:** DATA-009, RUN-012, AND-006.
- **Risks:** test hooks changing behavior.
- **Non-goals:** random production chaos.

## QLT-009 — Test the server against real PostgreSQL

- **State:** proposed
- **Outcome:** SQL constraints, transactions, timezones, and auth behavior are
  verified on the actual database.
- **Deliverables:** containerized integration environment, migration tests,
  seeded capabilities/manifests, auth/authorization, ingestion, ranking, award,
  deletion, and restore cases.
- **Acceptance:**
  - In-memory database substitutes are not the only server evidence.
  - Unique constraints prove event idempotency under concurrency.
  - Every owner/member/nonmember permission path is tested.
  - Today/week/DST/tie queries use real PostgreSQL.
  - Backup restores into clean current service.
- **Evidence:** server integration and restore reports.
- **Dependencies:** LDB-001 through LDB-012.
- **Risks:** container tests being too slow for normal work.
- **Non-goals:** large-scale load before usage warrants it.

## QLT-010 — Maintain UI regression and accessibility evidence

- **State:** proposed
- **Outcome:** core states remain legible/operable across themes, sizes, inputs,
  and assistive technology.
- **Deliverables:** screenshot/golden set, semantic assertions, focus tests,
  keyboard scripts, TalkBack/screen-reader scripts, text scaling, contrast, and
  reduced-motion cases.
- **Acceptance:**
  - Loading/empty/offline/error/running/passed/failed/finalized states are
    represented.
  - Desktop review completes keyboard-only.
  - Android primary loop completes with TalkBack/manual script.
  - 200% scaling retains critical controls.
  - Visual diffs require human review, not automatic approval.
- **Evidence:** UI/accessibility report.
- **Dependencies:** UX-002, DSK-005, AND-011.
- **Risks:** flaky snapshots becoming ignored.
- **Non-goals:** pixel-identical rendering across OSes.

## QLT-011 — Define nonwaivable release blockers

- **State:** proposed
- **Outcome:** deadline pressure cannot normalize defects that lose data,
  duplicate reviews, hang runners, bypass auth, leak code/secrets, or break
  restore/signing.
- **Deliverables:** severity rubric, blocker list, waiver authority for lower
  severities, expiry/owner, and release checklist automation.
- **Acceptance:**
  - Data loss/corruption, duplicate finalized review/award, runner unbounded
    hang, auth/authorization bypass, sensitive upload/logging, broken restore,
    and invalid signing block stable release.
  - Missing required platform/device evidence blocks support claim.
  - Waivers are visible, time-limited, and cannot redefine severity.
- **Evidence:** dry-run release with injected blockers.
- **Dependencies:** SEC-012, OPS-010.
- **Risks:** calling severe defects “known limitations”.
- **Non-goals:** zero defects.

## QLT-012 — Run human acceptance journeys

- **State:** proposed
- **Outcome:** a person other than the implementer proves the product's main
  workflows and documentation.
- **Deliverables:** scripts and evidence for:
  1. install, solve, pass, rate, restart, see due state;
  2. fail and finalize Again without social solved count;
  3. work offline and later sync without duplicates;
  4. process death during edit/run/finalization;
  5. earn 5am Club under injected dates exactly once;
  6. create/join private Leaderboard with two accounts;
  7. author/validate/package one new Problem folder;
  8. export, reset/clean install, and restore;
  9. delete account without deleting local study data.
- **Acceptance:**
  - Scripts use release artifacts and public documentation.
  - Observed friction/ambiguity is recorded, not coached around.
  - Source/privacy consent is obtained before collecting diagnostics.
- **Evidence:** signed milestone acceptance record.
- **Dependencies:** all vertical slices.
- **Risks:** one happy-path tester.
- **Non-goals:** replacing automated regression.

## Quality exit gate

The declared platform matrix, critical invariant traceability, performance
budgets, accessibility scripts, fault scenarios, and human journeys must all
have current evidence. A compile-only Android result or desktop-only runner
result cannot satisfy cross-platform support.

