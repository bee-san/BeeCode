Given a list of integers `nums`, return a list `answer` of the same length where
`answer[i]` is the product of every element of `nums` **except** `nums[i]`.

The empty product is `1`, so a one-element list returns `[1]` and an empty list returns
`[]`.

You must solve this **without using division**, and in O(n) time.

## Constraints

- `0 <= len(nums) <= 100_000`
- `-30 <= nums[i] <= 30`
- `nums` may contain zeros and negative values.
- Every answer fits in a Python integer.

## Follow-up

Everything except `nums[i]` splits cleanly into two pieces: what lies to its left and what
lies to its right. Can you compute the answer with O(1) extra space, not counting the
output list — that is, using the output list itself as your scratch space?
