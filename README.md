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

BeeCode is at the beginning of a year-scale build. The repository will grow in
verified vertical slices, with the complete roadmap tracked under
[`goals/`](goals/README.md).

## Naming

- **BeeCode** — the application.
- **Problem** — a study item and coding challenge.
- **Review** — one scheduled attempt at a Problem.
- **Leaderboard** — a private group in which friends compare activity.

Avoid calling individual Problems “BeeCodes”; that term belongs to the product
name only.

