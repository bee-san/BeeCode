# bee-fsrs provenance

`bee-fsrs` is the FSRS memory-mathematics engine BeeCode schedules with. It is
kept as a self-contained, dependency-free module so it can be extracted to the
independent `bee-san/bee-fsrs` repository without edits.

## Chain of custody

| Layer | Identity |
|---|---|
| Algorithm | FSRS-6.x, 21-parameter snapshot |
| Upstream reference | `open-spaced-repetition/py-fsrs` v6.3.1 |
| Upstream commit | `3abe686e9c058d3f3c00bbeb92e68b71211b2b31` |
| Upstream scheduler blob | `6d42ecb259bbaaa02101f13c5e1b2ec7cdc77eae` |
| Kotlin implementation | [`bee-san/kanji_anki`](https://github.com/bee-san/kanji_anki), module `fsrs-java` |
| Source commit | `93f8c3fe756944312d96c559b8d29701af43f5d0` |
| Source tree object | `c3a95c555bfc717de0606f1345cec3c3774d60e4` |
| Vendored into BeeCode | 2026-07-29 |
| Author / rights holder | Autumn Skerritt (`bee-san`), same owner as BeeCode |

The upstream identity is also asserted in code by `FsrsAlgorithmInfo` and
verified by `FsrsEngineReferenceTest`, so a silent algorithm swap fails the
build rather than silently changing every learner's schedule.

## What was and was not changed during extraction

Changed:

- the Gradle build file, to drop the `kani.*` convention plugins this repository
  does not have;
- one test path fallback, `fsrs-java/testdata/…` → `bee-fsrs/testdata/…`.

Not changed: every `src/main` source file is byte-identical to the source
commit. The 38-case reference fixture is byte-identical. No mathematics, no
parameter values, no validation, and no public API were touched. This matters
because the fixture is the engine's oracle: if the maths had drifted during a
copy-paste, the fixture would be the only thing that noticed.

## Extraction rules

These rules keep the eventual repository split mechanical:

1. **No BeeCode types.** The engine knows nothing about Problems, reviews,
   sessions, or ratings-from-evidence. It takes an `FsrsRating` and returns
   memory state.
2. **No dependencies beyond the Kotlin stdlib.** No coroutines, no
   serialization, no datetime, no logging. A consumer must never inherit a
   transitive dependency from the scheduler.
3. **No clock and no I/O.** Elapsed days are an input, never read from a clock.
   This is what makes the engine testable and its results reproducible.
4. **Pure and total.** Every function is deterministic and either returns a
   value or throws on invalid input. No nullable-success returns.
5. **Additive API changes only** within a major version, because BeeCode records
   the package version in every stored schedule transition and must be able to
   interpret old rows.

## Why BeeCode records so much per transition

BeeCode stores the algorithm ID, package version, parameter set and hash,
previous-state hash, elapsed days, and rating alongside every resulting state.
That is deliberate redundancy: it means operational state can be rebuilt by
folding recorded *outputs*, with no old engine binary present. Recomputing
history from inputs is then only an integrity check, available when the exact
historical implementation still is.

## Upgrade gate

Changing the engine version or the default parameters is not a routine
dependency bump; it silently rewrites future due dates for existing learners.
The reference fixture must pass on both the desktop and Android compositions
before an upgrade may change any schedule.
