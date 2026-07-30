`grid` is an `n` by `n` grid where `grid[row][column]` is the elevation of that cell.

At time `t` the water level is `t`, and you may move to a horizontally or vertically
adjacent cell if **both** cells have elevation at most `t`. Moving takes no time.

Starting at `[0, 0]`, return the earliest time you can reach `[n - 1, n - 1]`.

BeeCode passes test inputs as JSON, so the grid arrives as a list of lists. The
adjacency is implied by the indices rather than stored.

## Constraints

- `1 <= n <= 50`
- `0 <= grid[row][column] < n * n`
- Every elevation is distinct.

## Follow-up

The time a route requires is the **highest** elevation along it, not the sum, so this is a
shortest path where a path's cost is its maximum edge rather than its total. Dijkstra's
algorithm adapts directly — change how a route's cost is combined with the next step.
There is also a binary search on the answer: for a candidate `t`, is the target reachable
using only cells at or below `t`?
