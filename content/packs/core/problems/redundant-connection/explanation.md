## The insight

Walk the edges in order, maintaining the components with a disjoint-set forest. Each edge
either joins two separate components — keep it — or joins two vertices already in the same
component, which means it closes a cycle:

```python
for first, second in edges:
    left, right = root_of(first), root_of(second)
    if left == right:
        return [first, second]      # both ends already connected
    parent[left] = right
```

Every edge before it was genuinely joining, so the first edge that fails is the last one
that could be removed. Scanning forwards therefore satisfies the tie-break automatically —
there is no need to collect candidates and pick the latest.

## Why union-find rather than a traversal

You could, for each edge, remove it and test whether the rest is still connected: O(n^2).
Or find the cycle by traversal and take its latest edge, which needs the cycle's vertices
*and* their edge order. Union-find answers "are these two already connected?" in near
O(1) and is the natural tool whenever connectivity grows one edge at a time.

## Path compression

`parent[label] = parent[parent[label]]` inside the walk halves the path each time it is
traversed. Without it, a long chain makes each `root_of` O(n) and the whole thing O(n^2) —
correct, but it throws away the reason for using the structure. Union by size or rank
gives the same asymptotic benefit; either alone is enough here.

## Labels start at 1

A `parent` list sized `n` and indexed from `0` is off by one, so either size it `n + 1`
and ignore index `0`, or use a dict as above. This is a real source of silent wrong
answers.

## Pitfalls

**Returning `[left, right]`, the roots.** The answer is the edge as given, not the roots
of its components.

**Uniting before comparing.** Every edge then looks connected.

**Assigning `parent[first] = second` instead of `parent[left] = right`.** Joins the
vertices instead of the components, so later queries walk into the wrong tree.

**Sorting the edges.** Their order is the tie-break; sorting destroys the answer.

## Cost

O(n) for practical purposes — near-constant per operation with path compression. O(n)
space. See also [Is This Graph a Tree](graph-valid-tree), where the same structure
answers a yes-or-no question.
