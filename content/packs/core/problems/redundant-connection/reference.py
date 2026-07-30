# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def redundant_edge(edges):
    parent = {}

    def root_of(label):
        while parent[label] != label:
            parent[label] = parent[parent[label]]
            label = parent[label]
        return label

    for first, second in edges:
        if first not in parent:
            parent[first] = first
        if second not in parent:
            parent[second] = second
        left = root_of(first)
        right = root_of(second)
        if left == right:
            return [first, second]
        parent[left] = right

    return []
