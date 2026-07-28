# BeeCode year-one execution plan

The 164 target goals are the north-star product plan, not a claim that one
developer can verify all 164 in 52 weeks. Completing the entire plan to its
written release gates is more plausibly an 18–30 month programme.

Year one targets a strong private beta containing the complete local study loop
on desktop and Android, FSRS 7, a small high-quality Problem pack, 5am Club, and
a minimal private Leaderboard. Stable 1.0 at week 52 is conditional on the
release gates; slipping to a well-tested private beta is preferable to waiving
data-loss, runner, privacy, or restore requirements.

Local Python execution on Android is a core product requirement, not an
optional scope lever. If every Android runtime fallback fails its honest
capability gate, the committed BeeCode outcome is blocked and the whole
year-one plan must be reforecast; a desktop-only build is not completion of
this plan.

## Capacity rules

- Plan around 40–44 productive feature weeks, not 52.
- Reserve weeks 45–52 for unknowns, beta findings, migrations, signing, and
  release recovery.
- Treat large goals as containers to split when activated.
- No goal may move from `proposed` to `ready` without an owner, estimate,
  milestone, evidence owner, and hard predecessors.
- Any `XL` estimate must be split before implementation.
- Do not infer progress from commit count.
- Move dates or scope; never waive the nonwaivable release blockers.

## Commitment classes

| Class | Meaning |
|---|---|
| Committed | Required for the year-one private-beta outcome. |
| Conditional | Planned only if the preceding gate/date is healthy. |
| Stretch | Valuable if capacity remains; not allowed to endanger the core. |
| Post-v1 | Retained in target docs but not scheduled into year one. |
| Operational | A recurring control that is initially established, then maintained. |

The class belongs in the active milestone board, not permanently in a goal
heading. Evidence may reclassify work at monthly forecasting.

## Year-one product cut

### Committed local product

- One stable desktop OS target; other desktop OSes may remain experimental until
  their packaging, Python, signing, and containment evidence exists.
- Android on one supported API range, emulator `x86_64`, and physical-device
  `arm64-v8a`.
- One official pack of roughly 12–20 original/licensed high-quality Problems.
- Only codecs/comparators required by that pack.
- One-folder Problem generation, validation, reference verification, preview,
  and deterministic packaging.
- Local Python execution on desktop and Android with honest platform capability
  labels, timeout/cancellation, bounded output, and source recovery.
- Durable drafts, append-only reviews, exactly-once finalization, FSRS 7 due
  scheduling, queue/history basics, and verified export/restore.
- Local achievement projection, 5am Club, and two or three additional restrained
  achievements.
- Accountless and offline study throughout.

### Conditional social beta

If the Android local-alpha gate passes by week 27:

- one Ktor/PostgreSQL/Caddy/Docker Compose server;
- opaque-token account flow;
- private Leaderboard create/join/leave;
- invite rotation and owner member removal;
- account-global idempotent activity ingestion;
- no pre-join or pre-account-link backfill;
- Today/week/all-time Problems counts and streak;
- generated/local avatar, display name, and server-accepted 5am Club title;
- offline outbox and one documented backup/restore path.

If Android slips beyond week 27, the Leaderboard remains an integration preview
or moves after year one rather than compressing security/beta time.

### Stretch

- Stable packaging for additional desktop OS families.
- Broad curriculum across every proposed topic/codec.
- Advanced editor features beyond the defined fundamentals.
- Full local analytics and FSRS migration-simulation UI.
- Large achievement launch set or generic server-side award catalogue.
- Independent signed Problem-pack release channel.
- Rich avatar upload/storage.
- Several generations of frozen client/server compatibility.
- Extensive historical source snapshots.
- Full localization beyond resource/pseudolocale readiness.

### Post-v1

- Live personal cross-device study sync.
- More programming languages.
- iOS or browser clients.
- Public Leaderboards, discovery, social feed, chat, comments, or DMs.
- Unreviewed community packs or executable content plugins.
- Package installation inside learner Python.
- Prize-grade anti-cheat.

## Corrected calendar

