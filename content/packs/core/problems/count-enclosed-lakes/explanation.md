## The insight

Two phases.

**Phase one: disqualify.** Flood from every water cell on the border, marking everything reachable.
Any region touching the border is now entirely marked.

**Phase two: count.** Sweep the grid. Every unmarked water cell begins a region that cannot reach the
border, so count it and flood it so its other cells are not counted again.

Seeding from the border rather than testing each region is what removes the repeated work: one flood
disqualifies all the border-connected water at once, instead of each region separately discovering
it can escape.

## Which cells seed the flood

Every cell in row `0`, every cell in the last row, every cell in column `0`, and every cell in the
last column. Corners get seeded twice, which is harmless — `seen` makes the second visit a no-op.

Seeding only the corners, or only the first row and column, is the mistake this framing invites.

## Why the second phase floods too

Without flooding in phase two, a single enclosed region of five cells would be counted five times.
The flood is what turns "count the cells" into "count the regions" — the same role it plays in
[Count the Islands](number-of-islands).

## Iterative rather than recursive

A 200 by 200 grid of water is 40000 cells, and a recursive flood would nest that deep and exceed
Python's recursion limit. The explicit stack has no such limit. Bounds are checked when a cell is
*popped* rather than before pushing, which keeps the push sites to one line each at the cost of a few
extra stack entries.

## Pitfalls

**Seeding only the corners.** Misses most border water.

**Not flooding in phase two.** Counts cells, not regions.

**Counting diagonal adjacency.** The statement says horizontal and vertical.

**Recursing on a large grid.** Exceeds the recursion limit.

**A grid that is all water.** Every cell reaches the border, so the answer is `0`.

## Cost

O(rows * columns) time — each cell is flooded at most once — and O(rows * columns) space for the
marks and the stack.
