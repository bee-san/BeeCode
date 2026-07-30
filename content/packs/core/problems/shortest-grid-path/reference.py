# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def shortest_path(grid):
    from collections import deque

    rows, columns = len(grid), len(grid[0])
    if grid[0][0] == 1 or grid[rows - 1][columns - 1] == 1:
        return -1
    seen = [[False] * columns for _ in range(rows)]
    seen[0][0] = True
    pending = deque([(0, 0, 1)])
    while pending:
        row, column, length = pending.popleft()
        if row == rows - 1 and column == columns - 1:
            return length
        for step_row, step_column in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            next_row = row + step_row
            next_column = column + step_column
            if 0 <= next_row < rows and 0 <= next_column < columns:
                if not seen[next_row][next_column]:
                    if grid[next_row][next_column] == 0:
                        seen[next_row][next_column] = True
                        pending.append((next_row, next_column, length + 1))
    return -1
