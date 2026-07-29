Given a list of integers `nums` containing at least one element, return the largest
sum obtainable from any **contiguous** non-empty subarray.

## Constraints

- `1 <= len(nums) <= 100_000`
- `-10^4 <= nums[i] <= 10^4`
- The subarray must be contiguous and must contain at least one element.
- Your solution must run in O(n) time and O(1) extra space.

## Follow-up

The trap here is the all-negative case. A running sum that resets to `0` whenever it
goes negative will answer `0` for `[-3, -1, -2]`, which is the sum of the *empty*
subarray — and the empty subarray is not allowed. The fix is to seed the answer with
the first element rather than with zero, so "best so far" always refers to a real
subarray.

The O(1) space requirement rules out building a prefix-sum list. You only need two
running values: the best sum ending at the current index, and the best seen anywhere.
