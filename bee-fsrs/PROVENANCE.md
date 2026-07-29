# bee-fsrs in BeeCode

**The engine now lives in its own repository:
[`bee-san/bee-fsrs`](https://github.com/bee-san/bee-fsrs).**

This directory is a *vendored checkout* of the released `v0.1.0` sources, kept so that
BeeCode builds offline and on a fresh clone with no extra setup. It is not the place to
change the engine.

## Where to make changes

| Change | Where |
|---|---|
| FSRS mathematics, parameters, engine API | [`bee-san/bee-fsrs`](https://github.com/bee-san/bee-fsrs), then re-vendor |
| Which ratings BeeCode permits, due-queue order, persistence | `fsrs-adapter/` in this repository |

Editing the sources here without upstreaming them would silently fork the engine, and
the whole point of the split is that BeeCode and kanji_anki resolve the *same* tested
artifact. `FsrsProvenanceTest` asserts the vendored copy matches the released version
it claims to be.

## What is vendored

| Field | Value |
|---|---|
| Package | `dev.bee:bee-fsrs` |
| Version | `0.1.0` |
| Upstream repository | https://github.com/bee-san/bee-fsrs |
| Release tag | [`v0.1.0`](https://github.com/bee-san/bee-fsrs/releases/tag/v0.1.0) |
| Algorithm | FSRS-6.x, 21-parameter snapshot |
| Ports | `open-spaced-repetition/py-fsrs` `v6.3.1`, commit `3abe686e9c058d3f3c00bbeb92e68b71211b2b31` |
| Originally from | `bee-san/kanji_anki`, module `fsrs-java`, commit `93f8c3fe756944312d96c559b8d29701af43f5d0` |
| License | MIT |

Full chain of custody, the extraction rules, and the reasoning about the version label
are in the upstream repository's
[PROVENANCE.md](https://github.com/bee-san/bee-fsrs/blob/main/PROVENANCE.md).

## Why vendored rather than resolved from a repository

`dev.bee:bee-fsrs` is not published to Maven Central yet. The realistic options were:

- **A composite build or git submodule.** Both make a fresh clone or an offline build
  fail in ways that are annoying to diagnose, and Chaquopy's Android build is already
  the fragile part of this project.
- **JitPack.** Resolves from a tag without publishing, but adds a network dependency to
  every build including CI, and its build failures are opaque.
- **Vendoring the released sources.** Builds offline, no extra configuration, and the
  version claim is verified by a test.

Vendoring won on the trade, and the decision is reversible: `fsrs-adapter` is the only
module that imports `dev.bee.fsrs`, so switching to a resolved coordinate later changes
one dependency declaration.

## On the "FSRS 7" label

kanji_anki's README describes its scheduler as "FSRS 7". This code is labelled
FSRS-6.x, and that is deliberate:

- upstream py-fsrs has published **no v7**; its latest release is `v6.3.1`;
- kanji_anki's own planning notes say *"Algorithm label is FSRS-6-family 21-parameter
  snapshot, not FSRS-7 unless upstream explicitly labels it that way"*;
- the 21-parameter count matches the FSRS-6 family.

BeeCode records `FsrsAlgorithmInfo.ALGORITHM_LABEL` with every stored schedule
transition, so if upstream ever does publish a v7, old rows stay interpretable and the
upgrade is a gated decision rather than a silent relabel.
