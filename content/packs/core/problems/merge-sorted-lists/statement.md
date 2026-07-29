Given two lists of integers `a` and `b`, each already sorted in ascending order, return
a single new list containing all of their elements in ascending order.

Every element of both inputs appears in the result, so `len(result) == len(a) + len(b)`.
Duplicates are kept, whether they occur within one list or across both.

## Constraints

- `0 <= len(a), len(b) <= 50_000`
- `-10^9 <= a[i], b[i] <= 10^9`
- Both inputs are sorted ascending. Either or both may be empty.
- Your solution must run in O(len(a) + len(b)) time.

## Follow-up

Concatenating and calling `sorted()` gives the right answer in O(n log n) and throws away
the fact that the inputs were already sorted. Use that fact instead: at every step, the
smallest remaining element overall is at the front of one of the two lists.
