`nums` was sorted ascending and then rotated left some number of times, possibly
zero. All values are distinct.

Return the smallest value.

For `[3, 4, 5, 1, 2]` the answer is `1`: the original was `[1, 2, 3, 4, 5]` rotated
three times.

## Constraints

- `1 <= len(nums) <= 100_000`
- `-10**9 <= nums[i] <= 10**9`, all distinct
- The rotation may be zero, so `nums` may already be sorted.

## Follow-up

Scanning is O(n). The array is in two sorted runs, and the minimum is exactly the
start of the second one. Compare the middle element with the **last** element and
you can always tell which run you are in.
