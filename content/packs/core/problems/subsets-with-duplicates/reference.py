# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def subsets_with_duplicates(nums):
    ordered = sorted(nums)
    found = []
    chosen = []

    def build(start):
        found.append(list(chosen))
        for index in range(start, len(ordered)):
            if index > start and ordered[index] == ordered[index - 1]:
                continue
            chosen.append(ordered[index])
            build(index + 1)
            chosen.pop()

    build(0)
    return found
