Given a list `nums` and a window size `k`, return the maximum of every contiguous
window of length `k`, from left to right.

## Constraints

- `1 <= k <= len(nums) <= 100_000`
- `-10^9 <= nums[i] <= 10^9`
- The result has exactly `len(nums) - k + 1` entries.

## Follow-up

Calling `max()` on each window is O(n·k) — 10^9 operations at the limits. The linear
solution keeps a deque of *candidate* indices and maintains one invariant that makes
the front of the deque always the answer. What makes a value in the window permanently
irrelevant?
