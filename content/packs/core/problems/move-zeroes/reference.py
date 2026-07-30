# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def move_zeroes(values):
    write = 0
    for read in range(len(values)):
        if values[read] != 0:
            held = values[write]
            values[write] = values[read]
            values[read] = held
            write += 1
    return values
