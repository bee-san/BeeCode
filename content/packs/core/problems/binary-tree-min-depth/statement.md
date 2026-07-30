Return the number of nodes on the shortest path from the root down to any **leaf** — a node with no
children at all.

An empty tree has depth `0`.

## How the tree is given to you

`tree` is a **level-order list**: `tree[0]` is the root, and for the node at index `i` its children
are at `2*i + 1` and `2*i + 2`. A missing node is `null` (Python `None`). Trailing `null`s are
omitted.

BeeCode passes test inputs as JSON, which cannot carry node objects, so you get a list rather than
a chain of `TreeNode`s. That is an honest simplification, not a disguise: index arithmetic replaces
`node.left` and `node.right`, and the algorithm you are practising is unchanged.

## Constraints

- `0 <= len(tree) <= 4095`
- Node values are integers; only `null` marks an absent node.
- The list is well formed: a node's parent is never `null`.

## Follow-up

The maximum-depth recursion is `1 + max(left, right)`. Replacing `max` with `min` is wrong, and the
reason is the whole problem. Once you see why, notice that breadth-first search can stop at the
first leaf it meets.
