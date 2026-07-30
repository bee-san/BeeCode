`grid` holds `0` for water and `1` for land. Count the regions of water that are **fully enclosed** —
those from which no cell touches the border of the grid.

Water cells connect horizontally and vertically only.

## Constraints

- `1 <= rows, columns <= 200`
- Each cell is `0` or `1`.

## Follow-up

Testing each water region for whether it reaches the border works and repeats effort. The cheaper
framing inverts it: flood from the border first, then whatever water is left must be enclosed. Which
cells do you seed the flood from?
