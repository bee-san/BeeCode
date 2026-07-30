# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def remove_nth_from_end(values, n):
    lead = n
    trail = 0
    while lead < len(values):
        lead += 1
        trail += 1
    return values[:trail] + values[trail + 1:]
