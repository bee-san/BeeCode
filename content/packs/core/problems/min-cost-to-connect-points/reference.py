# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def min_cost_to_connect(points):
    count = len(points)
    if count <= 1:
        return 0

    reached = [False] * count
    cheapest = [None] * count
    cheapest[0] = 0

    total = 0
    for _ in range(count):
        best = -1
        for index in range(count):
            if reached[index]:
                continue
            if cheapest[index] is None:
                continue
            if best == -1 or cheapest[index] < cheapest[best]:
                best = index
        reached[best] = True
        total += cheapest[best]
        for index in range(count):
            if reached[index]:
                continue
            span = abs(points[best][0] - points[index][0]) + abs(
                points[best][1] - points[index][1]
            )
            if cheapest[index] is None or span < cheapest[index]:
                cheapest[index] = span
    return total
