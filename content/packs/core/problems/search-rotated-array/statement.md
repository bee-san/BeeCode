`nums` was sorted ascending and then rotated left some number of times, possibly
zero. All values are distinct.

Return the index of `target`, or `-1` if it is absent.

## Constraints

- `1 <= len(nums) <= 100_000`
- `-10**9 <= nums[i] <= 10**9`, all distinct
- The rotation may be zero.

## Follow-up

At any midpoint, **one of the two halves is guaranteed to be properly sorted** — you
can tell which by a single comparison. Inside a sorted half you can decide by range
check whether the target could be there. If it cannot, it is in the other half.
