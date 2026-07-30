## The insight

The cheapest route to a cell arrives either from above or from the left, so

```text
best[r][c] = grid[r][c] + min(best[r-1][c], best[r][c-1])
```

with the first row and first column having only one predecessor each, and the top-left having none —
it is the start, and its cost is just its own.

## Collapsing to one row

`best[r][*]` depends only on `best[r-1][*]`, so one array suffices. Sweeping left to right, when you
reach column `c`:

- `best[c]` still holds the row above — that is the "from above" value.
- `best[c - 1]` has already been overwritten this row — that is the "from the left" value.

Both are exactly what is needed, from one array, because of the sweep direction. That is the same
observation as in [Paths Across a Grid](unique-paths): the direction of dependence decides whether an
in-place update is a bug or the point.

## The four cases

- **Top-left**: no predecessor; the answer is its own cost.
- **Top row**: only from the left.
- **First column**: only from above, which in the one-row form means `best[0] += grid[r][0]`.
- **Everything else**: the minimum of the two.

Collapsing these into one expression with sentinel infinities is possible and reads worse.

## Why greedy fails

Always stepping to the cheaper neighbour is not optimal: a cheap step can lead into an expensive
region. `[[1, 2], [1, 1]]` is enough — stepping right to the `2` because it looks fine costs 4,
while going down first costs 3. Every cell must be considered, which is what the table does.

## Pitfalls

**Not special-casing the first row and column.** Reading `best[-1]` wraps to the end of the array in
Python, silently giving a wrong answer rather than an error.

**Sweeping right to left.** Then `best[c - 1]` still holds the previous row and the recurrence is
wrong.

**Assuming the grid is square.** Rows and columns are independent.

**A one-by-one grid.** The answer is that single cell.

## Cost

O(rows * columns) time, O(columns) space.
