# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def climb_stairs(n):
    # ways_two_below is the count for step i - 2, ways_one_below for step i - 1.
    ways_two_below, ways_one_below = 1, 1
    for _ in range(n):
        ways_two_below, ways_one_below = ways_one_below, ways_one_below + ways_two_below
    return ways_two_below
