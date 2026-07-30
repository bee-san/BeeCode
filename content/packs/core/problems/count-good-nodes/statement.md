A node is **good** when no node on the path from the root down to it holds a strictly
greater value. The root is always good.

Return how many good nodes the tree has.

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

- Values may be negative.
- An empty tree has no good nodes.

## Follow-up

Each node's verdict depends only on the largest value seen on the way down to it.
That is a single number, and it flows in the opposite direction from most tree
recursions — pass it *down* as an argument rather than returning it *up*.
