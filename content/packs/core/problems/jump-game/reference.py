# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def can_reach_end(jumps):
    furthest = 0
    for index in range(len(jumps)):
        if index > furthest:
            return False
        reach = index + jumps[index]
        if reach > furthest:
            furthest = reach
    return True
