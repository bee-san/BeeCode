# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_good_nodes(tree):
    def walk(index, highest):
        if index >= len(tree) or tree[index] is None:
            return 0
        value = tree[index]
        good = 1 if value >= highest else 0
        if value > highest:
            highest = value
        return good + walk(2 * index + 1, highest) + walk(2 * index + 2, highest)

    if not tree or tree[0] is None:
        return 0
    return walk(0, tree[0])
