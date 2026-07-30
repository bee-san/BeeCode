# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def add(first, second):
    mask = 0xFFFFFFFF
    limit = 0x7FFFFFFF

    a = first & mask
    b = second & mask
    while b != 0:
        carry = (a & b) << 1
        a = (a ^ b) & mask
        b = carry & mask
    if a <= limit:
        return a
    return ~(a ^ mask) & mask | ~mask
