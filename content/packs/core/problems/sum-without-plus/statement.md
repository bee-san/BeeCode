Return the sum of two integers without using `+` or `-`. Both may be negative.

Treat the values as signed 32-bit integers: the result is in `[-2^31, 2^31 - 1]`.

## Constraints

- `-1000 <= first, second <= 1000`

## Follow-up

XOR is addition that forgets to carry, and AND finds exactly where the carries belong. Loop until
there is no carry left. The complication in Python is that its integers are unbounded, so a
negative result never stops carrying on its own — what mask makes it stop?
