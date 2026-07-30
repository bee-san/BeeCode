`grid` holds `0` (empty), `1` (a fresh orange) and `2` (a spoiled orange).

Every minute, each fresh orange that is horizontally or vertically adjacent to a spoiled
one becomes spoiled. Return the number of minutes until no fresh orange remains, or `-1`
if that never happens.

If there is no fresh orange to begin with, the answer is `0`.

BeeCode passes test inputs as JSON, so the grid arrives as a list of lists rather than
as a graph object. That is not a simplification at all here: a grid *is* the graph, with
each cell joined to the neighbour above, below, left and right. The adjacency is implied
by the indices instead of stored.

## Constraints

- `1 <= len(grid) <= 50` and `1 <= len(grid[0]) <= 50`
- Every row has the same length.
- Each cell is `0`, `1` or `2`.

## Follow-up

Spoilage spreads one step per minute from **every** spoiled orange at once, so the
minute a cell turns is its distance from the nearest spoiled orange. That is a
breadth-first search seeded with all of them — and the answer is the depth of the last
level reached, which is not the same as the number of cells visited.
