# ADR 0005 — The topic is the SRS unit

- Status: accepted
- Date: 2026-07-30
- Constrains `shared/.../StudyService.kt`, `shared/.../TopicMastery.kt`,
  `persistence/.../ReviewRepository.kt`
- Builds on [0002](0002-personal-sync-direction.md) (schedules are replayed, never merged)

## Context

BeeCode shipped with one FSRS card per Problem: `problem_schedule.problem_id` was the
primary key and the queue answered "which Problem is due". That is the wrong unit. A
learner does not forget *two-sum*; they forget *dynamic programming*. The Problem is an
exercise that rehearses a technique, and the technique is the thing being remembered.

Problems already carried `topics: List<String>`, so the information was present and unused
by the scheduler.

## Decision

**Each DS&A topic is its own FSRS card**, with its own stability, difficulty, interval,
and due date, stored in `topic_schedule` keyed by the topic slug. The queue lists due
*topics*; per-Problem schedules keep updating underneath, because they are what answers
*which* member Problem to show.

One review advances **every** topic its Problem is tagged with. `median-two-sorted`
advances `arrays`, `binary-search`, and `two-pointers`, each with its own
`lastReviewedAt`, so their intervals stay independent. Bursts are self-limiting rather
than inflationary: FSRS-7's recall-stability gain is `exp((1 - R) · w) - 1` and R ≈ 1.0 at
elapsed ≈ 0, so five `arrays` Problems in one sitting buy almost no stability after the
first.

Member-Problem selection is `lastReviewedAt` ascending, then `lapseCount` descending, then
`stability` ascending, then id. Rotation is self-sustaining: practising a member updates
its `lastReviewedAt`, so a different member leads next time. This is what makes the queue
show "a DP problem" rather than the same one forever, and it is the whole reason the
per-Problem schedules were kept.

## Consequences

**"Frequently forgets DP" needs no heuristic.** Repeated `AGAIN` on DP lowers DP's
stability, FSRS shortens DP's interval, and DP surfaces more often. Topic queue order is
`due_at ASC` and nothing else — no weakness score, no blend. Inventing a ranking would
have meant inventing the thing FSRS already computes, and computing it worse.

**Retrievability ranks nothing.** It is 1.0 at the moment of review and decays with time,
so it measures recency of practice, not ability: a topic just failed and re-solved scores
maximal for hours afterwards. It is used for no ordering or scoring anywhere in this
design.

**Topic state is a projection, so the sync format does not move.** `topic_schedule` is
folded from the append-only `problem_review` log crossed with the pack's current tags,
exactly as `problem_schedule` is folded from the log alone. `ProfileTransfer.restore`
rebuilds it; the payload never carries it; `SnapshotMerge` was not touched and
`ProfileTransfer.FORMAT_VERSION` stays 2. That `SnapshotMergeTest` needed no change is the
evidence the projection claim is true rather than asserted. Bumping the version would have
been actively harmful — `SnapshotMerge.kt:66,72` fails on `!=`, not `>`, so a bump breaks
sync between an updated and a non-updated device in both directions.

**Retagging rewrites topic history.** Topics are deliberately excluded from
`ProblemLoader.computeRevision`, so replay uses *current* tags. Moving `max-subarray` out
of `dynamic-programming` retroactively removes it from DP's folded history, and DP's card
comes back different after the next rebuild. This is the honest behaviour of a projection
over mutable metadata: the alternative is a topic history that disagrees with the pack a
learner is looking at.

**Two reported numbers, never blended.** No single number can separate "weak at DP" from
"hasn't done DP", and pretending otherwise is the product bug this feature could most
easily ship:

- *Durability* is the topic's own FSRS interval, rendered by `formatIntervalDays` — the
  algorithm's own output.
- *Recall rate* is Bayesian shrinkage toward the learner's global accuracy,
  `(successes + k·prior) / (n + k)` with `k = 4` (`TopicMastery.SHRINKAGE_STRENGTH`),
  falling back to `desiredRetention` when there is no history at all. It is `null` below
  `MIN_TOPIC_REVIEWS = 5`, and both clients render null as "not enough practice yet" —
  never "0%".
- *Coverage* is a fraction with a visible numerator ("8 of 10 solved"), reported beside the
  rate and never multiplied into it.

Because the prior is the learner's *own* average, 0% and 100% stay reachable when a topic
is their entire history — the prior equals the topic's rate and pulls it nowhere. That is
deliberate, and a second invented prior to hide it would make the number less honest.

**Copy says "recall of Problems you have already solved", never "your DP ability".** The
evidence base is recall of previously-solved Problems, not fresh problem-solving, and the
label is the one place that could turn a true number into a lie.

**Slugs are unvalidated by choice.** No canonical vocabulary and no allow-list; display
names are humanised from the slug. The accepted risk is that a content typo mints a phantom
topic card. It is contained rather than ignored: a topic with no attempted member Problems
never enters the queue, so the worst case is a stray row and a stray line in Progress, not
a learner stuck on an unpractisable card. `StudyJourneyTest` covers exactly that path with
a deliberately misspelt `dynmaic-programming`.

## Alternatives considered

- **Keep the Problem as the card and add a weakness score over topics.** Rejected: the
  score would be a second scheduler, unvalidated, competing with FSRS for the same
  decision.
- **Advance only the Problem's primary topic.** Rejected: it needs a notion of primary
  that the content does not have, and it discards real evidence about the other techniques
  a Problem rehearses.
- **Validate slugs against a canonical topic list.** Rejected by the maintainer for v1:
  free-form tagging keeps content authoring cheap, and the phantom-topic failure mode is
  visible and harmless.
- **Carry topic schedules in the sync payload.** Rejected: it would make derived state
  something two devices could disagree over, which ADR 0002 exists to prevent.

## What would reverse this

- Topic cards drifting from their log — `verifyTopicScheduleIntegrity()` folds and
  compares, so this is observable rather than a suspicion.
- The recall rate proving misleading in practice. The gate and the shrinkage strength are
  named constants with tests asserting their behaviour, so they can move without a
  redesign; what would need a new ADR is reporting a single blended score.
- Topics growing structure — hierarchy, prerequisites, or difficulty bands — at which point
  the card key stops being a bare slug and the projection needs a real vocabulary, which is
  the point at which slug validation stops being optional.
