# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def max_coins(values):
    padded = [1] + [value for value in values] + [1]
    size = len(padded)
    best = [[0] * size for _ in range(size)]

    for width in range(2, size):
        for left in range(0, size - width):
            right = left + width
            for last in range(left + 1, right):
                gained = (
                    padded[left] * padded[last] * padded[right]
                    + best[left][last]
                    + best[last][right]
                )
                if gained > best[left][right]:
                    best[left][right] = gained

    return best[0][size - 1]
