# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_fleets(target, positions, speeds):
    cars = sorted(zip(positions, speeds), reverse=True)
    fleets = 0
    slowest_ahead = 0.0
    for position, speed in cars:
        arrival = (target - position) / speed
        if arrival > slowest_ahead:
            fleets += 1
            slowest_ahead = arrival
    return fleets
