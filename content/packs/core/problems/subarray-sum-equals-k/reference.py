# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_subarrays(nums, k):
    seen = {0: 1}
    running = 0
    total = 0
    for value in nums:
        running += value
        total += seen.get(running - k, 0)
        seen[running] = seen.get(running, 0) + 1
    return total
