Put a `+` or a `-` in front of every entry of `values`, then add them up. Return how many of
the `2^len(values)` sign assignments produce exactly `target`.

Every entry must get a sign. Entries are distinguished by position, so two equal values give
two independent choices.

## Constraints

- `1 <= len(values) <= 20`
- `0 <= values[i] <= 1000`
- `-1000 <= target <= 1000`

## Follow-up

`2^20` is about a million, so brute force is actually feasible here — but the table is much
better and generalises. Two routes to it: count the reachable sums level by level as a map
from sum to number of ways, or notice that choosing which entries get `-` turns this into a
subset-sum question. What subset sum, exactly?
