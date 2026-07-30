## The insight

A graph is not a tree: following neighbours revisits nodes, so a plain recursive copy
loops forever. One map fixes it, and *where* you write to the map is the whole trick.

```python
copies = {}

def copy_of(label):
    if label in copies:
        return copies[label]
    made = new_node(label)
    copies[label] = made            # register BEFORE recursing
    for neighbour in adjacency[label]:
        made["neighbours"].append(copy_of(neighbour))
    return made
```

Register after the loop instead and the cycle is never cut: copying node 0 recurses into
1, which recurses back into 0, which is still not in the map, and the recursion never
bottoms out. Registering first makes the second visit to 0 return the half-built copy —
which is exactly right, because by the time anyone reads its neighbours the loop will
have filled them in.

## Deep versus shallow

The shallow bug is subtle and this Problem's readout cannot catch it: build copy nodes
but fill their neighbour lists with the *original* nodes. Every label reads back
correctly, and the two graphs are permanently entangled. The test for it is not a value
comparison, it is a discipline — a copy's neighbour list must only ever receive things
that came out of `copy_of`.

## Iterative form

A stack or queue plus the same map works identically:

```python
copies = {start: new_node(start)}
pending = [start]
while pending:
    label = pending.pop()
    for neighbour in adjacency[label]:
        if neighbour not in copies:
            copies[neighbour] = new_node(neighbour)
            pending.append(neighbour)
        copies[label]["neighbours"].append(copies[neighbour])
```

Same insight, no recursion limit. The `if neighbour not in copies` guard plays the role
that the early return played above.

## Why the loop over every label

The statement promises a connected graph, so one traversal reaches everything. Looping
over all labels anyway costs nothing and makes the function correct on a disconnected
input too — which is the kind of unpromised robustness worth having for free.

## Pitfalls

**Registering the copy after the recursion.** Infinite recursion on any cycle, which is
every graph with more than one edge.

**Returning `adjacency`.** Passes here. Fails the actual question.

**Forgetting the empty graph.** `[]` in, `[]` out, with no traversal started.

## Cost

O(n + e) time and O(n) space for the map, both optimal — every node and edge must be
looked at once.
