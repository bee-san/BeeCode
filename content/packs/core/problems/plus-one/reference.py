# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def plus_one(digits):
    result = list(digits)
    index = len(result) - 1
    while index >= 0:
        if result[index] < 9:
            result[index] += 1
            return result
        result[index] = 0
        index -= 1
    return [1] + result
