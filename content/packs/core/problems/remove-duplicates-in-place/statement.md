`values` is sorted in non-decreasing order. Remove the duplicates in place so each distinct value
appears once, keeping ascending order, and return how many distinct values remain.

The first `count` positions of `values` must hold those distinct values. What lies beyond position
`count` does not matter.

## Constraints

- `1 <= len(values) <= 30000`
- `values` is sorted non-decreasing.
- `-10^9 <= values[i] <= 10^9`

## Follow-up

Because the input is sorted, equal values are adjacent — so you never need a set. One write index
and one read index suffice. What does the write index have to be initialised to, and why not `0`?
