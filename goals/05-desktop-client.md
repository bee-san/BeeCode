# Target 05: desktop client

This target makes desktop BeeCode the fastest place to author, code, inspect
results, and complete review sessions. “Desktop first” is a sequencing choice
for runner and editor feedback, not permission to make Android second-class.

## Planned information architecture

| Destination | Primary job |
|---|---|
| Today | Start due/new Problems and see today's workload. |
| Problems | Search, filter, preview, suspend, and inspect all installed content. |
| Workspace | Recall, edit Python, run tests, inspect results, and finalize. |
| History | Inspect attempts, reviews, ratings, due transitions, and saved source. |
| Achievements | View progress, unlocks, and equip a title. |
| Leaderboards | Opt into private boards and inspect rankings/sync. |
| Settings | Configure study, runtime, appearance, data, privacy, and account. |

The review workspace can use resizable panes on large displays, but it must
collapse into focused modes for small laptops and accessibility.

## DSK-001 — Define supported desktop targets

- **State:** proposed
- **Outcome:** the project claims only operating systems and architectures it
  can build, package, and test.
- **Deliverables:** OS/version/architecture matrix, JDK/Python strategy,
  packaging format, support window, and phased rollout decision.
- **Acceptance:**
  - Windows, macOS, and Linux are each explicitly supported, deferred, or
    experimental.
  - x86_64/ARM64 status is stated per OS.
  - Python distribution/discovery is part of the decision, not assumed.
  - Each supported target has a smoke and upgrade path.
- **Evidence:** matrix attached to milestone/release evidence.
- **Dependencies:** PROD-005, RUN-002.
- **Risks:** trying to ship three OSes before signing and runtime differences
  are understood.
- **Non-goals:** accidental support because a developer build happens to run.

## DSK-002 — Build the desktop application shell

- **State:** proposed
- **Outcome:** navigation, window state, error boundaries, and application
  lifecycle are stable enough for vertical slices.
- **Deliverables:** top-level navigation, dependency assembly, window
  persistence, theme, offline state, and global error presentation.
- **Acceptance:**
  - Every planned primary destination is reachable.
  - Closing during an edit requests durable flush without blocking forever.
  - Unexpected destination errors do not lose the whole process where recovery
    is safe.
  - Navigation and window state have deterministic restoration behavior.
- **Evidence:** UI smoke suite.
- **Dependencies:** ARCH-002, ARCH-003.
- **Risks:** navigation framework complexity.
- **Non-goals:** polished final visual design in the shell milestone.

## DSK-003 — Deliver the review workspace

- **State:** proposed
- **Outcome:** the learner can complete the north-star loop without leaving one
  coherent workspace.
- **Deliverables:** prompt, examples, recall state, source editor, run controls,
  results, reveal action, reflection, rating, next interval, and completion
  summary.
- **Acceptance:**
  - Statement and editor sizes are adjustable.
  - Run/cancel/result states mirror the execution contract.
  - Finalization is visibly separate from passing tests.
  - Reveal behavior changes rating/achievement eligibility.
  - Leaving preserves draft without creating a review.
- **Evidence:** end-to-end reference Problem script and state screenshots.
- **Dependencies:** PROB-010, RUN-007, SRS-003.
- **Risks:** too many controls obscuring recall.
- **Non-goals:** a multi-file IDE in v1.

## DSK-004 — Establish code-editor fundamentals

- **State:** proposed
- **Outcome:** writing common Python solutions is comfortable and predictable.
- **Deliverables:** line numbers, monospaced font, Python highlighting,
  indentation, bracket behavior, selection, find, undo/redo, horizontal scroll,
  error location, font controls, and autosave integration.
- **Acceptance:**
  - Editing remains responsive at the maximum supported source size.
  - Tab/Shift-Tab and newline indentation behave predictably.
  - Undo history is not destroyed by autosave or result decoration.
  - Syntax navigation never mutates source.
  - IME, clipboard, and Unicode cases are tested.
- **Evidence:** editor interaction suite and keyboard usability session.
- **Dependencies:** RUN-008, UX-003.
- **Risks:** an editor component consuming the project.
- **Non-goals:** refactoring, language server, debugger, Git, terminal, or
  arbitrary project files.

## DSK-005 — Make the entire review keyboard-first

- **State:** proposed
- **Outcome:** a learner can solve and review without pointer travel.
- **Deliverables:** shortcut map, focus traversal, command palette/help,
  conflict policy, platform conventions, and discoverability.
- **Acceptance:**
  - Shortcuts cover run, cancel, results, pane focus, reveal, finalize, rating,
    queue navigation, and escape/back.
  - Focus is always visible.
  - Text-editing shortcuts retain expected platform behavior.
  - Destructive actions require deliberate confirmation.
- **Evidence:** keyboard-only acceptance script on each supported OS.
- **Dependencies:** DSK-003, UX-002.
- **Risks:** shortcut collisions with editor and assistive technology.
- **Non-goals:** Vim/Emacs emulation in v1.

## DSK-006 — Build the Today queue

- **State:** proposed
- **Outcome:** the learner understands workload and can begin the right Problem
  quickly.
- **Deliverables:** due/new sections, counts, estimated time, ordering,
  refresh behavior, completion progress, and empty/error states.
