Return `base` raised to `exponent`, where `exponent` may be negative.

Return the result as a float. A negative exponent means the reciprocal:
`base ** -n == 1 / (base ** n)`.

## Constraints

- `-100.0 < base < 100.0`
- `-1000 <= exponent <= 1000`
- `base` is not `0` when `exponent` is negative.

## Follow-up

Multiplying `exponent` times is O(exponent). Squaring gets you there in O(log exponent), because
`base^(2k) == (base^k)^2`. What do you do when the exponent is odd, and what breaks if you handle
a negative exponent by negating it first?
