You are given a `grid` of `1`s (land) and `0`s (water). An **island** is a group of
`1`s connected horizontally or vertically. Return how many islands the grid contains.

Cells touching only at a corner are *not* connected.

Assume the whole grid is surrounded by water.

## Constraints

- `0 <= len(grid) <= 300` and `0 <= len(grid[0]) <= 300`
- Every row has the same length.
- Each cell is `0` or `1`.
- You may modify `grid` if you want to.

## Follow-up

Every cell belongs to exactly one island, so the total work should be proportional to
the number of cells — not to the number of cells times the number of islands. What has
to be true of your visited-marking for that to hold?
