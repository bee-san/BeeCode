# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def shortest_covering(intervals, queries):
    ordered = sorted(intervals, key=lambda pair: pair[0])
    order = sorted(range(len(queries)), key=lambda position: queries[position])

    answers = [-1] * len(queries)
    available = []
    index = 0
    for position in order:
        value = queries[position]
        while index < len(ordered) and ordered[index][0] <= value:
            start = ordered[index][0]
            end = ordered[index][1]
            heapq.heappush(available, (end - start + 1, end))
            index += 1
        while available and available[0][1] < value:
            heapq.heappop(available)
        if available:
            answers[position] = available[0][0]
    return answers
