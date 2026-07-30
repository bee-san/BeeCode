# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def summarise(values):
    ranges = []
    index = 0
    while index < len(values):
        start = values[index]
        while index + 1 < len(values) and values[index + 1] == values[index] + 1:
            index += 1
        end = values[index]
        if start == end:
            ranges.append(str(start))
        else:
            ranges.append(str(start) + "->" + str(end))
        index += 1
    return ranges
