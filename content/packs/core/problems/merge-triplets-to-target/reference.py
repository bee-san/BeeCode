# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def can_reach_target(triplets, target):
    matched = [False, False, False]
    for triplet in triplets:
        usable = True
        for position in range(3):
            if triplet[position] > target[position]:
                usable = False
        if not usable:
            continue
        for position in range(3):
            if triplet[position] == target[position]:
                matched[position] = True
    for position in range(3):
        if not matched[position]:
            return False
    return True
