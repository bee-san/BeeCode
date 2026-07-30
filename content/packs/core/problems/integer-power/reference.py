# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def power(base, exponent):
    if exponent < 0:
        return 1.0 / power(base, -exponent)

    result = 1.0
    value = float(base)
    remaining = exponent
    while remaining > 0:
        if remaining % 2 == 1:
            result *= value
        value *= value
        remaining //= 2
    return result
