# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def value_span(values, target):
    def lower_bound(wanted):
        low = 0
        high = len(values)
        while low < high:
            middle = (low + high) // 2
            if values[middle] < wanted:
                low = middle + 1
            else:
                high = middle
        return low

    first = lower_bound(target)
    if first == len(values) or values[first] != target:
        return [-1, -1]
    last = lower_bound(target + 1) - 1
    return [first, last]
