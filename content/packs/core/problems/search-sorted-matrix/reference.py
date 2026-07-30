# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def matrix_contains(grid, target):
    if not grid or not grid[0]:
        return False
    columns = len(grid[0])
    low = 0
    high = len(grid) * columns - 1
    while low <= high:
        middle = (low + high) // 2
        value = grid[middle // columns][middle % columns]
        if value == target:
            return True
        if value < target:
            low = middle + 1
        else:
            high = middle - 1
    return False
