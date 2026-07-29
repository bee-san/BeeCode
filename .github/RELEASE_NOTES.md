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
release.**

### Linux

Download the `.tar.gz`, extract it, and run `BeeCode/bin/BeeCode`. Needs `python3`.

## What works

- Read a Problem, write Python, run the tests, see what failed, fix it, and finalize
  the review.
- FSRS scheduling: better ratings mean longer intervals, and a lapse brings a Problem
  back sooner.
- 16 Problems with 126 tests, 30 of them hidden.
- Local statistics, a solve streak, and four achievements including the 5am Club.
- Export your whole profile to a file and restore it — merging rather than
  overwriting, so importing twice is safe.

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
the file should be kept somewhere private.

## Not in this release

The private Leaderboard, cross-device sync, and a wider Problem curriculum. See the
[year-one plan](https://github.com/bee-san/BeeCode/blob/main/goals/YEAR-ONE.md).

## Verification

216 JVM tests and 18 Android instrumented tests, including the full
answer → fail → fix → pass → finalize → restart journey against real CPython and real
SQLite on both platforms.

One honest gap: 9 of the Android tests are Compose UI tests needing an emulator that
accepts injected touch input, which neither the dev host nor CI's headless emulator
provides. They are written but have not yet run, so treat the UI's finer details as
less verified than its behaviour.
