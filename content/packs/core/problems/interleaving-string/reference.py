# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_interleaving(first, second, whole):
    if len(first) + len(second) != len(whole):
        return False

    reachable = [False] * (len(second) + 1)
    reachable[0] = True
    for other in range(1, len(second) + 1):
        reachable[other] = reachable[other - 1] and second[other - 1] == whole[other - 1]

    for index in range(1, len(first) + 1):
        reachable[0] = reachable[0] and first[index - 1] == whole[index - 1]
        for other in range(1, len(second) + 1):
            from_first = reachable[other] and first[index - 1] == whole[index + other - 1]
            from_second = (
                reachable[other - 1] and second[other - 1] == whole[index + other - 1]
            )
            reachable[other] = from_first or from_second

    return reachable[len(second)]
