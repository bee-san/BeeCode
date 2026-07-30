Given a list of integers `nums` and an integer `k`, return the length of the
**shortest non-empty contiguous subarray whose sum is at least `k`**. Return `-1`
if no such subarray exists.

The values may be negative. That is the whole difficulty: a longer subarray is not
necessarily a larger sum, so the usual grow-and-shrink sliding window does not
apply.

## Constraints

- `0 <= len(nums) <= 50_000`
- `-10^5 <= nums[i] <= 10^5`
- `1 <= k <= 10^9`

## Follow-up

Write the sums as a prefix array, so the sum from `i` to `j` becomes
`prefix[j + 1] - prefix[i]`. You are then looking for the closest pair of prefix
indices whose difference is at least `k`. Which earlier prefixes can you throw
away forever, and why does that leave the survivors in increasing order?
