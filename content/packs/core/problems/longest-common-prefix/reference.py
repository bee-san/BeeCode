# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def longest_common_prefix(strs):
    if not strs:
        return ""

    shortest = min(strs, key=len)
    for index, character in enumerate(shortest):
        for word in strs:
            if word[index] != character:
                return shortest[:index]
    return shortest
