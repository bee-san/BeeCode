# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def reverse_values(values):
    reversed_values = list(values)
    low, high = 0, len(reversed_values) - 1
    while low < high:
        reversed_values[low], reversed_values[high] = (
            reversed_values[high],
            reversed_values[low],
        )
        low += 1
        high -= 1
    return reversed_values
