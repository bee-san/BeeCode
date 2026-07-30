Place `n` queens on an `n` by `n` board so that no two attack each other. Queens attack
along rows, columns, and both diagonals.

Return every distinct arrangement. Represent an arrangement as a list of `n` strings,
one per row from the top, where `Q` is a queen and `.` an empty square. The rows are in
order; the order of the arrangements themselves is not judged.

## Constraints

- `1 <= n <= 9`

## Follow-up

Placing one queen per row makes row conflicts impossible by construction, which leaves
columns and diagonals. Each of those three constraints can be checked in O(1) with a
set — and the diagonals have a neat index property: along one diagonal `row + column` is
constant, and along the other `row - column` is. Convince yourself before you rely on
it.
