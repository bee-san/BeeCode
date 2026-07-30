# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def diameter(tree):
    best = [0]

    def height(index):
        if index >= len(tree) or tree[index] is None:
            return 0
        left = height(2 * index + 1)
        right = height(2 * index + 2)
        if left + right > best[0]:
            best[0] = left + right
        return 1 + max(left, right)

    height(0)
    return best[0]
