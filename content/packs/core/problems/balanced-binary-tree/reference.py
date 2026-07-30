# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_balanced(tree):
    def measure(index):
        if index >= len(tree) or tree[index] is None:
            return 0
        left = measure(2 * index + 1)
        if left == -1:
            return -1
        right = measure(2 * index + 2)
        if right == -1:
            return -1
        if abs(left - right) > 1:
            return -1
        return 1 + max(left, right)

    return measure(0) != -1
