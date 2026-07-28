# ADR 0001: Product boundaries

- Status: Accepted
- Date: 2026-07-28

## Context

BeeCode combines spaced repetition, coding execution, achievement tracking, and
a small social surface. Without firm boundaries, these concerns can make the
client dependent on a server or leak sensitive learner data.

## Decision

BeeCode is an offline-first local application with an optional, metadata-only
Leaderboard service.

The local application owns:

- Problem content and revisions;
- source code and test output;
- review history and FSRS memory state;
- due-date calculation;
- achievement evaluation;
- detailed personal statistics.

The Leaderboard service owns:

- accounts and authentication;
- private Leaderboard membership and invitations;
- idempotent activity receipts;
- aggregate ranks and server-accepted friendly-trust social achievements.

The server never needs a learner's solution source, test output, or FSRS state.

## Consequences

- A learner can use every study feature without creating an account.
- Sync conflicts concern append-only activity and account metadata, not
  scheduling truth.
- Some anti-cheat measures remain intentionally lightweight because the social
  feature is friendly competition rather than a prize-bearing contest.
- Platform-specific Python sandboxes may differ internally, but they must
  implement one shared execution contract.

## Revisit when

Revisit this boundary only if BeeCode adds cross-device study-state sync. That
feature requires its own encryption, conflict-resolution, and migration design;
it must not be smuggled through the Leaderboard protocol.
