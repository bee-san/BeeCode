Given a binary search tree and two integers `low` and `high`, return the sum of
every node value `v` with `low <= v <= high`.

The tree is encoded as a **level-order list**: index `0` is the root, and the
children of index `i` live at `2i + 1` and `2i + 2`. Absent nodes are `None`.

Adding up every value in range is easy. The point of the Problem is to use the
ordering: a search tree lets you rule out whole subtrees without looking inside
them.

## Constraints

- `0 <= len(tree) <= 4000`
- `-10^6 <= low <= high <= 10^6`
- Node values are distinct
- The input encodes a valid binary search tree

## Follow-up

Scanning the list from end to end also gives the right answer. What does the
ordering buy you that the scan does not — and for which trees and ranges is the
difference largest?
