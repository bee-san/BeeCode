Given a list of `intervals`, each a pair `[start, end]` with `start <= end`, merge all
overlapping intervals and return the result sorted by start.

Intervals that merely touch — one ending exactly where the next begins, like `[1, 4]`
and `[4, 5]` — **do** overlap and must be merged.

## Constraints

- `0 <= len(intervals) <= 100_000`
- `-10^9 <= start <= end <= 10^9`
- The input is in no particular order.

## Follow-up

The hard part is that an interval can overlap one that appears much later in the
input. Sorting removes that possibility entirely. After sorting by start, what is the
only interval a new one can possibly overlap?
