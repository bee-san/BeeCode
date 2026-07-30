## The insight

Three stretches, one pass, no sorting:

**Before.** While an interval ends strictly before the new one starts, copy it through
untouched.

**Overlapping.** While an interval starts at or before the running end, absorb it: widen
`start` to the smaller start and `end` to the larger end. Then append the merged interval once.

**After.** Copy the rest through.

```python
result, index = [], 0
start, end = fresh
while index < len(intervals) and intervals[index][1] < start:
    result.append(intervals[index]); index += 1
while index < len(intervals) and intervals[index][0] <= end:
    start = min(start, intervals[index][0])
    end = max(end, intervals[index][1])
    index += 1
result.append([start, end])
result.extend(intervals[index:])
return result
```

## Why the comparisons are what they are

`intervals[index][1] < start` is strict, and `intervals[index][0] <= end` is not. Both follow
from touching counting as overlapping: an interval ending exactly at `start` must be merged, so
it must not be copied through in the first loop, and an interval starting exactly at `end` must
be merged too.

Get either comparison the wrong way round and touching intervals stay separate — which
`[[1, 3]]` with `[3, 5]` catches immediately.

## Why `end` must grow inside the loop

Absorbing one interval can extend `end` far enough to reach the next, so the loop condition has
to see the *updated* `end`. That is what makes the second example's `[4, 8]` swallow three
intervals rather than one.

## Widening the start

`start` can only shrink, and only from the first overlapping interval — the input is sorted, so
later ones start later. The `min` is therefore only load-bearing once, but writing it as a `min`
costs nothing and removes a special case.

## Pitfalls

**Re-sorting the input.** Correct but wasteful; the sortedness is given.

**Appending the merged interval inside the loop.** Emits one interval per absorbed interval
instead of one in total.

**Strict `<` in the overlap test.** Leaves touching intervals unmerged.

**An empty input.** The answer is `[fresh]`; all three loops handle it without a special case.

## Cost

O(n) time, O(n) space for the result.
