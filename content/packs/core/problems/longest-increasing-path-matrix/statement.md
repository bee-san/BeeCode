Return the length of the longest strictly increasing path in `grid`.

A path steps between cells sharing an edge — up, down, left or right — and each step must move
to a **strictly greater** value. Diagonal steps and wrapping are not allowed. A single cell is
a path of length `1`.

BeeCode passes test inputs as JSON, so the grid arrives as a list of lists rather than as a
graph object. That is not a simplification at all here: a grid *is* the graph, with each cell
joined to the neighbour above, below, left and right. The adjacency is implied by the indices
instead of stored.

## Constraints

- `1 <= rows, columns <= 200`
- `0 <= grid[r][c] <= 2^31 - 1`
- The grid is rectangular and non-empty.

## Follow-up

The longest path starting at a cell depends only on that cell, never on how you arrived — so
it can be computed once and stored. What does the strictly-increasing rule guarantee about
whether this recursion can loop forever?
