Wherever `grid` holds a `0`, set that cell's whole row and whole column to `0`. Modify `grid` in
place and return it.

A cell zeroed by this process does **not** cause further rows and columns to be zeroed — only
the zeroes present in the original grid count.

## Constraints

- `1 <= rows, columns <= 200`
- `-1000 <= grid[r][c] <= 1000`

## Follow-up

Zeroing as you scan destroys the information you are scanning for. Two passes with two marker
sets fixes that in O(rows + columns) space. Then there is a way to use the grid's own first row
and column as the markers — which cell does both jobs at once, and how do you handle it?
