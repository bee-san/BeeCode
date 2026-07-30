The complete local study loop, on Android and desktop. No account, no server, no
network access.

## Install

### Android

Download `BeeCode-*-android.apk` and open it on your phone. You will need to allow
installing from your browser or file manager the first time.

The APK contains CPython 3.12 for `arm64-v8a` and `x86_64`, so it needs no extra
setup — about 48 MB for that reason.

### macOS (Apple Silicon)

Download `BeeCode-*-macos-arm64.dmg`, open it, and drag BeeCode to Applications.

**Gatekeeper will refuse the first launch**, because this build is not signed with an
Apple Developer certificate. To open it anyway: right-click BeeCode in Applications,
choose **Open**, then confirm. Or run:

```bash
xattr -dr com.apple.quarantine /Applications/BeeCode.app
```

You also need Python 3 available as `python3`. macOS ships one; if BeeCode reports
Python as unavailable, install it from [python.org](https://www.python.org/downloads/)
or with `brew install python@3.12`, and if it still cannot find it you can set the
exact path in Settings.

The DMG is built on an Apple Silicon runner. **Intel Macs are not covered by this
release**, and will not be in future ones: GitHub retired its free Intel macOS
runners, and the replacements are billed per-minute with no free allowance. On an
Intel Mac you can still build your own with `./gradlew :desktopApp:packageDmg`,
which is the same command that produces the DMG above.

### Linux

Download the `.tar.gz`, extract it, and run `BeeCode/bin/BeeCode`. Needs `python3`.

## What works

- Read a Problem, write Python, run the tests, see what failed, fix it, and finalize
  the review.
- FSRS scheduling: better ratings mean longer intervals, and a lapse brings a Problem
  back sooner.
- 200 Problems with 1850 tests, 977 of them hidden, classified on two axes — what
  each one is made of and what it trains — so you can study by structure or by
  technique.
- Local statistics, a solve streak, and four achievements including the 5am Club.
- Export your whole profile to a file and restore it — merging rather than
  overwriting, so importing twice is safe.
- **Cross-device sync**, new in this release: a snapshot file you choose (in a folder
  Dropbox or Syncthing already replicates), or a WebDAV server. The merge is
  order-independent, so two devices syncing in either order reach the same result.
- **A Leaderboard queue** you can inspect in Settings. Nothing is uploaded — the
  server does not exist yet, and this is the client half only.

## What to know before trusting it with your practice

**The Android APK is signed with a locally generated debug key.** A future release
signed with a different key cannot upgrade it in place — you would have to uninstall
first, which deletes your local profile. **Export before updating**, or treat this
release as a trial rather than the start of a long streak.

**Neither Python runner is a security sandbox, and BeeCode says so in Settings.** On
Android your code runs inside the app's own process, so BeeCode cannot force an
infinite loop to stop — it stops waiting and asks you to restart. The app requests no
Android permissions at all, so your code has no network and no file access beyond
BeeCode's own storage. On desktop your code runs in a separate process BeeCode can
kill, but with your own user account's privileges.

**An export contains your solutions.** That is the point of a backup, and it is why
the file should be kept somewhere private. Your sync file too — it is written `0600`,
readable only by your own account.

**Where your WebDAV password is kept.** On Android, encrypted with a key in the
platform keystore, which on most devices is hardware-backed. On desktop, handed to your
OS keyring (`secret-tool` on Linux, `security` on macOS) so the profile itself holds no
credential. If no keyring is available — Windows, or a headless machine — it falls back
to storing the password unencrypted in the profile, and **Settings tells you which of
the two happened.** It is never included in an export or a sync payload.

*This is the least-tested thing in the release.* No machine available to this project
has either keyring binary installed, so the code is verified against a stand-in and
agreement with the real `secret-tool`/`security` is an assumption. If Settings claims
your password went to the keyring, it is worth confirming with
`secret-tool lookup service dev.bee.beecode.webdav account beecode-sync`.

## Not in this release

The Leaderboard server (the client queue is there; nothing is uploaded), Google Drive
sync, and a wider Problem curriculum. See the
[year-one plan](https://github.com/bee-san/BeeCode/blob/main/goals/YEAR-ONE.md).

## Verification

580 automated tests: 554 JVM tests across nine modules and 26 Android instrumented
tests, including the full answer → fail → fix → pass → finalize → restart journey
against real CPython and real SQLite on both platforms. None of the 554 skip.

Every Problem's reference solution is executed against every one of its declared
tests by real CPython on each build, so a wrong expected value fails the build
instead of failing a learner. The same gate proves each starter does *not* already
pass, and that no reference solution is readable before you choose to reveal it.

Both clients' UI is tested headlessly on every push. The Android UI is covered by
Robolectric on the JVM rather than by instrumented Compose tests: those need an emulator
that accepts injected touch input, and neither the dev host (no `/dev/kvm`) nor CI's
headless emulator provides one. Moving them to Robolectric is what got those assertions
running at all — previously they were written and never executed.

FSRS-7 (35 parameters) is checked against 384 reference vectors generated from
upstream's own `models/fsrs_v7.py`, at 1e-9 relative tolerance.
