# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import collections


def least_interval(tasks, cooldown):
    counts = collections.Counter(tasks)
    highest = max(counts.values())
    at_the_highest = 0
    for count in counts.values():
        if count == highest:
            at_the_highest += 1
    framed = (highest - 1) * (cooldown + 1) + at_the_highest
    if framed > len(tasks):
        return framed
    return len(tasks)
