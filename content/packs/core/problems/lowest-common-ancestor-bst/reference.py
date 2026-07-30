# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def lowest_common_ancestor(tree, first, second):
    index = 0
    while index < len(tree) and tree[index] is not None:
        value = tree[index]
        if first < value and second < value:
            index = 2 * index + 1
        elif first > value and second > value:
            index = 2 * index + 2
        else:
            return value
    return None
