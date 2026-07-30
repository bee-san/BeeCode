Given a **binary search tree** and two values `first` and `second` that both appear
in it, return the value of their lowest common ancestor: the deepest node that has
both of them as descendants.

A node counts as a descendant of itself, so if one value is an ancestor of the other,
that value is the answer.

`first` and `second` are given in no particular order.

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

- The tree is a valid binary search tree with distinct values.
- Both `first` and `second` appear in the tree. They may be equal.

## Follow-up

In a general binary tree this needs a traversal that reports back up. In a search
tree it does not: the ordering tells you which way to go at every step, so the answer
comes from one walk down from the root with no recursion and no extra space. What is
the condition that says "stop, this is it"?
