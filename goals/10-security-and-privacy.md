# Target 10: security and privacy

This target provides an honest threat model for local Python execution, protects
accounts and self-hosted deployments, minimizes social data, and ensures
diagnostics never become an accidental source-code collection system.

## Threat actors and assets

| Actor/situation | Assets at risk | Required stance |
|---|---|---|
| Buggy learner code | UI availability, device resources, drafts | Bound and recover worker failure. |
| Deliberately hostile local code | Local files, credentials, network, processes | Enforce documented capability level; never overclaim. |
| Malicious/untrusted Problem pack | Execution, paths, rendering, resource use | Treat pack as data; trusted codecs/comparators only. |
| Network attacker | Credentials/activity/account | TLS, token validation, no secret leakage. |
| Compromised account/device token | Private board access and impersonation | Device tokens, rotation/revocation, audit. |
| Abusive board member | Privacy, service resources, rankings | Private membership, limits, owner removal. |
| Self-host operator | All server-readable data | Minimize readable data; state operator trust explicitly. |
| Dependency/supply-chain compromise | Clients/server/build/release | Pins, provenance, SBOM, scanning, signing. |
| Lost/corrupt device | Drafts and study history | Export, optional encryption, restore, safe deletion. |

## Security levels for local execution

BeeCode should expose a capability status rather than one unqualified “secure
sandbox” badge:

- **Hardened:** OS boundary restricts process, filesystem, network, and
  resources according to tested policy.
- **Process isolated:** code cannot crash the UI and receives a constrained
  workspace, but some host capabilities cannot be strongly denied.
- **Trusted code only:** runtime isolation claims are insufficient; execution
  is suitable only for code the device owner trusts.

The plan should try to reach hardened or clearly bounded process-isolated
behavior on supported targets, and refuse to disguise gaps.

## SEC-001 — Maintain the threat model

- **State:** proposed
- **Outcome:** architecture/release choices are reviewed against named assets,
  actors, boundaries, and abuse cases.
- **Deliverables:** data-flow diagrams, trust boundaries, attack trees,
  mitigations, accepted risks, owners, and revisit cadence.
- **Acceptance:**
  - Covers runners, packs, local DB/backups, credentials, API, invites,
    self-hosting, logs, builds, and updates.
  - Every accepted high-risk assumption has owner/revisit trigger.
  - Threat model updates when a boundary or stored/transferred field changes.
  - Release gate references unresolved findings.
- **Evidence:** review record each milestone.
- **Dependencies:** ARCH-008.
- **Risks:** a one-time document decaying.
- **Non-goals:** claiming a formal certification.

## SEC-002 — Control dependency and build provenance

- **State:** proposed
- **Outcome:** the cross-platform supply chain is inventoried and releases can
  name what they contain.
- **Deliverables:** version locks/checksums, SBOM, license report, vulnerability
  scan, source provenance, update SLA, build metadata, and exception workflow.
- **Acceptance:**
  - FSRS provenance/permission, Python runtime, Compose, database, crypto, Ktor,
    PostgreSQL image, and packaging tools are recorded.
  - Critical advisories block release or receive explicit time-bounded risk
    acceptance.
  - Release artifacts map to source revision and dependencies.
  - Unreviewed repository/binary sources are prohibited.
- **Evidence:** archived release report.
- **Dependencies:** ARCH-002.
- **Risks:** noisy scans masking meaningful vulnerabilities.
- **Non-goals:** blindly upgrading dependencies during a release freeze.

## SEC-003 — Specify and test runner containment

- **State:** proposed
- **Outcome:** every platform restriction is a testable contract.
- **Deliverables:** per-platform capability matrix, OS primitives, fallback
  level, adversarial suite, UI status, and documentation.
- **Acceptance:**
  - Tests attempt network, host paths, app credentials, environment, process
    spawn, symlink traversal, native imports, output flood, memory pressure, and
    infinite execution.
  - Worker receives no account token, server URL, local database path, or
    arbitrary host environment.
  - Termination kills the intended process tree/service.
  - Unsupported containment level is visible before executing untrusted packs.
- **Evidence:** desktop OS and Android device reports.
- **Dependencies:** SEC-001, ARCH-003.
- **Risks:** OS updates changing sandbox primitives.
- **Non-goals:** guaranteeing protection against kernel/VM vulnerabilities.

