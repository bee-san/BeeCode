# Target 13: accessibility and user experience

This target makes the study loop fast, understandable, calm, and inclusive.
BeeCode is not a generic flashcard skin around an IDE; its interaction design
must connect recall, coding evidence, reflection, and future scheduling.

## Experience principles

1. **Recall before reveal.** The prompt encourages an approach/pattern recall
   before previous source or explanation is shown.
2. **Source is precious.** Draft status and recovery are visible; destructive
   reset is deliberate.
3. **Execution is evidence.** Passing/failing tests inform review, but do not
   silently choose the learner's memory rating.
4. **One dominant next action.** Each workspace state clearly exposes what is
   possible without flooding the learner with controls.
5. **Offline is ordinary.** Network absence is a social-sync state, not a
   product failure.
6. **Errors preserve agency.** Explain what happened, what data is safe, and
   what can be tried next.
7. **Gamification stays optional.** Progress is celebratory, never punitive or
   sleep/volume-prescriptive.
8. **Accessible by construction.** Semantics, keyboard, focus, scaling,
   contrast, and reduced motion are acceptance criteria.

## Screen/state inventory

| Surface | Critical states |
|---|---|
| First launch | local-only start, content present/missing, runtime ready/repair |
| Today | due, new, done, daily limit, no content, data error |
| Workspace | recall, editing, queued, running, cancelling, every result, reveal, finalize |
| Problems | loaded, filtered empty, incompatible revision, suspended/retired |
| History | no reviews, run-only attempt, finalized review, correction, missing content |
| Achievements | locked/progress/unlocked, secret, pending social confirmation |
| Leaderboards | signed out, offline stale, empty, member, owner, removed, sync rejected |
| Settings/data | valid/invalid, export/import progress, conflict, destructive preview |

## UX-001 — Define adaptive layout behavior

- **State:** proposed
- **Outcome:** the same domain state is presented effectively on phone, tablet,
  laptop, and desktop without identical chrome.
- **Deliverables:** window classes/breakpoints, navigation variants, workspace
  mode/pane mapping, resize behavior, and shared/platform-specific component
  policy.
- **Acceptance:**
  - Phone uses focused recall/code/results/finalize modes.
  - Desktop supports resizable panes and focus mode.
  - Expanded Android can combine modes without changing state semantics.
  - Critical action remains reachable at minimum size and 200% scaling.
- **Evidence:** responsive prototype and screenshot matrix.
- **Dependencies:** ARCH-001, PROD-001.
- **Risks:** excessive shared UI conditional logic.
- **Non-goals:** pixel-identical platforms.

## UX-002 — Establish focus and input accessibility

- **State:** proposed
- **Outcome:** keyboard, screen reader, touch, switch, and pointer users can
  traverse the core flow predictably.
- **Deliverables:** focus order, visible focus, semantic headings/labels,
  keyboard shortcuts, touch target minimum, live-region policy, and escape/back
  behavior.
- **Acceptance:**
  - Desktop review is keyboard-completable.
  - Android review has logical TalkBack order.
  - Focus does not jump unexpectedly after run/result updates.
  - Shortcut/action names are discoverable.
  - Editor controls and ratings expose role/state/value.
- **Evidence:** manual assistive-technology scripts plus semantic tests.
- **Dependencies:** UX-001, PROD-001.
- **Risks:** editor component having poor semantics.
- **Non-goals:** declaring conformance solely from an automated checker.

## UX-003 — Design editor ergonomics deliberately

- **State:** proposed
- **Outcome:** the editor supports Problem-sized Python comfortably without
  trying to be a full IDE.
- **Deliverables:** text behavior specification, desktop shortcuts, phone symbol
  row, indentation, syntax/error decoration, source size target, and replaceable
  `CodeEditorSurface` boundary.
- **Acceptance:**
  - Editing does not block on autosave/highlighting.
  - Indentation, undo/redo, selection, IME composition, and paste are tested.
  - Diagnostics can focus a location without altering text.
  - Plain source remains the domain/persistence representation.
  - A platform-specific editor can replace shared UI without data migration.
