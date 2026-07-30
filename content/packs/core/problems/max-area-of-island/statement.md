`grid` holds `1`s (land) and `0`s (water). An **island** is a group of `1`s connected
horizontally or vertically. Return the number of cells in the largest island, or `0` if
there is no land.

Cells touching only at a corner are not connected.

BeeCode passes test inputs as JSON, so the grid arrives as a list of lists rather than
as a graph object. That is not a simplification at all here: a grid *is* the graph, with
each cell joined to the neighbour above, below, left and right. The adjacency is implied
by the indices instead of stored.

## Constraints

- `0 <= len(grid) <= 50` and `0 <= len(grid[0]) <= 50`
- Every row has the same length.
- Each cell is `0` or `1`.
- You may modify `grid`.

## Follow-up

This is [Number of Islands](number-of-islands) with the flood returning a size instead
of just happening. Getting the count right means each flood must return the total for
its whole component — one for the current cell plus whatever the four recursive calls
report — and each cell must be counted exactly once, which is what the visited-marking
guarantees.