## SEC-004 — Treat Problem packs as untrusted data

- **State:** proposed
- **Outcome:** imported content cannot obtain arbitrary code execution or escape
  its directory through metadata/assets.
- **Deliverables:** size/path/type limits, parser hardening, trusted evaluator/
  codec IDs, signature/trust policy, quarantine, and render sanitization.
- **Acceptance:**
  - Absolute/parent/symlink traversal is rejected.
  - YAML/JSON/Markdown parsing has size/depth limits.
  - HTML/links/assets follow a documented safe-render policy.
  - No runtime pack contains reference/executable validator code.
  - Community/untrusted pack social activity cannot count by default.
- **Evidence:** malformed/fuzzed pack suite and artifact inspection.
- **Dependencies:** PROB-006, PROB-008.
- **Risks:** Markdown renderer or archive parser vulnerabilities.
- **Non-goals:** DRM or hiding tests from device owner.

## SEC-005 — Secure authentication and sessions

- **State:** proposed
- **Outcome:** passwords/tokens support small self-hosted communities without
  easy replay or accidental exposure.
- **Deliverables:** Argon2id password parameters, password policy, opaque
  high-entropy access tokens, atomically rotating hashed/HMAC device refresh
  tokens, device sessions, logout/revoke, recovery, brute-force limits, trusted
  proxy/TLS policy, and clock policy.
- **Acceptance:**
  - Passwords and raw access/refresh tokens never appear in database/logs.
  - Argon2id is used for passwords; fast cryptographic hash/HMAC is used for
    high-entropy access, refresh, invite, and recovery tokens as appropriate.
  - Refresh rotation is an atomic compare-and-swap with explicit concurrent
    refresh and replay-family behavior.
  - Token hash/scope/expiry/revocation cases fail and revocation is immediate.
  - User can view and revoke device sessions.
  - Recovery codes are one-time and stored safely.
  - Credential storage uses platform facilities where available.
  - Caddy proxy headers are trusted only from configured proxies; production
    clients cannot enable “accept any TLS certificate”.
- **Evidence:** authentication attack-case suite and parameter review.
- **Dependencies:** SEC-001, ARCH-007.
- **Risks:** password recovery and operator powers being unclear.
- **Non-goals:** enterprise identity federation in v1.

## SEC-006 — Maintain a field-level privacy inventory

- **State:** proposed
- **Outcome:** every collected, stored, transmitted, displayed, logged, and
  deleted field has a stated purpose.
- **Deliverables:** inventory with authority, sensitivity, purpose, location,
  transfer, recipients, retention, deletion, and logging policy.
- **Acceptance:**
  - Server schema/API diff cannot add an undocumented field.
  - Source, stdout, test values, FSRS state, detailed review history, and backup
    never enter social payloads.
  - Board member visibility is distinguished from operator visibility.
  - Optional diagnostics/telemetry have consent and redaction.
- **Evidence:** automated schema allowlist plus human review.
- **Dependencies:** PROD-006, ARCH-006.
- **Risks:** harmless-looking diagnostic fields becoming sensitive in
  combination.
- **Non-goals:** default behavioral telemetry.

## SEC-007 — Define retention, export, and deletion rights

- **State:** proposed
- **Outcome:** users and operators can understand and exercise data control.
- **Deliverables:** privacy explanation, local/server retention table, export,
  account deletion, board removal, backup/log policy, and administrator guide.
- **Acceptance:**
  - Account deletion and local data reset are separate.
  - Export is available before destructive action.
  - Server deletion/anonymization completes in documented time and preserves
    only justified integrity/audit data.
  - Backup/log deletion limitations are disclosed.
  - Operator process can verify completion.
- **Evidence:** timed deletion drill.
- **Dependencies:** SEC-006.
- **Risks:** promising impossible deletion from independent backups.
- **Non-goals:** legal advice for every jurisdiction; obtain review before public
  service.

## SEC-008 — Protect authorization and social integrity

- **State:** proposed
- **Outcome:** membership and owner actions are enforced server-side, while
  obvious ranking abuse is bounded.
