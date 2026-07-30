Rotate a square `grid` by 90 degrees clockwise and return it.

Rotate the grid **in place**: modify the given lists rather than building a new grid. The return
value is the same object you were handed.

## Constraints

- `1 <= len(grid) <= 20`
- `grid` is square.
- `-1000 <= grid[r][c] <= 1000`

## Follow-up

Building a fresh grid is easy and is not what is asked. In place, two reflections do the job —
and which two, in which order, is the whole trick. What does transposing followed by reversing
each row give you?
