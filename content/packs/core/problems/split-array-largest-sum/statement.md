Given a list of non-negative integers `nums` and an integer `k`, split `nums` into
`k` **non-empty contiguous** parts so that the largest part-sum is as small as
possible. Return that smallest possible largest sum.

The order of `nums` is fixed — you are choosing where to cut, not rearranging.

## Constraints

- `1 <= len(nums) <= 1000`
- `1 <= k <= len(nums)`
- `0 <= nums[i] <= 10^6`

## Follow-up

Searching over *arrangements* is expensive. Search over **answers** instead: for a
candidate largest-sum `cap`, it is easy to check greedily whether `k` parts suffice.
That check is monotonic in `cap` — true for every value above some threshold, false
below it — which makes the threshold binary-searchable. What are the tightest lower
and upper bounds to start from?
