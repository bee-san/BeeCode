# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def run_operations(k, initial, additions):
    smallest_of_the_largest = []
    for value in initial:
        heapq.heappush(smallest_of_the_largest, value)
        if len(smallest_of_the_largest) > k:
            heapq.heappop(smallest_of_the_largest)

    answers = []
    for value in additions:
        heapq.heappush(smallest_of_the_largest, value)
        if len(smallest_of_the_largest) > k:
            heapq.heappop(smallest_of_the_largest)
        answers.append(smallest_of_the_largest[0])
    return answers
