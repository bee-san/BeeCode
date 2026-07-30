# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def last_stone(stones):
    pile = [-weight for weight in stones]
    heapq.heapify(pile)
    while len(pile) > 1:
        heaviest = -heapq.heappop(pile)
        second = -heapq.heappop(pile)
        if heaviest != second:
            heapq.heappush(pile, -(heaviest - second))
    if pile:
        return -pile[0]
    return 0
