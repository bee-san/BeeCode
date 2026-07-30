## The insight

Keep a frontier of reachable nodes ordered by the cheapest cost found so far, and always
finalise the cheapest one next.

That greedy step is safe **because costs are non-negative**. If the cheapest thing on
the frontier costs `c`, no other route to it can be cheaper: any alternative goes
through some other frontier node costing at least `c`, and then adds more non-negative
edges. So the first time you pop a node, you have its true minimum, and you never need
to revisit it.

```python
cheapest = [None] * nodes
frontier = [(0, start)]
while frontier:
    so_far, node = heapq.heappop(frontier)
    if cheapest[node] is not None:      # already finalised: this is a stale entry
        continue
    cheapest[node] = so_far
    if node == target:
        return so_far
    for destination, cost in outgoing[node]:
        if cheapest[destination] is None:
            heapq.heappush(frontier, (so_far + cost, destination))
return -1
```

## Two things worth being deliberate about

**The stale-entry skip.** The same node can be pushed several times, once per route
discovered. Rather than hunting down and decreasing an existing heap entry — which a
binary heap does not support — push the new cost and ignore the node when it comes off
the heap already finalised. Delete that `continue` and you re-expand nodes through worse
routes; the answer stays right but the work can blow up.

**Cost first in the tuple.** `heapq` compares tuples left to right, so `(cost, node)`
orders by cost. `(node, cost)` silently turns this into a search by node label — it
still runs, still terminates, and returns wrong answers.

## Adjacency, built once

Building `outgoing` up front costs O(edges) and turns "which edges leave this node?"
from a scan of all 50,000 edges into a direct lookup. Parallel edges and self-loops
need no special handling: a self-loop only ever offers a route that is not cheaper, and
a duplicate edge is just another push.

## Cost

O((nodes + edges) log nodes) time, O(nodes + edges) space.

## Where it stops working

With a negative edge, the greedy claim collapses — a cheap route can arrive later via a
negative edge and undercut a node you already finalised. That is what Bellman-Ford is
for, at O(nodes × edges).
