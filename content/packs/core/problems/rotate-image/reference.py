# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def rotate(grid):
    size = len(grid)
    for row in range(size):
        for column in range(row + 1, size):
            held = grid[row][column]
            grid[row][column] = grid[column][row]
            grid[column][row] = held
    for row in range(size):
        left = 0
        right = size - 1
        while left < right:
            held = grid[row][left]
            grid[row][left] = grid[row][right]
            grid[row][right] = held
            left += 1
            right -= 1
    return grid