- **Deliverables:** authorization matrix, unique event constraints, trusted
  manifest, invite hashing/rotation, quotas/rate limits, audit events, and
  moderation commands.
- **Acceptance:**
  - Every endpoint has owner/member/nonmember/anonymous tests.
  - Object IDs cannot bypass board membership.
  - Old invites fail after rotation/revocation.
  - Duplicate/future/flooded events cannot inflate current rank.
  - Audit logs reveal action/actor without raw secret/activity detail.
- **Evidence:** integration, abuse, and IDOR test suite.
- **Dependencies:** SEC-001, LDB-003, LDB-004.
- **Risks:** custom boards creating moderation expectations.
- **Non-goals:** perfect local anti-cheat.

## SEC-009 — Fuzz critical parsers and protocols

- **State:** proposed
- **Outcome:** malformed packs, backups, runner frames, API bodies, codecs, and
  migration data fail safely within resource bounds.
- **Deliverables:** property generators, fuzz targets, corpus retention,
  timeout/memory limits, reproducible counterexamples, and CI/release cadence.
- **Acceptance:**
  - Critical parsers have explicit size/depth limits.
  - Crashes/hangs save minimal reproducible fixtures.
  - Fixes retain regression cases.
  - Fuzzing never uploads real learner data.
- **Evidence:** campaign summaries and zero unresolved critical crashes.
- **Dependencies:** QLT-007.
- **Risks:** low-value random fuzzing without invariants.
- **Non-goals:** fuzzing every UI component.

## SEC-010 — Create incident response and secret rotation

- **State:** proposed
- **Outcome:** a compromised token/key/dependency or data incident has a tested
  response.
- **Deliverables:** severity matrix, contact/ownership, containment, credential
  rotation, release revocation, evidence preservation, notification, recovery,
  and post-incident process.
- **Acceptance:**
  - Server signing/token secret rotation is documented and rehearsed.
  - Compromised device token can be revoked without deleting local study data.
  - Bad client/content releases have a stop/rollback/forward-fix path.
  - Logs needed for response follow privacy retention policy.
- **Evidence:** tabletop for token compromise and bad migration.
- **Dependencies:** SEC-001, SEC-005.
- **Risks:** single-maintainer response gaps.
- **Non-goals:** a 24/7 SOC promise.

## SEC-011 — Enforce privacy-safe observability

- **State:** proposed
- **Outcome:** logs, metrics, crash reports, and support bundles help diagnose
  behavior without collecting code or credentials.
- **Deliverables:** structured allowlist logging, redaction library, event/run
  correlation IDs, metric list, diagnostic export choices, and retention.
- **Acceptance:**
  - Logs reject/source-redact Python source, stdout/stderr, test values,
    passwords, bearer/refresh tokens, raw invite codes, and full backups.
  - Exception object dumps are prohibited at trust boundaries.
  - Crash reporting is opt-in where used and source-safe.
  - Support bundle previews exactly what will be shared.
- **Evidence:** automated canary-secret/source redaction tests.
- **Dependencies:** QLT-008, SEC-006.
- **Risks:** framework default request/exception logging.
- **Non-goals:** remote replay/session recording.

## SEC-012 — Gate releases on critical security properties

- **State:** proposed
- **Outcome:** known data-loss, auth bypass, secret leak, runner escape from
  claimed boundary, or unsigned artifact defects cannot be deadline-waived.
- **Deliverables:** severity definitions, release blocker list, exception
  authority/expiry, evidence bundle, and disclosure channel.
- **Acceptance:**
  - Critical blockers include auth/authorization bypass, credential/source
    logging, remote code execution through packs, data loss, and broken signing.
  - Accepted noncritical risks have owner and due date.
  - Security documentation states unresolved capability differences.
  - Stable release links threat-model/scans/tests.
- **Evidence:** release-candidate gate rehearsal.
- **Dependencies:** QLT-011, SEC-001.
- **Risks:** vague severity allowing exceptions by wording.
- **Non-goals:** shipping with “security later” for core boundaries.

## Security/privacy exit gate

The threat model and privacy inventory must match the actual architecture;
runner claims, authorization, deletion, redaction, and restore must be exercised;
and no captured social request/database/log may contain learner source, test
output, or FSRS state.
