# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def trapped_water(heights):
    if not heights:
        return 0
    left = 0
    right = len(heights) - 1
    left_max = heights[left]
    right_max = heights[right]
    total = 0
    while left < right:
        if left_max <= right_max:
            left += 1
            if heights[left] > left_max:
                left_max = heights[left]
            else:
                total += left_max - heights[left]
        else:
            right -= 1
            if heights[right] > right_max:
                right_max = heights[right]
            else:
                total += right_max - heights[right]
    return total
