A **path** is any sequence of nodes where each consecutive pair is joined by an edge,
and no node appears twice. A path does not have to touch the root, and it may consist
of a single node.

Return the largest sum of the values on any path.

Values may be negative, so the answer may be negative.

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

- `1 <= len(tree) <= 4095`, and the tree has at least one node
- `-1000 <= node value <= 1000`
- Only `null` marks an absent node.

## Follow-up

Every path has a single highest node, where it turns around. That gives one traversal
computing two different things, as in
[Diameter of a Binary Tree](binary-tree-diameter) — but with a twist the diameter
does not have: a subtree can be worth *less* than nothing. What do you do with a
branch whose best contribution is negative?
