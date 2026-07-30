A robot starts at the top-left cell of a `rows` by `columns` grid and must reach the
bottom-right cell. It may move only **right** or **down**.

Return how many distinct paths it can take.

## Constraints

- `1 <= rows, columns <= 100`
- The answer fits in a 32-bit signed integer.

## Follow-up

The number of paths to a cell is the number to the cell above plus the number to the cell on
its left, since those are the only two ways in. That gives an O(rows * columns) table.

There is also a closed form: every path is a fixed-length sequence of moves, of which a fixed
number are "down". How many such sequences are there?
