# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def minutes_until_spoiled(grid):
    if not grid or not grid[0]:
        return 0

    rows = len(grid)
    columns = len(grid[0])

    frontier = []
    fresh = 0
    for row in range(rows):
        for column in range(columns):
            if grid[row][column] == 2:
                frontier.append((row, column))
            elif grid[row][column] == 1:
                fresh += 1

    if fresh == 0:
        return 0

    minutes = 0
    while frontier and fresh > 0:
        following = []
        for row, column in frontier:
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
                grid[next_row][next_column] = 2
                fresh -= 1
                following.append((next_row, next_column))
        frontier = following
        minutes += 1

    if fresh > 0:
        return -1
    return minutes
