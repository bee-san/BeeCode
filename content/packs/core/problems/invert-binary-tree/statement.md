Invert a binary tree: swap the left and right child of every node, so the result is
the mirror image of the input.

Return the inverted tree in the same level-order form, with trailing `null`s omitted.

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

The recursion is three lines. Write the iterative version too — it needs a stack or
a queue of nodes still to visit, and it is the answer to give when the interviewer
says "now do it without recursion".
