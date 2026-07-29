Given a list of integers `nums`, return `True` if any value appears at least twice,
and `False` if every value is distinct.

## Constraints

- `0 <= len(nums) <= 100_000`
- `-10^9 <= nums[i] <= 10^9`
- An empty list and a one-element list contain no duplicates.

## Follow-up

The brute-force version compares every pair, which is O(n²). Getting to O(n) costs
you O(n) extra memory. If you were forbidden from allocating that memory but were
allowed to reorder `nums` in place, what would you do instead, and what would it
cost?
