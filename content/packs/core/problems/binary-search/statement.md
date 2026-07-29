Given a list of integers `nums` sorted in strictly increasing order and an integer
`target`, return the index of `target` in `nums`, or `-1` if it is not present.

## Constraints

- `0 <= len(nums) <= 100_000`
- `-10^9 <= nums[i], target <= 10^9`
- `nums` is sorted in ascending order and all values are distinct.
- Your solution must run in O(log n) time.

## Follow-up

Write the loop so that it terminates for every input, including the empty list and a
one-element list. Getting the boundary conditions right on the first try is the actual
skill this Problem trains — off-by-one errors here cause infinite loops, not just wrong
answers.
