# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def bit_counts(limit):
    counts = [0] * (limit + 1)
    for value in range(1, limit + 1):
        counts[value] = counts[value >> 1] + (value & 1)
    return counts
