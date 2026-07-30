You are given `successors`, a list where `successors[i]` is the index you move to from
position `i`, or `-1` if position `i` is the end. Starting from `start`, decide whether
following the successors forever ever revisits a position.

Return `True` if the walk enters a cycle, `False` if it reaches `-1`.

This is the classic "does a linked list have a cycle" question. BeeCode passes test
inputs as JSON, which cannot carry node objects, so the chain is given as a successor
table rather than as `node.next` pointers. That is an honest simplification, not a
disguise: `successors[i]` *is* `next`, and the algorithm is unchanged.

## Constraints

- `1 <= len(successors) <= 100_000`
- Each entry is `-1` or a valid index into `successors`.
- `0 <= start < len(successors)`

## Follow-up

A visited set solves this in O(n) space. Floyd's algorithm does it in O(1): run one
walker at one step per turn and another at two. Why must they eventually land on the
same position if a cycle exists — and why does the fast one never "jump over" the slow
one?
