Return the largest product obtainable from a **contiguous, non-empty** run of `values`.

## Constraints

- `1 <= len(values) <= 2 * 10^4`
- `-10 <= values[i] <= 10`
- The answer fits in a 32-bit signed integer.

## Follow-up

[Largest Sum of a Contiguous Run](max-subarray) works by keeping the best sum ending at
each position. The same idea nearly works here, but multiplication has a property addition
does not: a very *bad* running value can become the best one in a single step. What has to
be tracked alongside the maximum?
