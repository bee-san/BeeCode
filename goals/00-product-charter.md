# Target 00: product charter

This target fixes what BeeCode is, whom it serves first, and how success is
measured. Architecture and UI decisions should be rejected when they optimize a
different product.

## PROD-001 — Define the primary learner

- **State:** proposed
- **Outcome:** BeeCode is designed first for a self-directed learner who knows
  Python syntax and wants long-term recall of algorithmic Problem patterns.
- **Deliverables:** primary persona, jobs-to-be-done, current workflow map, and
  ten representative study sessions.
- **Acceptance:**
  - The persona distinguishes learning algorithms from preparing for a timed
    interview.
  - Desktop-heavy, phone-heavy, and mixed-device routines are represented.
  - Expected Problem volume, session duration, and typing constraints are
    quantified as assumptions.
  - The plan identifies which assumptions require user research.
- **Evidence:** signed-off persona document and five structured interviews or
  diary-study entries before beta.
- **Dependencies:** none.
- **Risks:** designing for the developer alone; conflating advanced competitive
  programming with interview practice.
- **Non-goals:** supporting first-time programming education in v1.

## PROD-002 — Define the core study loop

- **State:** proposed
- **Outcome:** every feature supports a simple loop: choose due Problem, recall
  approach, code, run tests, reflect, finalize, schedule.
- **Deliverables:** state diagram, interruption paths, failure paths, and
  platform-specific interaction notes.
- **Acceptance:**
  - “Tests passed” and “Review finalized” are separate states.
  - A learner can save a draft and leave without corrupting schedule state.
  - Revealing a reference solution changes the permitted review outcome.
  - Retry behavior is explicit and cannot create duplicate reviews.
  - Offline behavior is complete.
- **Evidence:** clickable prototype review and executable domain-state tests
  once implemented.
- **Dependencies:** PROD-001.
- **Risks:** optimizing only for successful solutions; hiding failure and
  reflection behavior.
- **Non-goals:** competitive timed contests.

## PROD-003 — Establish Problem content policy

- **State:** proposed
- **Outcome:** BeeCode can distribute original or appropriately licensed
  Problems without depending on scraped proprietary content.
- **Deliverables:** content provenance fields, attribution rules, allowed
  licenses, takedown process, and contributor attestation.
- **Acceptance:**
  - Every bundled Problem has an author/provenance and license declaration.
  - Validation rejects missing provenance.
  - “Inspired by” Problems use independently written statements, examples, and
    tests.
  - Removal of a Problem does not destroy existing review history.
- **Evidence:** policy review plus audit of every release pack.
- **Dependencies:** legal review before public distribution.
- **Risks:** copying LeetCode statements or hidden tests; incompatible licenses.
- **Non-goals:** automatic scraping or account integration with LeetCode.

## PROD-004 — Define learning success

- **State:** proposed
- **Outcome:** product decisions are measured against recall quality and
  sustainable practice, not raw activity.
- **Deliverables:** metric dictionary and a privacy classification for each
  metric.
- **Acceptance:**
  - Primary metrics include due-review completion, delayed recall success, lapse
    recovery, and learner-reported usefulness.
  - Problems solved is treated as an activity count, not proof of mastery.
  - Metrics can be calculated locally.
  - No metric requires uploading solution source.
- **Evidence:** metric review at M2, M4, and beta.
- **Dependencies:** PROD-002, SRS-001.
- **Risks:** gamification encouraging trivial repeated submissions.
- **Non-goals:** ad-tech engagement or retention optimization.

## PROD-005 — Set platform support

- **State:** proposed
- **Outcome:** supported operating systems, architectures, input modes, and
  upgrade windows are explicit.
- **Deliverables:** compatibility matrix and support policy.
- **Acceptance:**
  - Minimum Android API is selected from runtime and security evidence.
  - Desktop v1 support names exact Windows, macOS, and/or Linux baselines.
  - ARM64 and x86_64 expectations are stated for each platform.
  - Unsupported configurations fail clearly rather than silently losing Python
    execution.
