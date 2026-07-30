## The insight

The difficulty is that a random pointer may aim at a node you have not created yet.
You cannot resolve it on first sight, so you need either two passes or a way of
finding "the copy of this node" on demand.

**Two passes with a map.** First pass: create one copy per original and record the
correspondence in a dictionary keyed by the original node. Second pass: now that
every copy exists, wire up `next` and `random` by looking up each target in the map.

```python
copies = {}
cursor = head
while cursor:
    copies[cursor] = Node(cursor.value)
    cursor = cursor.next

cursor = head
while cursor:
    copies[cursor].next = copies.get(cursor.next)
    copies[cursor].random = copies.get(cursor.random)
    cursor = cursor.next
return copies.get(head)
```

`copies.get(None)` returning `None` is what makes the null cases fall out for free
rather than needing two conditionals per pointer.

## The O(1)-space weave

Three passes, no map, using the original chain itself as the correspondence:

1. **Interleave.** After each original node, splice in its copy:
   `A -> A' -> B -> B' -> C -> C'`. Now the copy of any node is literally
   `node.next`.
2. **Set the randoms.** For each original `node`, the copy of `node.random` is
   `node.random.next`. So `node.next.random = node.random.next` — with a guard for
   a null random. This is the step the weave exists for.
3. **Unweave.** Walk once more, restoring every original's `next` and linking the
   copies to each other. Leaving the lists interleaved corrupts the input, which is
   as much a bug as a wrong copy.

## Pitfalls

**Copying values but sharing nodes.** If `clone.random` ends up pointing at an
original node, the copy is not independent — mutate the original afterwards and the
"copy" changes with it. This is the actual bug the Problem is about, and it is the
one a value readout cannot see.

**Keying the map by value.** Values repeat. Two nodes holding `7` are different
nodes, and a value-keyed map silently merges them.

**A recursive copy that revisits.** Following `random` recursively without a visited
map either loops forever on a cycle of randoms or copies shared nodes twice.

## Cost

O(n) time. O(n) space for the map version, O(1) for the weave.
