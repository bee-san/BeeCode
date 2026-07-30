## The insight

Two steps, and the discipline is keeping them separate.

**Count.** One pass builds a value-to-frequency map. `collections.Counter` is exactly
this; writing it by hand is three lines.

**Select.** Now find the `k` largest *counts* and report their *values*. That is the
kth-largest problem again, with one twist: you compare by the count but return the
key.

```python
counts = Counter(nums)
return [value for value, _ in heapq.nlargest(k, counts.items(), key=lambda pair: pair[1])]
```

**Return the key, not the count.** The single most common wrong answer here is
`[3, 2]` — the frequencies — instead of `[1, 2]`, the values that occur that often. The
`key=` argument keeps the comparison and the payload distinct.

**Do not sort the input.** Sorting `nums` costs O(n log n) on the whole input; the
selection only needs to touch the `d` distinct values, and `d` can be far smaller.

## Doing better

Every count is between `1` and `n`, so you can **bucket by frequency**: make a list of
`n + 1` buckets, put each value in the bucket for its count, then walk the buckets from
the high end taking values until you have `k`. No comparison sort at all, O(n) total.
That is the answer to the follow-up.

## Why the uniqueness guarantee matters

Without it, `[1, 2]` with `k = 1` would have two equally valid answers and no
comparator could accept both. The statement rules that out so the tests can use
`unordered_list` and still be exact about *which* values are correct — only the order
is free.

## Cost

O(n + d log k) time with the heap, O(n) with buckets. O(d) space for the counts.
