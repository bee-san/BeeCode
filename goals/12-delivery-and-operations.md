# Target 12: delivery and operations

This target turns a source tree into signed desktop/Android releases and a
Leaderboard service that a technically capable user can deploy, upgrade, back
up, restore, and diagnose.

## Release channels

| Channel | Audience | Compatibility/data expectation |
|---|---|---|
| Development | Contributors | May reset disposable dev data; never presented as upgrade-safe. |
| Internal | Maintainer/test devices | Migration path exercised; diagnostics enabled explicitly. |
| Alpha | Technical testers | Forward migrations supported; known rough UX documented. |
| Beta | Wider invited learners | Data-loss/duplicate-review blockers are nonwaivable. |
| Stable | Supported users/self-hosters | Full release gate, signed artifacts, compatibility window. |

Every artifact and server reports app version, build/source revision, database
schema, Problem pack schema, runner/harness, FSRS, achievement definition, and
API/event versions.

## Commit strategy for a year-scale build

The request for “many commits” should result in useful history, not empty
churn. A feature normally lands as separate contract, pure behavior, test,
integration/UI, and documentation/migration commits when those divisions remain
buildable.

Example subjects:

```text
feat(problems): [PROB-001] define the versioned Problem schema
feat(problems): [PROB-004] discover Problem directories deterministically
test(problems): [PROB-006] reject invalid codecs and path traversal

feat(runner): [RUN-001] add typed execution requests and results
feat(runner): [RUN-002] launch the desktop worker out of process
test(runner): [RUN-005] terminate infinite Python programs

feat(reviews): [SRS-006] finalize review sessions idempotently
test(reviews): [SRS-006] survive retry after uncertain commit

feat(achievements): [ACH-004] reduce 5am Club progress
test(achievements): [ACH-004] cover DST and 06:00 boundary

feat(server): [LDB-004] accept idempotent activity batches
test(server): [LDB-006] rank board periods across DST
```

Rules:

- Never create empty/cosmetic commits to raise the number.
- Keep unrelated targets and mechanical formatting separate.
- Prefer a green compile/test state after each commit.
- Put generated artifacts in their own commit only when deliberately tracked.
- Record incompatible decisions/migrations in an ADR.
- Include goal ID in substantive commit subjects.
- Use milestone tags with evidence summaries.

A realistic first year may produce roughly 100–180 meaningful commits, but
quality of boundaries matters more than count.

## OPS-001 — Make development setup reproducible

- **State:** proposed
- **Outcome:** a new contributor can diagnose prerequisites and run focused
  workflows without global-machine surprises.
- **Deliverables:** requirements, version manager/toolchain policy, Gradle
  wrapper verification, Android SDK/emulator guide, desktop runtime guide,
  server container setup, and prerequisite diagnostic.
- **Acceptance:**
  - Setup does not silently mutate global tools.
  - Missing JDK/SDK/KVM/ADB/Python/container capability is identified precisely.
  - Clean cached and uncached builds are documented.
  - Development secrets use examples and remain untracked.
- **Evidence:** clean environment onboarding exercise.
- **Dependencies:** ARCH-002, ARCH-009.
- **Risks:** documentation matching only one workstation.
- **Non-goals:** supporting every Linux distribution/host package manager.

## OPS-002 — Define versioning and compatibility channels

- **State:** proposed
- **Outcome:** clients, packs, backups, server, events, and database versions
  can evolve independently but predictably.
- **Deliverables:** SemVer/application build policy, schema version rules,
  client/server window, pack compatibility, deprecation, changelog, and channel
  promotion.
- **Acceptance:**
  - Breaking wire/pack/backup changes require explicit version/migration.
  - Server advertises capabilities.
  - Stable support window is documented.
  - Alpha-to-beta-to-stable promotion preserves the intended profile data.
- **Evidence:** simulated promotion and old-client fixtures.
- **Dependencies:** ARCH-006, LDB-011, DATA-008.
- **Risks:** one app version used as a proxy for every format.
- **Non-goals:** perpetual backward compatibility.

## OPS-003 — Sign and identify client artifacts

- **State:** proposed
- **Outcome:** Android and desktop packages can be verified and upgraded without
  losing identity or data.
- **Deliverables:** Android signing, desktop signing/notarization per supported
  OS, protected key process, checksums, provenance/build metadata, rotation/
  compromise plan, and release artifact manifest.
- **Acceptance:**
  - Signing keys never enter source or ordinary CI logs/artifacts.
  - Release can be traced to tag/source/toolchain/dependencies.
  - Fresh install and upgrade verification pass.
  - Key rotation/revocation has a rehearsed response.
