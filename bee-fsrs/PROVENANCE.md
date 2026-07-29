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
| Algorithm | FSRS-6.x, 21-parameter snapshot — **not** FSRS-7, which has 35 |
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
FSRS-6.x. Both facts are worth stating precisely, because an earlier version of this
file got the reasoning wrong.

**FSRS-7 does exist.** It is defined in
[`open-spaced-repetition/srs-benchmark`](https://github.com/open-spaced-repetition/srs-benchmark)
as `models/fsrs_v7.py`, whose README calls it "the newest version". It is a genuine
algorithm revision, not a loose label: **35 parameters** (indices 0–34), designed for
*fractional* interval lengths, with a forgetting curve that mixes two power laws under
eight optimizable parameters. On that benchmark it beats FSRS-6 on log loss.

**This code is not it.** The distinguishing evidence is in the sources, not in anyone's
README:

| | this engine | py-fsrs `v6.3.1` | FSRS-7 |
|---|---|---|---|
| Parameter count | **21** | 21 | **35** |
| First four defaults | 0.212, 1.2931, 2.3065, 8.2956 | identical | 0.041, 2.4175, 4.1283, 11.9709 |
| Forgetting curve | single power law | single power law | 8-parameter mixed power |
| Interval lengths | integer days | integer days | fractional |

`FsrsParameters.PARAMETER_COUNT` is 21 and the defaults are byte-exact py-fsrs
`v6.3.1`. kanji_anki's own documentation agrees with that reading — its
`docs/ladder-and-srs-system.md`, `docs/modularization-roadmap.md`, and
`FsrsWeightFitter.kt` ("FSRS-6 bounds from the upstream optimizer's
`parameter_clipper.rs`") all say FSRS-6, and its `FsrsAlgorithmInfo.kt` self-labels
`"FSRS-6.x 21-parameter snapshot"`. Only the README line says otherwise.

**Correction to an earlier claim.** This file previously asserted that upstream "has
published no v7". That was false. The conclusion it was used to support — that this code
is FSRS-6 — happens to be true for an entirely different reason: the parameter count. A
claim about what exists upstream was never the right evidence; the numbers in the file
are.

**Adopting FSRS-7 is real work, not a relabel.** No published scheduler library ships
it — not py-fsrs, not fsrs-rs, not ts-fsrs, not Anki itself. It exists as PyTorch and
Rust research/benchmark code, so there is nothing to port from a released artifact. It
also changes the *shape* of a schedule: 35 parameters instead of 21, and fractional
intervals, so persisted memory state and the interval calculation both change.

BeeCode records `FsrsAlgorithmInfo.ALGORITHM_LABEL` and the parameter hash with every
stored schedule transition, which is what makes a future migration possible: old rows
stay interpretable, and adopting FSRS-7 is a decision with a fixture diff rather than a
silent change of mathematics under an existing history.
