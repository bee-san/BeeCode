# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def drop_repeats(values):
    kept = []
    for value in values:
        if not kept or kept[-1] != value:
            kept.append(value)
    return kept
