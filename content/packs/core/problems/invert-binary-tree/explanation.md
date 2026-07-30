## The insight

With real nodes this is as short as an algorithm gets:

```python
def invert(node):
    if node is None:
        return None
    node.left, node.right = invert(node.right), invert(node.left)
    return node
```

Swap the children, then invert each of them. The order does not matter — swap first
and recurse, or recurse first and swap — because the two subtrees are independent.

## In the level-order representation

Here the swap is index arithmetic. Walking the source tree from the root, a node at
source index `s` lands at destination index `d`, and its children cross over:

```python
place(2 * s + 1, 2 * d + 2)   # source's left becomes destination's right
place(2 * s + 2, 2 * d + 1)   # source's right becomes destination's left
```

The one wrinkle is that trailing `null`s are omitted on input, so a node's child
index can run off the end of the list. Padding the list up to a full level
(`2**depth - 1` entries) makes every index in range and the arithmetic
unconditional; trimming trailing `null`s at the end restores the canonical form.

## Without recursion

Push the root onto a stack. Pop a node, swap its children, push whichever children
exist. The traversal order is irrelevant — every node needs the same local
operation, so breadth-first with a queue works identically. That freedom is unusual
and worth noticing: most tree algorithms care deeply about visit order.

## Pitfalls

**Swapping values instead of children.** Exchanging the two child *values* while
leaving their subtrees attached mirrors one level and nothing below it.

**Returning nothing.** In place the mutation is the answer, but the recursion still
has to return the node so the parent can reattach it.

**Trailing `null`s.** `[1, null, 2]` and `[1, null, 2, null, null]` describe the same
tree; only the trimmed form is expected here.

## Cost

O(n) time, O(h) space for the recursion stack where `h` is the height.
