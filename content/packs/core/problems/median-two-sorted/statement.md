Given two lists `a` and `b`, each sorted in ascending order, return the median of all
their values combined, as a float.

The median of an odd number of values is the middle one. For an even number it is the
mean of the two middle values.

## Constraints

- `0 <= len(a), len(b) <= 50_000` and `len(a) + len(b) >= 1`.
- `-10^6 <= a[i], b[i] <= 10^6`
- Either list may be empty, but not both.
- Values may repeat, within a list and across the two.
- Your solution must run in O(log(min(len(a), len(b)))) time.

## Comparing floats

The answer is a float and is compared with a small tolerance, so `2.5` and
`2.4999999999` both pass. Do not try to return an exact fraction or a string.

## Follow-up

Merging the two lists and indexing the middle is O(n + m) and will not meet the bound.
Neither will merging halfway and stopping.

The reframing that works: you do not need the merged list, only a way to **split** it.
Choose how many values to take from `a`; that forces how many come from `b`, since the
total on the left must be half of everything. Now ask what has to be true about the
four values either side of those two cuts for the split to be the correct one — and
notice that if it is wrong, you can tell *which way* to move.
