## The insight

Same recurrence as [Paths Across a Grid](unique-paths) — routes to a cell are routes from above plus
routes from the left — with one addition: **a blocked cell has zero routes to it**, regardless of its
neighbours.

```python
counts = [0] * columns
counts[0] = 1 if grid[0][0] == 0 else 0
for row in grid:
    for column in range(columns):
        if row[column] == 1:
            counts[column] = 0
        elif column > 0:
            counts[column] += counts[column - 1]
return counts[-1]
```

## How a zero propagates

Setting a blocked cell to `0` is not merely bookkeeping — it is what makes obstacles work. The zero
flows onwards through the additions: cells to its right lose the contribution from the left, and
cells below lose the contribution from above. A block early in the first row zeroes the entire rest
of that row, because the only route along the top edge is through it.

That is the whole mechanism, and it means no explicit "is this cell reachable" test is ever needed.

## Why the top-left needs handling before the loop

`counts[0] = 1` seeds the start. If the start is itself blocked, the answer is `0` — and the loop
would set `counts[0] = 0` on the first row anyway, so the explicit early return is belt and braces.
The bottom-right needs no special handling: if it is blocked, the last thing the loop does is zero
it.

## Why the first column works without a case

The in-place `counts[column] += counts[column - 1]` is skipped for `column == 0`, so `counts[0]`
carries down the rows unchanged — which is correct, since there is exactly one route down the left
edge, until a block zeroes it and it stays zero.

## Pitfalls

**Seeding `counts[0] = 1` without checking the start.** Reports routes through a blocked start.

**Skipping blocked cells rather than zeroing them.** They keep a stale count from the row above.

**Using the binomial formula with a correction.** Inclusion-exclusion over obstacle subsets is
exponential; the table is linear in the grid.

**A grid with a blocked destination.** The answer is `0`, and the loop produces it.

## Cost

O(rows * columns) time, O(columns) space.
