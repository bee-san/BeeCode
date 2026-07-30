Given a binary search tree and a value that does not already appear in it, insert
the value as a new leaf and return the resulting tree.

The tree is encoded as a **level-order list**: index `0` is the root, and the
children of index `i` live at `2i + 1` and `2i + 2`. Absent nodes are `None`, and
trailing `None`s are omitted. Return the new tree in the same encoding.

Insert as a leaf, without rebalancing or otherwise rearranging the existing nodes.
Under that rule exactly one position is valid, so the answer is unique.

## Constraints

- `0 <= len(tree) <= 2000`
- `-10^6 <= value <= 10^6`
- The tree's values are distinct, and `value` is not among them
- The input encodes a valid binary search tree

## Follow-up

On a balanced tree of `n` nodes this touches about `log n` of them. What does the
same work cost on a tree whose nodes form one long chain, and what does that imply
about the length of the list you return?
