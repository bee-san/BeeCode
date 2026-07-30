Standing to the right of a binary tree and looking left, you see exactly one node per
level: the rightmost one.

Return those values, top level first.

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

"Rightmost on its level" is not the same as "reachable by following right children".
A level's rightmost node can be the left child of something. Level-order traversal
gets this right by construction — but so does a depth-first walk that visits the
right child first. What does the depth-first version need to remember?
