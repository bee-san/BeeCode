Given a binary tree, return its values grouped **by level**: one inner list per
depth, top to bottom, and left to right within each level.

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
- Return `[]` for an empty tree. Never return an empty inner list.

## Follow-up

The encoding hands you the levels almost for free — index `0` is level 0, indices
`1..2` are level 1, and so on. Solve it that way if you like, but then solve it again
with a queue, because the queue version is the one that works when you are handed
real nodes and no indices.
