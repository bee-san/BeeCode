Return whether the tree has a root-to-**leaf** path whose node values sum to `target`.

A leaf is a node with no children. Values may be negative.

BeeCode passes test arguments as JSON, so a tree arrives as a level-order list with `null` for an
absent child. That is an honest simplification, not a disguise: the list determines the tree
exactly.

## Constraints

- `0 <= number of nodes <= 5000`
- `-1000 <= node value <= 1000`
- `-10^6 <= target <= 10^6`

## Follow-up

Subtract each value from the target as you descend, and at a leaf ask whether nothing remains. The
tempting shortcut — stopping early once the running total passes the target — is wrong here. Why?
