Turn a binary tree into a single string, and turn that string back into the same
tree.

Serialise as a **pre-order walk that also records absent children**: visit the root,
then the left subtree, then the right. Write each node's value as decimal digits, and
write `#` for an absent node. Join every item with a single comma, no spaces.

So the tree

```text
    1
   / \
  2   3
     / \
    4   5
```

serialises to `1,2,#,#,3,4,#,#,5,#,#`, and an empty tree serialises to `#`.

Return a two-element list: the string, and the tree you get by parsing that string
back. Both halves are checked, so a serialiser that loses information will be caught
by the tree it reconstructs.

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
- Your output tree must have trailing `null`s omitted, as the input does.

## Follow-up

Recording `#` for absent children is the reason this works, and it is worth being
able to say why: a pre-order walk *without* those markers is ambiguous, because a
node with only a left child and the same node with only a right child produce the
same sequence. The markers are what make the parse deterministic.
