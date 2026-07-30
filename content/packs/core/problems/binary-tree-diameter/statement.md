The **diameter** of a binary tree is the number of edges on the longest path between
any two nodes. The path need not pass through the root.

Return the diameter. A tree with fewer than two nodes has diameter `0`.

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

## Follow-up

There are two different quantities here and confusing them is the whole difficulty.
A recursive call can return the height of its subtree, or the best diameter within
it, but not both in one integer. What if the answer is recorded on the side while
the return value stays the height?
