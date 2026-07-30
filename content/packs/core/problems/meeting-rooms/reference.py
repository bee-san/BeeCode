# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def can_use_one_room(meetings):
    ordered = sorted(meetings, key=lambda pair: pair[0])
    for index in range(1, len(ordered)):
        if ordered[index][0] < ordered[index - 1][1]:
            return False
    return True
