## Turn the question around

Searching for the best *split* is awkward: there are many ways to place `k - 1`
cuts. But the reverse question is easy:

> Given a cap, can `nums` be covered by at most `k` parts, none exceeding it?

Walk left to right, extending the current part while it fits and cutting when it
does not. Greedy is optimal for this check: cutting *earlier* than necessary can
only need more parts, never fewer.

And crucially, the check is **monotonic**. If a cap works, every larger cap works
too. The feasible caps are therefore an unbroken run at the top of the number line,
and the answer is exactly where that run begins — a boundary, so binary search
finds it.

This is "binary search on the answer": the space being halved is not the input, it
is the set of possible results.

## Bounds and the search

```python
def split_array(nums, k):
    def parts_needed(cap):
        parts, current = 1, 0
        for value in nums:
            if current + value > cap:
                parts += 1
                current = value
            else:
                current += value
        return parts

    low, high = max(nums), sum(nums)
    while low < high:
        middle = (low + high) // 2
        if parts_needed(middle) <= k:
            high = middle
        else:
            low = middle + 1
    return low
```

Why those bounds, and why this loop shape:

**`low = max(nums)`, not `0`.** Every element must live in some part, so no cap
below the largest element is ever feasible — `parts_needed` would loop forever
trying to place it. Starting at `0` is the classic way to make this hang or
mis-answer.

**`high = sum(nums)`.** One part holding everything always works, so the answer can
never exceed it.

**`high = middle`, not `middle - 1`.** A feasible `middle` might *be* the answer, so
it stays in the range. The pair `high = middle` / `low = middle + 1` is what makes
the loop converge on the smallest feasible value rather than overshooting it.

**`parts_needed` starts at 1.** A non-empty array is already one part before any
cut is made. Starting at `0` undercounts by one and accepts caps that are too small.

## Cost

O(n log S) time, where `S = sum(nums) - max(nums)`: each feasibility check is one
linear pass, and there are about `log S` of them. O(1) extra space.

The dynamic-programming alternative — best split of a suffix into `p` parts — is
O(n² k) and correct, but here the monotonic structure of the answer makes it
unnecessary work.