- **Acceptance:**
  - Queue reflects the injected study clock and SRS ordering.
  - Changes after finalization appear atomically.
  - A Problem can be opened, postponed/suspended where allowed, or inspected.
  - Empty states distinguish “done”, “limited”, “no content”, and “data error”.
- **Evidence:** queue integration and clock tests.
- **Dependencies:** SRS-005.
- **Risks:** estimates being mistaken for promises.
- **Non-goals:** a calendar-planning suite.

## DSK-007 — Build the Problem library

- **State:** proposed
- **Outcome:** installed Problems remain discoverable beyond the due queue.
- **Deliverables:** search, pack/topic/difficulty/state filters, sort,
  prerequisites, preview, revision status, suspend/restore, and installed-pack
  view.
- **Acceptance:**
  - Queries stay within performance budget for 10,000 Problems.
  - Filters compose predictably and have clear reset.
  - Removed/incompatible Problems remain identifiable from history.
  - Starting unscheduled practice does not silently mutate SRS state.
- **Evidence:** seeded query and UI regression suite.
- **Dependencies:** PROB-004, SRS-005.
- **Risks:** catalog browsing displacing due reviews.
- **Non-goals:** a public Problem marketplace in v1.

## DSK-008 — Expose history and schedule truth

- **State:** proposed
- **Outcome:** learners can inspect what happened without editing canonical
  history accidentally.
- **Deliverables:** chronological reviews, run summaries, rating, due changes,
  achievement effects, source snapshot access, filters, and correction flow.
- **Acceptance:**
  - Review and run attempts are distinguished.
  - Exactly-once IDs and schedule versions are available in advanced detail.
  - Corrections use explicit audit events.
  - Missing/retired content does not make history unreadable.
- **Evidence:** history snapshot and replay integration tests.
- **Dependencies:** SRS-011, ACH-003.
- **Risks:** exposing source snapshots without privacy clarity.
- **Non-goals:** editing old reviews in place.

## DSK-009 — Provide complete settings and diagnostics

- **State:** proposed
- **Outcome:** study behavior, runtime health, data ownership, appearance, and
  optional social configuration are controllable.
- **Deliverables:** review settings, timezone, runtime status, themes/fonts,
  accessibility, storage, export/import, privacy, server URL, account, and
  diagnostic bundle.
- **Acceptance:**
  - Invalid values explain allowed bounds.
  - Schedule-changing settings preview consequences where material.
  - Account/social settings are absent from the local critical path.
  - Diagnostic export defaults to excluding source and secrets.
- **Evidence:** settings persistence, migration, redaction, and invalid-value
  tests.
- **Dependencies:** DATA-003, SRS-009, SEC-011.
- **Risks:** an unstructured settings dump.
- **Non-goals:** exposing unsafe experimental toggles in stable builds.

## DSK-010 — Recover edits and sessions after crashes

- **State:** proposed
- **Outcome:** process termination during any workspace state is boring and
  recoverable.
- **Deliverables:** durable autosave checkpoints, recovered-session prompt,
  incomplete-run cleanup, uncertain-finalization reconciliation, and safe mode.
- **Acceptance:**
  - Killing the process during typing restores the latest durable source.
  - Killing during execution yields no false result/review.
  - Killing during finalization shows either the committed review or a safe
    retry, never a duplicate.
  - Recovery works after application upgrade.
- **Evidence:** scripted process-kill suite.
- **Dependencies:** RUN-008, SRS-006, DATA-009.
- **Risks:** hard-to-reproduce transaction/UI races.
- **Non-goals:** restoring unsaved keystrokes that never reached durable state.

## DSK-011 — Adapt across desktop form factors

- **State:** proposed
- **Outcome:** BeeCode remains usable on a small laptop, standard monitor,
  ultrawide, high-DPI display, and scaled text.
- **Deliverables:** breakpoints, pane collapsing, resize constraints, focus
  mode, scroll ownership, and display persistence.
- **Acceptance:**
  - Primary actions remain visible or discoverable at minimum supported size.
  - 200% UI/text scaling does not clip critical controls.
  - Moving between different-DPI monitors recovers.
  - Pane sizes reset safely when a screen disappears.
- **Evidence:** screenshot/layout matrix and manual high-DPI checks.
- **Dependencies:** UX-001, DSK-003.
- **Risks:** overly desktop-specific shared composables.
- **Non-goals:** pixel-identical layout across OSes.

## DSK-012 — Package, sign, install, upgrade, and uninstall

- **State:** proposed
- **Outcome:** a user receives a coherent native desktop product rather than a
  developer launch command.
- **Deliverables:** packages for declared OSes, embedded/discovered runtime
  policy, icons/metadata, signing/notarization, update channel, migration, and
  uninstall-data policy.
- **Acceptance:**
  - Fresh install and upgrade preserve study data.
  - Missing Python/runtime components have a repair path.
  - Package signature/checksum can be verified.
  - Uninstall clearly distinguishes application from user data.
- **Evidence:** clean VM install/upgrade/uninstall matrix.
- **Dependencies:** DSK-001, OPS-003.
- **Risks:** signing/notarization delayed until release.
- **Non-goals:** silent auto-update before signed rollback design.

## Desktop exit gate

A supported packaged desktop build must complete a fully offline Problem review,
survive process death, schedule exactly once, remain usable entirely by
keyboard, and reopen with the correct draft/history/due state.

