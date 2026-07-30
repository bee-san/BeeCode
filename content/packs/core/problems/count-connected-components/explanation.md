## The insight

Start by assuming the worst: `n` vertices, `n` components, nothing joined. Then read
the edges. An edge either joins two vertices that were already in the same
component — in which case nothing changes — or it merges two components into one, and
the count drops by exactly one.

That is the whole algorithm, and it needs one operation: *are these two vertices
already together, and if not, put them together*. A disjoint-set forest (union-find)
provides it.

```python
components = n
for a, b in edges:
    root_a, root_b = find(a), find(b)
    if root_a == root_b:
        continue          # already together; a duplicate edge or a cycle
    union(root_a, root_b)
    components -= 1
```

## Why the two optimisations are not optional

**Path compression** — `parent[x] = parent[parent[x]]` while walking up — flattens the
tree as a side effect of looking things up.

**Union by size** — always hang the smaller tree under the larger — stops the forest
degenerating into a linked list.

Without them, `find` is O(n) in the worst case and a chain of 100,000 edges is
quadratic. With them, the whole run is effectively linear.

**Skip the merge when the roots already match.** This is what makes duplicate edges
and self-loops harmless: both endpoints resolve to the same root, so the count is not
decremented. Forgetting the check undercounts components on any input with a repeated
edge.

## Cost

O(n + m·α(n)) time, which is linear for any input that fits in memory, and O(n) space.