- **Evidence:** editor spike and usability comparison.
- **Dependencies:** RUN-008, ARCH-008.
- **Risks:** editor perfection delaying the study loop.
- **Non-goals:** language server/refactoring/debugger/package manager in v1.

## UX-004 — Create a finite error language

- **State:** proposed
- **Outcome:** errors are consistent, actionable, nonjudgmental, and safe.
- **Deliverables:** error taxonomy, stable reason codes, user copy, technical
  detail toggle, recovery actions, redaction, and localization guidance.
- **Acceptance:**
  - Learner error, Problem/content error, runtime/worker error, data error,
    network/server error, auth error, and compatibility error are distinct.
  - Copy says what happened and whether source/review is safe.
  - Wrong answers are not called crashes.
  - Technical detail never includes secrets or internal sensitive paths.
  - Error is not conveyed only by color/icon.
- **Evidence:** golden copy/state review.
- **Dependencies:** RUN-001, SEC-006.
- **Risks:** technical precision overwhelming novice users.
- **Non-goals:** exposing raw stack traces as primary UI.

## UX-005 — Explain review and scheduling decisions

- **State:** proposed
- **Outcome:** the learner understands the relationship between test evidence,
  rating, and next due date.
- **Deliverables:** rating guidance, projected intervals, due reason, reveal
  consequences, completion summary, schedule settings copy, and advanced view.
- **Acceptance:**
  - Passing tests never appears to auto-rate memory.
  - Failed/revealed sessions explain permitted outcomes.
  - Finalization preview names rating and projected schedule.
  - Committed summary reflects the exact stored transition.
  - Settings warn before materially changing due load.
- **Evidence:** comprehension testing and golden explanation suite.
- **Dependencies:** SRS-003, SRS-004.
- **Risks:** false precision or overly academic FSRS explanation.
- **Non-goals:** requiring users to understand FSRS equations.

## UX-006 — Make progress and achievements healthy

- **State:** proposed
- **Outcome:** motivation celebrates practice without punishment, shame, or
  inaccessible spectacle.
- **Deliverables:** streak language, progress states, reduced-motion unlock,
  hide controls, secret-achievement policy, and ethical review.
- **Acceptance:**
  - A missed streak uses neutral copy.
  - Achievement/streak surfaces can be hidden.
  - 5am Club does not imply early study is healthier/better for everyone.
  - Unlock animations can be skipped/reduced.
  - Progress is not encoded only by color.
- **Evidence:** ethical rubric and accessibility review.
- **Dependencies:** ACH-001, PROD-009.
- **Risks:** competitive pressure bleeding into local study.
- **Non-goals:** manipulative retention mechanics.

## UX-007 — Communicate offline and sync honestly

- **State:** proposed
- **Outcome:** network state never casts doubt on local study truth.
- **Deliverables:** local-saved status, outbox state, snapshot freshness,
  retry/rejection copy, sign-out behavior, and account capability matrix.
- **Acceptance:**
  - Local review completion is confirmed independently of social sync.
  - Pending/failed social upload cannot look like lost study.
  - Cached ranking displays last-updated time.
  - Permanent rejection explains social effect without accusing the user.
  - “Account” is never described as study backup in v1.
- **Evidence:** offline/outage state review.
- **Dependencies:** DATA-004, LDB-007.
- **Risks:** badges/spinners making an offline-first app feel broken.
- **Non-goals:** real-time rank updates.

## UX-008 — Design privacy and destructive actions

- **State:** proposed
- **Outcome:** export, reset, delete, sign out, leave, remove, and reveal have
  precise scope and confirmation proportional to consequence.
- **Deliverables:** action matrix, preview, confirmation, undo/recovery where
  possible, sensitive-content warning, and post-action summary.
