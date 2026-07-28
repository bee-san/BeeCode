# BeeCode

BeeCode is an offline-first spaced-repetition app for practising
LeetCode-style algorithm Problems. It targets Android phones and desktop
computers, schedules reviews with the user's FSRS 7 engine from
[`bee-san/kanji_anki`](https://github.com/bee-san/kanji_anki), and lets a
learner write and run Python solutions inside each review.

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
- **Execution has an honest capability contract.** Python code receives bounded
  time/output and is kept outside the UI process where the platform design
  permits, but v1 is not claimed to be a hardened hostile-code sandbox.
- **Social is optional and small.** Private custom Leaderboards synchronize
  activity metadata, never source code or FSRS state.

## Status

This repository currently contains the **planning baseline only**. It does not
contain an application scaffold or claim that any feature has been built.

- [High-level architecture](docs/architecture.md)
- [North-star catalogue: 164 goals across all targets](goals/README.md)
- [Realistic year-one execution roadmap](goals/YEAR-ONE.md)
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
