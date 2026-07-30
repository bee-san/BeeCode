# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def insert_interval(intervals, fresh):
    result = []
    index = 0
    start = fresh[0]
    end = fresh[1]

    while index < len(intervals) and intervals[index][1] < start:
        result.append([intervals[index][0], intervals[index][1]])
        index += 1

    while index < len(intervals) and intervals[index][0] <= end:
        if intervals[index][0] < start:
            start = intervals[index][0]
        if intervals[index][1] > end:
            end = intervals[index][1]
        index += 1
    result.append([start, end])

    while index < len(intervals):
        result.append([intervals[index][0], intervals[index][1]])
        index += 1

    return result
