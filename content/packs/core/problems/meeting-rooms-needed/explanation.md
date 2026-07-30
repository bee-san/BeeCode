## The insight

Rooms are interchangeable, so the answer is just the peak number of simultaneous meetings.
Split the meetings into two sorted lists — all the starts, all the ends — and sweep the starts
in order, releasing every room whose meeting has already finished:

```python
starts = sorted(pair[0] for pair in meetings)
ends = sorted(pair[1] for pair in meetings)
most = running = next_end = 0
for start in starts:
    while next_end < len(ends) and ends[next_end] <= start:
        running -= 1
        next_end += 1
    running += 1
    most = max(most, running)
return most
```

Pairing is discarded deliberately: which end goes with which start never matters for the count,
and dropping that information is what makes the sweep so short.

## Why `<=` releases the room

`ends[next_end] <= start` means a meeting ending exactly at `start` frees its room in time,
which the statement allows. Using `<` would count back-to-back meetings as simultaneous and
overstate the answer — `[[1, 2], [2, 3]]` needs one room, not two.

## The heap alternative

Sort by start and keep a min-heap of end times. For each meeting, pop every end at or before
its start, push its own end, and track the largest heap size. Same O(n log n), and the heap
*does* track which room is which — useful if you had to report assignments, unnecessary here.

The two-list sweep is the leaner version of the same idea: the sorted `ends` list is a heap
that was allowed to know the future.

## Why not merge intervals

Merging tells you which stretches of time are busy, not how deeply. `[[1, 10], [2, 3], [4, 5]]`
merges to one interval but needs two rooms. Depth and coverage are different questions.

## Pitfalls

**Strict `<` when releasing.** Overcounts on back-to-back meetings.

**Sorting the pairs together.** The two lists must sort independently; that is the trick.

**Resetting `running` per meeting.** It is a running total across the sweep.

**No meetings.** `0`, which the loop gives without a special case.

## Cost

O(n log n) time, O(n) space.
