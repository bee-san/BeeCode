`grid` holds `0` for a free cell and `1` for a blocked one. Count the routes from the top-left to the
bottom-right that move only **right or down** and never enter a blocked cell.

If either endpoint is blocked, there are no routes.

## Constraints

- `1 <= rows, columns <= 100`
- Each cell is `0` or `1`.

## Follow-up

Without obstacles this is a binomial coefficient. With them the closed form is gone and the table is
the only way. A blocked cell contributes what to its neighbours — and what does that do to the whole
row and column beyond it?
