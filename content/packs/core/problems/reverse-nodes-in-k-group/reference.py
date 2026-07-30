# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def reverse_groups(values, k):
    result = []
    start = 0
    while start + k <= len(values):
        group = values[start:start + k]
        group.reverse()
        result.extend(group)
        start += k
    result.extend(values[start:])
    return result
