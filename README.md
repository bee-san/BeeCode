# BeeCode

BeeCode is an offline-first spaced-repetition app for practising LeetCode-style
algorithm Problems. It runs on Android and desktop, schedules reviews with FSRS,
and lets you write and run Python solutions inside each review.

Everything is local. There is no account, no server, and no network access.

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
| Verified by | 18 instrumented tests on an API 35 x86_64 emulator | 21 JVM tests |

**232 automated tests**: 214 JVM tests across eight modules and 18 Android
instrumented tests, including the complete answer → fail → fix → pass → finalize →
restart journey against real CPython and real SQLite on both platforms.

One caveat stated plainly: 9 of the Android tests are Compose UI tests that need a
rendering-capable emulator, and they **skip** on the automated-test-device image a
host without `/dev/kvm` is limited to. CI enables KVM so they run for real. The 9
behavioural tests — which cover the full study journey, the timeout, and the
no-network check — have no such requirement and always run.

Not built yet: the private Leaderboard, personal cross-device sync, and the wider
Problem curriculum. See [the year-one plan](goals/YEAR-ONE.md).

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
bee-fsrs/        FSRS-6.x memory mathematics, vendored with provenance
domain/          Pure models, review state machine, and the rating policy
fsrs-adapter/    BeeCode's review policy over bee-fsrs
python-api/      Execution contracts and the shared Python harness
persistence/     SQLite schema, migrations, exactly-once finalization
content-tools/   Problem loading, validation, and pack compilation
shared/          Study loop, statistics, achievements, export/restore
androidApp/      Android client and the Chaquopy runner
desktopApp/      Desktop client and the process runner
content/packs/   The Problem pack: 12 Problems, 83 tests
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
- [FSRS provenance](bee-fsrs/PROVENANCE.md)
- [Contributing](CONTRIBUTING.md)

## Naming

- **BeeCode** — the application.
- **Problem** — a study item and coding challenge.
- **Review** — one scheduled attempt at a Problem.
- **Leaderboard** — a planned private group in which friends compare activity.

Avoid calling individual Problems "BeeCodes"; that term belongs to the product
name only.
