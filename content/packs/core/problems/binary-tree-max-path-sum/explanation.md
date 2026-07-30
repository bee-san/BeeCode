## The insight

Every path turns around at exactly one node — its highest. So consider each node as
that turning point: the best path through it is its own value plus the best downward
path into the left child plus the best downward path into the right child.

One traversal computes two things:

- the **return value** is the best path that goes *downward only* from this node,
  which is what the parent can extend, and
- the **best-so-far**, which considers this node as a turning point and is the answer.

```python
def max_path_sum(root):
    best = float("-inf")
    def down(node):
        nonlocal best
        if node is None:
            return 0
        left = max(down(node.left), 0)
        right = max(down(node.right), 0)
        best = max(best, node.value + left + right)
        return node.value + max(left, right)
    down(root)
    return best
```

## The `max(..., 0)`

This is what makes the Problem harder than the diameter. A branch whose best
downward sum is negative is worth **skipping**: clamping it to `0` says "do not
extend into this child at all". Without the clamp, `[-10, 9, 20, null, null, 15, 7]`
drags the `-10` root into paths that are better off without it.

Clamping the *return* value would be wrong in a different way. The return must stay
truthful about this node's own value even when negative, because the parent needs to
know that extending here costs something. Clamp the children's contributions on the
way in, not this node's answer on the way out.

## Why the answer cannot start at zero

A tree of all negative values has a negative answer — the single largest node. Seeding
`best` at `0` reports `0`, which is not the sum of any path. Seed with negative
infinity, or with the first node visited.

## Pitfalls

**Returning `value + left + right` upward.** That is a turning path, not a downward
one, and a parent that extends it would use the node twice. Return the one-sided sum.

**Assuming the path passes through the root.** The suite has a case whose best path
lives entirely in one subtree while the root has two children — the shape that looks
most like it should include the root.

**Confusing "best downward" with "best".** They differ at every node whose two
children are both positive, and the return value must be the former.

## Cost

O(n) time, O(h) space.
