# Target 07: achievements

This target adds deterministic, local-first motivation. Achievements consume
canonical study events; they never infer progress from page views, run-button
presses, or duplicated sync receipts.

## Achievement architecture

Definitions are declarative content, but evaluation is performed by a finite
set of trusted, versioned reducer kinds. BeeCode does not embed a general
scripting language for achievements.

```text
content/achievements/
├── first-solution.yaml
├── consistency-week.yaml
├── topic-breadth.yaml
├── lapse-recovery.yaml
└── five-am-club.yaml
```

A definition describes:

- stable achievement ID and definition version;
- title, description, artwork/visibility metadata;
- event source and trusted evaluator kind;
- parameters such as threshold, distinct key, date window, or topic set;
- progress display policy;
- reward such as an equippable profile title.

The evaluator code—not YAML—defines legal predicates and state transitions.

## Canonical local events

`ProblemReviewFinalized` is emitted for every finalized session. It records the
run outcome, whether the official suite passed, reveal/assistance state, rating,
selected run/source-snapshot IDs, UTC instant, profile timezone at finalization,
derived local date/time, Problem revision, and stable event/session IDs. It
drives non-qualification explanations and achievements that legitimately
include failures or recovery.

`ProblemSolved` is derived in the same core transaction only when the official
suite passed and no explanation, solution, or prior successful source was
revealed. Those conditions are invariants, not optional booleans on the event.
`ProblemSolved` records:

| Field | Purpose |
|---|---|
| `eventId` | Idempotency and replay identity. |
| `reviewSessionId` | Ensures retries in one session count once. |
| `problemId` / revision / content hash | Connects activity to trusted content. |
| `occurredAtUtc` | Immutable chronological evidence. |
| `profileZoneAtFinalization` | IANA zone captured as audit metadata. |
| `observedLocalDate` / `observedLocalTime` | Derived audit values, validated from UTC and zone. |
| `eventSchemaVersion` | Safe replay and migration. |

Both events are emitted only from the atomic finalized-review transaction.
5am Club and social completion counts consume `ProblemSolved`; failure/reveal
reason UI consumes `ProblemReviewFinalized`.

## ACH-001 — Define ethical achievement principles

- **State:** proposed
- **Outcome:** achievements reinforce useful practice without turning BeeCode
  into a compulsion system.
- **Deliverables:** design principles, prohibited mechanics, review rubric,
  opt-out/visibility policy, and language guide.
- **Acceptance:**
  - Achievements reward finalized learning behavior, consistency, breadth, and
    recovery—not raw button presses.
  - Streak presentation avoids shame and can be hidden.
  - No purchases, energy, loot boxes, streak repair currency, or dark patterns
    exist.
  - 5am Club is presented as an optional accomplishment, not advice to reduce
    sleep.
- **Evidence:** rubric applied to every initial definition.
- **Dependencies:** PROD-009.
- **Risks:** novelty outweighing learning quality.
- **Non-goals:** optimizing daily-active-user metrics.

## ACH-002 — Version achievement definitions

- **State:** proposed
- **Outcome:** content, progress, and rewards are reviewable and migratable.
- **Deliverables:** schema, compiled format, validation, localization fields,
  evaluator-kind registry, and compatibility policy.
- **Acceptance:**
  - IDs are stable and definitions have monotonic versions.
  - Duplicate IDs, unsupported evaluator kinds, invalid thresholds, or missing
    rewards fail validation.
  - Wording/art changes are distinguished from semantic rule changes.
  - Definitions cannot execute arbitrary code or queries.
- **Evidence:** valid/malformed fixtures and deterministic compilation.
- **Dependencies:** PROB-006, ARCH-006.
- **Risks:** using configuration to smuggle in an unsafe DSL.
- **Non-goals:** user-authored evaluator scripts in v1.

## ACH-003 — Build deterministic event projection

- **State:** proposed
- **Outcome:** progress and awards can always be rebuilt from canonical events.
- **Deliverables:** reducer interface, progress state, cursor/checkpoint,
  separate idempotent projection transaction, immediate post-commit catch-up,
  replay command, and invariant checks.
- **Acceptance:**
  - Duplicate event IDs and review-session IDs do not advance progress twice.
  - Processing order is deterministic; out-of-order handling is defined.
  - Projection crash rolls back or resumes without lost/duplicate progress.
  - Unknown or broken reducer versions cannot prevent the core review/schedule
    transaction from committing.
  - Full replay produces the same progress and awards as incremental operation.
  - Unknown event/definition versions are quarantined visibly.
- **Evidence:** duplicate, reorder, crash, checkpoint, and randomized replay
  tests.
- **Dependencies:** ARCH-005, SRS-006, DATA-002.
- **Risks:** reducer version changes rewriting earned history.
- **Non-goals:** treating projection rows as canonical truth.

