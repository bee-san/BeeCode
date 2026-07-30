# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def cheapest_route(nodes, edges, start, target):
    import heapq

    outgoing = [[] for _ in range(nodes)]
    for source, destination, cost in edges:
        outgoing[source].append((destination, cost))

    cheapest = [None] * nodes
    frontier = [(0, start)]
    while frontier:
        so_far, node = heapq.heappop(frontier)
        if cheapest[node] is not None:
            continue
        cheapest[node] = so_far
        if node == target:
            return so_far
        for destination, cost in outgoing[node]:
            if cheapest[destination] is None:
                heapq.heappush(frontier, (so_far + cost, destination))
    return -1
