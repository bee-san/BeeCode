You start with a tree on `n` vertices labelled `1` through `n`, then one extra edge is
added between two existing vertices. The result has exactly one cycle.

Given `edges` in the order they were added, return the edge that can be removed to leave
a tree. If more than one edge would do, return the one that appears **last** in `edges`.

Return it as a `[a, b]` pair in the same order it was given.

## Constraints

- `3 <= n <= 1000` and `len(edges) == n`
- Vertices are labelled `1` through `n`.
- There are no repeated edges and no self-loops.
- The input always contains exactly one cycle.

## Follow-up

Process the edges in order and keep track of which vertices are already connected to each
other. The first edge whose endpoints are **already** connected is the one that closes the
cycle — and because you are scanning forwards, "the first one that closes a cycle" and
"the last edge that could be removed" are the same edge. Convince yourself of that before
you write it.
