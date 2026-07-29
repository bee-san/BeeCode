# BeeCode year-one execution plan

> **Open decision: the "FSRS 7" commitments below need reconciling.** This plan
> says "FSRS 7" in the committed scope, the M0 gate, and the M2 milestone. The
> engine that was built and shipped is **FSRS-6.x, 21 parameters**. FSRS-7 is a
> real but different algorithm — **35 parameters**, fractional intervals, shipped
> in no scheduler library, existing only as benchmark research code. Adopting it
> would be an `SRS-009` migration, not a dependency bump. The plan text is the
> out-of-date part; it is deliberately left standing rather than rewritten to
> match what was built. See
> [ADR 0004](../docs/adr/0004-bee-fsrs-is-its-own-repository.md).

The 164 target goals are the north-star product plan, not a claim that one
developer can verify all 164 in 52 weeks. Completing the entire plan to its
written release gates is more plausibly an 18–30 month programme.

Year one targets a strong private beta containing the complete local study loop
on desktop and Android, FSRS 7, a small high-quality Problem pack, and 5am Club.
A minimal private Leaderboard is a conditional follow-on that starts only after
the complete local-product gate passes. Stable 1.0 at week 52 is conditional on
the release gates; slipping to a well-tested private beta is preferable to
waiving data-loss, runner, privacy, or restore requirements.

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

## The first two hands-on milestones

The engineering roadmap starts at M0, but the owner-facing test milestones are
deliberately numbered separately and made prominent:

| Hands-on milestone | Engineering exit | Target | What the owner can test |
|---|---|---:|---|
| **Test 1: Answer a Problem** | M3 | End week 27 | Install desktop and Android builds; read a Problem, write Python, fail/pass tests, finalize, restart, and retain source/history/due state offline. |
| **Test 2: Complete local product** | M4 | End week 34 | Use both clients offline with the reviewed LeetCode-style Problem pack, reviews/FSRS, history and local statistics, achievements, settings, export/restore, and accessibility basics. |

Test 1 is the first product checkpoint and therefore excludes achievements,
Leaderboards, analytics, broad content, visual polish, and release machinery.
It requires both platforms: the earlier desktop slice is useful evidence but
does not pass Test 1 by itself.

Test 2 is the first feature-complete product checkpoint. It covers the committed
local desktop and Android product—including achievements and content—without an
account, server, or Leaderboard. The conditional social experiment follows in
M5 only after this local gate passes; it cannot displace unfinished local work.

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
- A versioned `bee-san/bee-fsrs` repository/package that BeeCode and clean
  external consumers resolve as the same pinned FSRS 7 artifact.
- Durable drafts, append-only reviews, exactly-once finalization, FSRS 7 due
  scheduling, queue/history basics, and verified export/restore.
- Local achievement projection, 5am Club, and two or three additional restrained
  achievements.
- Basic local statistics and settings on both clients.
- Accountless and offline study throughout.

### Conditional social beta

If the complete local-product gate passes by the end of week 34 and the
remaining beta/reserve forecast is healthy:

- one Ktor/PostgreSQL/Caddy/Docker Compose server;
- opaque-token account flow;
- private Leaderboard create/join/leave;
- invite rotation and owner member removal;
- account-global idempotent activity ingestion;
- no pre-join or pre-account-link backfill;
- Today/week/all-time Problems counts and streak;
- generated/local avatar and display name for the Leaderboard owner test;
- offline outbox and one documented backup/restore path.

The server-accepted 5am Club title is integrated in M5 after the local
achievement rule passes its boundary suite.

