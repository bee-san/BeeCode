Given a list of integers `nums`, return the length of the longest run of
**consecutive** integers that appear in it.

The numbers do not have to be adjacent in the list, or in order. Duplicates do
not lengthen a run.

For `[100, 4, 200, 1, 3, 2]` the answer is `4`, from `1, 2, 3, 4`.

## Constraints

- `0 <= len(nums) <= 100_000`
- `-10**9 <= nums[i] <= 10**9`
- The answer for an empty list is `0`.

## Follow-up

Sorting solves this in O(n log n). There is an O(n) solution. The trick is to
notice that most numbers are not worth starting from — only a few are the
*beginning* of a run. Which ones?
