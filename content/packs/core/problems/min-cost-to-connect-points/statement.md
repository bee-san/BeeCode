`points` is a list of `[x, y]` coordinates. The cost of connecting two points is their
**Manhattan distance**, `abs(x1 - x2) + abs(y1 - y2)`.

Return the least total cost of adding connections so that every point is reachable from
every other. Points are reachable through intermediate points, not only directly.

## Constraints

- `1 <= len(points) <= 1000`
- `-10^6 <= x, y <= 10^6`
- All points are distinct.

## Follow-up

Every pair of points can be connected, so the graph is complete: `n` points give
`n * (n - 1) / 2` candidate edges. You need the cheapest set that connects everything —
a **minimum spanning tree**. Prim's algorithm grows one tree outwards and never needs the
edge list materialised; Kruskal's sorts all the edges and unions greedily. At `n = 1000`,
that is half a million edges, which is the number to weigh.
