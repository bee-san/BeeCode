# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_subtree(tree, sub):
    def at(values, index):
        if index >= len(values):
            return None
        return values[index]

    def identical(tree_index, sub_index):
        tree_value = at(tree, tree_index)
        sub_value = at(sub, sub_index)
        if tree_value is None and sub_value is None:
            return True
        if tree_value is None or sub_value is None:
            return False
        if tree_value != sub_value:
            return False
        return identical(2 * tree_index + 1, 2 * sub_index + 1) and identical(
            2 * tree_index + 2, 2 * sub_index + 2
        )

    if at(sub, 0) is None:
        return True

    def search(index):
        if at(tree, index) is None:
            return False
        if identical(index, 0):
            return True
        return search(2 * index + 1) or search(2 * index + 2)

    return search(0)
