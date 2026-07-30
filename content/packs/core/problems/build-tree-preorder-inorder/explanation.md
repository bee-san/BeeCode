## The insight

Two facts, each from one traversal:

- **Pre-order visits the root first.** So `preorder[0]` is the root.
- **In-order visits the left subtree, then the root, then the right.** So finding the
  root inside `inorder` splits the remaining values into exactly the left subtree's
  and the right subtree's.

Once you know the left subtree has `m` values, you also know where it sits in
`preorder`: right after the root, occupying the next `m` entries. Recurse on both
halves.

```python
def build(low, high):
    if low > high:
        return None
    value = preorder[cursor]
    cursor += 1
    node = Node(value)
    split = position[value]
    node.left = build(low, split - 1)
    node.right = build(split + 1, high)
    return node
```

The single moving `cursor` into `preorder` is what makes this clean. Because the
recursion descends left before right, and pre-order lays the tree out in exactly
that order, the cursor is always sitting on the next node to create — no index
arithmetic to slice `preorder` at all.

## Where the O(n^2) hides

Searching `inorder` for the root with `inorder.index(value)` inside the recursion is
O(n) per node, and O(n^2) overall — worst on a chain, which is also the case where
the recursion is deepest. Precomputing `value -> index` once makes each split O(1)
and the whole rebuild O(n). This is only sound because the values are distinct; with
duplicates the split point is genuinely ambiguous and the tree is not recoverable.

## Which pairs of traversals work

Pre-order plus in-order, or post-order plus in-order: both work, because in-order
supplies the split and the other supplies the root. **Pre-order plus post-order does
not** — neither tells you where the left subtree ends, and a node with one child is
indistinguishable from the same node with that child on the other side.

## Pitfalls

**Slicing instead of passing bounds.** `preorder[1:m+1]` is correct and allocates
O(n) per call, turning O(n) space into O(n^2).

**Resetting the cursor.** It advances monotonically across the entire recursion.
Recomputing it per subtree is where this goes wrong quietly.

**The empty input.** No values means no tree, so `[]`.

## Cost

O(n) time and O(n) space, dominated by the position map.
