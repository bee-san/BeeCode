# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def fewest_jumps(jumps):
    taken = 0
    current_end = 0
    furthest = 0
    for index in range(len(jumps) - 1):
        reach = index + jumps[index]
        if reach > furthest:
            furthest = reach
        if index == current_end:
            taken += 1
            current_end = furthest
    return taken
