A **peak** is an element strictly greater than both of its neighbours. Elements
outside the list count as negative infinity, so `nums[0]` is a peak if it is greater
than `nums[1]`, and the last element is a peak if it is greater than the one before it.

Given a list `nums` in which no two adjacent elements are equal, return the index of
**any** peak.

## Constraints

- `1 <= len(nums) <= 100_000`
- `-2^31 <= nums[i] <= 2^31 - 1`
- `nums[i] != nums[i + 1]` for every valid `i`.
- Your solution must run in O(log n) time.

## Returning any peak

A list can have several peaks and **any** of their indices is accepted. You do not
need to find the largest element, and you should not try to — that cannot be done in
O(log n).

## Follow-up

The O(log n) requirement is the whole Problem, and it should look impossible at first:
the list is not sorted, so how can you discard half of it?

The question to sit with is why a peak is *guaranteed* to exist in a given half. If
`nums[middle] < nums[middle + 1]`, what must be true of the right-hand side, no matter
what the values are?
