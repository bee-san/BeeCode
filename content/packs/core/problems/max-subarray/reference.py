# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def max_subarray_sum(nums):
    # Seeded with the first element rather than 0, which is what makes the
    # all-negative case correct: the subarray must be non-empty.
    best = current = nums[0]
    for value in nums[1:]:
        # Either extend the previous subarray or start again at this element.
        current = max(value, current + value)
        best = max(best, current)
    return best
