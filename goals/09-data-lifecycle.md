# Target 09: local data lifecycle

This target makes device-local data authoritative for study, durable through
failures, portable to the learner, and synchronizable only where a feature
genuinely needs a server.

## Ownership model

| Data | Authority | Server visibility |
|---|---|---|
| Installed Problem definitions | Signed/validated local pack | Trusted IDs/revisions/hashes only |
| Solution drafts and history | Local database | Never |
| Execution output | Local, bounded retention | Never |
| Review history | Local append-only log | Derived successful activity only |
| FSRS memory/schedule | Local projection | Never |
| Achievement progress/awards | Local projection/award log | Confirmed award metadata only |
| Account and board membership | Server | Server authority |
| Social activity acceptance/ranks | Server | Minimal activity metadata |
| Equipped social title | Server, limited to confirmed award | Board members |

The Leaderboard server cannot repair, delete, reschedule, or veto a local
review.

## Planned local schema areas

- installed packs and Problem revision metadata;
- solution drafts and bounded draft history;
- execution run summaries;
- review sessions, immutable review events, and materialized schedules;
- domain events and projection checkpoints;
- achievement progress, awards, and equipped local title;
- durable social outbox and last Leaderboard snapshots;
- local profile, settings, database metadata, and migration journal.

SQLite with a shared migration/query layer such as SQLDelight is a strong
candidate, subject to an early Android/desktop packaging spike.

## Atomic review transaction

One local transaction must:

1. prove the review session is not already finalized;
2. append the review event;
3. calculate and persist the FSRS transition;
4. update the current Problem schedule;
5. append `ProblemSolved` if eligible;
6. project achievement progress and append new awards;
7. insert the minimal social activity into the outbox if an account is linked;
8. mark the session finalized.

The transaction produces all effects or none.

## DATA-001 — Version the local schema before alpha data

- **State:** proposed
- **Outcome:** every durable entity and index has an explicit migration path.
- **Deliverables:** schema, foreign/unique constraints, indexes, metadata table,
  migration numbering, and reference ER diagram.
- **Acceptance:**
  - Problem/revision, run, review session, event, schedule, achievement, and
    outbox identities are constrained at the database.
  - `reviewSessionId` cannot finalize twice.
  - Derived/projected tables can name their source/cursor/version.
  - Foreign-key behavior on pack/account removal is deliberate.
  - Database bootstrap and integrity checks are deterministic.
- **Evidence:** schema review and real-database tests.
- **Dependencies:** ARCH-004, ARCH-005.
- **Risks:** using application checks where uniqueness must be transactional.
- **Non-goals:** supporting multiple local database engines.

## DATA-002 — Build transactional repositories

- **State:** proposed
- **Outcome:** critical use cases own a clear transaction boundary rather than
  coordinating independent repository writes in UI code.
- **Deliverables:** transaction runner, review finalizer, draft saver, pack
  importer, achievement projector, and outbox repository contracts.
- **Acceptance:**
  - Forced failure after each review-finalization write yields all or none.
  - Retried finalization returns the existing outcome.
  - Domain events and projections cannot commit with mismatched cursors.
  - Long runner/network work never holds a database transaction open.
- **Evidence:** failure injection with actual database.
- **Dependencies:** DATA-001, ARCH-003.
- **Risks:** nested/implicit transactions obscuring atomicity.
- **Non-goals:** one giant transaction for unrelated settings.

## DATA-003 — Define backup, export, and import

- **State:** proposed
- **Outcome:** the learner can move or recover all local study data without a
  server.
- **Deliverables:** versioned `.beecodebackup` manifest, database/data export,
  source inclusion choices, checksums, optional encryption, preview, import
  transaction, and conflict policy.
- **Acceptance:**
  - Full round trip preserves Problems/revisions, drafts, review history,
    schedule, achievements, and settings as documented.
  - Archive lists schema/app versions and source sensitivity.
  - Corrupt, truncated, wrong-password, and unsupported archives fail before
    modifying live data.
  - Import never silently overwrites a divergent draft.
  - A pre-import backup/rollback path exists.
