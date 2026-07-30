# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def max_area(grid):
    if not grid or not grid[0]:
        return 0

    rows = len(grid)
    columns = len(grid[0])

    def flood(row, column):
        if row < 0 or row >= rows or column < 0 or column >= columns:
            return 0
        if grid[row][column] != 1:
            return 0
        grid[row][column] = 0
        return (
            1
            + flood(row - 1, column)
            + flood(row + 1, column)
            + flood(row, column - 1)
            + flood(row, column + 1)
        )

    largest = 0
    for row in range(rows):
        for column in range(columns):
            if grid[row][column] == 1:
                area = flood(row, column)
                if area > largest:
                    largest = area
    return largest
