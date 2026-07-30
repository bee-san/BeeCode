## The insight

Two structures, each covering the other's blind spot.

- A **hash map** from key to node: finds any key in O(1), but knows nothing about
  recency.
- A **doubly linked list** of nodes in recency order, most recent at one end: knows
  the eviction victim instantly, but cannot find a key without walking.

Store the *node* as the map's value, and both operations become O(1). Given a key,
the map hands you the node; because the list is doubly linked and you hold the node,
unlinking it and re-attaching it at the recent end is a constant number of pointer
writes. No search.

Two sentinel nodes — a permanent head and tail — remove every "is this the first
node" and "is this the last node" special case. It is the same trick as the dummy
head in [Remove the Nth Element From the End](remove-nth-from-end).

```python
def get(key):
    if key not in nodes:
        return -1
    node = nodes[key]
    unlink(node)
    push_front(node)
    return node.value

def put(key, value):
    if key in nodes:
        unlink(nodes[key])
    node = Node(key, value)
    nodes[key] = node
    push_front(node)
    if len(nodes) > capacity:
        victim = tail.previous
        unlink(victim)
        del nodes[victim.key]
```

## Why the victim node stores its key

Eviction starts from the *list* and must delete from the *map*. Without the key on
the node you have no way back, and the map grows without bound while the list stays
at capacity — a leak that only shows up as memory growth, never as a wrong answer.

## In Python

`collections.OrderedDict` is exactly this structure — a dict with a doubly linked
list threaded through it — and `move_to_end` and `popitem(last=False)` are the two
operations, both O(1). Reach for it in production. In an interview, say that it
exists and then build the pair by hand, because the pair is the question.

## Pitfalls

**Overwriting without refreshing.** `put` on a key already present counts as a use.
Set the value and leave it where it was in the order, and the wrong key gets evicted
later.

**Evicting before inserting.** Check the size *after* the insert, or a `put` that
overwrites an existing key will evict something for no reason.

**A singly linked list.** Unlinking needs the predecessor, so a singly linked list
makes every touch O(n) and quietly loses the whole point.

## Cost

O(1) expected time for both operations. O(capacity) space.
