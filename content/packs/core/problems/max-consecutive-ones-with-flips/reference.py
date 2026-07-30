# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def longest_run(bits, budget):
    best = 0
    zeroes = 0
    low = 0
    for high in range(len(bits)):
        if bits[high] == 0:
            zeroes += 1
        while zeroes > budget:
            if bits[low] == 0:
                zeroes -= 1
            low += 1
        span = high - low + 1
        if span > best:
            best = span
    return best
