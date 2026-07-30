## The insight

Three independent rules, one pass. Keep 27 sets — one per row, one per column,
one per box — and as you walk the grid, try to insert each digit into its three
sets. A digit already present in any of them is a violation.

The only piece of arithmetic worth remembering is the box index:

```python
box = (row // 3) * 3 + column // 3
```

`row // 3` says which band of three rows you are in, `column // 3` which stack of
three columns. Multiplying the band by 3 numbers the boxes 0-8 in reading order.

## The scan

```python
def is_valid_sudoku(grid):
    rows = [set() for _ in range(9)]
    columns = [set() for _ in range(9)]
    boxes = [set() for _ in range(9)]
    for row in range(9):
        for column in range(9):
            digit = grid[row][column]
            if digit == ".":
                continue
            box = (row // 3) * 3 + column // 3
            if digit in rows[row] or digit in columns[column] or digit in boxes[box]:
                return False
            rows[row].add(digit)
            columns[column].add(digit)
            boxes[box].add(digit)
    return True
```

## Pitfalls

**Counting `"."`.** Empty cells repeat constantly and mean nothing. Skip them
before any set work.

**`[set()] * 9`.** That is nine references to *one* set, so every row shares a
single tally and almost any board is rejected. Use a comprehension.

**Checking rows and columns only.** `BOX_ONLY_CLASH` in this Problem's suite has
two 3s in one box but in different rows and different columns. Rule-of-two
solutions pass it happily.

**Rebuilding the sets three times.** Correct, but it walks the grid three times
for no gain; the single pass is no harder to write.

## Cost

O(81) time and O(81) space — constant, since the board size is fixed. In terms of
side length `n` it is O(n^2) time and space, which is optimal: you must at least
read every cell.
