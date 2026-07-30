`digits` holds the decimal digits of a non-negative integer, most significant first, with no
leading zeroes except for the number `0` itself.

Add one and return the resulting digit list.

## Constraints

- `1 <= len(digits) <= 100`
- `0 <= digits[i] <= 9`
- No leading zeroes, other than `[0]`.

## Follow-up

Joining the digits into an integer, adding one, and splitting it back works in Python and is not
the exercise — the point is the carry. Walk from the least significant digit; where does the walk
stop, and what is the one case where the result is longer than the input?
