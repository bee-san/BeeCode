One value in `values` occurs **more than half** the time. Return it.

You may assume such a value always exists.

## Constraints

- `1 <= len(values) <= 50000`
- `-10^9 <= values[i] <= 10^9`

## Follow-up

A frequency table answers this in O(n) time and O(n) space. There is an O(1)-space way that keeps
just one candidate and one tally, and it works only because the majority is strict — more than
half, not merely the most common. Why does that strictness matter?
