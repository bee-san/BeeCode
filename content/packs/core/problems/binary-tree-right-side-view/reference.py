# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def right_side_view(tree):
    if not tree or tree[0] is None:
        return []
    view = []
    frontier = [0]
    while frontier:
        view.append(tree[frontier[-1]])
        following = []
        for index in frontier:
            for child in (2 * index + 1, 2 * index + 2):
                if child < len(tree) and tree[child] is not None:
                    following.append(child)
        frontier = following
    return view
