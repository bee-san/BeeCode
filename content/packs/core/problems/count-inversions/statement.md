An **inversion** is a pair of positions `i < j` where `nums[i] > nums[j]` — two
elements that are in the wrong order relative to each other.

Given a list of integers, return how many inversions it contains. A sorted list has
`0`; a strictly descending list of `n` elements has every pair inverted.

Equal values are **not** an inversion: the comparison is strict.

## Constraints

- `0 <= len(nums) <= 50_000`
- `-10^9 <= nums[i] <= 10^9`
- Values may repeat

## Follow-up

Counting pairs directly is O(n²), which at 50,000 elements is more than a billion
comparisons. Merge sort compares elements from two already-sorted halves — at the
moment it takes an element from the right half, what does it know about how many
elements on the left are greater than it?
