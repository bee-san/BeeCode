# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def minimum_speed(piles, hours):
    def hours_needed(speed):
        total = 0
        for pile in piles:
            total += -(-pile // speed)
        return total

    low, high = 1, max(piles)
    while low < high:
        middle = (low + high) // 2
        if hours_needed(middle) <= hours:
            high = middle
        else:
            low = middle + 1
    return low