## ACH-004 — Implement the 5am Club rule

- **State:** proposed
- **Outcome:** BeeCode awards the title **5am Club** after qualifying successful
  Problem reviews before 06:00 on seven consecutive local dates.
- **Deliverables:** `DAILY_WINDOW_STREAK` evaluator, definition, progress
  explanation, timezone policy, award/title, and boundary suite.
- **Acceptance:**
  - Window is exactly `[00:00:00, 06:00:00)`.
  - Seven distinct consecutive local calendar dates are required.
  - At most one date counts per day.
  - Full official suite passed, review finalized, and no packaged explanation/
    solution or prior successful source reveal are required.
  - Same Problem may count on a later date through a legitimate review.
  - The first qualifying event starts an epoch and locks `streakZoneId`.
  - Every later event in that epoch is converted from `occurredAtUtc` using the
    locked zone, regardless of device/profile changes.
  - After a missing locked-zone calendar date, the next qualifying event starts
    a new epoch using the then-current profile zone.
  - Progress is recomputed from the ordered set of qualifying UTC events
    (`occurredAtUtc`, then `eventId`), so late/backdated events cannot corrupt an
    increment-only counter.
  - Stored local audit values must validate against UTC plus their recorded
    zone; they are not trusted instead of recomputation.
  - Award is idempotent and grants equippable title `5am Club`.
- **Evidence:** normative test matrix below.
- **Dependencies:** ACH-003, SRS-007.
- **Risks:** device-clock manipulation; ambiguous travel semantics.
- **Non-goals:** invasive location/time attestation.

### 5am Club normative examples

| Scenario | Result |
|---|---|
| Completion at 05:59:59 | Date qualifies. |
| Completion at 06:00:00 | Does not qualify. |
| Three qualifying Problems on one date | One qualifying date. |
| Same Problem reviewed successfully on two qualifying dates | Both dates may qualify. |
| Successful tests but session never finalized | Does not qualify. |
| Packaged explanation/solution or prior successful source revealed | Does not qualify. |
| Duplicate event or upload | Counts once. |
| Dates 1–6 and 8 | Streak resets; no award. |
| Seven consecutive dates across month/year boundary | Qualifies. |
| DST transition | Uses zoned calendar dates, not seven 24-hour intervals. |
| Timezone change during active streak | Existing epoch keeps its locked zone; a post-gap epoch uses the new profile zone. |

Required automated cases:

- 00:00:00, 05:59:59, and 06:00:00 boundaries;
- six versus seven consecutive dates;
- gap on day seven;
- duplicate local event and duplicate social upload;
- multiple sessions on one date;
- failed tests, timeout, cancellation, reveal, and abandoned review;
- process death before and after atomic finalization;
- leap day, year rollover, DST gap/overlap, and timezone without DST;
- manual clock rollback and implausible future timestamp classification;
- definition/evaluator replay after application upgrade.

## ACH-005 — Define award identity and immutability

- **State:** proposed
- **Outcome:** an honestly earned achievement cannot be duplicated or casually
  rewritten.
- **Deliverables:** award entity, unique key, granted instant/evidence range,
  definition version, notification state, and correction/revocation policy.
- **Acceptance:**
  - Unique `(profileId, achievementId, semanticAwardKey)` prevents duplicates.
  - Wording/art changes retain the award.
  - Semantic changes use a new definition version and explicit grandfather or
    re-evaluation policy.
  - Notification replay after restore does not pretend the award is newly
    earned unless requested.
- **Evidence:** migration and restore fixtures.
- **Dependencies:** ACH-002, ACH-003.
- **Risks:** changing past requirements retroactively.
- **Non-goals:** silently removing local awards after server disagreement.

## ACH-006 — Design the achievement gallery

- **State:** proposed
- **Outcome:** learners can understand requirements, progress, unlock time, and
  rewards without spoilers or pressure.
- **Deliverables:** gallery, detail/progress, locked/secret policy, categories,
  title reward display, empty/error states, and accessibility semantics.
- **Acceptance:**
  - Progress explains what counted and what remains.
  - 5am Club shows the next required calendar date/time window under its active
    timezone.
  - Locked/secret states are intentional per definition.
  - Unlock celebrations respect reduced-motion settings.
  - Color is not the only state indicator.
- **Evidence:** screenshot/accessibility suite and comprehension sessions.
- **Dependencies:** ACH-003, UX-006.
- **Risks:** displaying sensitive exact timestamps socially.
- **Non-goals:** a public global achievement feed.

## ACH-007 — Equip one earned profile title

- **State:** proposed
- **Outcome:** an earned reward such as 5am Club can appear beside the user's
  name in private Leaderboards.
- **Deliverables:** equipped-title state, none option, local UI, server request,
  validation, and fallback behavior.
