Given a list of integers `nums` and an integer `k`, return the `k`th largest element.

This is the `k`th largest **by position in sorted order**, counting duplicates
separately — not the `k`th distinct value. In `[3, 3, 1]` the 2nd largest is `3`.

## Constraints

- `1 <= k <= len(nums) <= 200_000`
- `-10^9 <= nums[i] <= 10^9`

## Follow-up

Sorting is one line and O(n log n). A heap of size `k` gets you O(n log k), which is a
real win when `k` is small and `n` is huge. Which of the two would you reach for if
`k` were `1`?
