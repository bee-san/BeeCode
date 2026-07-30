# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def k_closest(points, k):
    furthest_of_the_closest = []
    for x, y in points:
        heapq.heappush(furthest_of_the_closest, (-(x * x + y * y), x, y))
        if len(furthest_of_the_closest) > k:
            heapq.heappop(furthest_of_the_closest)

    chosen = []
    while furthest_of_the_closest:
        squared, x, y = heapq.heappop(furthest_of_the_closest)
        chosen.append([x, y])
    chosen.reverse()
    return chosen
