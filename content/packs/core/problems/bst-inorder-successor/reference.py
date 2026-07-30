# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def inorder_successor(tree, target):
    if not tree or tree[0] is None:
        return None

    best = None
    index = 0
    while index < len(tree) and tree[index] is not None:
        value = tree[index]
        if value > target:
            # A candidate. Anything better is smaller, so it must be to the left.
            best = value
            index = 2 * index + 1
        else:
            index = 2 * index + 2
    return best
