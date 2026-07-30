# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def longest_increasing_path(grid):
    if not grid or not grid[0]:
        return 0

    rows = len(grid)
    columns = len(grid[0])
    longest = [[0] * columns for _ in range(rows)]

    def walk(row, column):
        if longest[row][column]:
            return longest[row][column]
        best = 1
        for step_row, step_column in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            next_row = row + step_row
            next_column = column + step_column
            if 0 <= next_row < rows and 0 <= next_column < columns:
                if grid[next_row][next_column] > grid[row][column]:
                    found = 1 + walk(next_row, next_column)
                    if found > best:
                        best = found
        longest[row][column] = best
        return best

    answer = 0
    for row in range(rows):
        for column in range(columns):
            found = walk(row, column)
            if found > answer:
                answer = found
    return answer
