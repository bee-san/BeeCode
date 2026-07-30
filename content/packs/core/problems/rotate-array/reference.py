# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def rotate_right(values, steps):
    size = len(values)
    shift = steps % size

    def flip(low, high):
        while low < high:
            held = values[low]
            values[low] = values[high]
            values[high] = held
            low += 1
            high -= 1

    flip(0, size - 1)
    flip(0, shift - 1)
    flip(shift, size - 1)
    return values
