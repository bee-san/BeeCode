# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def split_array(nums, k):
    def parts_needed(cap):
        # Greedy: extend the current part while it fits, then cut. Cutting later
        # can never need fewer parts, so this is the minimum for this cap.
        parts = 1
        current = 0
        for value in nums:
            if current + value > cap:
                parts += 1
                current = value
            else:
                current += value
        return parts

    # No part can be smaller than the largest single element, and one part holding
    # everything is always achievable.
    low = max(nums)
    high = sum(nums)
    while low < high:
        middle = (low + high) // 2
        if parts_needed(middle) <= k:
            high = middle
        else:
            low = middle + 1
    return low
