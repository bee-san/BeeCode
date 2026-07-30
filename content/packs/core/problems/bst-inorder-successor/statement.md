Given a binary search tree and an integer `target`, return the **smallest value in
the tree that is strictly greater than `target`** — the value that would follow it
in an in-order walk. Return `None` if no such value exists.

The tree is encoded as a **level-order list**: index `0` is the root, and the
children of index `i` live at `2i + 1` and `2i + 2`. Absent nodes are `None`.

`target` need not be present in the tree. When it is absent, the answer is still
the smallest value above it.

## Constraints

- `0 <= len(tree) <= 4000`
- `-10^6 <= target <= 10^6`
- Node values are distinct
- The input encodes a valid binary search tree

## Follow-up

You could walk the whole tree in order and take the first value past `target`. The
ordering allows something better: you never need to visit two nodes at the same
depth. What is the state you have to carry down as you descend?
