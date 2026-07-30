`intervals` is a list of `[start, end]` pairs, sorted by start and with no two overlapping.
Insert `fresh` so that the result is still sorted and still non-overlapping, merging wherever
the new interval touches existing ones.

Return the resulting list.

Two intervals overlap if they share any point, so `[1, 3]` and `[3, 5]` merge into `[1, 5]`.

## Constraints

- `0 <= len(intervals) <= 10000`
- `intervals[i][0] <= intervals[i][1]`
- `fresh[0] <= fresh[1]`
- The input is sorted by start and already non-overlapping.

## Follow-up

The input is already sorted, so re-sorting is wasted work. Walk it once in three stretches:
what comes strictly before the new interval, what overlaps it, and what comes strictly after.
