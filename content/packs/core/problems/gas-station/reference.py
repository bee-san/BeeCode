# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def starting_station(gas, cost):
    total = 0
    running = 0
    start = 0
    for index in range(len(gas)):
        gained = gas[index] - cost[index]
        total += gained
        running += gained
        if running < 0:
            start = index + 1
            running = 0
    if total < 0:
        return -1
    return start
