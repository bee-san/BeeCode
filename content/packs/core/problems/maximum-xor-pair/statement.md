Given a list of non-negative integers, return the largest value of
`nums[i] XOR nums[j]` over all pairs `i != j`. Return `0` if the list has fewer
than two elements.

The two elements may be equal in value as long as they are different positions —
`[5, 5]` has one valid pair, whose XOR is `0`.

## Constraints

- `0 <= len(nums) <= 20_000`
- `0 <= nums[i] <= 2^31 - 1`

## Follow-up

Checking every pair is O(n²). The largest XOR can instead be built one bit at a
time, from the top down: for each bit, ask whether *some* pair can differ there
while agreeing with the choices you have already committed to. What structure
answers that question in one pass per number?
