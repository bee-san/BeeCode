`values` is sorted non-decreasing. Return `[first, last]`, the lowest and highest indices at which
`target` appears, or `[-1, -1]` if it is absent.

## Constraints

- `0 <= len(values) <= 100000`
- `-10^9 <= values[i], target <= 10^9`
- Sorted non-decreasing.

## Follow-up

Finding one occurrence and then walking outwards is O(n) when the value fills the list. Two binary
searches give O(log n): one for the leftmost position, one for the rightmost. What single change to
a standard binary search turns "find any" into "find the leftmost"?
