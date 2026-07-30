# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_paths(rows, columns):
    if rows <= 0 or columns <= 0:
        return 0

    counts = [1] * columns
    for _ in range(1, rows):
        for column in range(1, columns):
            counts[column] += counts[column - 1]
    return counts[columns - 1]
