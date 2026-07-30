A binary tree is **height-balanced** when, for *every* node, the heights of its two
subtrees differ by at most one.

Return `True` if the tree is height-balanced.

Note the "every node": the condition has to hold throughout the tree, not just at
the root.

## How the tree is given to you

`tree` is a **level-order list**: `tree[0]` is the root, and for the node at index
`i` its children are at `2*i + 1` and `2*i + 2`. A missing node is `null` (Python
`None`). Trailing `null`s are omitted.

So `[1, 2, 3, null, 4]` is:

```text
    1
   / \
  2   3
   \
    4
```

BeeCode passes test inputs as JSON, which cannot carry node objects, so you get a
list rather than a chain of `TreeNode`s. That is an honest simplification, not a
disguise: index arithmetic replaces `node.left` and `node.right`, and the algorithm
you are practising is unchanged.

## Constraints

- `0 <= len(tree) <= 4095`
- Node values are integers; only `null` marks an absent node.
- The list is well formed: a node's parent is never `null`.

- An empty tree is balanced.

## Follow-up

The direct reading — for each node, compute both subtree heights and compare — is
O(n^2) because the heights get recomputed all the way down. A single traversal can
carry both the height and the verdict upward. What value can stand in for "this
subtree is already unbalanced" so that the answer short-circuits?
