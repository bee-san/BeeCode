# Target 02: Problem system

This target makes Problem authoring routine: add one self-contained folder, run
one validation command, and obtain a deterministic pack. There is no central
registry to update by hand.

## Canonical source layout

```text
content/
└── packs/
    └── core/
        ├── pack.yaml
        └── problems/
            └── two-sum/
                ├── problem.yaml
                ├── statement.md
                ├── starter.py
                ├── tests.yaml
                ├── explanation.md
                ├── reference.py
                └── assets/
```

`reference.py` exists only to validate content during development and CI. It is
never distributed inside the application or a `.beecodepack`.

`explanation.md` is distributable, revealable pedagogy. It may describe the
pattern, complexity, and canonical source snippets, but clients treat it as
rendered data and never execute it. Revealing it—or a previous successful
learner solution—marks the current review session assisted. This gives the UI a
real reveal action without shipping the executable CI reference.

## Proposed Problem contract

| Concern | Required representation |
|---|---|
| Identity | Stable namespaced `problemId` plus monotonic content revision. |
| Prompt | Original/licensed Markdown statement and examples. |
| Explanation | Revealable approach/solution text that is never executed. |
| Entry point | Function/class signature with named input/output codecs. |
| Starter | Syntactically valid editable Python source. |
| Tests | Structured values, limits, comparator ID, and human-safe labels. |
| Provenance | Author, source relationship, license, attribution, review date. |
| Classification | Difficulty, plus `dataStructures` and `algorithms` drawn from the pack's closed `taxonomy.yaml`; `topics` is their derived union. Prerequisites and estimated solve/review time. |
| Compatibility | Minimum pack schema, runner contract, and Python version. |

The authoring source may be YAML/Markdown/Python, but compilation produces one
strict canonical runtime representation so app clients do not contain a loose
YAML interpreter.

## PROB-001 — Version the Problem source schema

- **State:** proposed
- **Outcome:** every Problem has enough information to render, run, migrate,
  audit, and schedule safely.
- **Deliverables:** schema, field reference, examples, JSON Schema or equivalent
  validation, and compatibility policy.
- **Acceptance:**
  - Stable ID and revision are mandatory.
  - Function signature, codecs, runtime, limits, tests, and provenance are
    explicit.
  - Revealable explanation/solution content is distinct from the CI reference.
  - Unknown mandatory schema versions fail with remediation.
  - Optional extensions cannot change execution semantics invisibly.
- **Evidence:** valid, minimal, maximal, and malformed fixtures.
- **Dependencies:** ARCH-004, PROD-003.
- **Risks:** an over-flexible schema becoming a programming language.
- **Non-goals:** arbitrary author-supplied executable validators.

## PROB-002 — Define trusted codecs

- **State:** proposed
- **Outcome:** structured test values map predictably to Python values and back.
- **Deliverables:** versioned codec IDs for scalar, list, tuple, optional,
  matrix, linked list, binary tree, and simple graph structures.
- **Acceptance:**
  - Each codec defines canonical input, Python representation, output, size
    limits, and diagnostic rendering.
  - Round trips are deterministic.
  - Cycles, depth, numeric range, Unicode, nullability, and malformed values are
    handled explicitly.
  - App clients select trusted implementations by ID.
- **Evidence:** round-trip, property, malformed-input, and cross-platform tests.
- **Dependencies:** PROB-001.
- **Risks:** inconsistent Android/desktop decoding; enormous structures.
- **Non-goals:** arbitrary object deserialization.

## PROB-003 — Define trusted comparators

- **State:** proposed
- **Outcome:** expected/actual judgment is deterministic and reviewable.
- **Deliverables:** exact, float-tolerance, unordered collection, nested
  structure, and Problem-specific normalized comparator policies where needed.
- **Acceptance:**
  - Comparator IDs are closed and versioned.
  - Equality, tolerance, ordering, duplicate, and NaN behavior are documented.
  - Comparators produce bounded human-readable differences.
  - Authors cannot embed code in comparator configuration.
- **Evidence:** property and golden-diagnostic tests.
- **Dependencies:** PROB-002.
- **Risks:** comparators accepting invalid solutions or producing huge diffs.
- **Non-goals:** arbitrary Python predicates from community packs.

## PROB-004 — Auto-discover Problem folders

- **State:** proposed
- **Outcome:** adding a conforming folder is sufficient to register a Problem.
- **Deliverables:** deterministic scanner, duplicate detection, ignore rules,
  and generated index.
- **Acceptance:**
  - No hand-maintained Problem list exists.
  - Discovery order does not affect the generated output.
  - Duplicate IDs, paths, aliases, or revisions fail the build.
  - Unrecognized files are rejected or explicitly ignored by policy.
- **Evidence:** deterministic checksum and filesystem mutation tests.
- **Dependencies:** PROB-001.
- **Risks:** platform-specific path/order behavior.
- **Non-goals:** runtime crawling of arbitrary user directories in v1.

## PROB-005 — Generate new Problem skeletons

- **State:** proposed
- **Outcome:** an author starts from a structurally valid folder.
- **Deliverables:** `newProblem` command, templates, ID rules, overwrite
  protection, and guided codec selection.
- **Acceptance:**
  - The generated skeleton passes structural validation before TODO content is
    made releasable.
  - The command never overwrites existing content.
  - IDs and paths are normalized consistently.
  - Examples cover the common function shapes.
- **Evidence:** CLI integration tests and contributor walkthrough.
- **Dependencies:** PROB-001, PROB-004.
- **Risks:** templates ossifying a weak schema.
- **Non-goals:** a graphical CMS.

