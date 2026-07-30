## The insight

One traversal, and a sentinel return value that means "already broken".

`measure` returns the height of a subtree, except that it returns `-1` if that
subtree — or anything inside it — is unbalanced. Since a real height is never
negative, one integer carries both facts:

```python
def measure(node):
    if node is None:
        return 0
    left = measure(node.left)
    if left == -1:
        return -1
    right = measure(node.right)
    if right == -1:
        return -1
    if abs(left - right) > 1:
        return -1
    return 1 + max(left, right)
```

The two early returns are the short-circuit: once any subtree reports `-1`, nothing
above it is measured. Each node is visited once, so O(n) instead of the O(n^2) of
"call `height()` at every node".

Returning a `(height, balanced)` tuple is the same algorithm with the sentinel
spelled out. Prefer it if the sentinel feels like a trick; it costs nothing and
reads better.

## Why "every node" matters

The condition is easy to under-check. `[1, 2, 3, 4, null, 6, 7, 8]` has heights 3
and 2 below the root, a difference of one, so the root is fine. But node `2` has a
two-deep left subtree and no right subtree at all, and that is what makes the tree
unbalanced. Any solution that only looks at the root passes this and is wrong.

## Pitfalls

**Only checking the root.** See above; the suite contains exactly that tree.

**Recomputing heights.** Correct but quadratic, and on a 4000-node chain it is
visible.

**Confusing balance with completeness.** A balanced tree need not be full or
complete — `[3, 9, 20, null, null, 15, 7]` is neither and is balanced.

**Depth versus height off by one.** An empty subtree must measure `0` and a leaf
`1`, or the difference at every leaf's parent comes out wrong.

## Cost

O(n) time, O(h) space.
