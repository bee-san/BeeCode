# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def range_sum(tree, low, high):
    total = 0
    pending = [0]
    while pending:
        index = pending.pop()
        if index >= len(tree) or tree[index] is None:
            continue
        value = tree[index]
        if low <= value <= high:
            total += value
        # Prune: everything left of a node is smaller than it, so a node already
        # at or below `low` has no in-range descendants on its left.
        if value > low:
            pending.append(2 * index + 1)
        if value < high:
            pending.append(2 * index + 2)
    return total
