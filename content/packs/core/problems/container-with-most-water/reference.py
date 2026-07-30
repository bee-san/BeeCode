# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def max_water(heights):
    left = 0
    right = len(heights) - 1
    best = 0
    while left < right:
        span = right - left
        shorter = heights[left]
        if heights[right] < shorter:
            shorter = heights[right]
        volume = span * shorter
        if volume > best:
            best = volume
        if heights[left] <= heights[right]:
            left += 1
        else:
            right -= 1
    return best
