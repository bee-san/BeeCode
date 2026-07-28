# Target 06: Android client

This target creates a phone-first BeeCode experience for recall, Python editing,
local execution, and review. It shares domain behavior and visual language with
desktop, but it must not compress a desktop three-pane layout onto a phone.

## Mobile workspace modes

| Mode | Primary content | Persistent affordances |
|---|---|---|
| Recall | Problem statement, examples, constraints | Draft status, move to code, reveal |
| Code | Full-height editor and mobile symbol row | Run/cancel, problem peek, results |
| Results | Test summary and focused failure detail | Return to code, rerun, finalize |
| Finalize | Reflection, allowed rating, next interval | Commit once or return |

On tablets/foldables, modes may appear side by side, but they retain the same
state and focus semantics.

## AND-001 — Define Android support and device matrix

- **State:** proposed
- **Outcome:** the minimum API, ABIs, screens, input methods, and test devices
  match Python runtime and product evidence.
- **Deliverables:** API/ABI policy, phone/tablet/foldable stance, emulator image,
  physical devices, keyboard/IME coverage, and support window.
- **Acceptance:**
  - Minimum API is no lower than the chosen Python provider supports.
  - `arm64-v8a` device and `x86_64` emulator execution are covered.
  - At least one low/mid-range physical phone is in the release matrix.
  - Unsupported ABI/runtime state fails before a review is started.
- **Evidence:** published device matrix.
- **Dependencies:** PROD-005, ARCH-002.
- **Risks:** emulator-only confidence; large ABI packaging.
- **Non-goals:** every OEM/API combination.

## AND-002 — Build adaptive Android navigation

- **State:** proposed
- **Outcome:** primary destinations work naturally on compact and expanded
  windows.
- **Deliverables:** bottom navigation/rail policy, deep links, back behavior,
  saved state, window size classes, and offline/account states.
- **Acceptance:**
  - Today, Problems, Achievements, Leaderboards, History, and Settings are
    reachable without desktop menus.
  - System back and predictive back behave consistently.
  - Rotation/resizing restores destination and meaningful focus.
  - Social routes degrade cleanly when signed out/offline.
- **Evidence:** Compose navigation and screenshot matrix.
- **Dependencies:** ARCH-002, UX-001.
- **Risks:** duplicated navigation logic diverging from desktop.
- **Non-goals:** identical navigation chrome across platforms.

## AND-003 — Design the mobile review workspace

- **State:** proposed
- **Outcome:** learners can move between recall, code, results, and finalization
  without losing context.
- **Deliverables:** mode state, transitions, compact/expanded layouts, scroll
  position, run state, draft status, and interruption behavior.
- **Acceptance:**
  - Critical actions remain reachable above/around the soft keyboard.
  - Switching modes never restarts an active run accidentally.
  - Statement/result position is preserved.
  - Finalization is clearly distinct from a passing run.
  - Expanded screens can use parallel panes without changing behavior.
- **Evidence:** end-to-end mobile usability script.
- **Dependencies:** RUN-007, SRS-003, AND-002.
- **Risks:** excessive mode switching.
- **Non-goals:** fitting every desktop panel on one phone screen.

## AND-004 — Make Python input tolerable on a phone

- **State:** proposed
- **Outcome:** common Problem solutions can be written with a soft keyboard
  without constant layout switching.
- **Deliverables:** configurable symbol row, indentation/outdent, newline
  indentation, bracket/quote helpers, cursor/selection, line numbers, scroll,
  undo/redo, find, font controls, and external keyboard shortcuts.
- **Acceptance:**
  - Frequently used Python symbols are reachable in one tap.
  - Helpers never corrupt selection or unexpected IME composition.
  - Editor remains responsive at maximum source size.
  - Hardware keyboard preserves conventional shortcuts.
  - TalkBack and switch access can reach editor-adjacent actions.
- **Evidence:** IME matrix, external keyboard test, and typing usability study.
- **Dependencies:** RUN-008, UX-003.
- **Risks:** Compose text component limitations; helper behavior surprising
  experienced users.
- **Non-goals:** mobile IDE completion/refactoring in v1.

## AND-005 — Integrate the Android Python worker

- **State:** proposed
- **Outcome:** the mobile workspace implements the same runner result contract
  with Android-appropriate lifecycle control.
- **Deliverables:** provider adapter, isolated worker/service, source/test
  transfer, cancellation, termination, result mapping, and runtime status.
- **Acceptance:**
  - Shared conformance suite passes on emulator and physical phone.
  - Infinite CPU loop can be stopped within the declared budget.
  - Killing the worker cannot kill or corrupt the UI process.
  - Worker startup failure leaves source editable and explains remediation.
  - No network is required.
- **Evidence:** runner conformance and fault-injection report.
- **Dependencies:** RUN-003, RUN-005.
- **Risks:** provider cannot live in desired isolated process; OEM behavior.
- **Non-goals:** running code in background without explicit user action.

## AND-006 — Survive Android lifecycle events

- **State:** proposed
- **Outcome:** rotation, backgrounding, process death, reboot, and update do not
  lose drafts or double-count reviews.
- **Deliverables:** saved UI state, durable domain state, worker ownership,
  incomplete-run cleanup, session restoration, and update migrations.
- **Acceptance:**
  - `Don't keep activities` does not lose durable source.
  - Low-memory process recreation returns to a coherent workspace.
  - A killed active run becomes interrupted, not passed.
  - Uncertain finalization reconciles by `reviewSessionId`.
  - Device reboot preserves queue and drafts.
