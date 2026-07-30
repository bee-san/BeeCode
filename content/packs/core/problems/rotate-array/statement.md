Rotate `values` to the right by `steps` places, in place, and return it.

`steps` may exceed the length of the list, and may be `0`.

## Constraints

- `1 <= len(values) <= 100000`
- `0 <= steps <= 10^9`

## Follow-up

Slicing is one line and allocates a second list. In place with O(1) extra space, three reversals
do it: reverse the whole list, then reverse each of the two pieces. Which two pieces, and where
does `steps` have to be reduced first?
