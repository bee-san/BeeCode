`first` and `second` are lists of closed intervals `[start, end]`. Within each list the intervals are
sorted and do not overlap each other.

Return the list of intervals covered by **both**, sorted.

Two intervals that meet at a single point overlap there, and that one-point interval belongs in the
answer.

## Constraints

- `0 <= len(first), len(second) <= 1000`
- `0 <= start <= end <= 10^9`
- Each list is sorted by start and its intervals are pairwise disjoint.

## Follow-up

Comparing every interval against every other is O(n*m) and does not use the sorting. Two pointers get
it to O(n + m) — and the only real decision is which pointer to advance after each comparison.
