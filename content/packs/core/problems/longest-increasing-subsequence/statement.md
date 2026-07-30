Return the length of the longest **strictly increasing subsequence** of `values`.

A subsequence keeps the original order but need not be contiguous — entries may be skipped.

## Constraints

- `0 <= len(values) <= 2500`
- `-10^4 <= values[i] <= 10^4`

## Follow-up

The O(n^2) recurrence is the natural first answer: the best subsequence ending at each
position, extending from every smaller earlier entry. There is also an O(n log n) method
that maintains, for each length `k`, the smallest possible value that a length-`k`
increasing subsequence can end with. Why is that list always sorted, and what does binary
searching it accomplish?
