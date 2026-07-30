`lists` is a list of sorted integer lists. Merge them into one sorted list and
return it.

Some of the input lists may be empty, and `lists` itself may be empty.

BeeCode passes test inputs as JSON, which cannot carry node objects, so the chain is
given as a plain list of its values rather than as `node.next` pointers. That is an
honest simplification, not a disguise: a list gives you random access a linked list
does not, so if you want the exercise the original intends, work with a cursor that
only ever moves forward.

## Constraints

- `0 <= len(lists) <= 10_000`
- `0 <= len(lists[i])`, and the total number of values is at most 100_000
- `-10**9 <= value <= 10**9`
- Each `lists[i]` is sorted ascending.

## Follow-up

Concatenating everything and sorting is O(N log N) and ignores that the input is
already sorted. Two better routes exploit it: a heap holding one candidate per list,
or merging the lists pairwise in rounds. Both reach O(N log k) — what is `k` doing
in each?
