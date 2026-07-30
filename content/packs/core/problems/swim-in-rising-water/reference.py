# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def least_time(grid):
    size = len(grid)
    if size == 0:
        return 0

    settled = set()
    pending = [(grid[0][0], 0, 0)]
    while pending:
        height, row, column = heapq.heappop(pending)
        if (row, column) in settled:
            continue
        settled.add((row, column))
        if row == size - 1 and column == size - 1:
            return height
        for next_row, next_column in (
            (row - 1, column),
            (row + 1, column),
            (row, column - 1),
            (row, column + 1),
        ):
            if not (0 <= next_row < size and 0 <= next_column < size):
                continue
            if (next_row, next_column) in settled:
                continue
            reachable_at = grid[next_row][next_column]
            if height > reachable_at:
                reachable_at = height
            heapq.heappush(pending, (reachable_at, next_row, next_column))
    return -1
