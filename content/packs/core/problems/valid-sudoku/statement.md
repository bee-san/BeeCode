A partially filled 9x9 Sudoku board is given as a list of 9 rows, each a list of
9 single-character strings. A cell holds either a digit `"1"`-`"9"` or `"."` for
empty.

Return `True` if the board is valid. It is valid when no digit repeats within:

- any one of the 9 rows,
- any one of the 9 columns, or
- any one of the 9 boxes — the nine non-overlapping 3x3 blocks.

Only the digits already present are judged. The board does not have to be
solvable, and an entirely empty board is valid.

## Constraints

- The board is always exactly 9x9.
- Every cell is `"."` or one of `"1"` through `"9"`.

## Follow-up

Rows and columns are easy to index. Which arithmetic on `(row, column)` names the
box a cell belongs to?
