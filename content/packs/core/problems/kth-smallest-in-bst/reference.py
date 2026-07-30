# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def kth_smallest(tree, k):
    remaining = [k]
    answer = [None]

    def walk(index):
        if index >= len(tree) or tree[index] is None:
            return
        walk(2 * index + 1)
        if remaining[0] == 0:
            return
        remaining[0] -= 1
        if remaining[0] == 0:
            answer[0] = tree[index]
            return
        walk(2 * index + 2)

    walk(0)
    return answer[0]
