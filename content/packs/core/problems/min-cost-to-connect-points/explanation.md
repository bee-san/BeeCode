## The insight

The cheapest connecting set is a minimum spanning tree. Prim's algorithm grows it from a
single point: repeatedly absorb the unreached point that is cheapest to attach to what you
already have.

```python
cheapest[0] = 0                       # start anywhere
for _ in range(count):
    best = the unreached point with the smallest cheapest[...]
    reached[best] = True
    total += cheapest[best]
    for each unreached point:
        cheapest[point] = min(cheapest[point], distance(best, point))
```

`cheapest[i]` means "the least cost of attaching point `i` to the tree as it stands". Each
absorption can only lower those values, so one pass over the unreached points after each
absorption keeps them exact.

## Why greedy is safe here

The cut property: for any way of splitting the points into "in the tree" and "not in the
tree", the cheapest edge crossing that split belongs to some minimum spanning tree. Prim's
choice *is* that edge, every time. This is worth being able to state — it is the difference
between knowing the algorithm and knowing why it terminates with the right answer.

## Prim's versus Kruskal's, and the density argument

The graph is complete, and that decides it.

- **Prim's, dense form** — as above, no heap, no edge list. O(n^2) time, O(n) space. At
  `n = 1000` that is a million operations.
- **Kruskal's** — build all `n * (n - 1) / 2` edges, sort them, union greedily.
  O(n^2 log n) time and O(n^2) space: half a million edges here, and the sort dominates.

The dense form of Prim's wins on a complete graph precisely because it never materialises
the edges. On a **sparse** graph the ranking flips, and a heap-based Prim's or Kruskal's
is much better. Recognising which regime you are in is the actual skill.

## Manhattan, not Euclidean

`abs(dx) + abs(dy)`. No square roots, so the arithmetic stays exact in integers and there
are no floating-point comparisons to get wrong.

## Pitfalls

**Connecting every pair.** That is a complete graph, not a spanning tree — vastly more
than the minimum.

**A single point.** Cost `0`, no edges, and the loop must not add anything.

**Absorbing a point twice.** The `reached` check must come before the comparison, or the
total counts an edge more than once.

**Adding `cheapest[best]` before marking `best` reached.** Works, but only if the update
loop then skips it. Mark first.

## Cost

O(n^2) time, O(n) space.
