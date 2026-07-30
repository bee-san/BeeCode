## The insight

Cut anywhere and at most one of the halves can contain the rotation point. The other
is an ordinary sorted run, and that is the half you can reason about with a plain
range check.

`nums[low] <= nums[middle]` tells you the left half is the sorted one. Then:

- if `nums[low] <= target < nums[middle]`, the target can only be in that sorted
  left half — search it
- otherwise it is in the right half, rotation point and all

And symmetrically when the right half is the sorted one.

```python
def search_rotated(nums, target):
    low, high = 0, len(nums) - 1
    while low <= high:
        middle = (low + high) // 2
        if nums[middle] == target:
            return middle
        if nums[low] <= nums[middle]:                    # left half is sorted
            if nums[low] <= target < nums[middle]:
                high = middle - 1
            else:
                low = middle + 1
        else:                                            # right half is sorted
            if nums[middle] < target <= nums[high]:
                low = middle + 1
            else:
                high = middle - 1
    return -1
```

Every branch halves the range, so this is a genuine O(log n) — not a search of both
halves dressed up as one.

## The boundary conditions

The `<=` placements are not decorative. `nums[low] <= nums[middle]` must include
equality, because when the range narrows to a single element `low == middle` and the
half is trivially sorted; strict `<` sends such a range down the wrong branch.

In the range checks, the inclusive end is the side *away* from the middle: the
middle has already been tested and excluded, so `target < nums[middle]` on the left
and `target > nums[middle]` on the right.

## An easier route

Find the rotation point with [Minimum of a Rotated Sorted Array](minimum-in-rotated-array),
then binary search whichever run could contain the target — or search the whole
array with indices shifted by the rotation offset. Two simple searches instead of
one intricate one, still O(log n), and much easier to argue for under pressure.

## Cost

O(log n) time, O(1) space.
