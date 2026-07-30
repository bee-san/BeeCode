# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def reverse_bits(number):
    result = 0
    for _ in range(32):
        result = (result << 1) | (number & 1)
        number >>= 1
    return result
