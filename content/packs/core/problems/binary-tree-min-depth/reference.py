# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def min_depth(tree):
    if not tree or tree[0] is None:
        return 0

    def left_of(index):
        return 2 * index + 1

    def right_of(index):
        return 2 * index + 2

    def present(index):
        return index < len(tree) and tree[index] is not None

    frontier = [0]
    depth = 1
    while frontier:
        following = []
        for index in frontier:
            left = left_of(index)
            right = right_of(index)
            if not present(left) and not present(right):
                return depth
            if present(left):
                following.append(left)
            if present(right):
                following.append(right)
        frontier = following
        depth += 1
    return depth
