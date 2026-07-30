Return `True` if `values` can be split into two groups with **equal sums**.

Every entry must go into exactly one group. Neither group may be empty.

## Constraints

- `1 <= len(values) <= 200`
- `1 <= values[i] <= 100`

## Follow-up

If the total is odd, stop — no split can work. Otherwise the question becomes: can some
subset sum to exactly half the total? That is a subset-sum problem, and the reachable sums
can be tracked as a set of booleans rather than by enumerating subsets. Why is one pass over
the values enough?
