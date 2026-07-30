You are given `nodes`, the number of nodes labelled `0` to `nodes - 1`, and a list of
`edges`, each `[from, to, cost]` describing a **directed** edge with a non-negative
cost. Return the cheapest total cost of travelling from `start` to `target`, or `-1` if
`target` is unreachable.

## Constraints

- `1 <= nodes <= 10_000`
- `0 <= len(edges) <= 50_000`
- `0 <= cost <= 10^6`
- There may be several edges between the same pair of nodes, and self-loops.
- `0 <= start, target < nodes`

## Follow-up

Breadth-first search finds the route with the fewest *edges*, which is not the cheapest
once edges have different costs. Dijkstra's algorithm fixes that by always expanding
the cheapest-known unfinished node next. Why does non-negativity make that greedy
choice safe — and what breaks if a cost may be negative?
