## The insight

Symmetry is not a property of one node, so recursing on one node at a time cannot
express it. Recurse on **pairs**: ask whether two subtrees mirror each other.

Two subtrees mirror when their roots hold the same value, and — this is the crossing
that makes it a mirror rather than an equality check — the left one's *left* child
mirrors the right one's *right* child, and vice versa.

```python
def mirror(left, right):
    left_value, right_value = at(left), at(right)
    if left_value is None and right_value is None:
        return True
    if left_value is None or right_value is None:
        return False
    if left_value != right_value:
        return False
    return mirror(2 * left + 1, 2 * right + 2) and mirror(2 * left + 2, 2 * right + 1)
```

Then the answer is `mirror(1, 2)`: the root's two children.

**The two `None` cases are different.** Both absent means these two positions agree,
so return `True`. Exactly one absent is a shape mismatch, so return `False`. Merging
them into a single check is how a lopsided tree gets accepted.

**Cross the recursive calls.** `mirror(left.left, right.left)` tests whether the
subtrees are *equal*, not mirrored — and equal is the wrong question.

## Cost

O(n) time, O(h) space.
