# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def subsets(nums):
    found = []
    chosen = []

    def explore(index):
        if index == len(nums):
            found.append(list(chosen))
            return
        explore(index + 1)
        chosen.append(nums[index])
        explore(index + 1)
        chosen.pop()

    explore(0)
    return found
