Move every `0` in `values` to the end, keeping the non-zero values in their original relative
order. Do it in place and return the list.

## Constraints

- `1 <= len(values) <= 100000`
- `-10^9 <= values[i] <= 10^9`

## Follow-up

Building a new list is easy. In place, keep a write index for where the next non-zero belongs.
Then there is a variant that minimises the number of writes rather than just the space — when is a
swap wasted work?
