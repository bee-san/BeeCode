# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_palindrome_chain(values):
    low = 0
    high = len(values) - 1
    while low < high:
        if values[low] != values[high]:
            return False
        low += 1
        high -= 1
    return True
