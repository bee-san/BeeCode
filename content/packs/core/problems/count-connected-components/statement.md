You are given `n` vertices labelled `0` through `n - 1`, and a list of undirected
`edges` where each edge is a pair `[a, b]`. Return the number of **connected
components**.

A vertex with no edges is a component on its own.

## Constraints

- `0 <= n <= 100_000`
- `0 <= len(edges) <= 200_000`
- Each edge is a pair of valid vertex labels.
- Edges may repeat, and an edge may join a vertex to itself.

## Follow-up

Two standard solutions exist: flood each unvisited vertex, or union the endpoints of
every edge and count the distinct roots. Both are linear for practical purposes. The
union-find version is the one that extends to edges arriving one at a time, where you
must answer "how many components now?" after each.
