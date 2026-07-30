# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def invert(tree):
    if not tree or tree[0] is None:
        return []

    depth = 0
    while (1 << depth) - 1 < len(tree):
        depth += 1
    size = (1 << depth) - 1
    padded = list(tree) + [None] * (size - len(tree))
    mirrored = [None] * size

    def place(source, destination):
        if source >= size or padded[source] is None:
            return
        mirrored[destination] = padded[source]
        place(2 * source + 1, 2 * destination + 2)
        place(2 * source + 2, 2 * destination + 1)

    place(0, 0)
    while mirrored and mirrored[-1] is None:
        mirrored.pop()
    return mirrored
