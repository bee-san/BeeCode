# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def has_path_sum(tree, target):
    def present(index):
        return index < len(tree) and tree[index] is not None

    def walk(index, remaining):
        remaining -= tree[index]
        left = 2 * index + 1
        right = 2 * index + 2
        if not present(left) and not present(right):
            return remaining == 0
        if present(left) and walk(left, remaining):
            return True
        if present(right) and walk(right, remaining):
            return True
        return False

    if not present(0):
        return False
    return walk(0, target)
