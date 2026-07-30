## The insight

Two passes. The first records *which* rows and columns contain a zero; the second applies them.

```python
zero_rows, zero_columns = set(), set()
for row in range(rows):
    for column in range(columns):
        if grid[row][column] == 0:
            zero_rows.add(row)
            zero_columns.add(column)
for row in range(rows):
    for column in range(columns):
        if row in zero_rows or column in zero_columns:
            grid[row][column] = 0
return grid
```

## Why one pass cannot work

Zeroing a row during the scan creates zeroes that the rest of the scan cannot distinguish from
original ones, and they cascade. `[[1, 0], [1, 1]]` should become `[[0, 0], [1, 0]]`, but zeroing
in place puts a fresh zero at `(0, 0)`, which the rest of the scan then reads as original and uses
to blank column `0` too — and from there the whole grid goes. Separating "find" from "apply" is
the entire fix, and it is why the statement is explicit that only original zeroes count.

## The O(1) space version

Use row `0` and column `0` as the marker arrays: to mark row `r`, set `grid[r][0] = 0`; to mark
column `c`, set `grid[0][c] = 0`. Then apply from the inside out.

The complication is `grid[0][0]`, which would have to mark both row `0` and column `0`. Keep one
extra flag for one of them — say `first_column_has_zero` — decided before any marking, and apply
row `0` and column `0` last, after the interior is done. Applying them first would overwrite the
markers still being read.

That is genuinely O(1) extra space, and it is the version worth having thought through even if
the two-set form is what you write under time pressure.

## Pitfalls

**Zeroing during the first pass.** Cascades.

**Applying the first row or column before the interior.** Destroys the markers.

**Assuming the grid is square.** Rows and columns are independent here.

**Recording only positions, not rows and columns.** A single zero blanks a whole cross, not a
cell.

## Cost

O(rows * columns) time; O(rows + columns) space for the sets, or O(1) with the in-grid markers.
