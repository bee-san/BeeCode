# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def course_order(n, prerequisites):
    unlocks = {}
    for label in range(n):
        unlocks[label] = []
    outstanding = [0] * n

    for course, prerequisite in prerequisites:
        unlocks[prerequisite].append(course)
        outstanding[course] += 1

    ready = []
    for label in range(n):
        if outstanding[label] == 0:
            ready.append(label)

    order = []
    while ready:
        label = ready.pop(0)
        order.append(label)
        for unlocked in unlocks[label]:
            outstanding[unlocked] -= 1
            if outstanding[unlocked] == 0:
                ready.append(unlocked)

    if len(order) != n:
        return []
    return order