- **Evidence:** signed release-candidate verification.
- **Dependencies:** SEC-002, DSK-012, AND-012.
- **Risks:** platform signing discovered too late.
- **Non-goals:** auto-update without secure rollback/compatibility design.

## OPS-004 — Ship a production self-host stack

- **State:** proposed
- **Outcome:** the Leaderboard server deploys as a conventional small service.
- **Deliverables:** pinned Ktor API image, PostgreSQL image, Docker Compose,
  Caddy TLS example, non-root user, persistent volume, resource limits,
  configuration schema, health/readiness, and migration lock.
- **Acceptance:**
  - Empty-host deployment and restart follow documentation.
  - Production startup rejects default secrets/unsafe URL configuration.
  - Database survives container replacement.
  - Service can run behind a user-provided reverse proxy as documented.
  - Upgrade command backs up or verifies backup before risky migration.
- **Evidence:** clean VPS/VM deployment exercise.
- **Dependencies:** LDB-001, SEC-004.
- **Risks:** Docker Compose called “one click” despite DNS/TLS/operator duties.
- **Non-goals:** Kubernetes/HA/multi-region deployment.

## OPS-005 — Automate backup and restore

- **State:** proposed
- **Outcome:** server and client backups are produced, verified, and restorable.
- **Deliverables:** `pg_dump` command/job, encryption/retention guidance,
  manifest/checksum, clean restore, application consistency check, client
  archive documentation, and drill cadence.
- **Acceptance:**
  - Timed server restore into clean deployment succeeds.
  - Restore verifies migrations, counts, memberships, ranks, and auth recovery.
  - Backup failure is observable.
  - Client backup sensitivity/source inclusion is explicit.
  - Operators know that an untested backup is not a recovery plan.
- **Evidence:** release-candidate restore report.
- **Dependencies:** DATA-011, LDB-012.
- **Risks:** backing up corrupt state or losing encryption keys.
- **Non-goals:** automatic off-site provider selection.

## OPS-006 — Add privacy-safe observability and incident workflow

- **State:** proposed
- **Outcome:** maintainers can distinguish service health, failures, abuse, and
  performance without collecting learner code.
- **Deliverables:** structured logs, request/event correlation, health/readiness,
  basic request/ingestion/rejection/DB metrics, alert suggestions, client
  support bundle, incident playbook, and retention.
- **Acceptance:**
  - Logs/metrics pass canary source/secret redaction tests.
  - Health does not expose private configuration.
  - Operator can diagnose database unavailable/full, migration failure, token
    rejection spike, and ingestion backlog.
  - Incident drill covers token-secret compromise.
- **Evidence:** outage and compromise tabletop.
- **Dependencies:** SEC-010, SEC-011.
- **Risks:** observability stack overcomplicating self-hosting.
- **Non-goals:** mandatory centralized telemetry or a large monitoring stack.

## OPS-007 — Release Problem packs independently and safely

- **State:** proposed
- **Outcome:** content corrections can ship with deterministic provenance and
  rollback without rebuilding the whole application where architecture allows.
- **Deliverables:** validation pipeline, deterministic pack, manifest/checksum/
  optional signature, compatibility, changelog, promotion, rollback, and
  trusted-server manifest update.
- **Acceptance:**
  - Broken reference/validation/leakage blocks promotion.
  - Released pack maps to source revision and content review.
  - Client/server trusted manifests update in compatible order.
  - Rollback does not erase learner history or source.
- **Evidence:** broken-pack and rollback rehearsal.
- **Dependencies:** PROB-008, LDB-005.
- **Risks:** pack/client/server version choreography.
- **Non-goals:** automatic unreviewed community-pack distribution.

## OPS-008 — Define support and issue triage

- **State:** proposed
- **Outcome:** user reports can be reproduced and prioritized without asking for
  sensitive source by default.
- **Deliverables:** templates, severity/area classification, environment/version
  capture, source-sharing consent, diagnostic preview, known-issues page,
  response expectation, and duplicate policy.
- **Acceptance:**
  - Templates ask for result/reason IDs before source.
  - Critical data-loss/security reports have a private path.
  - Diagnostic bundle is redacted and user-previewed.
  - One simulated report proceeds through reproduce/fix/evidence/release notes.
- **Evidence:** triage rehearsal.
- **Dependencies:** QLT-012, SEC-011.
- **Risks:** maintainers soliciting full backups/source casually.
- **Non-goals:** guaranteed enterprise support SLA.

## OPS-009 — Complete legal and user documentation

- **State:** proposed
- **Outcome:** users/operators understand licenses, content provenance, privacy,
  runner limitations, data control, and support boundaries.
