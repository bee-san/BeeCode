# BeeCode

BeeCode is an offline-first spaced-repetition app for practising LeetCode-style
algorithm Problems. It runs on Android and desktop, schedules reviews with
[bee-fsrs](https://github.com/bee-san/bee-fsrs) (FSRS-6.x, Kotlin), and lets you write
and run Python solutions inside each review.

Python is what you *write* — the scheduling engine is Kotlin.

Everything is local. There is no account and no BeeCode server — studying needs no
network at all, and the Android app declares no permissions. Optional cross-device sync
writes a snapshot to a file *you* choose, in storage you already own.

The product is built around **Problems**, not generic cards:

- a Problem contains its prompt, starter code, examples, executable tests, and
  stable identity;
- solving and reviewing a Problem is one continuous flow;
- successful reviews feed the scheduler, statistics, and achievements;
- your source code stays on your device.

## Status

The complete local study loop works on both platforms today.

| | Android | Desktop |
|---|---|---|
| Study loop: read, write Python, run, rate, schedule | ✅ | ✅ |
| Python execution | Chaquopy, CPython 3.12, in-process | `python3` child process |
| Runner containment | `IN_PROCESS` — not a sandbox | `SEPARATE_PROCESS`, killable |
| Local statistics and achievements | ✅ | ✅ |
| Export and restore | ✅ | ✅ |
| Sync between devices | ✅ Settings → Sync | ✅ Settings → Sync |
| Verified by | 11 Robolectric UI + 18 instrumented tests | 31 JVM tests, 8 of them UI |

**289 automated tests**: 271 JVM tests across nine modules and 18 Android
instrumented tests, including the complete answer → fail → fix → pass → finalize →
restart journey against real CPython and real SQLite on both platforms.

**Both clients' UI is tested, headlessly, on every push.** The two suites assert
deliberately overlapping rules — a failed run permits only *Again*, an unaided pass
permits every rating, Settings never calls the runner a sandbox — so a divergence
between platforms fails on one and not the other. That is how conformance gets checked
rather than assumed.

The Android UI is verified by **Robolectric on the JVM**, and that is a deliberate
choice rather than a convenience. The equivalent instrumented Compose tests need an
emulator that accepts injected touch input, and no emulator available to this project
provides one: this dev host has no `/dev/kvm`, so only Google's automated-test-device
images boot and they render nothing at all, while CI's `-no-window` emulator refuses
injection. Those tests therefore skipped in both places — written, compiled, and never
run. Robolectric removes the emulator from the question, so the 9 UI assertions now run
on every push and every `./gradlew test`.

Running them found real bugs in the tests themselves: assertions that a node was
displayed when it sat below the fold of a scrolling column, and a `performScrollTo` on
rating buttons that live in an always-visible bottom bar and have no scrollable
ancestor. Both had been invisible precisely because nothing ever executed them.

What Robolectric does **not** prove: real rendering, since its canvas is a no-op, and
real Python, since these use a scripted runner. Both are covered elsewhere — the
instrumented `AndroidStudyJourneyTest` runs the journey against Chaquopy and real SQLite
on a device, and `:content-tools` runs every reference solution through a real
interpreter. One assertion is also weaker under Robolectric than on a device: the symbol
row is a horizontal scroller nested in a vertical one, whose children compose with a
real size but are never placed, so it is asserted by existence rather than by display.

The Problem pack holds **16 Problems** — 10 easy, 5 medium, 1 hard — with 126 tests, 30
of them hidden. That meets the year-one target of 12–20, and it covers all four
comparators; `any_of` and `approximate_numeric` had been implemented but unused by any
Problem, so those code paths shipped untested.

**Cross-device sync works, over a file you own.** It follows the chimahon model in
[ADR 0002](docs/adr/0002-personal-sync-direction.md): pull, merge, apply locally, then
push under a compare-and-swap. Point two devices at the same file in a Dropbox,
Syncthing, or network-share folder and they converge — no account, no BeeCode server, no
OAuth.

The merge is where data would be lost, so it is a pure function of two snapshots: reviews
by set union on session, drafts and settings by last-write-wins, schedules **replayed**
from the merged log rather than merged, and the device identity never merged. It is
commutative and byte-deterministic, which is what makes the compare-and-swap meaningful —
two devices computing the same merge must agree on its token. A lost race re-pulls and
retries; a push is never forced.

33 tests cover the merge, the loop, and both clients' UI, and each layer is
mutation-checked: inverting any merge comparison, pushing the local snapshot instead of
the merged one, skipping the local restore, or stubbing out the UI's sync call each fail
named tests.

Both clients expose it under Settings → Sync between devices, and they interoperate: the
desktop uses a file path, Android a document you pick with the system picker, and the
token is a content hash on both so they agree on what "unchanged" means. Android still
declares **no storage permission** — it holds a persisted URI grant for the one file you
chose, which the system gives and you can revoke.

Not built yet: the private Leaderboard, and networked backends (WebDAV, Drive).
See [the year-one plan](goals/YEAR-ONE.md).

## What is honest about this build

- **The Android runner is not a sandbox.** Chaquopy embeds CPython in the app
  process, so BeeCode cannot forcibly kill a runaway loop — it stops waiting and
  tells you to restart if running code stops working. The app declares *no
  Android permissions at all*, so your code has no network access and no file
  access beyond BeeCode's own storage. The Settings screen says all of this.
- **The desktop runner is stronger but still not a sandbox.** Learner code runs in
  a killable child process with a cleaned environment and a fresh temporary
  directory, but with your own user account's privileges.
- **An export contains your source code.** That is the point of a backup, and it
  is why the file should be kept somewhere private.

## Running it

Requires JDK 17 and Python 3 on desktop; the Android APK bundles its own CPython.

```bash
# Desktop
./gradlew :desktopApp:run

# Android, to a connected device or emulator
./gradlew :androidApp:installDebug

# Everything
./gradlew test
```

## Repository layout

```text
bee-fsrs/        Vendored checkout of dev.bee:bee-fsrs (its own repo)
domain/          Pure models, review state machine, and the rating policy
fsrs-adapter/    BeeCode's review policy over bee-fsrs
python-api/      Execution contracts and the shared Python harness
persistence/     SQLite schema, migrations, exactly-once finalization
content-tools/   Problem loading, validation, and pack compilation
shared/          Study loop, statistics, achievements, export/restore, sync merge
androidApp/      Android client and the Chaquopy runner
desktopApp/      Desktop client and the process runner
content/packs/   The Problem pack: 16 Problems, 126 tests
```

The domain does not import Compose, Android, SQL, HTTP, or any Python-provider
class, and both clients drive the same shared study service — which is how the two
platforms are kept from disagreeing about what a review means.

## Adding a Problem

One self-contained directory under `content/packs/core/problems/`, and no registry
to edit:

```text
two-sum/
├── problem.yaml      metadata, entry point, examples, provenance, limits
├── statement.md      what the learner reads
├── starter.py        what the editor is pre-filled with
├── tests.yaml        the official suite
├── reference.py      a working solution — excluded from the shipped pack
└── explanation.md    revealable; revealing it caps the rating at Hard
```

`./gradlew :content-tools:test` runs every reference solution through real Python
and requires every starter to fail, so a wrong expected value or an unsolvable
Problem fails the build rather than reaching a learner.

## How reviews stay honest

Three rules, enforced in pure code and covered by tests:

- **Ratings are bounded by evidence.** A non-pass permits only *Again*. A pass
  after revealing the explanation is capped at *Hard*, because that is
  recognition rather than recall. Only an unaided pass counts as solved — which is
  what stops the 5am Club being farmed by reading the answer.
- **BeeCode's own failures are never your fault.** A cancelled run or a crashed
  worker cannot become a review, so they cannot damage your schedule.
- **Finalizing credits the code that actually ran**, not whatever the editor
  happens to contain.

Finalization is exactly-once: idempotent on the review session, and guarded by a
schedule-version compare-and-swap under `BEGIN IMMEDIATE`. Eight threads
finalizing the same session produce exactly one review, and that is a test.

## Documentation

- [Architecture overview](docs/architecture.md)
- [Architecture decisions](docs/adr/README.md)
- [Year-one execution plan](goals/YEAR-ONE.md)
- [North-star catalogue: 164 goals](goals/README.md)
- [FSRS provenance](bee-fsrs/PROVENANCE.md) and the engine's own repository,
  [bee-san/bee-fsrs](https://github.com/bee-san/bee-fsrs)
- [Contributing](CONTRIBUTING.md)

## Naming

- **BeeCode** — the application.
- **Problem** — a study item and coding challenge.
- **Review** — one scheduled attempt at a Problem.
- **Leaderboard** — a planned private group in which friends compare activity.

Avoid calling individual Problems "BeeCodes"; that term belongs to the product
name only.
