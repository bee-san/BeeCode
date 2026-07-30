# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def reverse_digits(number):
    limit = 2147483647
    floor = -2147483648

    sign = 1
    if number < 0:
        sign = -1
    remaining = number * sign

    result = 0
    while remaining > 0:
        digit = remaining % 10
        remaining = remaining // 10
        if result > limit // 10:
            return 0
        if result == limit // 10 and digit > 7:
            return 0
        result = result * 10 + digit

    signed = result * sign
    if signed > limit or signed < floor:
        return 0
    return signed
