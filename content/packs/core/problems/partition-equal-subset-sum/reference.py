# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def can_partition(values):
    total = 0
    for value in values:
        total += value
    if total % 2 != 0:
        return False

    half = total // 2
    reachable = [False] * (half + 1)
    reachable[0] = True

    for value in values:
        for target in range(half, value - 1, -1):
            if reachable[target - value]:
                reachable[target] = True
    return reachable[half]
