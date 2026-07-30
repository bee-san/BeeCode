# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def min_cost(cost):
    if len(cost) < 2:
        return 0

    two_below = 0
    one_below = 0
    for index in range(2, len(cost) + 1):
        stepping_one = one_below + cost[index - 1]
        stepping_two = two_below + cost[index - 2]
        if stepping_two < stepping_one:
            cheapest = stepping_two
        else:
            cheapest = stepping_one
        two_below = one_below
        one_below = cheapest
    return one_below