- **Evidence:** lifecycle/process-recreation suite.
- **Dependencies:** DATA-002, RUN-008, SRS-006.
- **Risks:** accidentally persisting large source/results in saved-instance
  state.
- **Non-goals:** keeping CPU execution alive indefinitely in background.

## AND-007 — Provide optional due reminders

- **State:** proposed
- **Outcome:** learners may receive useful local reminders without accounts,
  always-on services, or manipulative notification volume.
- **Deliverables:** permission flow, schedule, quiet hours, frequency controls,
  due-count content, deep link, timezone handling, and disable path.
- **Acceptance:**
  - Notifications are opt-in and obey platform permission.
  - Reminders derive from local due state.
  - Timezone/clock changes reschedule safely.
  - Opening a reminder navigates to the current queue, not stale Problem state.
  - Denying permission has no nag loop.
- **Evidence:** time-controlled notification tests.
- **Dependencies:** SRS-005, PROD-009.
- **Risks:** OEM background restrictions; unhealthy pressure.
- **Non-goals:** social push notifications in v1.

## AND-008 — Make data portable through Android document APIs

- **State:** proposed
- **Outcome:** backup/export/import works without broad storage permissions.
- **Deliverables:** create/open document flows, archive preview, encryption
  option, progress, cancellation, conflict prompts, and error recovery.
- **Acceptance:**
  - Export/import round trip preserves review history and drafts.
  - The app requests no all-files permission.
  - Corrupt/incompatible archives are rejected before mutation.
  - Import is transactional or recoverable.
  - Sensitive backup contents are explained.
- **Evidence:** permission and archive fixture tests.
- **Dependencies:** DATA-003.
- **Risks:** document provider quirks and interrupted large transfers.
- **Non-goals:** background cloud-drive synchronization.

## AND-009 — Budget performance, memory, storage, and battery

- **State:** proposed
- **Outcome:** embedded Python and rich editing do not make daily use sluggish
  or wasteful.
- **Deliverables:** cold/warm launch benchmarks, editor input latency, worker
  startup, run duration overhead, memory peaks, APK/installed size, database
  growth, and background-work budget.
- **Acceptance:**
  - Measurements name device/API/build type.
  - UI main thread remains responsive while code executes.
  - Worker memory is released after configured retention.
  - No unbounded run output/history/cache growth exists.
  - Background work is minimal and constrained.
- **Evidence:** macrobenchmarks, profiler captures, and long-running profile.
- **Dependencies:** QLT-004, AND-005.
- **Risks:** runtime/ABI size and cold start dominating.
- **Non-goals:** a single performance number detached from device context.

## AND-010 — Verify offline behavior explicitly

- **State:** proposed
- **Outcome:** airplane mode is a normal supported condition for all study
  capabilities.
- **Deliverables:** offline state model, cached content behavior, queued social
  events, account token handling, reconnect behavior, and UI copy.
- **Acceptance:**
  - First local study after content installation needs no DNS/server.
  - Code execution, FSRS, achievements, history, and backup work offline.
  - Social activity queues and retries idempotently later.
  - Offline state does not block navigation or show spurious auth failure.
- **Evidence:** full airplane-mode acceptance script.
- **Dependencies:** DATA-004, LDB-007.
- **Risks:** framework/network checks leaking into local use cases.
- **Non-goals:** joining a new Leaderboard while offline.

## AND-011 — Meet Android accessibility baselines

- **State:** proposed
- **Outcome:** the mobile review loop is operable with TalkBack, large text,
  reduced motion, and alternative input.
- **Deliverables:** semantic labels, traversal order, headings, live-region
  policy, touch sizes, contrast, text scaling, motion policy, and manual script.
- **Acceptance:**
  - TalkBack can traverse statement, editor controls, results, and ratings in
    logical order.
  - 200% font/display scaling does not hide finalization.
  - Errors do not rely only on color.
  - Animations respect system reduction preferences where available.
  - Touch targets meet the declared minimum.
- **Evidence:** automated scan and manual TalkBack session.
- **Dependencies:** UX-002, UX-006.
- **Risks:** code editor accessibility gaps.
- **Non-goals:** claiming full accessibility based only on automated scans.

## AND-012 — Package and sign Android releases

- **State:** proposed
- **Outcome:** debug, beta, and stable builds have controlled identity,
  upgrades, signing, and compatibility.
- **Deliverables:** application IDs/channels, ABI packaging, signing protection,
  versioning, reproducibility metadata, APK/AAB strategy, and upgrade tests.
- **Acceptance:**
  - Release secrets are outside source control.
  - Installed beta/stable data boundaries are deliberate.
  - Upgrade preserves database, drafts, runtime, and packs.
  - Signature/checksum/provenance can be verified.
  - Unsupported platform/runtime reports before data damage.
- **Evidence:** clean install and upgrade matrix.
- **Dependencies:** OPS-003, AND-001.
- **Risks:** signing or store requirements appearing late.
- **Non-goals:** choosing a distribution store before product/legal needs.

## Android verification environment

The development plan should provision build tools independently of emulator
availability. An emulator gate requires hardware acceleration (`/dev/kvm` on
Linux) or a reachable external emulator/device. If acceleration is unavailable,
software emulation may be used for debugging but should not be treated as a
reliable release gate. Physical-device evidence remains required because soft
keyboards, lifecycle behavior, power, and ABI packaging differ from emulators.

## Android exit gate

The same representative Problem pack and runner conformance suite must pass
offline on the supported emulator and at least one physical phone. Drafts must
survive lifecycle/process death, infinite code must terminate without killing
the app, and review finalization must remain exactly once.