If the local product slips beyond week 34, the Leaderboard remains an
integration preview or moves after year one rather than compressing local
quality, security, beta, or reserve time.

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
| Weeks 1–4 | M0: feasibility and contracts | Toolchain shells plus Android Python, desktop worker, editor/IME, persistence, reusable FSRS package/provenance, KVM/device, and threat-model decisions. |
| Weeks 5–11 | M1: thin desktop slice | One compiled Problem loads, source persists, Python runs, typed result displays; then generalize minimum content tooling. |
| Weeks 12–18 | M2: review truth | Atomic selected-run finalization, FSRS 7 transition, due queue, replay/audit, backup baseline, desktop dogfooding. |
| Weeks 19–27 | **M3 / Test 1: playable desktop + Android alpha** | Install both clients and complete the same offline answer–run–retry–finalize–restart journey. |
| Weeks 28–34 | **M4 / Test 2: complete local product** | Finish achievements, content, daily-driver local features, recovery, accessibility, and offline acceptance on desktop and Android. |
| Weeks 35–38 | M5: conditional Leaderboard beta | Only after Test 2 passes, test a minimal private Leaderboard, outbox, server deployment, auth, period ranks, and restore with two accounts. |
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
| Android Python boundary | End week 3 | Embedded Python starts on emulator/device, GIL-bound loop is killable, UI/source survive, claimed UID/network/storage/Logcat boundary is tested | Separate no-permission runner APK; then trusted-code-only label; if all fail, block and reforecast the committed product | Drop Leaderboard work before any committed local work, then drop advanced local stretch work; all downstream work stops if no fallback is acceptable |
| Desktop worker | End week 3 | Supervisor/child topology, separate control channel, process-tree kill, timeout, output cap on first stable OS | Support one desktop OS; label weaker capability honestly | Additional desktop OSes |
| Editor/IME | End week 3 | Problem-sized source remains responsive; indentation/selection/undo work on desktop and Android IME | Platform-specific editor behind `CodeEditorSurface` | Syntax polish |
| Persistence packaging | End week 3 | One migration/transaction works on both targets; process-kill recovery understood | Platform drivers behind same repository contract | Nonessential settings/history UI |
| Reusable FSRS package | End week 2 | `bee-san/bee-fsrs` repository, exact source commit/tree, owner grant/license/SPDX record, independent vectors, version/API policy, first tagged JVM artifact, and clean-consumer resolution | Keep the engine in `kanji_anki` only as a temporary source and block M0 exit, BeeCode scheduling, and public distribution until the tagged package and clean-consumer smoke pass | None; this is a gate |
| Android test access | End week 1 | Accelerated emulator or reachable ADB device plus one physical-device route | Remote/host emulator or physical device; compile-only is not a pass | Android schedule if unavailable |
| Problem rights policy | End week 2 | Original/licensed statement/test policy and provenance fields accepted | Keep all Problems private/local until reviewed | Public content release |

### Gate

- Android execution architecture is accepted or downgraded to an honest
  capability level that still runs Python. If neither is possible, the
  committed product outcome is blocked and the entire plan is reforecast.
- The chosen desktop baseline can kill a learner process tree.
- The `bee-fsrs` repository, first tagged package, clean-consumer smoke,
  FSRS 7 reuse authorization, and provenance are recorded.
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
- pinned `bee-fsrs` release integration through the BeeCode scheduler adapter;
- recorded FSRS 7 inputs/outputs and due projection;
- no minute-based learning ladder in v1;
- due queue and basic history;
- idempotent post-commit achievement cursor;
- first whole-profile export and clean restore.

### Gate

- Two different sessions cannot transition one prior schedule silently.
- BeeCode and the clean sample consumer pass the same golden vectors against the
  same pinned `bee-fsrs` release.
- Restart/retry cannot duplicate reviews or events; once M5 begins, the same
  rule also covers the outbox.
- Recorded transition folding rebuilds operational state without an old engine
  binary; exact historical engine can optionally recheck math.
- Desktop can be dogfooded for two weeks without manual database repair.
- Backup restores to the expected draft/review/due state.

If exactly-once finalization or restore remains unreliable, stop feature work.

## M3 / Test 1 — weeks 19–27: playable desktop + Android alpha

Build only after the M0 runtime decision:

- installable desktop tester package and Android tester APK;
- phone recall/code/results/finalize modes;
- symbol row/indentation and external keyboard basics;
- the accepted Android worker topology;
- conformance with desktop semantic outcomes;
- process recreation, rotation, background, reboot, update, and low-storage
  behavior;
- offline use, document-provider backup, TalkBack baseline.

### Gate

- On both installed clients, the owner can open a bundled Problem, enter a
  Python answer, see one intentional failure, correct it, pass, finalize, and
  see the next due date.
- Closing and relaunching each client preserves the source, finalized history,
  and due state.
- Representative Problems run offline on accelerated emulator/reachable device
  and physical phone.
- Infinite code stops without killing the UI or losing source.
- Process death during edit/run/finalize produces the specified durable state.
- Android and desktop agree on review and FSRS semantics.
- No account, achievement, Leaderboard, or network is required for this gate.

If this gate completes after week 27, protect the M4 local-product work and move
social work as needed; do not steal time from local quality, beta, or reserve.