- **Acceptance:**
  - At most one title is equipped.
  - An unearned title cannot be equipped locally or socially.
  - Signing out/offline does not remove local selection.
  - Server-visible title must reference a server-accepted award.
  - Revoked/incompatible title falls back safely without erasing local award.
- **Evidence:** state, authorization, and offline reconciliation tests.
- **Dependencies:** ACH-005.
- **Risks:** local/server acceptance confusion.
- **Non-goals:** free-form title text.

## ACH-008 — Reconcile local and social awards

- **State:** proposed
- **Outcome:** local feedback is immediate while socially visible awards remain
  independently accepted from friendly-trust social events.
- **Deliverables:** acceptance state, upload linkage, server reducer version,
  disagreement reason, retry behavior, and privacy boundary.
- **Acceptance:**
  - Local award works without account/network.
  - Accepted duplicate events do not duplicate award.
  - Server rejection never deletes local review or award.
  - Social title remains unavailable until server acceptance where required.
  - The client can explain pending/rejected confirmation without accusing the
    learner of cheating.
- **Evidence:** offline, partial batch, duplicate, and version mismatch tests.
- **Dependencies:** DATA-004, LDB-004.
- **Risks:** two implementations drifting; “server accepted” being mistaken for
  cryptographic proof of an honest client.
- **Non-goals:** sending detailed source/test evidence to prove an award.

## ACH-009 — Govern definition migrations

- **State:** proposed
- **Outcome:** rule evolution is explicit, simulated, and recoverable.
- **Deliverables:** semantic-version rules, backfill/grandfather options,
  simulation report, replay version, and server/client compatibility.
- **Acceptance:**
  - Copy-only changes do not re-evaluate progress.
  - Semantic changes name whether existing progress/awards are preserved.
  - Old clients do not misinterpret new evaluator kinds.
  - Migration can be tested against long synthetic histories.
- **Evidence:** multi-version fixtures and before/after simulation.
- **Dependencies:** ACH-002, ACH-005.
- **Risks:** a fix granting/revoking many awards unexpectedly.
- **Non-goals:** editing canonical events to fit new definitions.

## ACH-010 — Create the initial achievement set

- **State:** proposed
- **Outcome:** v1 offers a small coherent collection spanning first use,
  consistency, breadth, recovery, and long-term effort.
- **Deliverables:** proposed names/copy/art briefs/rules for:
  - first successful finalized Problem review;
  - seven-day study consistency;
  - topic breadth across trusted Problems;
  - successful review after a lapse;
  - cumulative finalized reviews at restrained milestones;
  - 5am Club.
- **Acceptance:**
  - Every definition passes ethical rubric.
  - Every predicate has positive, negative, boundary, duplicate, and replay
    fixtures.
  - No pair of achievements rewards the same trivial behavior redundantly.
  - Titles are rare enough to remain meaningful.
- **Evidence:** reviewed definition pack and test matrix.
- **Dependencies:** ACH-001 through ACH-005.
- **Risks:** overbuilding dozens of low-value awards.
- **Non-goals:** a huge launch catalog.

## ACH-011 — Explain non-qualification

- **State:** proposed
- **Outcome:** learners can distinguish a bug from a session that did not meet
  written rules.
- **Deliverables:** trusted reason codes, localized copy, progress ledger, time
  display, and support export.
- **Acceptance:**
  - `ProblemReviewFinalized` supplies tests-failed/revealed/outside-window/
    untrusted-content/version reasons; an unfinalized session is explained from
    session state rather than a fabricated event.
  - Explanations do not expose hidden test details.
  - Advanced evidence uses stable IDs/times but excludes source.
  - Screen-reader order communicates reason before recovery.
- **Evidence:** golden reason-code suite.
- **Dependencies:** ACH-003, ACH-004.
- **Risks:** overly technical or judgmental copy.
- **Non-goals:** publishing non-qualification history to friends.

## ACH-012 — Test achievement abuse and resilience

- **State:** proposed
- **Outcome:** common retries and friendly tampering do not corrupt progress.
- **Deliverables:** duplicate/reorder/clock/manifest/version abuse fixtures,
  sanity classifications, and support behavior.
- **Acceptance:**
  - Only finalized events from trusted installed Problem revisions qualify for
    social-visible awards.
  - Implausible time can be flagged socially without destroying local state.
  - Rebuild and incremental result remain equivalent under generated histories.
  - Corrupt definition/progress storage enters recoverable safe mode.
- **Evidence:** property/fuzz/fault suite.
- **Dependencies:** ACH-003, LDB-010, SEC-008.
- **Risks:** building surveillance-grade anti-cheat for friendly boards.
- **Non-goals:** perfect prevention of determined local database modification.

## Achievement exit gate

Achievement progress must rebuild deterministically from canonical review
events, awards must be idempotent, and the complete 5am Club boundary/timezone/
DST/offline matrix must pass before the title can appear socially.
