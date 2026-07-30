# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq
from collections import Counter


def top_k_frequent(nums, k):
    counts = Counter(nums)
    return [value for value, _ in heapq.nlargest(k, counts.items(), key=lambda pair: pair[1])]
