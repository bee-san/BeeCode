Given a `grid` of `0`s (open) and `1`s (blocked), return the number of cells on the
shortest path from the top-left cell to the bottom-right cell, counting both ends. You
may move up, down, left, or right — not diagonally. Return `-1` if no path exists.

If either the start or the end cell is blocked, there is no path.

## Constraints

- `1 <= len(grid) <= 300` and `1 <= len(grid[0]) <= 300`
- Every row has the same length.
- Every cell is `0` or `1`.

## Follow-up

Depth-first search will find *a* path but not reliably the shortest one. Breadth-first
search explores in order of distance, so the first time it reaches the end, that is the
shortest. Given that, when should a cell be marked as visited — when you take it out of
the queue, or when you put it in?
