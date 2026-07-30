## The insight

Let `P(j)` be the sum of the first `j` elements. The sum of the range `i..j-1` is
`P(j) - P(i)`, so asking for a subarray summing to `k` is asking for

```
P(j) - P(i) = k     i.e.     P(i) = P(j) - k
```

Now walk left to right keeping the running prefix `P(j)`, and at each step ask
how many earlier prefixes equalled `P(j) - k`. A counter of prefixes seen so far
answers that in O(1).

```python
def count_subarrays(nums, k):
    seen = {0: 1}
    running = 0
    total = 0
    for value in nums:
        running += value
        total += seen.get(running - k, 0)
        seen[running] = seen.get(running, 0) + 1
    return total
```

## The two subtleties

**`{0: 1}` is not decoration.** The empty prefix has sum 0, and it is what makes a
subarray *starting at index 0* countable. Drop it and `[3], k = 3` returns 0.

**Count, do not just record.** `seen` maps a prefix sum to how many times it has
occurred, because several different starting points can share one prefix sum —
`[0, 0, 0]` with `k = 0` has six subarrays, and a set would report three.

**Look up before inserting.** Insert the current prefix first and, when `k == 0`,
you match yourself and count an empty subarray.

## Why not a sliding window

Sliding windows rely on the sum increasing as the window grows, so that shrinking
from the left is a safe way to come back down. With negative numbers the sum is
not monotonic in the window width, and the window has no rule to follow. If the
values were all positive, a two-pointer window would work in O(1) space.

## Cost

O(n) time, O(n) space for the counter.
