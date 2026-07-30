## The insight, twice

**The O(n^2) recurrence.** `best[i]` is the length of the longest increasing subsequence
ending at `i`:

```python
best = [1] * len(values)
for i in range(len(values)):
    for j in range(i):
        if values[j] < values[i]:
            best[i] = max(best[i], best[j] + 1)
return max(best, default=0)
```

Every entry alone is a subsequence of length 1, hence the initialisation. The answer is the
maximum over all positions, not `best[-1]` — the best subsequence need not end at the last
entry.

**The O(n log n) method.** Maintain `tails`, where `tails[k]` is the smallest value that any
increasing subsequence of length `k + 1` can end with:

```python
for value in values:
    position = index of the leftmost tail >= value      # binary search
    if position == len(tails):
        tails.append(value)                             # extends the longest
    else:
        tails[position] = value                         # a better ending for that length
return len(tails)
```

## Why `tails` is sorted

A length-`k+1` subsequence contains a length-`k` one whose last value is smaller, so the
smallest achievable ending strictly increases with length. That is what makes binary search
legitimate — and it is the part to be able to justify, because the algorithm looks like a
trick otherwise.

Replacing `tails[position]` never shortens anything: it records that the same length is now
achievable with a smaller ending, which can only help later values extend it. `len(tails)`
is therefore the answer, even though `tails` itself is generally **not** one of the actual
subsequences.

## Strict versus non-decreasing

Strictly increasing means the binary search looks for the leftmost tail `>= value`. For a
non-decreasing variant it would be the leftmost tail `> value`. That one comparison is the
entire difference, and `[7, 7, 7, 7]` is the test that tells them apart: `1` for strict,
`4` for non-decreasing.

## Pitfalls

**Returning `best[-1]`** in the quadratic form. Only right when the answer happens to end at
the last entry.

**Reading `tails` as the answer sequence.** It is not; it holds the best endings per length,
which may not co-occur. Reconstructing the actual subsequence needs predecessor indices.

**`<=` in the recurrence.** Admits equal entries, which are not strictly increasing.

**Empty input.** `0`. The quadratic form needs `max(..., default=0)` or an early return.

## Cost

O(n log n) time and O(n) space in the tails form; O(n^2) and O(n) in the recurrence.
