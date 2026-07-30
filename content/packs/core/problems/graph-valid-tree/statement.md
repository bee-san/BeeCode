You are given `n` vertices labelled `0` through `n - 1` and a list of undirected `edges`,
each a pair `[a, b]`. Return `True` if the graph is a **tree**.

A graph is a tree when it is connected and contains no cycle.

## Constraints

- `1 <= n <= 2000`
- `0 <= len(edges) <= 5000`
- Each edge is a pair of valid vertex labels.
- There are no repeated edges and no self-loops.

## Follow-up

Two conditions, and an edge count that gives one of them almost free: a tree on `n`
vertices has exactly `n - 1` edges. With that established, connected and acyclic become
the same question — so checking the count plus *either* property is enough. Which is
cheaper to check?
