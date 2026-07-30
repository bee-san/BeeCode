## The insight

Every path has a highest node — the one where it turns around. So the longest path
through a given node is its left subtree's height plus its right subtree's height,
counted in edges. Take the maximum of that over all nodes and you have the diameter.

That gives one traversal that computes two things:

- the **return value** is the height, which is what the parent needs, and
- the **best-so-far**, updated as a side effect, which is the answer.

```python
def diameter(tree):
    best = 0
    def height(node):
        nonlocal best
        if node is None:
            return 0
        left, right = height(node.left), height(node.right)
        best = max(best, left + right)
        return 1 + max(left, right)
    height(root)
    return best
```

Trying to return the diameter instead of the height is the classic wrong turn: the
parent cannot combine two child diameters into its own, because it needs to know how
*deep* each child reaches, not how long a path hides inside it.

## Counting edges, not nodes

`height` here returns a node count (an empty subtree is 0, a leaf is 1), but
`left + right` is an *edge* count — and it works out exactly because the two
child heights each already include the edge from this node down into that child.
Mixing the two conventions gives an answer one or two too large, so pick one and
check it against a two-node tree, where the answer must be 1.

## Pitfalls

**Assuming the path goes through the root.** It often does not. A tree whose root
has a single deep child has its longest path entirely inside that child.

**Recomputing heights.** Calling a separate `height()` for every node is O(n^2) on a
chain. One traversal is enough — the heights come back up as it unwinds.

**Off by one on the empty tree.** No nodes means no edges, so `0`, not `-1`.

## Cost

O(n) time, O(h) space.
