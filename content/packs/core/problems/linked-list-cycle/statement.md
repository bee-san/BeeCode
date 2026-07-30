A chain of nodes is given as a **successor table**: `successors[i]` is the index of the node that
follows node `i`, or `-1` if node `i` is the last one. Node `0` is the head; an empty table means an
empty chain.

Return whether following the successors from the head ever revisits a node.

BeeCode passes test arguments as JSON, so a linked list arrives as a successor table rather than as
node objects. That is an honest simplification, not a disguise: the table names exactly the same
links a chain of pointers would.

## Constraints

- `0 <= len(successors) <= 10000`
- Each entry is `-1` or a valid index.

## Follow-up

A set of visited nodes answers this in O(n) space. Two pointers at different speeds answer it in
O(1). Why must they meet if there is a cycle, and why can the fast one never step over the slow one
without landing on it?
