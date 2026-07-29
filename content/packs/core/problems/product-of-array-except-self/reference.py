# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def product_except_self(nums):
    answer = [1] * len(nums)

    # Left-to-right: answer[i] becomes the product of everything before i.
    prefix = 1
    for index in range(len(nums)):
        answer[index] = prefix
        prefix *= nums[index]

    # Right-to-left: multiply in the product of everything after i.
    suffix = 1
    for index in range(len(nums) - 1, -1, -1):
        answer[index] *= suffix
        suffix *= nums[index]

    return answer
