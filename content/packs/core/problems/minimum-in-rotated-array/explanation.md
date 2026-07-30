## The insight

A rotated sorted array is two ascending runs, and every value in the first run is
greater than every value in the second:

```
[3, 4, 5, 1, 2]
 ~~~~~~~  ~~~~
 first    second
```

The minimum is the first element of the second run. To find it, compare the middle
element against the **last** element:

- `nums[middle] > nums[high]` — the middle is in the first run, so the minimum lies
  strictly to its right: `low = middle + 1`
- otherwise the middle is in the second run, so it is a candidate and nothing to
  its right can be smaller: `high = middle`

```python
def find_minimum(nums):
    low, high = 0, len(nums) - 1
    while low < high:
        middle = (low + high) // 2
        if nums[middle] > nums[high]:
            low = middle + 1
        else:
            high = middle
    return nums[low]
```

## Why compare against the end and not the start

Comparing with `nums[low]` fails on the un-rotated array. If `nums` is
`[1, 2, 3]`, the middle is not less than the start, which looks like "I am in the
first run" — and sends the search the wrong way. The comparison against `high` has
no such blind spot: an un-rotated array is entirely "second run", every step takes
the `high = middle` branch, and the search converges on index 0.

## Pitfalls

**`low <= high` with `high = middle`.** That never terminates when `low == high`.
The loop condition and the non-shrinking assignment must match: `low < high` and
`high = middle`, or `low <= high` and `high = middle - 1` with a separate record of
the best candidate.

**`high = middle - 1`** in this form discards the candidate. The middle *may be* the
answer when it is in the second run, so it stays inside the range.

**Duplicates.** With repeated values the comparison can be inconclusive
(`nums[middle] == nums[high]` tells you nothing) and the worst case degrades to
O(n). This Problem guarantees distinct values; the harder variant does not.

## Cost

O(log n) time, O(1) space.
