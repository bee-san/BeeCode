`grid` is a rectangular matrix with two properties:

- each row is sorted ascending from left to right, and
- the first value of each row is greater than the last value of the row above it.

Taken together those mean the whole matrix is one sorted sequence, read row by row.

Return `True` if `target` appears in it.

## Constraints

- `0 <= rows, columns <= 100`, and every row has the same length
- `-10**9 <= grid[r][c] <= 10**9`, all values distinct
- An empty matrix contains nothing.

## Follow-up

Binary searching each row is O(rows * log columns). Since the matrix is one sorted
sequence, you can do better: treat the index `i` from `0` to `rows * columns - 1` as
a position in that sequence. What arithmetic recovers the row and column from `i`?
