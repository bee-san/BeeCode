`rooms` is a grid where

- `-1` is a wall,
- `0` is a gate,
- `2147483647` is an empty room, meaning "distance not yet known".

Replace every empty room with its distance to the **nearest** gate, counting one step per
horizontal or vertical move. A room that cannot reach any gate keeps `2147483647`.

Return the filled grid. Walls and gates are unchanged.

BeeCode passes test inputs as JSON, so the grid arrives as a list of lists rather than as
a graph object. That is not a simplification at all: a grid *is* the graph, with each
cell joined to the neighbour above, below, left and right.

## Constraints

- `1 <= len(rooms) <= 250` and `1 <= len(rooms[0]) <= 250`
- Every row has the same length.
- Each cell is `-1`, `0` or `2147483647`.

## Follow-up

One search per room is O(cells^2). One search per gate is better and still repeats work.
Seeding a single breadth-first search with **every gate at once** fills the whole grid in
one linear pass, because the first time a search like that reaches a cell, it has arrived
by a shortest route from the closest gate.
