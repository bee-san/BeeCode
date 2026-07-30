`first` and `second` are non-negative integers written as decimal strings. Return their product,
also as a decimal string.

Do not convert the strings to integers — do the multiplication digit by digit.

Neither input has leading zeroes, unless it is exactly `"0"`, and neither does your answer.

## Constraints

- `1 <= len(first), len(second) <= 200`
- Both strings contain only digits `0`-`9`.

## Follow-up

Long multiplication, the way it is taught on paper. The digit at position `i` of one number times
the digit at position `j` of the other lands at a predictable place in the answer. Where — and
why does that make a single array of length `len(first) + len(second)` enough?
