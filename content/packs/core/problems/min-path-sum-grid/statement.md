Each cell of `grid` holds a non-negative cost. Starting at the top-left and moving only **right or
down**, reach the bottom-right with the smallest total cost, counting both endpoints.

Return that total.

## Constraints

- `1 <= rows, columns <= 200`
- `0 <= grid[r][c] <= 1000`

## Follow-up

The cheapest way to reach a cell comes from exactly one of two places. That makes it a table filled
in one sweep — and because each row depends only on the one above, a single row of storage suffices.
Which is the odd cell out that has no predecessor at all?
