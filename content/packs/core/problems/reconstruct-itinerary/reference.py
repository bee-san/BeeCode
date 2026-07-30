# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def itinerary(tickets):
    onwards = {}
    for source, destination in tickets:
        if source not in onwards:
            onwards[source] = []
        onwards[source].append(destination)
    for source in onwards:
        onwards[source].sort(reverse=True)

    route = []
    stack = ["JFK"]
    while stack:
        airport = stack[-1]
        if airport in onwards and onwards[airport]:
            stack.append(onwards[airport].pop())
        else:
            route.append(stack.pop())
    route.reverse()
    return route
