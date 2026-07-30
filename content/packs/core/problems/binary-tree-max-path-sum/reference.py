# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def max_path_sum(tree):
    best = [None]

    def down(index):
        if index >= len(tree) or tree[index] is None:
            return 0
        left = down(2 * index + 1)
        if left < 0:
            left = 0
        right = down(2 * index + 2)
        if right < 0:
            right = 0
        value = tree[index]
        through = value + left + right
        if best[0] is None or through > best[0]:
            best[0] = through
        return value + (left if left > right else right)

    down(0)
    return best[0]
