Given a list of integers `nums`, return every triplet of **distinct positions**
whose values sum to zero.

Report each triplet as a list of its three values in ascending order. Two
triplets that contain the same three values are the same triplet: report it once.
The order of the triplets in your answer does not matter.

For `[-1, 0, 1, 2, -1, -4]` the answer is `[[-1, -1, 2], [-1, 0, 1]]`.

## Constraints

- `0 <= len(nums) <= 3000`
- `-100_000 <= nums[i] <= 100_000`
- Return `[]` when there is no such triplet.

## Follow-up

Fixing one number turns this into "find two numbers summing to `-x`" in the rest
of the list. Sort first and that inner problem has an O(n) two-pointer solution —
but the deduplication then needs care in two separate places.
