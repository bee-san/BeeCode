# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import collections


def shortest_subarray(nums, k):
    prefix = [0]
    for value in nums:
        prefix.append(prefix[-1] + value)

    best = len(nums) + 1
    candidates = collections.deque()
    for index, total in enumerate(prefix):
        # Any candidate far enough below this prefix is answered now, and can
        # never give a shorter answer later, so it leaves permanently.
        while candidates and total - prefix[candidates[0]] >= k:
            best = min(best, index - candidates.popleft())
        # A candidate no smaller than this one is dominated: this prefix is at
        # least as low and strictly closer.
        while candidates and prefix[candidates[-1]] >= total:
            candidates.pop()
        candidates.append(index)

    return best if best <= len(nums) else -1
