## The insight

Breadth-first search leaves the start and expands outward one ring at a time, so it
reaches every cell in ascending order of distance. The first arrival at the end is
therefore the shortest, and you can return immediately.

```python
pending = deque([(0, 0, 1)])
seen[0][0] = True
while pending:
    row, column, length = pending.popleft()
    if row == rows - 1 and column == columns - 1:
        return length
    for step_row, step_column in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ...
        if in_bounds and not seen[nr][nc] and grid[nr][nc] == 0:
            seen[nr][nc] = True          # mark on ENQUEUE
            pending.append((nr, nc, length + 1))
```

## Mark on enqueue, not on dequeue

This is the answer to the follow-up and the difference between a linear and a
disastrous BFS. Several neighbours can queue the same cell before any of them is
processed. Marking only on dequeue lets that cell enter the queue many times over, and
on a 300×300 open grid the queue balloons.

Marking as you push costs nothing and guarantees each cell is enqueued exactly once.
And because BFS arrives in distance order, the first push already carries the shortest
distance — nothing is lost by refusing later ones.

**`popleft`, not `pop`.** A deque popped from the right is a stack, and this becomes
depth-first search: it still finds a path, and the length it reports is whatever
wandering route it happened to take.

## The details

**Check the corners first.** A blocked start means no path — and `seen[0][0] = True`
followed by enqueueing it would otherwise walk straight out of a blocked cell. A blocked
end is unreachable, and the search would explore the whole grid to discover it.

**Bounds before contents.** Testing `grid[next_row][next_column]` before checking the
range lets Python's negative indexing wrap `-1` around to the far edge, which quietly
connects the top row to the bottom one.

**Count cells, not steps.** The start enqueues with length `1`, so a 1×1 open grid
answers `1`. Starting at `0` reports edges instead of cells and is off by one
everywhere.

## Cost

O(rows × columns) time and space. Each cell is enqueued at most once and does constant
work.
