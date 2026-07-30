## The insight

Carry the remaining target down instead of accumulating a total upward. At each node subtract its
value; at a leaf the question is whether nothing is left.

```python
def walk(node, remaining):
    remaining -= node.value
    if not node.left and not node.right:
        return remaining == 0
    return ((node.left and walk(node.left, remaining))
            or (node.right and walk(node.right, remaining)))
```

Subtracting downward means each call needs only one number, and the leaf test is a comparison
against zero rather than against a target threaded through every frame.

## Why the leaf test cannot be moved

Checking `remaining == 0` at *any* node, rather than only at a leaf, accepts paths that stop
partway. `[1, 2]` with target `1` must be `False`: the only root-to-leaf path is `1 + 2 = 3`, and
the root alone is not a path even though it hits the target exactly. The statement says
root-to-leaf, and the test has to be where the leaf is.

## Why pruning on the running total is wrong

With non-negative values you could stop as soon as the total exceeds the target. Values here may be
negative, so a path that has overshot can still come back — `[1, -2, 3]` with target `-1` overshoots
at the root and succeeds anyway. The moment negative values are allowed, monotonicity is gone and
with it every early exit that relies on it.

That is worth stating explicitly because the pruned version passes any test suite built only from
non-negative examples.

## An empty tree

Always `False`, including for `target == 0`. There is no root-to-leaf path at all, so there is
nothing that could sum to anything.

## Pitfalls

**Testing the remainder at every node.** Accepts partial paths.

**Treating a node with one child as a leaf.** The same mistake as in
[The Shallowest Leaf](binary-tree-min-depth): a leaf has no children.

**Returning `True` for an empty tree with target 0.** No path exists.

**Pruning on the running total.** Only sound if every value is non-negative.

## Cost

O(n) time, O(height) space for the recursion.
