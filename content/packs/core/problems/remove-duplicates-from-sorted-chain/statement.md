A chain's values are sorted non-decreasing. Remove the duplicate nodes so each value appears once,
and return the remaining values in order.

BeeCode passes test arguments as JSON, so the chain arrives and departs as a plain list of values in
order. That is an honest simplification, not a disguise.

## Constraints

- `0 <= len(values) <= 300`
- `-100 <= values[i] <= 100`
- Sorted non-decreasing.

## Follow-up

Sortedness means equal values are adjacent, so a single walk with one pointer suffices — no set, no
second pass. In the pointer version, what do you advance when you remove a node, and what do you
advance when you keep one? They are not the same.
