## The insight

Depth-first search from room `0`, then compare the number of rooms visited against the total:

```python
seen, stack = {0}, [0]
while stack:
    for key in rooms[stack.pop()]:
        if key not in seen:
            seen.add(key)
            stack.append(key)
return len(seen) == len(rooms)
```

The graph is already an adjacency list — `rooms[i]` *is* the neighbour list of vertex `i` — so there
is nothing to build. Recognising that is most of the problem.

## Why the visited set is not optional

Keys repeat, and a room may hold a key to itself or to a room that leads back. Without `seen`, room
`0` holding a key to room `1` and room `1` holding a key to room `0` is an infinite loop. This is the
difference between traversing a graph and traversing a tree: a tree has no way back, and a graph
does.

## Marking on push, not on pop

`seen.add(key)` happens when the key is pushed. Marking on pop instead lets the same room be pushed
several times before it is first popped — correct, and it inflates the stack. Marking on push keeps
each room in the stack at most once.

## Room 0 counts

Seeding `seen` with `0` matters: you start there without needing a key, so it is reachable by
definition. Forgetting it makes `[[]]` — one room, no keys — answer `False` when the right answer is
`True`.

## Breadth-first works identically

Swap the stack for a queue and nothing else changes. The question is only *whether* everything is
reachable, not how far away it is, so the traversal order is irrelevant. Worth noticing, because it
means neither choice needs defending.

## Pitfalls

**No visited set.** Loops forever on cyclic key graphs.

**Not counting room 0 as visited.** Off by one, and a single-room input catches it.

**Comparing against the number of keys rather than rooms.** Keys repeat.

**Assuming reachability is symmetric.** A key in room `1` for room `2` does not open room `1` from
room `2`.

## Cost

O(rooms + keys) time, O(rooms) space.
