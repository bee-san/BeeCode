# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def merge_all(lists):
    frontier = []
    for index, values in enumerate(lists):
        if values:
            heapq.heappush(frontier, (values[0], index, 0))

    merged = []
    while frontier:
        value, index, position = heapq.heappop(frontier)
        merged.append(value)
        position += 1
        if position < len(lists[index]):
            heapq.heappush(frontier, (lists[index][position], index, position))
    return merged
