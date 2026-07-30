Return every distinct subset of `nums`, which **may contain repeated values**.

Two subsets are the same if they contain the same values the same number of times, so
`[1, 2]` and `[2, 1]` are one subset, and from `[1, 1]` the subsets are `[]`, `[1]` and
`[1, 1]` — three, not four.

Return each subset sorted ascending. The order of the subsets themselves is not judged.

## Constraints

- `0 <= len(nums) <= 12`
- `-10 <= nums[i] <= 10`
- Values may repeat.

## Follow-up

Compare with [the distinct-values version](subsets), where every subset is
automatically unique. Sorting first makes equal values adjacent, and then the same
sibling-skip rule that solves
[Combinations Without Reuse](combination-sum-no-reuse) applies unchanged. Recognising
that these are the same problem with a different accept condition is the point.
