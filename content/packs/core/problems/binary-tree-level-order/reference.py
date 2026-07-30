# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def level_order(tree):
    if not tree or tree[0] is None:
        return []
    levels = []
    frontier = [0]
    while frontier:
        values = []
        following = []
        for index in frontier:
            values.append(tree[index])
            for child in (2 * index + 1, 2 * index + 2):
                if child < len(tree) and tree[child] is not None:
                    following.append(child)
        levels.append(values)
        frontier = following
    return levels
