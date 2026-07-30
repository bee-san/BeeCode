Decide whether `sub` appears inside `tree` as a **subtree**: some node of `tree`,
together with *all* of that node's descendants, is identical to `sub`.

"All of the descendants" is the strict part. A node whose value and children match
but which has extra grandchildren below them is not a match.

An empty `sub` is a subtree of every tree.

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

Both `tree` and `sub` are given this way, and they may pad differently with trailing
`null`s.

## Constraints

- `0 <= len(tree), len(sub) <= 2047`
- Node values are integers; only `null` marks an absent node.

## Follow-up

The direct solution asks "is the tree rooted here identical to `sub`?" at every node,
which is O(n * m). Serialising both trees turns it into substring search — but only
if the serialisation records absent children too. Why does it not work otherwise?
