# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def find_peak(nums):
    low, high = 0, len(nums) - 1
    # Invariant: the range [low, high] always contains at least one peak.
    while low < high:
        middle = (low + high) // 2
        if nums[middle] < nums[middle + 1]:
            # Ascending here, so a peak lies strictly to the right.
            low = middle + 1
        else:
            # Descending (equality is excluded), so middle itself may be the peak.
            high = middle
    return low
