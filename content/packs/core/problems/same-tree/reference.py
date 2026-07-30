# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def same_tree(left, right):
    def at(values, index):
        if index >= len(values):
            return None
        return values[index]

    def compare(index):
        left_value = at(left, index)
        right_value = at(right, index)
        if left_value is None and right_value is None:
            return True
        if left_value is None or right_value is None:
            return False
        if left_value != right_value:
            return False
        return compare(2 * index + 1) and compare(2 * index + 2)

    return compare(0)
