# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def blank_out(grid):
    rows = len(grid)
    columns = len(grid[0])

    zero_rows = set()
    zero_columns = set()
    for row in range(rows):
        for column in range(columns):
            if grid[row][column] == 0:
                zero_rows.add(row)
                zero_columns.add(column)

    for row in range(rows):
        for column in range(columns):
            if row in zero_rows or column in zero_columns:
                grid[row][column] = 0
    return grid
