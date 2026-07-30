# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_symmetric(tree):
    def at(index):
        if index >= len(tree):
            return None
        return tree[index]

    def mirror(left, right):
        left_value, right_value = at(left), at(right)
        if left_value is None and right_value is None:
            return True
        if left_value is None or right_value is None:
            return False
        if left_value != right_value:
            return False
        return mirror(2 * left + 1, 2 * right + 2) and mirror(2 * left + 2, 2 * right + 1)

    if not tree or tree[0] is None:
        return True
    return mirror(1, 2)
