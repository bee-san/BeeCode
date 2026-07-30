# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def cheapest_within(n, flights, start, target, k):
    unreachable = float("inf")
    cheapest = [unreachable] * n
    cheapest[start] = 0

    for _ in range(k + 1):
        updated = list(cheapest)
        for source, destination, price in flights:
            if cheapest[source] == unreachable:
                continue
            if cheapest[source] + price < updated[destination]:
                updated[destination] = cheapest[source] + price
        cheapest = updated

    if cheapest[target] == unreachable:
        return -1
    return cheapest[target]
