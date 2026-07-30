## The insight

A clockwise quarter turn is a transpose followed by a reversal of each row.

**Transpose** reflects across the main diagonal, sending `(r, c)` to `(c, r)`. **Reversing each
row** reflects left-to-right, sending `(r, c)` to `(r, size-1-c)`. Composed, `(r, c)` ends at
`(c, size-1-r)` — exactly where a clockwise rotation puts it.

Two reflections across intersecting lines always compose to a rotation, by twice the angle
between them. The diagonal and the vertical centre line meet at 45 degrees, hence 90.

## Why the transpose loop starts at `row + 1`

Swapping `(r, c)` with `(c, r)` for *every* pair visits each pair twice and undoes itself,
leaving the grid unchanged. `range(row + 1, size)` touches only the upper triangle, so each pair
is swapped once. The diagonal itself is fixed and needs no work.

This is the bug that produces a suspiciously clean "the transpose did nothing" result.

## Order matters

Transpose-then-reverse-rows gives clockwise. Reverse-rows-then-transpose gives
anticlockwise — the same two reflections in the other order, which is the inverse rotation.
Reflections do not commute, and this is the cheapest place to see it.

## Returning the same object

The statement asks for an in-place rotation, and returning `grid` makes the result observable
through the JSON test protocol. Building a new grid and returning that would pass the tests while
missing the point, so the statement says which is wanted rather than leaving the tests to imply
it.

## Pitfalls

**Transposing the full square.** A no-op.

**Reversing the columns instead of the rows.** Gives a different transformation.

**Building a new grid.** Correct output, wrong exercise.

**A 1 by 1 grid.** Unchanged; both loops run zero useful iterations.

## Cost

O(n^2) time — every cell must move — and O(1) extra space.
