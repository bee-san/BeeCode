# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def longest_common(first, second):
    previous = [0] * (len(second) + 1)
    for index in range(1, len(first) + 1):
        current = [0] * (len(second) + 1)
        for other in range(1, len(second) + 1):
            if first[index - 1] == second[other - 1]:
                current[other] = previous[other - 1] + 1
            elif previous[other] >= current[other - 1]:
                current[other] = previous[other]
            else:
                current[other] = current[other - 1]
        previous = current
    return previous[len(second)]
