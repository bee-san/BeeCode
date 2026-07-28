# BeeCode

BeeCode is an offline-first spaced-repetition app for practising coding
Problems. It targets Android phones and desktop computers, schedules reviews
with FSRS, and lets a learner write and run Python solutions inside each
review.

The product is intentionally built around **Problems**, not generic cards:

- a Problem contains its prompt, starter code, examples, executable tests, and
  stable identity;
- solving and reviewing a Problem is one continuous flow;
- successful reviews feed the scheduler, achievements, and an optional private
  Leaderboard;
- solution source stays on the learner's device.

## Product commitments

- **Desktop and Android are first-class.** The core behavior and data model are
  shared, while each platform owns the runtime and security details it needs.
- **Offline comes first.** Studying, scheduling, achievements, and local
  statistics do not require a server.
- **Problem authoring is repository-native.** A contributor adds one
  self-contained directory; validation and indexing are automated.
- **Execution is treated as untrusted.** Python code runs behind explicit time,
  memory, process, filesystem, and network boundaries appropriate to each
  platform.
- **Social is optional and small.** Private custom Leaderboards synchronize
  activity metadata, never source code or FSRS state.

## Status

This repository currently contains the **planning baseline only**. It does not
contain an application scaffold or claim that any feature has been built.

- [High-level architecture](docs/architecture.md)
- [Ultra-deep year roadmap and all targets](goals/README.md)
- [Architecture decisions](docs/adr/README.md)

The plan contains 164 stable goals across product, architecture, Problem
authoring, Python execution, FSRS reviews, desktop, Android, achievements,
Leaderboards, data recovery, security, testing, release operations, and
accessibility. Each goal defines acceptance evidence and non-goals so later
implementation can proceed in verified vertical slices.

## Naming

- **BeeCode** — the application.
- **Problem** — a study item and coding challenge.
- **Review** — one scheduled attempt at a Problem.
- **Leaderboard** — a private group in which friends compare activity.

Avoid calling individual Problems “BeeCodes”; that term belongs to the product
name only.
