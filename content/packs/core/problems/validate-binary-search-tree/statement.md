Given a binary tree, decide whether it is a valid **binary search tree**.

A binary search tree requires that for *every* node:

- every value in its left subtree is strictly less than the node's value, and
- every value in its right subtree is strictly greater.

Equal values are not allowed anywhere.

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
- An empty tree is a valid binary search tree.

## Follow-up

The rule is about whole subtrees, not about parents and children. `[5, 1, 6, null,
null, 4, 7]` has every node correctly placed relative to its own parent and is still
not a binary search tree — find the node that breaks it, then make sure your
solution rejects it.
