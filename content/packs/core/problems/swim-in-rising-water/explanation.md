## The insight

A route's cost is the highest cell on it — the water has to rise past the tallest
obstacle, and once it has, every lower cell on the route is already submerged. So run
Dijkstra's algorithm with `max` where you would normally write `+`:

```python
reachable_at = max(height, grid[next_row][next_column])
heappush(pending, (reachable_at, next_row, next_column))
```

Everything else is unchanged: pop the smallest, skip anything already settled, stop when
the target pops.

## Why min-of-max still works

Dijkstra's correctness needs the cost of extending a path to never decrease. `max` has
that property just as `+` does — adding a step can only raise or hold the maximum — so the
first time a cell is popped, its value is the least possible maximum over all routes to
it. This "minimax path" variant is worth recognising by name; it comes up as widest-path
and bottleneck-path too.

## Stop on pop, not on push

Returning when the target is *pushed* returns whatever the first route to touch it cost,
which need not be the cheapest. Return when it is popped and settled.

## The binary search alternative

Guess `t`, then flood from `[0, 0]` using only cells with elevation `<= t` and ask whether
the corner is reachable. Reachability is monotonic in `t` — more water never disconnects
anything — so binary search over `0 .. n*n - 1` gives O(n^2 log(n^2)). Comparable to
Dijkstra's O(n^2 log n) here. Recognising that the answer itself is searchable is the
transferable half.

## Pitfalls

**Summing the elevations.** That answers a different question entirely.

**Forgetting the starting cell's own elevation.** The initial cost is `grid[0][0]`, not
`0`. Only visible when the first cell is not the lowest.

**Ignoring the current cell's height in the step.** Both cells must be submerged, which is
what `max` encodes.

**A 1x1 grid.** The answer is `grid[0][0]`, reached by the target check firing before any
neighbour is examined.

## Cost

O(n^2 log n) time and O(n^2) space.