- **Deliverables:** application license, third-party notices/SBOM link, Problem
  provenance policy, privacy explanation, terms/community expectations if
  needed, runner trust statement, data guide, operator guide, and accessibility
  statement.
- **Acceptance:**
  - FSRS reuse authorization/provenance is recorded.
  - No LeetCode text/tests are distributed without rights.
  - Social data fields/visibility/deletion are accurately described.
  - Local Python containment wording matches tests.
  - Documentation receives appropriate legal review before public service.
- **Evidence:** release documentation checklist.
- **Dependencies:** PROD-003, SEC-006, SEC-007.
- **Risks:** boilerplate documents disagreeing with implementation.
- **Non-goals:** substituting project planning for legal advice.

## OPS-010 — Generate milestone evidence bundles

- **State:** proposed
- **Outcome:** each milestone/tag answers “what was built and why should we
  trust it?”
- **Deliverables:** source/tag, toolchain/SBOM, schema/contract versions, test
  matrix, benchmark summary, accessibility result, pack hashes, migration/
  restore report, security scan/findings, known risks, and goal status.
- **Acceptance:**
  - Bundle is generated automatically where reliable and reviewed by a human.
  - It links failures/exceptions rather than hiding skipped jobs.
  - Required platform/device absence blocks the associated support claim.
  - Evidence remains available with the release.
- **Evidence:** dry-run M0/M1 bundle.
- **Dependencies:** QLT-001, QLT-011, SEC-012.
- **Risks:** evidence as unreviewed CI noise.
- **Non-goals:** reproducing every raw log indefinitely.

## OPS-011 — Rehearse rollback and forward recovery

- **State:** proposed
- **Outcome:** failed client/server/pack/migration releases have deliberate,
  data-safe recovery.
- **Deliverables:** rollback matrix, database forward-fix policy, client
  downgrade limits, pack rollback, bad manifest handling, release revocation,
  and communications.
- **Acceptance:**
  - Server upgrade failure can restore backup or resume safely.
  - Client downgrade never opens newer data unsafely.
  - Bad pack can be disabled/rolled back without deleting reviews.
  - Signing/dependency compromise has revocation/advisory path.
  - Rehearsal identifies recovery time/data-loss window.
- **Evidence:** failed-upgrade drill.
- **Dependencies:** DATA-008, OPS-003, OPS-005, OPS-007.
- **Risks:** treating rollback as always safer than forward fix.
- **Non-goals:** schema downgrade scripts for every migration.

## OPS-012 — Maintain a post-v1 decision backlog

- **State:** proposed
- **Outcome:** valuable future ideas remain visible without entering the v1
  critical path accidentally.
- **Deliverables:** evidence/questions/dependencies for:
  - encrypted personal cross-device sync;
  - stronger platform sandboxing;
  - additional languages;
  - iOS/web;
  - trusted community packs;
  - richer editor/debugging;
  - separately published `bee-fsrs`;
  - advanced FSRS parameter analysis;
  - distinct-Problem and topic-quality social metrics.
- **Acceptance:**
  - Each candidate names the user need and proof required.
  - No candidate is scheduled into v1 without explicit scope tradeoff.
  - Architecture reserves only cheap, justified seams.
- **Evidence:** quarterly review.
- **Dependencies:** PROD-008.
- **Risks:** speculative abstraction.
- **Non-goals:** implementing the post-v1 list during this plan.

## Milestone/commit forecast

These ranges are descriptive, not quotas.

| Milestone | Approximate period | Cumulative meaningful commits | Exit result |
|---|---:|---:|---|
| M0: Decisions and shells | Weeks 1–4 | 12–20 | Reproducible Android/desktop shell and accepted critical ADRs. |
| M1: Problem + desktop run | Weeks 5–12 | 35–55 | One-folder Problem flow and bounded desktop runner. |
| M2: Review truth + FSRS | Weeks 13–20 | 55–80 | Exactly-once reviews and trusted due queue. |
| M3: Android parity | Weeks 21–30 | 75–110 | Offline mobile run/review on emulator and physical device. |
| M4: Achievements | Weeks 31–36 | 90–125 | Replayable achievements and verified 5am Club. |
| M5: Leaderboards | Weeks 37–44 | 115–150 | Private self-hosted boards and offline sync. |
| M6: Hardening/beta/1.0 | Weeks 45–52 | 140–180 | Signed releases, restore/security/accessibility gates. |

## Delivery/operations exit gate

Stable BeeCode requires signed verifiable clients, tested upgrades, a clean-host
self-host deployment, a timed backup restore, privacy-safe diagnostics, legal/
user/operator documentation, and a milestone evidence bundle with no
nonwaivable blocker.

