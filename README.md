# BeeCode

BeeCode is an offline-first spaced-repetition app for practising LeetCode-style
algorithm Problems. It runs on Android and desktop, schedules reviews with
[bee-fsrs](https://github.com/bee-san/bee-fsrs) (FSRS-7, Kotlin), and lets you write
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
| Sync between devices | ✅ file or WebDAV | ✅ file or WebDAV |
| Leaderboard queue | ✅ Settings → Leaderboard | ✅ Settings → Leaderboard |
| Credential storage | Android Keystore (hardware-backed on most devices) | OS keyring, or plaintext where none exists |
| Themes | 3 families x follow-OS/dark/light | 3 families x follow-OS/dark/light |
| Verified by | 49 JVM tests, 29 of them Robolectric UI, + 27 instrumented | 83 JVM tests, 39 of them UI |

**667 automated test cases**: 640 JVM tests across nine modules and 27 Android
instrumented tests. All 640 JVM tests and 18 non-UI device tests run in CI; the hosted
emulator skips 9 Compose touch tests because it refuses injected input. The passing
device tests include the complete answer → fail → fix → pass → finalize → restart
journey against real CPython and real SQLite.

Each build also runs every Problem's reference solution against every one of its
declared tests under real CPython, checks that each starter does *not* already pass,
and checks that no reference solution is reachable before the learner chooses to
reveal it. A wrong expected value fails the build rather than a learner.

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
run. Robolectric removes the emulator from the question, so the 19 UI assertions now run
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

The Problem pack holds **200 Problems** — 46 easy, 128 medium, 26 hard — with 1850
tests, 977 of them hidden. That is well past the year-one target of 12–20, and it
covers all four comparators; `any_of` and `approximate_numeric` had been implemented
but unused by any Problem, so those code paths shipped untested.

Every Problem is classified on two axes — the structures it is made of and the
techniques it trains — against a closed vocabulary in `taxonomy.yaml`, so a typo fails
the build instead of quietly creating a topic with one Problem in it. No slug in that
vocabulary is unused.

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
token is a content hash on both so they agree on what "unchanged" means. Verified on a
real device: two profiles converge through one file, with the schedule replayed and the
source transferred. Android still
declares **no storage permission** — it holds a persisted URI grant for the one file you
chose, which the system gives and you can revoke.

**The Leaderboard's client half is built, without a server.** Its two hard problems are
not networking — they are never double-counting a solve, and never uploading what a
learner did not consent to share. Both are pure state machines, so both are done and
verified now:

- `ActivityOutbox` — pending → in-flight → acknowledged, with parked and rejected kept
  distinct because the difference is whether a *server* decided. A duplicate counts as
  success, since a client that timed out cannot tell "accepted" from "already had it".
  A process kill mid-upload recovers; repeated outages park rather than drain a battery.
- `ActivityProjection` — no pre-link backfill, and only unaided passes count. Events are
  *derived* from the append-only log with the review's own session id as the idempotency
  key, so an outbox lost to a reinstall rebuilds identically instead of double-counting.

`ActivityEvent` cannot carry source, output, test values, or FSRS state — the type has
nowhere to put them, and a test asserts the exact field set so adding one is deliberate.

The queue is **durable** (schema v3), keyed by the review's own session id so one solve
cannot enqueue twice across a restart. `LeaderboardService` composes the three pieces, and
`refreshLeaderboardActivity()` is deliberately *not* wired into finalization: "review
finalization never waits for network" is only credible if finalization cannot fail for a
Leaderboard reason. It is off until an account is linked, and unlinking clears the queue
without touching a single review.

**WebDAV works, on both clients.** A shared file is the zero-setup option; a WebDAV
server — Nextcloud, ownCloud, Synology, `rclone serve webdav` — is the stronger one,
because `If-Match` makes the *server* refuse a stale write. That closes the one limitation
ADR 0002 recorded: the file store's compare-and-swap is a read-verify-write, which cannot
be made atomic without file locking that behaves differently on every platform. 19 tests
against a real HTTP server, and no new dependency — `HttpURLConnection` works on every
Android version BeeCode supports, where `java.net.http` would raise minSdk from 26 to 34.

On Android the WebDAV password is **encrypted with a key in the platform keystore** —
hardware-backed on most devices, so a database copied off the phone decrypts to nothing.
GCM specifically, so a tampered ciphertext fails rather than becoming plausible garbage
that would be sent to a server as a password. Desktop still stores it in the clear and its
UI says so: there is no cross-platform JVM keystore that is not either a large dependency
or a keystore protected by a password stored beside it. The asymmetry is real rather than
uniform pessimism.

Sync credentials never leave the device either: not in an export, not in a sync payload,
not merged from a remote one. That needed fixing rather than just adding — export and merge
excluded exactly one key, so a password would have travelled by default into every backup
and up to the server it authenticates to.

Both clients surface the queue under Settings → Leaderboard, with identical wording,
because where two clients make the same privacy promise the wording *is* the product. The
card says plainly that **the server does not exist yet** — a Join button with nothing
behind it would otherwise read as working — and states what a board would see (counts and
streaks, never code or output or schedule) before the join button rather than after.

Not built yet: the Leaderboard **server** — the client half is complete and its transport
is a lambda parameter, so the server is separable work — and Google Drive sync (which
needs OAuth, where WebDAV covers self-hosting without it).
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
- **Your profile folder is locked to your own user account** on desktop (0700, applied on
  every launch so older installs are fixed too). It was created 0755 — world-readable —
  which was invisible and wrong on any shared machine. The sync file gets the same
  treatment (0600, set before the rename so it is never briefly world-readable).
- **The desktop WebDAV password goes to your OS keyring, where there is one.**
  `secret-tool` on Linux, `security` on macOS — both are already installed with the
  desktop, so this adds no dependency. The profile then holds a marker, not the password,
  so a copy or backup of it contains no credential at all. Where no keyring is found
  (Windows, a headless box, a locked keyring) it falls back to storing the password in the
  profile exactly as before, and **Settings says which of the two happened** rather than
  making one promise for both.
  What is *not* verified: that a real `secret-tool` or `security` honours those commands.
  BeeCode's side of the pipe is tested against a stand-in client — arguments, stdin, the
  round trip, a refusal, an absent binary — but neither binary exists on this dev host or
  in CI, so agreement with the real ones is an assumption, not a test result.
  It also does not hide the secret from *your own account*: `secret-tool lookup` returns
  it, by design. What it stops is the credential travelling inside a file.

## Themes and accessibility

Appearance is **two independent settings**, not one list. A **family** picks the colours;
a **mode** picks dark, light, or follow-the-OS. They are separate so that "follow the
system" keeps working inside whichever family you chose — a single flat list has no entry
meaning "high contrast, tracking the OS", so that combination would either be missing or
would silently drop you back to the default colours.

| Family | What it is for |
|---|---|
| **Honey** | Warm amber. BeeCode's original colours. |
| **High contrast** | Maximum legibility. Body text meets WCAG **AAA** (7:1). |
| **Slate** | Cool blue-grey, easier on the eyes over a long session. |

Every family in both modes is checked against WCAG **AA** (4.5:1 body, 3:1 large) by a
test that walks the palettes, and High contrast is held to **AAA** because it advertises
it — a family whose whole promise is legibility should fail the build, not the learner, if
it stops keeping that promise. All 48 Material colour roles are assigned explicitly rather
than inherited, so a role added by a Material upgrade cannot quietly reintroduce the
purple baseline.

The accessibility work behind those numbers:

- **Nothing is signalled by colour alone** (WCAG 1.4.1). Every pass, failure, timeout, and
  cancellation carries a glyph — `✓`, `✗`, `!`, `–` — chosen to differ in *shape*, so the
  four states are distinguishable in greyscale and to anyone who cannot separate the reds
  from the greens.
- **Icons that carry information are labelled, and the rest deliberately are not.** A
  per-test row's `✓`/`✗` and an achievement's star/lock are the only two icons in either
  client that say something the surrounding text does not, so those get spoken labels
  ("Passed"/"Failed", "Earned"/"Not yet earned"). Everything else sits beside its own text
  label, and describing those would make a screen reader announce every destination twice
  — so they stay unlabelled, with the reason recorded at the call site rather than left
  for the next audit to re-litigate.
- **The desktop client is drivable from the keyboard alone.** Verified by walking the UI
  one keystroke at a time rather than by inspecting modifiers, which is what found the
  defect: the code editor claims Tab for Python indentation — it has to, indentation is
  syntax — and *Run tests* sits after the editor in traversal order, so forward tabbing
  dead-ended in the editor and the pane's primary action was reachable only by tabbing
  backwards from the top. **Escape** now advances out of the editor to the next control
  instead of merely releasing focus.

The spoken labels and the glyphs live in `shared/`, so the two clients cannot word the
same state differently — a divergence fails a test rather than shipping.

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
content/packs/   The Problem pack: 200 Problems, 1850 tests
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
