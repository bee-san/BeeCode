# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_happy(number):
    def step(value):
        total = 0
        while value > 0:
            digit = value % 10
            total += digit * digit
            value //= 10
        return total

    slow = number
    fast = step(number)
    while fast != 1 and slow != fast:
        slow = step(slow)
        fast = step(step(fast))
    return fast == 1
