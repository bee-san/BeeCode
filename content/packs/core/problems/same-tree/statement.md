Decide whether two binary trees are identical: the same shape, with the same value at
every corresponding position.

`left` and `right` are two level-order lists. **They may pad differently with
trailing `null`s**, so compare the trees rather than the lists — `[1, 2]` and
`[1, 2, null, null]` describe the same tree.

## How each tree is given to you

`tree[0]` is the root, and for the node at index `i` its children are at `2*i + 1`
and `2*i + 2`. A missing node is `null` (Python `None`).

So `[1, 2, 3, null, 4]` is:

```text
    1
   / \
  2   3
   \
    4
```

BeeCode passes test inputs as JSON, which cannot carry node objects, so you get
lists rather than chains of `TreeNode`s. That is an honest simplification, not a
disguise: index arithmetic replaces `node.left` and `node.right`, and the algorithm
you are practising is unchanged.

## Constraints

- `0 <= len(left), len(right) <= 4095`
- Node values are integers; only `null` marks an absent node.
- Two empty trees are the same.

## Follow-up

The recursion mirrors the definition so exactly that it is worth memorising the
shape: agree on both being absent, disagree if only one is, then compare the value
and recurse pairwise. This same shape reappears in
[Are Two Trees Mirror Images](symmetric-tree) with one pair of arguments crossed
over.
