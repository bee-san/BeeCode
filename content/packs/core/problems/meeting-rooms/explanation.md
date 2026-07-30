## The insight

Sort by start, then compare each meeting with the one before it:

```python
ordered = sorted(meetings, key=lambda pair: pair[0])
for index in range(1, len(ordered)):
    if ordered[index][0] < ordered[index - 1][1]:
        return False
return True
```

## Why adjacent pairs are enough

Suppose meeting `i` overlaps meeting `j` with `j > i + 1`. Then `j` starts before `i` ends, and
because the list is sorted by start, every meeting between them starts before `j` does — so it
also starts before `i` ends, and `i` overlaps its immediate successor too. Any conflict at all
implies an adjacent conflict, so checking adjacent pairs cannot miss one.

That argument is what turns an apparent O(n^2) pairwise check into a single pass, and it is worth
being able to state, because the same reasoning underpins interval merging.

## Touching is allowed

`ordered[index][0] < ordered[index - 1][1]` is strict. Equality means one meeting ends exactly
as the next begins, which the statement permits. Using `<=` rejects back-to-back meetings, and
`[[1, 2], [2, 3]]` is the test that catches it.

## Relation to the counting version

[How Many Rooms Are Needed](meeting-rooms-needed) asks for the number of rooms; this asks
whether one suffices, so it is that Problem's answer compared against `1`. Solving it that way
is correct and slower to write — but noticing the relationship is what tells you the sweep
generalises.

## Pitfalls

**Not sorting.** The input order says nothing.

**`<=` instead of `<`.** Rejects back-to-back meetings.

**Comparing every pair.** O(n^2) for no gain.

**Sorting by end.** Also works here, with the comparison adjusted — but start order is what the
adjacency argument is stated in.

## Cost

O(n log n) time, O(n) space for the sort.