- **Evidence:** published matrix backed by CI and device results.
- **Dependencies:** RUN-002, RUN-003, DSK-001, AND-001.
- **Risks:** claiming platforms that cannot be tested or packaged.
- **Non-goals:** browser/iOS support in v1.

## PROD-006 — Define account optionality

- **State:** proposed
- **Outcome:** an account is needed only for Leaderboards.
- **Deliverables:** capability matrix for guest and authenticated users.
- **Acceptance:**
  - No login prompt blocks first launch, Problem study, Python runs, FSRS,
    achievements, export, or restore.
  - Signing out leaves all local study data intact.
  - Account deletion has a documented server-data effect without deleting local
    study data.
- **Evidence:** offline first-run and account lifecycle tests.
- **Dependencies:** LDB-001, DATA-005.
- **Risks:** accidentally coupling settings or achievements to server identity.
- **Non-goals:** cross-device study sync through Leaderboard accounts.

## PROD-007 — Protect learner agency

- **State:** proposed
- **Outcome:** BeeCode helps the learner judge their recall rather than
  manipulating ratings automatically.
- **Deliverables:** review-rating guidance, reveal policy, override behavior,
  and explanation of scheduling consequences.
- **Acceptance:**
  - Test results inform but do not invisibly choose an FSRS rating.
  - A failed required test cannot be finalized as a clean unaided success.
  - Manual overrides are recorded, reversible where safe, and explained.
  - The next due date and rating effect are inspectable.
- **Evidence:** usability sessions and state-model tests.
- **Dependencies:** PROD-002, SRS-003.
- **Risks:** punitive flow discouraging honest failure; opaque scheduling.
- **Non-goals:** preventing every possible self-cheat.

## PROD-008 — Define v1 release boundary

- **State:** proposed
- **Outcome:** the first public release is coherent and supportable.
- **Deliverables:** v1 capability list, explicit deferred list, and release gate.
- **Acceptance:**
  - v1 includes local Problem packs, Python execution, reviews, FSRS, local
    achievements, desktop and Android clients, and optional private
    Leaderboards.
  - Cross-device study sync, public rankings, chat, AI tutoring, third-party
    packages, and multiple languages remain deferred.
  - Every included capability has an owner and test strategy.
- **Evidence:** scope review at the end of each phase.
- **Dependencies:** all target leads.
- **Risks:** a year of development without a shippable slice.
- **Non-goals:** freezing post-v1 research.

## PROD-009 — Define ethical gamification

- **State:** proposed
- **Outcome:** achievements and Leaderboards reward useful consistency without
  making unhealthy schedules or volume pressure the default.
- **Deliverables:** gamification principles and review checklist.
- **Acceptance:**
  - Streaks have recovery-friendly presentation and can be hidden.
  - Leaderboards are opt-in and private.
  - No achievement requires sleep deprivation; 5am Club celebrates users whose
    existing schedule includes early study and is not pushed as a universal
    recommendation.
  - Accessibility does not depend on color, animation, or social comparison.
- **Evidence:** review of every achievement definition and social screen.
- **Dependencies:** ACH-001, LDB-001, UX-001.
- **Risks:** compulsion, shame, or gaming counts.
- **Non-goals:** clinical claims about habit formation.

## PROD-010 — Create a feedback discipline

- **State:** proposed
- **Outcome:** roadmap changes are based on structured evidence rather than the
  loudest request.
- **Deliverables:** issue templates, study-session feedback form, beta cohort
  plan, and decision log.
- **Acceptance:**
  - Feedback captures platform, Problem, session state, and whether code/data
    may be shared.
  - Reports default to excluding source code.
  - Monthly synthesis separates defects, usability friction, content gaps, and
    feature requests.
  - Accepted roadmap changes name the displaced work.
- **Evidence:** three monthly feedback synthesis notes during beta.
- **Dependencies:** SEC-008.
- **Risks:** collecting sensitive code or unbounded telemetry.
- **Non-goals:** public voting as product governance.

