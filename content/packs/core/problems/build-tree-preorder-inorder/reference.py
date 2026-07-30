# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def build_tree(preorder, inorder):
    if not preorder:
        return []

    position = {value: index for index, value in enumerate(inorder)}
    placed = {}
    cursor = [0]

    def build(low, high, index):
        if low > high:
            return
        value = preorder[cursor[0]]
        cursor[0] += 1
        placed[index] = value
        split = position[value]
        build(low, split - 1, 2 * index + 1)
        build(split + 1, high, 2 * index + 2)

    build(0, len(inorder) - 1, 0)
    size = max(placed) + 1
    return [placed.get(index) for index in range(size)]
