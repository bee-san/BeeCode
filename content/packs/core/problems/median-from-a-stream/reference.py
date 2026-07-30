# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def run_operations(operations):
    lower = []
    upper = []
    answers = []
    for name, argument in operations:
        if name == "add":
            heapq.heappush(lower, -argument)
            heapq.heappush(upper, -heapq.heappop(lower))
            if len(upper) > len(lower):
                heapq.heappush(lower, -heapq.heappop(upper))
        else:
            if len(lower) > len(upper):
                answers.append(float(-lower[0]))
            else:
                answers.append((-lower[0] + upper[0]) / 2.0)
    return answers
