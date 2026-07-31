# ADR 0002 — Personal sync direction (snapshot merge, not activity outbox)

- Status: accepted; **merge and loop implemented over a file store**; networked backends deferred
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

- ~~Which backends ship first.~~ **Resolved: a plain file first**, for exactly the
  reason given — testable with no Google Cloud project and no credentials. WebDAV and
  Drive remain deferred.
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

`SyncService.sync()` is the loop: export local, pull remote, merge, **restore locally,
then push** under the compare-and-swap. That order is deliberate — if the push fails the
local profile has already absorbed the remote's work, so nothing is lost and the next
sync sends it. Reversed, a crash between push and restore would leave the remote holding
reviews this device never applied. The pushed snapshot is always the *merged* one; pushing
local-only would discard the other device's reviews on every sync, which is the classic
way a sync feature eats data. A lost CAS re-pulls and retries up to
`MAX_ATTEMPTS`; a push is never forced.

`FileSyncStore` is the first backend, exactly as this ADR predicted: a file in a folder
Dropbox, Syncthing, or a network share already replicates, giving working sync with no
credentials and no BeeCode server. Its concurrency token is a **SHA-256 of the contents**
rather than a modification time — stable across coarse-resolution filesystems, and it
gives two devices that computed the same merge the same token, which is precisely what the
merge's determinism was for. Writes go via a sibling temp file and a rename, because a
truncated snapshot looks valid and a stale one does not.

Verified by 11 loop tests over two real profiles and a real file, including a genuine
lost race (a second device's full sync interposed before the push). Both dangerous
mutations are caught: pushing local instead of merged, and skipping the local restore.

**An empty remote is "nothing synced yet", in every backend.** This was not true at first,
and the disagreement was the one bug in sync that a learner could not recover from. A file
named ahead of time — by Android's `CreateDocument` picker, a WebDAV client, or a
folder-sync tool — exists with zero bytes. `FileSyncStore` returned those bytes as a
snapshot, the merge refused to parse them, and every sync failed while never pushing, so
nothing seeded the file. `WebDavSyncStore` read blank correctly but then sent
`If-None-Match: *`, which a server refuses because the resource exists. Only
`DocumentSyncStore` had it right on both sides. All three now agree, `pull` and `push`
alike — the two must agree per backend, or the seeding push mismatches its own token and
conflicts forever instead. The WebDAV seed re-guards on the blank resource's ETag, so it
stays a compare-and-swap rather than becoming a force.

**Known limitation.** `FileSyncStore`'s compare-and-swap is a read-verify-write, not an
atomic one. It closes the realistic window — two devices minutes apart — but not a truly
simultaneous write, and it cannot without file locking that behaves differently on every
platform and network filesystem. A real HTTP backend with a genuine ETag is the better
long-term target for that reason.

Still deferred: networked backends (WebDAV, Drive), and the sync UI. Those are plumbing
over a verified merge and a verified loop.

## Status of the year-one plan

`goals/YEAR-ONE.md` lists live personal sync as post-v1. That classification is
now doubtful, but this ADR does not reschedule it. The commitment made here is
narrow and immediate: **the local data model is built sync-ready** (properties
1–4), so that adopting the chimahon model is a feature addition rather than a
migration. The merge itself is now built too; what remains is a storage backend.
