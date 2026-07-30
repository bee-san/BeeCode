Given a list of integers `nums` and an integer `k`, return how many **contiguous**
non-empty subarrays sum to exactly `k`.

Subarrays are counted by position, not by content: two different ranges that
happen to hold the same values count twice.

For `nums = [1, 1, 1]` and `k = 2` the answer is `2` — positions 0..1 and 1..2.

## Constraints

- `0 <= len(nums) <= 20_000`
- `-1000 <= nums[i] <= 1000`, so values may be negative or zero.
- `-10**7 <= k <= 10**7`

## Follow-up

Checking every subarray is O(n^2). Write `sum(i..j)` in terms of prefix sums and
the condition becomes an equation between two of them — at which point you are
looking for pairs, and looking for pairs is what a hash map is for.

Note what the negative values rule out: sums do not grow as the window widens, so
a sliding window cannot work here.
