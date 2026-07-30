# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def insert_into_bst(tree, value):
    nodes = list(tree)
    if not nodes or nodes[0] is None:
        return [value]

    index = 0
    while True:
        child = 2 * index + 1 if value < nodes[index] else 2 * index + 2
        while len(nodes) <= child:
            nodes.append(None)
        if nodes[child] is None:
            nodes[child] = value
            break
        index = child

    while nodes and nodes[-1] is None:
        nodes.pop()
    return nodes
