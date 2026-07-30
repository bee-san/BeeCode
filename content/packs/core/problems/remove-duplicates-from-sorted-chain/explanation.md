## The insight

Sorted means every run of equal values is contiguous, so the only comparison needed is against the
last value kept:

```python
kept = []
for value in values:
    if not kept or kept[-1] != value:
        kept.append(value)
return kept
```

## The pointer version, and its asymmetry

Walking the real chain:

```python
node = head
while node and node.next:
    if node.next.value == node.value:
        node.next = node.next.next      # removed: do NOT advance
    else:
        node = node.next                # kept: advance
```

The asymmetry is the whole thing. After unlinking a duplicate, `node.next` is a node you have not
examined yet — it may be another duplicate of the same value. Advancing as well would skip it, so
`[1, 1, 1]` would come back as `[1, 1]`.

Advancing unconditionally is the classic bug here, and it needs a run of three equal values to
show up. Two are not enough, which is why the tests include a triple.

## Why no set is needed

A hash set would work and would also handle unsorted input. It costs O(n) space to answer a
question the ordering already answers for free. Whenever an input is described as sorted, that is
usually the intended lever.

## The head never needs special handling

Unlike removal problems where the head itself may go, the first node is always kept — it is the
first occurrence of its value. So no dummy node is required, which is a small mercy worth noticing
in a family of problems where one usually is.

## Pitfalls

**Advancing after a removal.** Misses a third consecutive duplicate.

**Comparing against the previous node examined rather than the last kept.** Equivalent here, and
the habit breaks on unsorted input.

**An empty chain.** Returns empty.

**Removing all copies of a repeated value.** The task keeps one.

## Cost

O(n) time; O(1) extra space in the pointer version, O(n) for the output list here.
