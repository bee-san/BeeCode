Treat `number` as an unsigned 32-bit value and return the integer formed by reversing its 32 bits.

Leading zeroes are part of the word: the bit at position 0 ends up at position 31, whether it is
set or not.

## Constraints

- `0 <= number <= 2^32 - 1`

## Follow-up

Read one bit off the bottom, push it onto the bottom of a result you keep shifting up. Exactly 32
iterations, no more and no fewer — and stopping early is the bug.
