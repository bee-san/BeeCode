# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_valid_bst(tree):
    def valid(index, low, high):
        if index >= len(tree) or tree[index] is None:
            return True
        value = tree[index]
        if low is not None and value <= low:
            return False
        if high is not None and value >= high:
            return False
        return valid(2 * index + 1, low, value) and valid(2 * index + 2, value, high)

    return valid(0, None, None)