## PROB-006 — Validate content deeply

- **State:** proposed
- **Outcome:** content errors are found before packaging and explained at the
  authoring location.
- **Deliverables:** structural, semantic, Python syntax, Markdown asset,
  provenance, codec, limit, and revision validators.
- **Acceptance:**
  - Errors include Problem ID, file, field/line where possible, and remediation.
  - Starter/reference source compiles for the declared Python version.
  - Tests are unique, bounded, codec-compatible, and nonempty.
  - Linked assets exist and remain inside the Problem directory.
  - CI treats release-blocking warnings consistently.
- **Evidence:** mutation suite proving each invalid class is caught.
- **Dependencies:** PROB-002, PROB-003, PROB-005.
- **Risks:** validator drift from runtime behavior.
- **Non-goals:** proving a Problem is pedagogically good automatically.

## PROB-007 — Verify reference solutions

- **State:** proposed
- **Outcome:** shipped expected results are executable evidence, not unchecked
  author claims.
- **Deliverables:** CI runner, reference-source policy, timeout/limit policy,
  coverage report, and negative fixtures.
- **Acceptance:**
  - Every release Problem has at least one trusted reference solution.
  - References pass every source and build-time test.
  - Deliberately broken references fail for the expected reason.
  - Reference source is omitted from all distributed artifacts.
- **Evidence:** CI report and archive inspection.
- **Dependencies:** RUN-004, PROB-006.
- **Risks:** only testing happy-path references; accidental source leakage.
- **Non-goals:** presenting the reference as the only correct approach.

## PROB-008 — Build deterministic packs

- **State:** proposed
- **Outcome:** the same content revision produces the same distributable
  `.beecodepack`.
- **Deliverables:** pack manifest, canonical file ordering, normalized
  timestamps/encoding, checksums, compression, and artifact denylist.
- **Acceptance:**
  - Repeated builds on supported hosts have the same logical manifest and
    checksum target.
  - Pack includes statements, starter source, revealable explanations, tests,
    assets, and compatibility metadata.
  - Pack excludes references, temporary files, caches, credentials, and author
    notes.
  - A corrupt or incompatible pack fails before import.
- **Evidence:** reproducibility and archive-inspection tests.
- **Dependencies:** PROB-004, PROB-006, PROB-007.
- **Risks:** assuming locally visible tests are secret.
- **Non-goals:** DRM or cryptographic secrecy from the device owner.

## PROB-009 — Govern content revisions

- **State:** proposed
- **Outcome:** statement corrections are easy while signature/test changes do
  not silently break saved work or history.
- **Deliverables:** change classes, revision policy, compatibility matrix,
  deprecation/removal policy, and migration prompts.
- **Acceptance:**
  - Cosmetic, statement, metadata, test, signature, and semantic changes have
    named effects.
  - Existing reviews remain attached to stable Problem ID.
  - Incompatible signature changes preserve the learner's old source and offer
    a deliberate migration/reset path.
  - Removed Problems remain understandable in history.
- **Evidence:** fixtures for every change class.
- **Dependencies:** PROB-001, ARCH-006.
- **Risks:** mutating old tests changing historical meaning.
- **Non-goals:** magically rewriting arbitrary learner solutions.

## PROB-010 — Create an inspection and preview workflow

- **State:** proposed
- **Outcome:** authors can see exactly what clients will render and execute
  before release.
- **Deliverables:** rendered preview, manifest view, test summary, codec view,
  artifact tree, and local run command.
- **Acceptance:**
  - Preview uses the canonical compiled representation.
  - It surfaces revision/provenance/compatibility warnings.
  - It can run the starter and reference under release limits.
  - It never imports the pack into the author's real study profile.
- **Evidence:** golden previews and contributor session.
- **Dependencies:** PROB-006, PROB-008.
- **Risks:** preview behavior diverging from clients.
- **Non-goals:** WYSIWYG statement editing in v1.

## PROB-011 — Ship a representative seed curriculum

- **State:** proposed
- **Outcome:** the first official pack validates the system across common
  algorithm/data-shape categories.
- **Deliverables:** original/licensed Problems spanning arrays, strings, maps,
  stacks, queues, recursion, linked lists, trees, heaps, graphs, intervals, and
  dynamic programming.
- **Acceptance:**
  - At least one Problem exercises each shipped codec/comparator.
  - Tags and prerequisite relationships are reviewed.
  - Each Problem has edge-case tests and at least one alternate-solution review.
  - Difficulty and expected recall time are calibrated in beta.
- **Evidence:** pack quality report and human content review.
- **Dependencies:** PROB-007, PROD-003.
- **Risks:** raw quantity displacing quality.
- **Non-goals:** matching LeetCode's catalog size.

## PROB-012 — Publish the contributor handbook

- **State:** proposed
- **Outcome:** a new contributor can add a Problem without maintainer
  intervention.
- **Deliverables:** tutorial, schema reference, testing guidance, provenance
  checklist, style guide, review rubric, and troubleshooting.
- **Acceptance:**
  - An unfamiliar contributor creates and validates a sample from the handbook.
  - Every validator error links to relevant documentation.
  - The review rubric covers correctness, pedagogy, accessibility, and rights.
  - Content release steps are reproducible.
- **Evidence:** observed contributor exercise.
- **Dependencies:** PROB-005, PROB-010.
- **Risks:** documentation lag.
- **Non-goals:** accepting unreviewed packs into the official catalog.

## Problem-system exit gate

A contributor must be able to generate a folder, author a Problem, validate and
preview it, run its reference, produce a deterministic pack, and prove the pack
contains no reference source—all without editing a central registry.
