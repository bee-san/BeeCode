Given a binary tree, decide whether it is a **mirror image of itself** — that is,
whether the left and right subtrees of the root are reflections of one another.

Both the shape and the values must mirror.

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
- An empty tree is symmetric.

## Follow-up

Comparing each level's values against their own reverse looks like it should work,
and fails: in `[1, 2, 2, null, 3, null, 3]` the bottom level reads `[3, 3]`, which is
its own reverse, yet the tree is not symmetric — both 3s are right children. Compare
*positions*, not multisets.
