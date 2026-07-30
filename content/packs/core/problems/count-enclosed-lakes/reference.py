# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_enclosed(grid):
    rows = len(grid)
    columns = len(grid[0])
    seen = set()

    def flood(start_row, start_column):
        stack = [(start_row, start_column)]
        while stack:
            row, column = stack.pop()
            if row < 0 or row >= rows or column < 0 or column >= columns:
                continue
            if grid[row][column] != 0 or (row, column) in seen:
                continue
            seen.add((row, column))
            stack.append((row + 1, column))
            stack.append((row - 1, column))
            stack.append((row, column + 1))
            stack.append((row, column - 1))

    for row in range(rows):
        flood(row, 0)
        flood(row, columns - 1)
    for column in range(columns):
        flood(0, column)
        flood(rows - 1, column)

    enclosed = 0
    for row in range(rows):
        for column in range(columns):
            if grid[row][column] == 0 and (row, column) not in seen:
                enclosed += 1
                flood(row, column)
    return enclosed
