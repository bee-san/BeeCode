`n` nodes are labelled `1` through `n`. `times` is a list of `[from, to, weight]` triples
describing **directed** links, where `weight` is how long a signal takes to traverse the
link.

A signal is sent from node `start`. Return how long until **every** node has received it,
or `-1` if some node never does.

## Constraints

- `1 <= n <= 100`
- `0 <= len(times) <= 6000`
- `1 <= weight <= 100`
- Nodes are labelled `1` through `n`.
- There may be several links between the same pair.

## Follow-up

The signal reaches each node by its shortest path, so this is Dijkstra's algorithm — and
the answer is the **largest** of those shortest distances, since everyone has it only once
the furthest node does. Note how the two extremes combine: minimum per node, then maximum
over nodes.
