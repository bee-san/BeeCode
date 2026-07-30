## The insight

A grid is a graph wearing a disguise. Each land cell is a vertex, and there is an edge
between orthogonally adjacent land cells. "How many islands" is then the standard
question: **how many connected components?**

The standard answer: scan every cell. When you find land you have not seen, that is a
new component — increment the count, then flood the entire component so none of its
other cells can start a second count.

```python
for start_row in range(rows):
    for start_column in range(columns):
        if grid[start_row][start_column] != 1 or seen[start_row][start_column]:
            continue
        islands += 1
        flood_from(start_row, start_column)
```

Whether `flood_from` is depth-first or breadth-first makes no difference to the
answer. The reference uses an explicit stack because recursion on a 300×300 grid of
solid land recurses 90,000 deep and blows Python's stack.

## Where this goes wrong

**Mark as seen when you push, not when you pop.** If you only mark on pop, a cell
reachable from two neighbours is pushed twice, and in the worst case the frontier
grows quadratically. Marking on push keeps every cell in the stack at most once.

**Check bounds before indexing.** Python's negative indexing is the trap here: at row
`0`, `grid[-1]` is the *last* row, so a missing `0 <= next_row` test silently wraps the
top edge to the bottom and merges two unrelated islands.

**Four neighbours, not eight.** Diagonal connectivity is a different problem, and
including corners merges islands the statement says are separate.

## Cost

O(rows × columns) time: each cell is examined a constant number of times. O(rows ×
columns) extra space for the visited grid — or O(1) extra if you are allowed to
overwrite visited land with `0`, which the constraints permit.
