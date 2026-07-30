Given a list of **distinct** integers `nums`, return all of its subsets (the power
set).

The order of the subsets, and the order of elements within each subset, does not
matter. The empty subset must be included.

## Constraints

- `0 <= len(nums) <= 14`
- `-1000 <= nums[i] <= 1000`
- All values are distinct.

## Follow-up

A list of `n` elements has exactly `2^n` subsets, and every subset corresponds to an
`n`-bit number saying which elements it contains. That observation turns this into a
loop with no recursion at all. Write both.
