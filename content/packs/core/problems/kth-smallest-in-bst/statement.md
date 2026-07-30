Given a **binary search tree**, return its `k`th smallest value, counting from `1`.

## How the tree is given to you

`tree` is a level-order list: `tree[0]` is the root, and for the node at index `i`
its children are at `2*i + 1` and `2*i + 2`. A missing node is `null` (Python
`None`). Trailing `null`s are omitted.

BeeCode passes test inputs as JSON, which cannot carry node objects, so you get a
list rather than a chain of `TreeNode`s. That is an honest simplification, not a
disguise: index arithmetic replaces `node.left` and `node.right`, and the algorithm
you are practising is unchanged.

## Constraints

- `1 <= k <= number of nodes <= 4095`
- The tree is a valid binary search tree with distinct integer values.

## Follow-up

One property of search trees answers this immediately, and it is worth being able to
state without hesitating: an in-order walk visits the values in sorted order. So the
`k`th smallest is the `k`th node visited — and you can stop there rather than
finishing the walk. Which traversal form lets you stop mid-way?
