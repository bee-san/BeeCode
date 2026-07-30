# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_paths_around(grid):
    columns = len(grid[0])
    counts = [0] * columns
    if grid[0][0] == 1:
        return 0
    counts[0] = 1

    for row in grid:
        for column in range(columns):
            if row[column] == 1:
                counts[column] = 0
            elif column > 0:
                counts[column] += counts[column - 1]
    return counts[columns - 1]
