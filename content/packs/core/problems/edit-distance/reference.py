# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def edit_distance(source, target):
    previous = list(range(len(target) + 1))
    for index in range(1, len(source) + 1):
        current = [index] + [0] * len(target)
        for other in range(1, len(target) + 1):
            if source[index - 1] == target[other - 1]:
                current[other] = previous[other - 1]
            else:
                best = previous[other - 1]
                if previous[other] < best:
                    best = previous[other]
                if current[other - 1] < best:
                    best = current[other - 1]
                current[other] = best + 1
        previous = current
    return previous[len(target)]
