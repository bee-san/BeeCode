Every value in `nums` appears exactly twice, except one, which appears once. Return
that value.

## Constraints

- `1 <= len(nums) <= 100_000`
- `len(nums)` is odd.
- `-10^9 <= nums[i] <= 10^9`
- Exactly one value appears once; every other value appears exactly twice.

## Follow-up

A hash set solves this in one pass and O(n) space. There is a solution using O(1)
extra space and no arithmetic overflow, built on a single property of XOR. What is
`x ^ x`, and what is `x ^ 0`?
