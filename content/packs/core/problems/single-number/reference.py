# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def single_number(nums):
    unpaired = 0
    for value in nums:
        unpaired ^= value
    return unpaired
