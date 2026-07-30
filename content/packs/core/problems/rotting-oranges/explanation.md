## The insight

Spoilage is breadth-first search where one level is one minute, seeded with every
already-spoiled orange:

```python
frontier = [every cell holding 2]
fresh = count of cells holding 1
minutes = 0
while frontier and fresh > 0:
    following = []
    for cell in frontier:
        for neighbour in four_neighbours(cell):
            if in_bounds and grid[neighbour] == 1:
                grid[neighbour] = 2
                fresh -= 1
                following.append(neighbour)
    frontier = following
    minutes += 1
```

Seeding with all of them is what makes it correct. One search per spoiled orange, taking
the minimum, gives the same distances for far more work — and does not compose, because
the oranges interfere.

## Counting the levels, not the cells

The answer is how many levels the search *advanced*, so `minutes` increments once per
level rather than once per cell. The `fresh > 0` guard in the loop condition is what stops
a final empty round from adding a phantom minute: once nothing is fresh, the work is
done, and a level that spoils nothing must not count.

Tracking `fresh` explicitly, rather than rescanning the grid, also gives the `-1` answer
for free: if the frontier empties while `fresh > 0`, those oranges were never adjacent to
anything spoiled.

## The two answers that are not distances

`0` when nothing is fresh — including a grid of all `0`s, and a grid of all `2`s. Check
this before the loop, because the loop would return `0` anyway but only by accident of
the guard.

`-1` when some orange is unreachable, which is not a long distance but no distance at
all.

## Pitfalls

**Incrementing `minutes` per cell.** Gives the number of spoiled oranges.

**Incrementing after a level that spoiled nothing.** Off by one, and it shows only on
inputs where the search runs out exactly at the end.

**Marking on dequeue.** A fresh orange next to two spoiled ones is enqueued twice and
double-counted out of `fresh`, which then goes negative and the `-1` check misfires.

**Rescanning for fresh oranges at the end instead of counting down.** Works, but costs a
sweep and hides the unreachability check.

## Cost

O(rows * columns) time and space — each cell enters the frontier at most once. This is
the same multi-source shape as
[Distance to the Nearest Gate](distance-to-nearest-gate), which asks for the distances
themselves instead of just the last one.
