# ADR 0002 — Personal sync direction (snapshot merge, not activity outbox)

- Status: accepted; **merge implemented**, storage backends deferred
- Date: 2026-07-29
- Supersedes nothing; constrains `goals/08-leaderboards.md` and
  `docs/architecture.md`

## Context

The original plan had exactly one networked feature: a private Leaderboard fed
by a minimal **activity outbox** (counts and streaks only, never source or FSRS
state). Personal cross-device study sync was listed as post-v1.

The owner has since identified [`bee-san/chimahon`](https://github.com/bee-san/chimahon)
as the model to copy for sync. Chimahon is a Mihon fork, and its sync design is
materially different from an activity outbox:

- `SyncService` is an abstract backend with concrete implementations for
  Google Drive, WebDAV, and a self-hosted SyncYomi server. The user chooses and
  owns the storage.
- The synced payload is a **whole-profile snapshot** (`SyncData { deviceId,
  backup }`), serialized with kotlinx-serialization / protobuf.
- Conflict handling is `mergeSyncData(local, remote)`: a per-entity merge, then
  an ETag-guarded compare-and-swap push. Losing the CAS means re-pull and retry.
- There is no server-side domain logic. The remote is dumb storage plus an
  optimistic-concurrency token.

This is a fundamentally different privacy and correctness posture from the
Leaderboard, and it is the posture the owner actually wants.

## Decision

BeeCode plans **two independent networked features**, and never conflates them:

| | Personal sync (this ADR) | Leaderboard (ADR 0001) |
|---|---|---|
| Purpose | One learner, many devices | Many learners, compared |
| Payload | Whole-profile snapshot: drafts, source, reviews, FSRS state, achievements | Counts and streaks only |
| Storage | User-owned (Drive / WebDAV / self-host) | Shared service |
| Trust | The learner already owns this data | Other people can see it |
| Merge | Per-entity merge + ETag CAS | Append-only idempotent ingestion |

**Personal sync may carry source code and FSRS state.** That is not a privacy
regression, because the destination is storage the learner already controls. The
`goals/10-security-and-privacy.md` prohibition on uploading source and FSRS state
is scoped to the **Leaderboard**, and must be reread that way. Personal sync
inherits the *backup* privacy rules instead: the payload is sensitive, so
encryption-at-rest is optional-but-offered and the export UI warns.

**Personal sync replaces the export/restore file as the primary recovery path**,
but does not remove it. Export/restore stays as the offline-only,
no-account path and as the format personal sync serializes.

## Consequences for work happening now

This ADR is written before the sync code, precisely so the local data model does
not have to be rewritten later. The snapshot-merge model demands three
properties of every syncable entity, and they are cheap now and expensive later:

1. **Stable, device-independent IDs.** No autoincrement integers in any
   syncable row's identity. Problem IDs come from content; review sessions,
   execution runs, and achievement awards get UUIDs generated on-device.
   Two devices must never mint the same ID, and the same logical row must have
   the same ID on both.
2. **A monotonic `updatedAt` on every mutable row.** Per-entity merge is
   last-write-wins over this column. Rows without it cannot be merged, only
   clobbered.
3. **Append-only history where merge is meaningless.** `ProblemReviewFinalized`
   is an immutable event log keyed by `reviewSessionId`; merging two review
   histories is a set union, which is always correct and needs no timestamp
   comparison. This is why the review log is append-only in the local schema
   too — it makes sync trivial. Prefer append-only over mutable-plus-timestamp
   wherever the domain allows.

A fourth property, now also in place:

4. **A device identity.** Chimahon's `SyncData.deviceId` exists so a device can
   recognize its own writes. BeeCode stores a local `deviceId` row in settings,
   generated on first launch. It is stamped onto every finalized review and
   excluded from merge, so it is no longer merely reserved.

## Deliberate non-decisions

- Which backends ship first. Drive needs OAuth; WebDAV needs almost nothing.
  WebDAV or a plain file is the likely first target because it is testable
  without a Google Cloud project.
- ~~Whether FSRS state merges by `updatedAt` or is **recomputed** from the merged
  append-only review log.~~ **Resolved: recomputed.** See "What is built" below.
- Whether sync lands before or after the Leaderboard. It is now plausibly
  *more* valuable to the owner than the Leaderboard, and it does not need a
  bespoke server.

## What is built

`SnapshotMerge.merge(local, remote)` in `:shared` implements the chimahon merge, as a
pure function of two exported snapshots. No network, no storage backend, no clock — so
the part of sync where data actually gets lost is verified before any backend exists.

| Entity | Rule | Why that rule is available |
|---|---|---|
| Reviews | set union on `sessionId` | property 3: append-only, immutable, device-minted IDs |
| Drafts | last-write-wins, ties to local | property 2: `updated_at` on every mutable row |
| Settings | last-write-wins per key, ties to local | property 2, per key rather than per map |
| Schedules | not merged — replayed from the merged log | recomputation, resolved above |
| `deviceId` | never merged | property 4 |

The merge is commutative on reviews and byte-deterministic on ties, which is what makes
the planned ETag compare-and-swap meaningful: two devices merging the same pair must
produce the same bytes, or each would read the other's push as a conflict and they would
ping-pong indefinitely.

Verified by 18 tests, and every rule is mutation-checked — inverting each comparison
fails a named test. Two of those tests exist *because* mutation found the suite blind:
`putIfAbsent` → `put` (remote clobbering local reviews) and `>` → `>=` (the settings
tie-break) both passed originally, since the tests used identical content or distinct
timestamps.

**Schedule recomputation is now decided, not deferred.** The merged snapshot carries no
schedules; restoring replays the merged review log to rebuild them, and
`verifyScheduleIntegrity()` asserts the fold reproduces stored state exactly. This is
the option the "deliberate non-decisions" section called the leading candidate, and it
is only correct because reviews are append-only.

Still deferred, and genuinely so: the storage backends (WebDAV, a plain file, Drive),
the ETag/CAS push loop, and the UI. Those are plumbing over a verified merge.

## Status of the year-one plan

`goals/YEAR-ONE.md` lists live personal sync as post-v1. That classification is
now doubtful, but this ADR does not reschedule it. The commitment made here is
narrow and immediate: **the local data model is built sync-ready** (properties
1–4), so that adopting the chimahon model is a feature addition rather than a
migration. The merge itself is now built too; what remains is a storage backend.
