## The insight

Removing the fewest means keeping the most, and the most non-overlapping intervals you can keep
comes from the classic activity-selection greedy: sort by **end**, then keep every interval that
starts at or after the last kept one's end.

```python
ordered = sorted(intervals, key=lambda pair: pair[1])
kept, reach = 0, None
for start, end in ordered:
    if reach is None or start >= reach:
        kept += 1
        reach = end
return len(intervals) - kept
```

The answer is `len(intervals) - kept`.

## Why sort by end and not by start

Among the intervals still available, the one that finishes earliest leaves the most room for
everything after it. Sorting by start does not have that property: a very long interval can
start first and block several short ones. `[[1, 100], [2, 3], [4, 5]]` shows it — by start you
consider `[1, 100]` first and keep 1, by end you keep 2.

The exchange argument: in any optimal set, replacing its first interval with the
earliest-finishing available one cannot conflict with anything that followed, since the
replacement ends no later. So there is always an optimal solution that starts with the greedy's
choice, and induction does the rest.

## Touching is not overlapping

`start >= reach` uses `>=`, not `>`. That is the "touching is fine" rule, and it is the whole
difference between this and a Problem where intervals are closed at both ends. The first example
depends on it: `[1, 2]`, `[2, 3]`, `[3, 4]` all stay.

## Why count rather than build

Only the count is asked for, so `reach` is enough — no list of kept intervals, O(1) extra space
beyond the sort. It also sidesteps the question of *which* maximal set to return when several
tie.

## Pitfalls

**Sorting by start.** Wrong greedy.

**Strict `>` in the comparison.** Removes intervals that merely touch.

**Returning `kept`.** The question asks for removals.

**Sorting by length.** Sounds plausible, also wrong: a short interval in a crowded region can
block more than a long one in an empty region.

## Cost

O(n log n) time, O(n) space for the sort.