| Period | Milestone | Primary outcome |
|---|---|---|
| Weeks 1–4 | M0: feasibility and contracts | Toolchain shells plus Android Python, desktop worker, editor/IME, persistence, FSRS provenance, KVM/device, and threat-model decisions. |
| Weeks 5–11 | M1: thin desktop slice | One compiled Problem loads, source persists, Python runs, typed result displays; then generalize minimum content tooling. |
| Weeks 12–18 | M2: review truth | Atomic selected-run finalization, FSRS 7 transition, due queue, replay/audit, backup baseline, desktop dogfooding. |
| Weeks 19–27 | M3: Android local alpha | Mobile editor modes, isolated worker decision, lifecycle recovery, offline solve/review, physical-device evidence. |
| Weeks 28–31 | M4: achievements/content | Deterministic projection, exact 5am Club, two or three other awards, seed-pack quality pass. |
| Weeks 32–38 | M5: conditional social beta | Minimal private Leaderboard, outbox, server deployment, auth, period ranks, restore. |
| Weeks 39–44 | M6: feature freeze/private beta | No new features; migrations, recovery, security, accessibility, performance, documentation, and beta fixes. |
| Weeks 45–52 | M7: contingency/release reserve | Unallocated reserve. Release stable only if gates pass; otherwise publish the honest private-beta status and next plan. |

## M0 — weeks 1–4: feasibility and contracts

### Demonstrable result

Minimal desktop/Android shells compile, but the milestone is not judged by
screens. It is judged by resolving the decisions most likely to invalidate the
architecture.

### Required spikes

| Spike | Decision deadline | Success threshold | Fallback | Work displaced |
|---|---:|---|---|---|
| Android Python boundary | End week 3 | Embedded Python starts on emulator/device, GIL-bound loop is killable, UI/source survive, claimed UID/network/storage/Logcat boundary is tested | Separate no-permission runner APK; then trusted-code-only label; if all fail, block and reforecast the committed product | Social beta first, then advanced content tooling; all downstream work stops if no fallback is acceptable |
| Desktop worker | End week 3 | Supervisor/child topology, separate control channel, process-tree kill, timeout, output cap on first stable OS | Support one desktop OS; label weaker capability honestly | Additional desktop OSes |
| Editor/IME | End week 3 | Problem-sized source remains responsive; indentation/selection/undo work on desktop and Android IME | Platform-specific editor behind `CodeEditorSurface` | Syntax polish |
| Persistence packaging | End week 3 | One migration/transaction works on both targets; process-kill recovery understood | Platform drivers behind same repository contract | Nonessential settings/history UI |
| FSRS 7 provenance | End week 2 | Exact commit/tree, owner grant/license/SPDX record, independent vectors, algorithm/implementation/parameter IDs | Block public distribution until resolved | None; this is a gate |
| Android test access | End week 1 | Accelerated emulator or reachable ADB device plus one physical-device route | Remote/host emulator or physical device; compile-only is not a pass | Android schedule if unavailable |
| Problem rights policy | End week 2 | Original/licensed statement/test policy and provenance fields accepted | Keep all Problems private/local until reviewed | Public content release |

### Gate

- Android execution architecture is accepted or downgraded to an honest
  capability level that still runs Python. If neither is possible, the
  committed product outcome is blocked and the entire plan is reforecast.
- The chosen desktop baseline can kill a learner process tree.
- FSRS 7 reuse/provenance is recorded.
- The first database transaction and editor input work on both platform shells.
- A threat model covers the chosen boundaries.

If any result remains unknown, do not build abstractions around the hoped-for
answer.

## M1 — weeks 5–11: thin desktop slice

Build vertically in this order:

1. hard-coded compiled Problem fixture;
2. desktop source edit and durable draft;
3. supervisor/learner Python run;
4. finite typed result;
5. one successful and one failing solution;
6. timeout/cancel/recovery;
7. only then generalize Problem schema/discovery/compiler;
8. add generator, validation, reference verification, pack inspection.

This deliberately avoids perfecting a content language before observing the
runtime needs.

### Gate

- Adding one folder requires no registry edit.
- `reference.py` passes CI and is absent from the pack.
- `explanation.md` is revealable but never executed.
- Passing, failing, syntax-error, runtime-error, timeout, cancellation, and
  worker-failure states are distinct.
- Source survives UI/worker process death.

## M2 — weeks 12–18: review truth and FSRS 7

Build:

- selected-run/source-snapshot binding;
- failed-review `Again` path and unaided/reveal rating matrix;
- `BEGIN IMMEDIATE` or equivalent schedule-version/CAS transaction;
- recorded FSRS 7 inputs/outputs and due projection;
- no minute-based learning ladder in v1;
- due queue and basic history;
- idempotent post-commit achievement cursor;
- first whole-profile export and clean restore.

### Gate

- Two different sessions cannot transition one prior schedule silently.
- Restart/retry cannot duplicate review/events/outbox.
- Recorded transition folding rebuilds operational state without an old engine
  binary; exact historical engine can optionally recheck math.
- Desktop can be dogfooded for two weeks without manual database repair.
- Backup restores to the expected draft/review/due state.

If exactly-once finalization or restore remains unreliable, stop feature work.

## M3 — weeks 19–27: Android local alpha

Build only after the M0 runtime decision:

- phone recall/code/results/finalize modes;
- symbol row/indentation and external keyboard basics;
- the accepted Android worker topology;
- conformance with desktop semantic outcomes;
- process recreation, rotation, background, reboot, update, and low-storage
  behavior;
- offline use, document-provider backup, TalkBack baseline.

### Gate

- Representative Problems run offline on accelerated emulator/reachable device
  and physical phone.
- Infinite code stops without killing the UI or losing source.
- Process death during edit/run/finalize produces the specified durable state.
- Android and desktop agree on review and FSRS semantics.

If this gate completes after week 27, reduce or move social work; do not steal
time from beta/reserve.

## M4 — weeks 28–31: achievements and content

- Project achievements from canonical events after the review commit.
- Implement the exact 5am Club epoch/timezone algorithm and full boundary suite.
- Add two or three other ethically reviewed achievements.
- Complete the 12–20 Problem launch pack and human review.
- Keep broad curriculum and achievement catalogue as stretch.

### Gate

- Full replay equals incremental projection.
- Broken/unknown reducer cannot block study.
- 5am Club passes 05:59:59/06:00, seven-day, gap, DST, travel, late event,
  duplicate, reveal, failure, and restore tests.
- Launch pack has zero validator/reference/leakage failures.

## M5 — weeks 32–38: conditional Leaderboard beta

- Build the modular monolith, opaque-token auth, membership episodes, and
  durable ingestion ledger.
- Upload account-global events once.
- Do not backfill pre-account-link or pre-membership activity.
- Derive periods server-side from UTC and immutable board timezone/week start.
- Use friendly-trust “server accepted”, never “server verified”.
- Deploy on one clean host and restore PostgreSQL.

### Gate

- Two accounts join a private board and see the correct Problems count.
- Duplicate/reordered/offline batches have one effect.
- Leave/rejoin membership episodes follow the written score/privacy rules.
- Captured requests, rows, and logs contain no source, test output, or FSRS
  state.
- Clean-host deploy, restart, backup, and restore pass.

## M6 — weeks 39–44: feature freeze and private beta

No new product feature enters after week 38.

Concentrate on:

- migration fixtures and interrupted upgrades;
- corruption/full-disk/process-death recovery;
- runner containment/adversarial regression;
- authorization/auth token/invite tests;
- accessibility/manual assistive-technology scripts;
- performance calibration on named hardware;
- signed test packages;
- privacy, operator, content-rights, and limitation documentation;
- observed beta friction and critical misunderstandings.

## M7 — weeks 45–52: contingency and conditional release

These weeks are intentionally unallocated. They absorb rejected spikes, illness,
dependency/toolchain changes, signing/notarization, device access, beta schema/
interaction changes, security upgrades, and recovery work.

Stable 1.0 is allowed only if:

- no known defect can lose source/reviews or duplicate a finalized effect;
- runner hangs recover on every claimed target;
- FSRS 7 provenance/vectors/migrations are archived;
- 5am Club semantics are stable;
- Android/desktop install and upgrade paths pass;
- private Leaderboard authorization/idempotency/privacy pass;
- client and server restore drills pass;
- accessibility and security blockers are clear.

Otherwise, the correct outcome is a documented private beta and a reforecasted
second-year plan.

## Slip and stop rules

- No acceptable Android Python boundary by week 4: do not commit to a false
  isolation claim; choose an acceptable fallback or block the committed
  product outcome and reforecast the entire plan.
- No Android alpha by week 27: make the Leaderboard an integration preview or
  move it after year one.
- Unreliable exactly-once finalization or restore: stop feature work.
- No new features after week 38.
- Missing KVM is not an emulator pass; secure a host emulator/ADB device or
  physical evidence.
- Never waive data-loss, duplicate-review, runner-hang, auth/authorization,
  source/privacy leak, restore, or signing blockers for the date.

## Goal activation template

When pulling one of the 164 goals into a milestone, add this planning metadata
to the milestone board:

```text
Goal ID:
Commitment: committed | conditional | stretch
Owner:
Reviewer/evidence owner:
Milestone:
Estimate: S | M | L
Hard predecessors:
Related goals:
Verified by:
Decision deadline:
Fallback:
Work displaced by fallback:
Evidence link:
```

`Dependencies` in target documents means hard implementation predecessor only.
Cross-cutting coordination and later verification belong under `Related goals`
and `Verified by` when activated. The hard-dependency graph must remain acyclic.

## Recurring operational goals

Threat-model maintenance, dependency review, support triage, incident
preparedness, and post-v1 backlog review are never permanently “finished”.
After their initial acceptance gate, mark them `operational` and attach a
`last_reviewed` date plus the next review deadline.
