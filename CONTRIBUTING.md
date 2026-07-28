# Contributing to BeeCode

BeeCode is being developed in small, runnable slices. A change is complete only
when its behavior, verification, and relevant goal evidence land together.

## Working agreement

1. Use **Problem** for study content and **BeeCode** for the application.
2. Prefer a vertical slice over an isolated framework layer.
3. Keep shared domain code independent of UI and platform runtimes.
4. Keep generated files out of source control unless they are deliberate test
   fixtures.
5. Add or update automated tests for behavioral changes.
6. Record architectural decisions that constrain later work.
7. Never upload learner source code, test output, or FSRS memory state to the
   Leaderboard service.

## Commit style

Use focused conventional commits such as:

- `docs: define the Problem authoring goals`
- `feat(fsrs): schedule successful Problem reviews`
- `feat(runner): enforce desktop process timeouts`
- `test(achievements): cover the 5am Club boundary`

Do not mix formatting, generated artifacts, and product behavior in one commit.

## Definition of done

A target is done when:

- its acceptance criteria in `goals/` are met;
- the relevant automated checks pass;
- desktop and Android impact has been considered;
- failure behavior is visible and recoverable;
- security and privacy assumptions are documented;
- a fresh contributor can reproduce the result from repository instructions.

