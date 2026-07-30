Return every distinct combination of values from `candidates` summing to `target`, where
**each element may be used at most once**.

`candidates` may contain repeated values. Two equal values are different elements, so
both may appear in one combination — but the *set of combinations returned* must contain
no duplicates: if two combinations use the same values the same number of times, only
one must appear.

Return each combination sorted ascending. The order of the combinations themselves is
not judged.

## Constraints

- `1 <= len(candidates) <= 100`, positive integers, **not necessarily distinct**
- `1 <= candidates[i] <= 50`
- `1 <= target <= 30`

## Follow-up

Compare with [the reuse variant](combination-sum). Not reusing an element is the easy
half — advance the index. The hard half is the repeated *values*: `[1, 1, 2]` with
target `3` must yield `[1, 2]` once, not twice, even though the two `1`s are genuinely
different elements. Sort first, then find the one condition that skips the second
branch without losing `[1, 1]`.
