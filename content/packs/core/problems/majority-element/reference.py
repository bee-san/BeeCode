# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def majority(values):
    candidate = None
    tally = 0
    for value in values:
        if tally == 0:
            candidate = value
            tally = 1
        elif value == candidate:
            tally += 1
        else:
            tally -= 1
    return candidate
