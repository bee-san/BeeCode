# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def three_sum(nums):
    ordered = sorted(nums)
    found = []
    size = len(ordered)
    for first in range(size - 2):
        if ordered[first] > 0:
            break
        if first > 0 and ordered[first] == ordered[first - 1]:
            continue
        left = first + 1
        right = size - 1
        while left < right:
            total = ordered[first] + ordered[left] + ordered[right]
            if total < 0:
                left += 1
            elif total > 0:
                right -= 1
            else:
                found.append([ordered[first], ordered[left], ordered[right]])
                left += 1
                right -= 1
                while left < right and ordered[left] == ordered[left - 1]:
                    left += 1
                while left < right and ordered[right] == ordered[right + 1]:
                    right -= 1
    return found
