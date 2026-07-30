## The insight

Do not track a direction and turn when you hit something. Track **four boundaries** —
`top`, `bottom`, `left`, `right` — and peel one complete ring per iteration, moving the
boundary inward after each of the four passes.

```python
while top <= bottom and left <= right:
    for column in range(left, right + 1):        # top row, rightward
        order.append(matrix[top][column])
    top += 1
    for row in range(top, bottom + 1):           # right column, downward
        order.append(matrix[row][right])
    right -= 1
    if top <= bottom:                            # bottom row, leftward
        for column in range(right, left - 1, -1):
            order.append(matrix[bottom][column])
        bottom -= 1
    if left <= right:                            # left column, upward
        for row in range(bottom, top - 1, -1):
            order.append(matrix[row][left])
        left += 1
```

## The two guards are the whole problem

Notice that the first two passes need no guard but the last two do. That asymmetry is
not arbitrary.

The loop condition held when we entered, so the top row and right column definitely
exist. But `top += 1` and `right -= 1` happen *before* the bottom and left passes. If
the region was a single row, `top` has now passed `bottom`, and walking the "bottom
row" would re-emit the row you just finished, backwards. Same for a single column and
the left pass.

So: `if top <= bottom` before the bottom pass, `if left <= right` before the left pass.
Without them, `[[1, 2, 3]]` returns `[1, 2, 3, 2, 1]`.

This is why the tests include a single row, a single column, and a 1×1 matrix — those
are the shapes that expose it, and a square-only test set never will.

## Cost

O(rows × columns) time — each cell is appended exactly once — and O(1) extra space
beyond the output.
