# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def permutations(nums):
    found = []
    order = []
    used = [False] * len(nums)

    def extend():
        if len(order) == len(nums):
            found.append(list(order))
            return
        for index in range(len(nums)):
            if used[index]:
                continue
            used[index] = True
            order.append(nums[index])
            extend()
            order.pop()
            used[index] = False

    extend()
    return found
