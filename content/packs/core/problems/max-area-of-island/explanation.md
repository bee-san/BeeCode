## The insight

Scan for land. Each time you find some, flood it and have the flood **return its own
size**:

```python
def flood(row, column):
    if out_of_bounds(row, column) or grid[row][column] != 1:
        return 0
    grid[row][column] = 0                     # sink it: counted, never again
    return (1
            + flood(row - 1, column) + flood(row + 1, column)
            + flood(row, column - 1) + flood(row, column + 1))
```

The base cases returning `0` do double duty — they stop the recursion at edges and at
water, and they contribute nothing to the sum, which is exactly what a non-cell should
contribute.

## Why sinking beats a visited set

Writing `0` over the cell before recursing is O(1) space and makes "already counted" and
"is water" the same condition. It mutates the input, which the constraints allow. If you
cannot mutate, a `visited` set of `(row, column)` is the equivalent — but it must be
marked *before* the recursive calls either way, or two neighbours each recurse into the
other and the same cell is counted twice.

## Total work is linear

Because a cell is sunk the moment it is visited, the outer double loop finds each cell
at most once as an unflooded start, and each cell is flooded exactly once. Total work is
O(rows * columns) — not the number of cells times the number of islands, which is the
trap the [Number of Islands](number-of-islands) follow-up asks about.

## Pitfalls

**Sinking after the recursion.** Infinite recursion: the cell's neighbour recurses
straight back into it.

**Indexing before the bounds check.** `grid[-1][0]` is a real cell in Python, so a
missing lower-bound test wraps around to the last row and merges islands that do not
touch.

**Tracking the maximum with `max(largest, area)` but never assigning it.** A
surprisingly common slip; the expression is pure.

**Returning `1` rather than `0` for the empty grid.** No land is area zero.

## Cost

O(rows * columns) time. Space is O(rows * columns) in the worst case for the recursion
stack — a snake-shaped island — which is why an explicit stack is safer on large grids.
