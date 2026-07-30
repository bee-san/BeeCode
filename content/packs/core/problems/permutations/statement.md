Given a list of **distinct** integers `nums`, return every ordering of them.

The order in which you return the permutations does not matter, but each permutation
itself must be a specific ordering.

## Constraints

- `0 <= len(nums) <= 7`
- `-1000 <= nums[i] <= 1000`
- All values are distinct.

## Follow-up

`len(nums)` is capped at 7 because `7!` is 5,040 and `8!` is already 40,320 — the
answer's own size is the limit. Given that, is there any point optimising the inner
loop?
