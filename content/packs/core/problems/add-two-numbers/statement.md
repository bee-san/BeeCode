Two non-negative integers are given as lists of decimal digits stored **least
significant first**, so `[2, 4, 3]` is the number 342.

Return their sum in the same format.

Each list has no leading zeros in the number it represents — meaning no trailing
zeros in the list — except that the number zero is `[0]`.

## Constraints

- `1 <= len(left), len(right) <= 1000`
- `0 <= digit <= 9`
- The result must have no trailing zeros either, except for zero itself.

## Follow-up

Backwards is the *convenient* order: it is the order you add in. Work digit by
digit with a carry, and do not forget that the carry can outlive both inputs.
