`intervals` is a list of `[start, end]` pairs, in no particular order. Return the fewest
intervals that must be removed so that no two of the rest overlap.

Intervals that merely touch at a point do **not** overlap: `[1, 2]` and `[2, 3]` may both stay.

## Constraints

- `1 <= len(intervals) <= 100000`
- `intervals[i][0] < intervals[i][1]`

## Follow-up

Removing the fewest is the same as *keeping* the most, and that flips a subtraction problem into
a scheduling one. Sort by something, then sweep. Sorting by start is the tempting choice and it
is the wrong one — what should you sort by, and why?
