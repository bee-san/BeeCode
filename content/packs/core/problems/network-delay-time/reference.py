# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def delay_time(n, times, start):
    outgoing = {}
    for label in range(1, n + 1):
        outgoing[label] = []
    for source, destination, weight in times:
        outgoing[source].append((destination, weight))

    settled = {}
    pending = [(0, start)]
    while pending:
        elapsed, label = heapq.heappop(pending)
        if label in settled:
            continue
        settled[label] = elapsed
        for destination, weight in outgoing[label]:
            if destination not in settled:
                heapq.heappush(pending, (elapsed + weight, destination))

    if len(settled) != n:
        return -1

    slowest = 0
    for label in settled:
        if settled[label] > slowest:
            slowest = settled[label]
    return slowest
