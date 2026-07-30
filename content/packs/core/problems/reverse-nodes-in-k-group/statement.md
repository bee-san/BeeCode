Reverse `values` in consecutive groups of `k`. A trailing group with fewer than `k`
elements is left in its original order.

With `k = 3`, `[1, 2, 3, 4, 5]` becomes `[3, 2, 1, 4, 5]` — the first three are
reversed and the remaining two are not, because they do not fill a group.

BeeCode passes test inputs as JSON, which cannot carry node objects, so the chain is
given as a plain list of its values rather than as `node.next` pointers. That is an
honest simplification, not a disguise: a list gives you random access a linked list
does not, so if you want the exercise the original intends, work with a cursor that
only ever moves forward.

## Constraints

- `0 <= len(values) <= 100_000`
- `1 <= k <= 100_000`
- `-10**9 <= values[i] <= 10**9`

## Follow-up

The linked-list version must decide whether a full group of `k` nodes exists
*before* reversing anything, because a partial group must be left untouched and
reversing it is not something you can undo cheaply. How do you check, and where do
the three connections — into the group, inside it, and out of it — get rewired?
