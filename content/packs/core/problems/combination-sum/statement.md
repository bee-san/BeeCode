Return every distinct combination of values from `candidates` that sums to `target`.
**A candidate may be used any number of times.**

Two combinations are the same if they use the same values the same number of times,
regardless of order — so `[2, 2, 3]` and `[2, 3, 2]` are one combination, and only one
must appear.

Return each combination sorted ascending. The order of the combinations themselves is
not judged.

## Constraints

- `1 <= len(candidates) <= 30`, all distinct positive integers
- `1 <= candidates[i] <= 200`
- `1 <= target <= 500`
- The candidates are not necessarily sorted on input.

## Follow-up

Naive recursion generates `[2, 3]` and `[3, 2]` separately and then has to deduplicate.
There is a way to make the duplicates impossible instead: at each step, allow only
candidates from the current index onwards. What ordering does that impose, and why does
allowing the *same* index again still permit reuse?
