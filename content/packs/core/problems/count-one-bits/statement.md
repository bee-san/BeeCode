Return how many bits are set to `1` in the binary representation of a non-negative integer.

## Constraints

- `0 <= number <= 2^32 - 1`

## Follow-up

Testing all 32 bits works and always costs 32 steps. There is a trick that costs one step per
*set* bit instead: `number & (number - 1)` clears the lowest set bit. Work out why before using
it.
