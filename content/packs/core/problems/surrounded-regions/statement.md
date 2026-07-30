`board` is a grid of `"X"` and `"O"`. A region is a group of `"O"`s connected
horizontally or vertically. A region is **surrounded** if none of its cells lies on the
border of the board.

Flip every surrounded region to `"X"` and return the resulting board. Regions touching
the border are left alone.

BeeCode passes test inputs as JSON, so the grid arrives as a list of lists rather than
as a graph object. That is not a simplification at all here: a grid *is* the graph, with
each cell joined to the neighbour above, below, left and right. The adjacency is implied
by the indices instead of stored.

## Constraints

- `1 <= len(board) <= 50` and `1 <= len(board[0]) <= 50`
- Every row has the same length.
- Each cell is `"X"` or `"O"`.

## Follow-up

Finding each region and then checking whether it touches the border works but is fiddly.
The inversion used in [Water That Reaches Both Oceans](water-flows-to-both-oceans)
applies again: mark what is **safe** by flooding inwards from every border `"O"` at once,
then flip everything still unmarked.
