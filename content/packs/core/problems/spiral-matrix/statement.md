Given a `matrix` of integers, return all its values in **spiral order**: left to right
along the top row, down the right column, right to left along the bottom, up the left
column, and inward.

## Constraints

- `0 <= len(matrix) <= 100` and `0 <= len(matrix[0]) <= 100`
- Every row has the same length.
- The matrix need not be square.

## Follow-up

Most wrong answers here are not wrong about the spiral — they are wrong about the last
ring. When the remaining region is a single row, or a single column, the "come back
along the bottom" and "go up the left" passes revisit cells they already emitted. What
condition stops that?