## M4 / Test 2 — weeks 28–34: complete local product

- Project achievements from canonical events after the review commit.
- Implement the exact 5am Club epoch/timezone algorithm and full boundary suite.
- Add two or three other ethically reviewed achievements.
- Complete the 12–20 original/licensed LeetCode-style Problem launch pack and
  human review.
- Finish the committed local daily-driver surfaces on both clients: due queue,
  history, local statistics, settings, import/export, restore, and recovery.
- Complete the local accessibility, offline, and release-shaped packaging
  acceptance paths; M6 hardens them rather than adding missing local features.
- Keep broad curriculum and achievement catalogue as stretch.

### Owner test journey

1. install the desktop package and Android APK;
2. solve and finalize reviews from the launch pack on both clients;
3. inspect due state, history, local statistics, settings, and achievements;
4. verify 5am Club and another achievement from deterministic fixtures;
5. export the profile, restore it into a clean install, and retain the expected
   source, review, schedule, statistics, and achievement state;
6. repeat the supported daily journey offline with no account or server.

### Gate

- Both installed clients expose every committed local year-one capability.
- Full achievement replay equals incremental projection, and a broken/unknown
  reducer cannot block study.
- 5am Club passes 05:59:59/06:00, seven-day, gap, DST, travel, late event,
  duplicate, reveal, failure, and restore tests.
- The launch pack has zero validator/reference/leakage failures.
- Export/restore and the offline acceptance journey pass on desktop and Android.
- No account, server, or Leaderboard is required for any local capability or
  for this gate.

## M5 — weeks 35–38: conditional Leaderboard beta

Enter this milestone only if the M4 gate passes and the remaining beta/reserve
forecast is healthy. Otherwise retain the work in the plan and move it later.

- Build the modular monolith, opaque-token auth, membership episodes, and
  durable ingestion ledger.
- Upload account-global events once.
- Do not backfill pre-account-link or pre-membership activity.
- Derive periods server-side from UTC and immutable board timezone/week start.
- Ship a documented self-host configuration suitable for owner testing.
- Add the optional server-accepted 5am Club title without changing local award
  ownership or Leaderboard counts.

### Owner test journey

1. deploy the server on one clean host;
2. register two test accounts;
3. create a private Leaderboard and join by invitation;
4. complete a Problem and observe the Problems count and streak;
5. complete while offline, reconnect, and observe exactly one effect;
6. retry uploads/refreshes and confirm no duplicate credit;
7. leave/rejoin and confirm the documented membership-episode reset.

### Gate

- The complete owner test journey passes with a friend or second account.
- Duplicate, reordered, and offline batches have one effect.
- Captured requests, rows, and logs contain no source, test output, or FSRS
  state.
- Clean-host deploy, restart, PostgreSQL backup, and restore pass.
- Failure of optional title integration cannot block the basic social journey.
- An earned 5am Club title may be equipped socially without changing counts.
- Leaderboard or title failure cannot block or undo the complete local product.

## M6 — weeks 39–44: feature freeze and private beta

No new product feature enters after week 38.

Concentrate on:

- migration fixtures and interrupted upgrades;
- corruption/full-disk/process-death recovery;
- runner containment/adversarial regression;
- authorization/auth token/invite tests if the M5 beta was entered;
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
- `bee-fsrs` source provenance, released artifact/checksum, vectors, and
  migrations are archived;
- 5am Club semantics are stable;
- Android/desktop install and upgrade paths pass;
- client restore drills pass;
- accessibility and security blockers are clear.

If the conditional Leaderboard is included in the release, its authorization,
idempotency, privacy, clean-host deployment, and server restore gates must also
pass. Deferring it does not block a complete local release.

Otherwise, the correct outcome is a documented private beta and a reforecasted
second-year plan.

## Slip and stop rules

- No acceptable Android Python boundary by week 4: do not commit to a false
  isolation claim; choose an acceptable fallback or block the committed
  product outcome and reforecast the entire plan.
- No complete local-product gate by week 34: make the Leaderboard an integration
  preview or move it after year one.
- Unreliable exactly-once finalization or restore: stop feature work.
- No new features after week 38.
- Missing KVM is not an emulator pass; secure a host emulator/ADB device or
  physical evidence.
- Never waive data-loss, duplicate-review, runner-hang, source/privacy leak,
  restore, or signing blockers for the date. If the Leaderboard is included,
  its auth/authorization blockers are equally nonwaivable.

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
