Given a list of integers, return the largest difference between two **successive**
elements once the list is sorted. Return `0` if the list has fewer than two
elements.

Sorting and scanning is the obvious answer and it is correct. The Problem is really
asking for something stronger: can you find the largest gap **without** sorting?

## Constraints

- `0 <= len(nums) <= 100_000`
- `-10^9 <= nums[i] <= 10^9`
- Values may repeat, in which case some gaps are `0`

## Follow-up

If `n` values span a range of `width`, the average gap is `width / (n - 1)`. The
largest gap can never be *smaller* than the average. So if you slice the range into
buckets narrower than that average, what can you conclude about the two values that
form the maximum gap — and which values inside each bucket do you then need to
keep?
