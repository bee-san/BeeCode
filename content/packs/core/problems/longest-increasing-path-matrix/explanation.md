## The insight

Define `longest(r, c)` as the length of the longest increasing path **starting** at `(r, c)`:

```text
longest(r, c) = 1 + max(longest(neighbour) for neighbour with a greater value, default 0)
```

This depends only on the cell, not on the route taken to reach it, so it is worth computing
once. Memoise it in a grid of the same shape and start a walk from every cell.

## No visited set is needed

Every step strictly increases, so a path can never return to a cell it has left — the values
along it are strictly increasing and a repeat would need a value greater than itself. Cycles
are impossible by construction.

That is why this reads like plain DFS but behaves like DP on a DAG, and why the usual
`visited` bookkeeping is absent. On a grid where steps could go to *equal* neighbours, cycles
would be possible again and the memoised recursion would not terminate — which is what makes
"strictly" load-bearing rather than decorative.

## `0` as the unfilled marker

Every real answer is at least `1`, so `0` is unambiguous and needs no separate `visited`
structure. The same sentinel trick appears in [Coin Change](coin-change).

## Why memoisation matters

Without it, a monotone staircase grid makes the recursion re-derive the same tails
exponentially often. With it, each cell is computed once and each of its four edges is examined
once: O(rows * columns).

## The iterative alternative

Sort every cell by value and process in increasing order; when a cell is handled, all smaller
neighbours are already final. That is the explicit topological order the memoised recursion
discovers on its own — same complexity, plus a sort, and no recursion depth to worry about.
For a 200 by 200 grid the recursion can nest 40000 deep, so the iterative form is the safer
choice at the top end.

## Pitfalls

**Allowing equal steps.** Not strictly increasing, and it admits cycles.

**Starting from only one cell.** The longest path can begin anywhere.

**Forgetting to store the result.** Turns O(n) into exponential; passes small tests and times
out on large ones.

**Counting edges rather than cells.** A single cell is `1`, not `0`.

**Diagonal neighbours.** Only the four edge-sharing cells count.

## Cost

O(rows * columns) time and space.
