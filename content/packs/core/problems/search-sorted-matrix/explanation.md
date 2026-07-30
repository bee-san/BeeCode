## The insight

The two stated properties are exactly the conditions under which reading the matrix
row by row yields a sorted list. So pretend it *is* a sorted list of length
`rows * columns` and binary search that, converting each flat index on demand:

```python
row = index // columns
column = index % columns
```

```python
def matrix_contains(grid, target):
    if not grid or not grid[0]:
        return False
    columns = len(grid[0])
    low, high = 0, len(grid) * columns - 1
    while low <= high:
        middle = (low + high) // 2
        value = grid[middle // columns][middle % columns]
        if value == target:
            return True
        if value < target:
            low = middle + 1
        else:
            high = middle - 1
    return False
```

No flattened copy is built. The division is the whole trick, and it is the same idea
as indexing a heap stored in an array.

## Pitfalls

**Dividing by the wrong dimension.** It is the number of *columns*, i.e. the row
length. Using the number of rows works on square matrices and fails everywhere
else — which is why this Problem's suite includes a single-row and a single-column
case.

**The empty matrix.** `[]` and `[[]]` are both possible and both must return
`False` before `len(grid[0])` is evaluated.

**Two-stage searching.** Find the row, then search it: also correct, and a fine
answer at O(log rows + log columns). It just needs care about which row could
contain the target.

The staircase walk from the top-right corner is O(rows + columns) and solves the
weaker variant where rows are sorted but do not chain together. Do not reach for it
here; the stronger precondition permits the faster search.

## Cost

O(log(rows * columns)) time, O(1) space.
