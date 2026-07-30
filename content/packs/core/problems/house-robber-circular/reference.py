# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def best_circular_total(values):
    if not values:
        return 0
    if len(values) == 1:
        return values[0]

    def linear(section):
        skipping = 0
        taking = 0
        for value in section:
            candidate = skipping + value
            if taking > candidate:
                candidate = taking
            skipping = taking
            taking = candidate
        return taking

    without_last = linear(values[:-1])
    without_first = linear(values[1:])
    if without_last > without_first:
        return without_last
    return without_first
