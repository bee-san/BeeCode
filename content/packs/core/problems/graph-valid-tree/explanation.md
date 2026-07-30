## The insight

Check the edge count first, then connectivity:

```python
if len(edges) != n - 1:
    return False
# ... one traversal from vertex 0 ...
return len(seen) == n
```

A tree on `n` vertices has exactly `n - 1` edges. Given that count, connected and acyclic
become equivalent — an acyclic graph with `n - 1` edges must be connected, and a connected
graph with `n - 1` edges must be acyclic. So one traversal decides it, and no cycle
detection is needed at all.

That is the whole trick, and it is worth stating explicitly in an interview: *because* the
edge count is right, I only have to check one of the two properties.

## Why connectivity is the easier half

Cycle detection in an undirected graph means tracking the edge you arrived by, so that
the immediate step back to the parent is not mistaken for a cycle — and with multiple
edges between the same pair it needs more care still. Counting reached vertices needs
none of that. The traversal is the plainest possible flood.

## The union-find version

Union the endpoints of each edge; if both are already in the same component, that edge
closes a cycle, so return `False`. Finish with "is there exactly one component?". It
detects the cycle directly and extends to edges arriving one at a time, which the
traversal does not. See [Redundant Connection](redundant-connection), where finding that
one closing edge *is* the question.

## Why `n = 1` with no edges is a tree

A single vertex is connected to everything there is, and `0 == 1 - 1` edges. The code
returns `True` without a special case. Worth checking against the count formula rather
than intuition.

## Pitfalls

**Checking only the edge count.** Two triangles sharing nothing have `n - 1` edges? Not
quite — but `n = 4` with edges `[[0,1],[1,0]]` is excluded by the no-repeats promise,
whereas `n = 6` with a triangle and a separate path can hit `n - 1` while being
disconnected *and* cyclic. The count alone is never sufficient.

**Starting the traversal from every vertex.** That finds components, not tree-ness, and
loses the point of the count argument.

**Traversing without a `seen` set.** An undirected edge is two directed edges, so the
traversal bounces between endpoints forever.

## Cost

O(n + e) time and space.
