# ADR 0004 — bee-fsrs is its own repository, vendored into BeeCode

- Status: accepted
- Date: 2026-07-29
- Satisfies the M0 "Reusable FSRS package" gate in `goals/YEAR-ONE.md`

## Context

The year-one plan makes the reusable FSRS package an M0 **gate**, with the fallback
column reading "None; this is a gate", and it blocks public distribution until "the
tagged package and clean-consumer smoke pass".

The first implementation did not meet that. The engine was copied into a `bee-fsrs/`
directory inside BeeCode with a provenance file, and a release was cut anyway. That
directory satisfied the *dependency* rules — dependency-free, clock-free, extractable —
but not the gate, which is specifically about a **separate versioned repository** and an
**external consumer resolving the same artifact**. Those two requirements exist because
kanji_anki depends on the same mathematics, and a copy inside one app is exactly how two
apps end up silently scheduling differently.

## Decision

[`bee-san/bee-fsrs`](https://github.com/bee-san/bee-fsrs) exists, is MIT-licensed, and is
tagged `v0.1.0`. It contains:

- the engine sources, byte-identical to kanji_anki's `fsrs-java` at commit `93f8c3f`;
- the 38-case upstream reference fixture;
- a Maven publication as `dev.bee:bee-fsrs`, with sources and POM;
- **a clean-consumer smoke build** — a separate Gradle build that resolves the library
  through its coordinate the way an unrelated project would;
- its own CI running the engine tests, the consumer tests, and a publish.

BeeCode's `bee-fsrs/` directory is now a **vendored checkout of that release**, not the
source of truth, and `FsrsProvenanceTest` asserts it is the version it claims to be.

## Why vendored rather than resolved

`dev.bee:bee-fsrs` is not on Maven Central yet. The options were:

| Option | Cost |
|---|---|
| Composite build / git submodule | A fresh clone or an offline build fails in ways that are irritating to diagnose. Chaquopy's Android build is already the fragile part of this project. |
| JitPack | Resolves from a tag without publishing, but puts a network dependency in every build including CI, and its failures are opaque. |
| **Vendor the released sources** | A copy can drift from the version it claims to be — mitigated by a test. |

Vendoring won, and the decision is cheap to reverse: `fsrs-adapter` is the only module
that imports `dev.bee.fsrs`, so moving to a resolved coordinate changes one dependency
declaration.

## The clean-consumer smoke test earns its place

It is not ceremony. It caught a wrong parameter name in its own first draft
(`maximumIntervalDays` for `maximumInterval`), and it is the only thing that can detect:

- an undeclared dependency, because that build declares only `dev.bee:bee-fsrs`;
- an `internal` type appearing in a public signature, which the engine's own tests can
  see and a consumer cannot.

Neither is visible from inside the engine's own test source set, because it can see
everything.

## On the "FSRS 7" label

kanji_anki's README describes its scheduler as "FSRS 7". BeeCode and bee-fsrs both label
it **FSRS-6.x, 21-parameter snapshot**. That is correct, but an earlier revision of this
ADR justified it with a false claim, and the correction matters more than the label.

**FSRS-7 exists.** It is `models/fsrs_v7.py` in
[`open-spaced-repetition/srs-benchmark`](https://github.com/open-spaced-repetition/srs-benchmark),
described there as "the newest version": 35 parameters, fractional interval lengths, and
a forgetting curve mixing two power laws under eight optimizable parameters. This ADR
previously stated that upstream "has published no v7". That was wrong.

**The engine here is still FSRS-6.x**, and the evidence is the parameter vector rather
than anyone's prose: `PARAMETER_COUNT` is 21, and the 21 defaults are byte-exact
py-fsrs `v6.3.1`. FSRS-7's vector is 35 long and its first four initial-stability values
differ. `FsrsProvenanceTest` now asserts both directions — that the count is 21, and that
the defaults are *not* FSRS-7's — so the distinction is enforced by a number.

**Adopting FSRS-7 is a project, not a version bump.** No published scheduler library
ships it: not py-fsrs, fsrs-rs, ts-fsrs, or Anki. It exists only as PyTorch and Rust
benchmark code, so there is no released artifact to vendor, and porting it would need
its own reference vectors. It also changes the shape of persisted state — 35 parameters
and fractional intervals rather than integer days — so `SRS-009`'s
preserve/reschedule/recompute machinery is the path, not a relabel.

Every BeeCode schedule transition records the algorithm label and the parameter hash,
which is what keeps that door open: old rows stay interpretable under a future engine.

### The plan's wording needs a decision

`goals/` still commits to "FSRS 7" in 12 places, including the year-one committed scope
and the M0 gate. Those were written when "FSRS 7" was taken to mean "the user's
current engine". Now that the two are known to be different algorithms, the plan is
either promising a 35-parameter port nobody has released, or it means FSRS-6.x and
should say so. **Shipped behaviour is FSRS-6.x**; the plan text is the part that is out
of date, and reconciling it is deliberately left as an open decision rather than being
silently rewritten to match what was built.

## Consequences

- Changing FSRS mathematics now means a change upstream plus a re-vendor. That friction
  is the point: it is what stops BeeCode forking an engine kanji_anki shares.
- BeeCode still builds offline and on a fresh clone with no extra setup.
- The M0 gate's remaining item — publishing to a public repository so an outside consumer
  can resolve it without vendoring — is done as far as the artifact and smoke test go.
  Maven Central publication is deferred and does not block BeeCode.
