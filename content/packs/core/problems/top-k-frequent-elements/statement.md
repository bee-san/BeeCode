Given a list of integers `nums` and an integer `k`, return the `k` most frequent
values. The order of your answer does not matter.

The input guarantees the answer is unique: there is never a tie for the `k`th place
that would make two different answers equally correct.

## Constraints

- `1 <= len(nums) <= 100_000`
- `-10^9 <= nums[i] <= 10^9`
- `1 <= k <= ` the number of distinct values in `nums`

## Follow-up

Counting is the easy half. Selecting the top `k` counts is the same question as the
previous Problem in this pack — except the thing you compare by is not the thing you
return. Can you do the selection in better than O(d log d) for `d` distinct values?
