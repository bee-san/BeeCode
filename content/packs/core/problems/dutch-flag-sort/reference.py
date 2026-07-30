# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def sort_colours(nums):
    low = 0
    cursor = 0
    high = len(nums) - 1
    while cursor <= high:
        value = nums[cursor]
        if value == 0:
            nums[low], nums[cursor] = nums[cursor], nums[low]
            low += 1
            cursor += 1
        elif value == 2:
            nums[high], nums[cursor] = nums[cursor], nums[high]
            high -= 1
        else:
            cursor += 1
    return nums
