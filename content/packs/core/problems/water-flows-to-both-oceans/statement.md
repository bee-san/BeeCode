`heights` is a grid of cell heights. One ocean touches the **top and left** edges, the
other touches the **bottom and right** edges.

Water flows from a cell to a horizontally or vertically adjacent one whose height is
**less than or equal** to the current cell's. A cell on an edge drains into that edge's
ocean directly.

Return every cell from which water can reach **both** oceans, as `[row, column]` pairs.
The order of the pairs is not judged.

BeeCode passes test inputs as JSON, so the grid arrives as a list of lists rather than
as a graph object. That is not a simplification at all here: a grid *is* the graph, with
each cell joined to the neighbour above, below, left and right. The adjacency is implied
by the indices instead of stored.

## Constraints

- `1 <= len(heights) <= 50` and `1 <= len(heights[0]) <= 50`
- Every row has the same length.
- `0 <= heights[row][column] <= 100_000`

## Follow-up

Asking "where can water from this cell get to?" for every cell is O((rows * columns)^2).
Turning the question around — "which cells can reach *this* ocean?" — lets one traversal
per ocean answer it for every cell at once, provided you start from all of that ocean's
edge cells simultaneously and walk **uphill**.
