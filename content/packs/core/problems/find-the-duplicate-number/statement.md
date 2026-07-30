`nums` has `n + 1` entries, every one of them an integer in the range `1` to `n`.
By the pigeonhole principle at least one value must repeat. Exactly one value does,
though it may appear more than twice.

Return that value.

Solve it **without modifying `nums`** and in O(1) extra space.

## Constraints

- `2 <= len(nums) <= 100_000`
- Every entry is between `1` and `len(nums) - 1` inclusive.
- Exactly one value is repeated.

## Follow-up

Read `nums` as a function: from index `i`, go to index `nums[i]`. Every index in
`1..n` is a valid destination, so the walk from index 0 never stops. A repeated
value means two indices point to the same place — which makes the walk enter a
cycle. Now it is [a cycle problem](sequence-cycle-detection), and finding the *entry
point* of the cycle finds the duplicate.