- **Acceptance:**
  - Sign out explicitly says local study remains.
  - Local reset/account deletion/board leave are visually and semantically
    distinct.
  - Export warns that archives may contain source.
  - Reset-to-starter preserves or previews source-history effect.
  - Member removal/board deletion identifies affected board only.
- **Evidence:** destructive-action usability script.
- **Dependencies:** DATA-007, SEC-007.
- **Risks:** confirmation fatigue.
- **Non-goals:** modal confirmation for every reversible action.

## UX-009 — Establish visual system and brand

- **State:** proposed
- **Outcome:** BeeCode has a recognizable, calm visual language that supports
  dense code/result content.
- **Deliverables:** color/typography/spacing/elevation/icon/motion tokens,
  light/dark/high-contrast themes, logo/app icon direction, and component
  catalog.
- **Acceptance:**
  - Code font and UI font have clear roles.
  - Syntax/result colors meet contrast needs or have redundant cues.
  - Bee theming does not turn every surface yellow/black or impair readability.
  - Components share states and semantics across platforms.
  - Brand assets have scalable/vector source and licensing.
- **Evidence:** theme/component review.
- **Dependencies:** UX-002.
- **Risks:** branding overriding code legibility.
- **Non-goals:** finalized marketing site in v1.

## UX-010 — Plan localization and text resilience

- **State:** proposed
- **Outcome:** UI strings, dates, numbers, layouts, and Problem metadata can
  support future locales without rearchitecture.
- **Deliverables:** string resource policy, pluralization, date/time formatting,
  locale versus study timezone distinction, pseudolocalization, and content
  language fields.
- **Acceptance:**
  - User-facing strings are not embedded in domain reason codes.
  - Dates show timezone where ambiguity matters.
  - Pseudolocalized text does not hide critical actions.
  - Problem content language is explicit and independent from UI locale.
- **Evidence:** pseudolocale screenshot matrix.
- **Dependencies:** ARCH-006.
- **Risks:** hard-coded English assumptions in generated content/UI.
- **Non-goals:** promising translated v1 packs before translators/review exist.

## UX-011 — Conduct staged usability research

- **State:** proposed
- **Outcome:** workflow decisions are informed by observed study behavior.
- **Deliverables:** research questions, consent/privacy script, prototype tasks,
  diary study, issue severity rubric, and synthesis cadence.
- **Acceptance:**
  - Sessions cover desktop-heavy, phone-heavy, and mixed routines.
  - At least one round tests recall/finalization semantics before visual polish.
  - Mobile typing and error recovery receive dedicated observation.
  - Shared diagnostics/source are opt-in and minimized.
  - Findings name decision/change/deferred reason.
- **Evidence:** anonymized synthesis at M1, M2, and beta.
- **Dependencies:** PROD-001, PROD-010.
- **Risks:** recruiting only expert developers identical to maintainer.
- **Non-goals:** treating five interviews as statistical proof.

## UX-012 — Define onboarding without an account wall

- **State:** proposed
- **Outcome:** first launch gets a learner to one safe local Problem quickly
  while disclosing runtime/data basics.
- **Deliverables:** local profile, bundled pack/runtime readiness, short study
  explanation, optional settings, first Problem, later account invitation, and
  recovery/help.
- **Acceptance:**
  - No account/server required.
  - Onboarding verifies execution support before the first coding commitment.
  - Learner knows drafts/history are local and how to back them up.
  - FSRS explanation is short with optional detail.
  - Leaderboard signup is offered only after local value is clear.
- **Evidence:** first-run acceptance and time-to-first-run measurement.
- **Dependencies:** PROD-006, DSK-002, AND-002.
- **Risks:** setup questions delaying first success.
- **Non-goals:** requesting notification/account/telemetry permissions all at
  once.

## Accessibility/UX exit gate

A learner must complete the primary flow keyboard-only on desktop and through
the declared Android accessibility script; core states must survive text
scaling and reduced motion; and usability evidence must show that “tests
passed”, “review finalized”, “saved locally”, and “synced socially” are
understood as distinct states.
