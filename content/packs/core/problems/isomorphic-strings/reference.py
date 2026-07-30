# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_isomorphic(first, second):
    if len(first) != len(second):
        return False

    forward = {}
    backward = {}
    for left, right in zip(first, second):
        if left in forward and forward[left] != right:
            return False
        if right in backward and backward[right] != left:
            return False
        forward[left] = right
        backward[right] = left
    return True
