`nums` contains only the values `0`, `1` and `2`. Return the list sorted
ascending, so that all the 0s come first, then the 1s, then the 2s.

Sorting it is trivial. The exercise is to do it in **one pass** over the list,
moving elements around rather than counting them.

## Constraints

- `0 <= len(nums) <= 100_000`
- Every element is `0`, `1` or `2`.

## Follow-up

Counting how many of each value there are and rewriting the list is two passes and
perfectly good. The one-pass version needs three regions, not two: settled 0s,
settled 1s, and settled 2s — with the unexamined middle shrinking between them.
