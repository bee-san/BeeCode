## The insight

Breadth-first search visits nodes in exactly this order already. The only extra work
is knowing where one level stops and the next begins.

The cleanest way is to process the frontier **a whole level at a time**: take the
current level's nodes, emit their values, and collect their children as the next
level. No counters, no sentinel markers in the queue.

```python
def level_order(tree):
    if not tree or tree[0] is None:
        return []
    levels = []
    frontier = [0]
    while frontier:
        values = []
        following = []
        for index in frontier:
            values.append(tree[index])
            for child in (2 * index + 1, 2 * index + 2):
                if child < len(tree) and tree[child] is not None:
                    following.append(child)
        levels.append(values)
        frontier = following
    return levels
```

**Filter absent children when you enqueue, not when you dequeue.** If `None`s enter
the frontier, a level of all-absent nodes produces an empty inner list in the output,
and the shape is wrong.

**Left before right, always.** Appending the children in the other order silently
mirrors every level.

## Why not `pop(0)`?

A textbook queue version calls `queue.pop(0)`, which is O(n) on a Python list because
every remaining element shifts. Use `collections.deque` and `popleft()` if you want a
per-node queue. The level-at-a-time version above sidesteps the issue: it never pops
at all, it just replaces the frontier.

## Cost

O(n) time and O(w) extra space, where `w` is the widest level.
