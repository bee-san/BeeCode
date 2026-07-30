# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def kth_largest(nums, k):
    smallest_k = []
    for value in nums:
        if len(smallest_k) < k:
            heapq.heappush(smallest_k, value)
        elif value > smallest_k[0]:
            heapq.heapreplace(smallest_k, value)
    return smallest_k[0]
