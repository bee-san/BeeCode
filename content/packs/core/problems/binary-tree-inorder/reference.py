# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def inorder(tree):
    result = []

    def walk(index):
        if index >= len(tree) or tree[index] is None:
            return
        walk(2 * index + 1)
        result.append(tree[index])
        walk(2 * index + 2)

    walk(0)
    return result
