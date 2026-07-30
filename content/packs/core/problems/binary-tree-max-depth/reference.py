# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def max_depth(tree):
    def depth(index):
        if index >= len(tree) or tree[index] is None:
            return 0
        return 1 + max(depth(2 * index + 1), depth(2 * index + 2))

    return depth(0)
