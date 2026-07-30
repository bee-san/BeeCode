# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_islands(grid):
    if not grid or not grid[0]:
        return 0
    rows, columns = len(grid), len(grid[0])
    seen = [[False] * columns for _ in range(rows)]
    islands = 0

    for start_row in range(rows):
        for start_column in range(columns):
            if grid[start_row][start_column] != 1 or seen[start_row][start_column]:
                continue
            islands += 1
            stack = [(start_row, start_column)]
            seen[start_row][start_column] = True
            while stack:
                row, column = stack.pop()
                for next_row, next_column in (
                    (row - 1, column),
                    (row + 1, column),
                    (row, column - 1),
                    (row, column + 1),
                ):
                    if not (0 <= next_row < rows and 0 <= next_column < columns):
                        continue
                    if grid[next_row][next_column] != 1:
                        continue
                    if seen[next_row][next_column]:
                        continue
                    seen[next_row][next_column] = True
                    stack.append((next_row, next_column))
    return islands