- **Evidence:** round-trip and adversarial archive fixtures across releases.
- **Dependencies:** DATA-001, SEC-007.
- **Risks:** backups becoming the largest source-code leak.
- **Non-goals:** automatic cloud backup in v1.

## DATA-004 — Implement the durable social outbox

- **State:** proposed
- **Outcome:** social events survive offline use and network ambiguity without
  duplicating rank.
- **Deliverables:** state machine, batch selection, attempt metadata, backoff/
  jitter, token refresh interaction, item-level acknowledgement, rejection
  reasons, and pruning.
- **Acceptance:**
  - Pending events are inserted atomically with review finalization.
  - Process death in any upload state is recoverable.
  - Timeout after server commit retries safely.
  - Final rejection remains inspectable and cannot affect local truth.
  - Worker frequency/battery/network constraints are bounded.
- **Evidence:** offline/reconnect/reorder/partial-failure tests.
- **Dependencies:** DATA-002, LDB-004.
- **Risks:** stale outbox growing forever.
- **Non-goals:** syncing drafts or FSRS state.

## DATA-005 — Define explicit conflict policies

- **State:** proposed
- **Outcome:** every incoming data type has a deterministic and user-visible
  merge rule.
- **Deliverables:** matrix for pack revisions, imported drafts, settings,
  account/profile cache, social acknowledgements, and future sync research.
- **Acceptance:**
  - Divergent source is never resolved by silent last-write-wins.
  - Canonical review history is append-only.
  - Server social acknowledgement cannot replace local event content.
  - Settings name local-only, server-authoritative, or merge behavior.
  - Conflict copies retain provenance.
- **Evidence:** scenario fixtures.
- **Dependencies:** DATA-003, DATA-004.
- **Risks:** treating all data as if it had the same authority.
- **Non-goals:** automatic semantic merging of Python source.

## DATA-006 — Store credentials in platform facilities

- **State:** proposed
- **Outcome:** account refresh tokens and recovery-sensitive state are not plain
  database/settings fields.
- **Deliverables:** Android keystore-backed storage, desktop credential-store
  strategy/fallback, token metadata separation, logout/rotation, and threat
  statement.
- **Acceptance:**
  - Raw refresh token is absent from normal database export/logs.
  - Logout removes device credentials even if server is unavailable, then
    retries remote revocation.
  - Token replacement is atomic enough to avoid losing both old and new state.
  - Unsupported secure storage is visible and follows declared fallback.
- **Evidence:** platform inspection and lifecycle tests.
- **Dependencies:** SEC-005.
- **Risks:** desktop credential-store inconsistencies.
- **Non-goals:** protecting secrets from a fully compromised logged-in OS user.

## DATA-007 — Separate deletion scopes

- **State:** proposed
- **Outcome:** local reset, source deletion, account deletion, board leave, and
  server event retention are not conflated.
- **Deliverables:** deletion matrix, confirmation copy, export-first option,
  server workflow, tombstone/anonymization rules, and operator process.
- **Acceptance:**
  - Signing out never deletes local study data.
  - Local reset works without server/account.
  - Account deletion request has trackable completion and retention disclosure.
  - Leaving a board removes access without necessarily deleting lawful/
    integrity-required aggregate rows beyond policy.
  - Destructive scope is previewed explicitly.
- **Evidence:** client/server end-to-end deletion tests.
- **Dependencies:** SEC-007, LDB-003.
- **Risks:** destructive copy ambiguity.
- **Non-goals:** promising deletion from independent user backups.

## DATA-008 — Make migrations a release discipline

- **State:** proposed
- **Outcome:** every released schema/pack/backup version remains upgradable
  throughout the stated support window.
- **Deliverables:** frozen fixtures, forward migration tests, interruption
  journal, backup-before-risk policy, compatibility matrix, and rollback/
  forward-fix guidance.
