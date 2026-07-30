`intervals` is a list of `[start, end]` pairs, inclusive at both ends. For every value in
`queries`, find the **shortest** interval containing it, and report that interval's size —
`end - start + 1`. If no interval contains the value, report `-1`.

Return the answers in the order the queries were given.

## Constraints

- `1 <= len(intervals) <= 100000`
- `1 <= len(queries) <= 100000`
- `1 <= intervals[i][0] <= intervals[i][1] <= 10000000`
- `1 <= queries[i] <= 10000000`

## Follow-up

Answering each query independently is O(len(intervals)) each. Answering the queries in
increasing order instead lets every interval be added once and dropped once — but the answers
must still come back in the original order. How do you keep both?
