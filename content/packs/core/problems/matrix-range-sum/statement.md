Given an integer `matrix` and a list of `queries`, answer each query with the sum
of the rectangle it names.

A query is `[row1, col1, row2, col2]`, giving the **inclusive** corners of the
rectangle: every cell `(r, c)` with `row1 <= r <= row2` and `col1 <= c <= col2`.

Return one sum per query, in order. There may be many queries against the same
matrix, so the interesting cost is *per query* rather than in total.

## Constraints

- `0 <= rows, cols <= 200`
- `0 <= len(queries) <= 10_000`
- `-10^5 <= matrix[r][c] <= 10^5`
- Every query is within bounds, with `row1 <= row2` and `col1 <= col2`
- The matrix is rectangular: every row has the same length

## Follow-up

Summing each rectangle directly costs its area. With one pass of setup you can
answer any rectangle with a fixed amount of arithmetic — four numbers, regardless
of how large the rectangle is. What are the four, and why is a plain subtraction of
two of them not enough?