- **Acceptance:**
  - CI migrates from every supported milestone fixture to current.
  - Interrupted migrations recover or fail into non-destructive safe mode.
  - Migration verifies invariants before replacing live version marker.
  - App downgrade limitations are explicit.
  - Pack/FSRS/achievement version migrations are represented separately.
- **Evidence:** upgrade matrix and forced-interruption drills.
- **Dependencies:** DATA-001, ARCH-006.
- **Risks:** fixtures not representing real long-lived data.
- **Non-goals:** bidirectional migration between arbitrary versions.

## DATA-009 — Recover from corruption and full storage

- **State:** proposed
- **Outcome:** database/filesystem trouble produces a safe diagnosis and
  recovery path rather than further destructive writes.
- **Deliverables:** integrity check, read-only safe mode, diagnostic export,
  backup restore, rebuildable projection repair, disk-space checks, and support
  guide.
- **Acceptance:**
  - Corrupt canonical history is never “repaired” by silently deleting it.
  - Derived schedules/achievement projections can rebuild after validation.
  - Full-disk failure leaves prior transaction consistent.
  - User can export diagnostics/backup where storage permits.
  - Recovery operations are previewed and logged.
- **Evidence:** deliberately corrupt database and full-volume scenarios.
- **Dependencies:** DATA-002, DATA-003.
- **Risks:** attempted repair worsening damage.
- **Non-goals:** guaranteed recovery from destroyed hardware without backup.

## DATA-010 — Bound retention and storage growth

- **State:** proposed
- **Outcome:** years of daily use do not create unbounded output, snapshots,
  caches, logs, or outbox data.
- **Deliverables:** retention classes, caps, pruning jobs, user controls,
  storage report, and preservation exceptions.
- **Acceptance:**
  - Canonical review history and current drafts have intentional durable policy.
  - Run stdout/tracebacks and intermediate snapshots are capped/pruned.
  - Acknowledged outbox and stale Leaderboard caches expire safely.
  - Installed packs/cache cleanup cannot orphan required history labels.
  - Pruning is transactional/idempotent.
- **Evidence:** multi-year synthetic profile storage benchmark.
- **Dependencies:** QLT-004.
- **Risks:** pruning useful source evidence unexpectedly.
- **Non-goals:** storing every keystroke/run forever.

## DATA-011 — Rehearse restore as an operation

- **State:** proposed
- **Outcome:** backups are proven recoverable, not merely produced.
- **Deliverables:** clean-device/clean-desktop restore scripts, validation
  summary, compatibility checks, and timed release drill.
- **Acceptance:**
  - Restore into a clean current client yields expected counts/drafts/due state.
  - Restored projections match canonical replay.
  - Account credentials are reauthenticated rather than copied insecurely where
    policy requires.
  - Failure leaves target profile recoverable.
- **Evidence:** restore report at every release candidate.
- **Dependencies:** DATA-003, DATA-008.
- **Risks:** testing only same-version backups.
- **Non-goals:** assuming an archive is healthy because checksum passes.

## DATA-012 — Keep personal cross-device sync out of v1

- **State:** proposed
- **Outcome:** Leaderboard metadata sync does not become accidental source/
  schedule sync.
- **Deliverables:** explicit ADR, future threat/conflict questions, encrypted
  export baseline, and criteria for reconsideration.
- **Acceptance:**
  - No v1 API accepts draft, detailed review history, or FSRS memory state.
  - UI never calls Leaderboard sync “backup”.
  - Future design starts from append-only review inputs, encrypted source blobs,
    and visible concurrent-review conflicts.
  - Scope can be added only through a new target and security review.
- **Evidence:** API/schema privacy diff.
- **Dependencies:** PROD-006, SEC-006.
- **Risks:** users assuming account means multi-device backup.
- **Non-goals:** implementing personal sync in the first-year critical path.

## Data-lifecycle exit gate

BeeCode must work offline indefinitely, finalize reviews atomically, migrate
every supported stored fixture, survive forced failure/full disk safely, and
round-trip a backup without source conflict or duplicate events.
