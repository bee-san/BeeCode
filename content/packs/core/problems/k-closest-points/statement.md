`points` is a list of `[x, y]` coordinates. Return the `k` closest to the origin by
straight-line distance, **sorted ascending by that distance**.

Distances are all distinct, so the answer is unique and the order is unambiguous.

## Constraints

- `1 <= k <= len(points) <= 100_000`
- `-10**4 <= x, y <= 10**4`
- No two points are the same distance from the origin.

## Follow-up

Sorting everything is O(n log n) and computes far more order than the question needs.
Two better routes: a heap of size `k`, or partition-based selection. And there is a
detail in the distance itself worth spotting — one operation in the usual formula can
be dropped entirely.
