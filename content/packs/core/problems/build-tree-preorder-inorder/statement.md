`preorder` and `inorder` are the pre-order and in-order traversals of the same binary
tree, whose values are all distinct. Rebuild the tree.

Return it as a **level-order list**: `tree[0]` is the root, and for the node at index
`i` its children are at `2*i + 1` and `2*i + 2`. Absent nodes are `null` (Python
`None`), and trailing `null`s are omitted.

So `[1, 2, 3, null, 4]` is:

```text
    1
   / \
  2   3
   \
    4
```

## Constraints

- `0 <= len(preorder) == len(inorder) <= 2047`
- All values are distinct integers.
- The two lists are genuine traversals of one tree.

## Follow-up

Each traversal alone is ambiguous — many trees share a pre-order sequence — but
together they pin down exactly one tree. Pre-order names the root first; in-order
splits at the root. That is the whole recursion. Where does the O(n^2) come from in
the naive version, and what removes it?
