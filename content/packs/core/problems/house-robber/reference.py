# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def best_total(values):
    skipping = 0
    taking = 0
    for value in values:
        candidate = skipping + value
        if taking > candidate:
            candidate = taking
        skipping = taking
        taking = candidate
    return taking
