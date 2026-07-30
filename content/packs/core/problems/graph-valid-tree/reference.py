# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_tree(n, edges):
    if n == 0:
        return False
    if len(edges) != n - 1:
        return False

    neighbours = {}
    for label in range(n):
        neighbours[label] = []
    for first, second in edges:
        neighbours[first].append(second)
        neighbours[second].append(first)

    seen = set()
    seen.add(0)
    pending = [0]
    while pending:
        label = pending.pop()
        for neighbour in neighbours[label]:
            if neighbour not in seen:
                seen.add(neighbour)
                pending.append(neighbour)

    return len(seen) == n
