## The insight

You can only arrive at a cell from above or from the left, and those two routes share no
paths, so:

```text
paths[r][c] = paths[r-1][c] + paths[r][c-1]
```

The top row and left column are all `1` — there is exactly one way to walk straight along an
edge — and that is the base case the whole table grows from.

## Collapsing to one row

Filling row by row, `paths[r-1][c]` is the value currently in the row buffer at `c`, and
`paths[r][c-1]` is the value just written at `c-1`. So a single array suffices:

```python
counts = [1] * columns
for _ in range(1, rows):
    for column in range(1, columns):
        counts[column] += counts[column - 1]
return counts[columns - 1]
```

`counts[column] += counts[column - 1]` reads the already-updated left neighbour and the
not-yet-updated value above, which is exactly the recurrence. Here the in-place update is
*required*, unlike the descending loop in
[Split Into Two Equal Halves](partition-equal-subset-sum) where in-place reuse was the bug —
worth noticing that the direction of dependence is what decides which is which.

## The closed form

Every path makes exactly `rows - 1` down moves and `columns - 1` right moves, in some order,
for `rows + columns - 2` moves total. A path is determined by choosing which of those moves
are down:

```text
paths = C(rows + columns - 2, rows - 1)
```

O(min(rows, columns)) time and O(1) space, and no table at all. It is the better answer when
the grid has no obstacles — the moment obstacles appear, the counting argument breaks and the
table is the only option.

## Pitfalls

**Initialising the whole table to `0`.** Nothing accumulates. The edges must be `1`.

**Iterating from index `0`.** `counts[-1]` wraps to the far end of the row in Python.

**A single row or column.** The answer is `1`; the loops simply do not execute.

**Computing the binomial coefficient with factorials of 200.** Correct in Python but wasteful;
multiply and divide incrementally.

## Cost

O(rows * columns) time and O(columns) space for the table; O(min(rows, columns)) time and
O(1) space for the closed form.
