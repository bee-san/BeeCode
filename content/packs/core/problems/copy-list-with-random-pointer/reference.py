# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

class Node:
    def __init__(self, value):
        self.value = value
        self.next = None
        self.random = None


def copy_list(nodes):
    if not nodes:
        return []

    originals = [Node(value) for value, _ in nodes]
    for position, original in enumerate(originals):
        if position + 1 < len(originals):
            original.next = originals[position + 1]
        target = nodes[position][1]
        if target is not None:
            original.random = originals[target]

    clones = {}
    cursor = originals[0]
    while cursor is not None:
        clones[id(cursor)] = Node(cursor.value)
        cursor = cursor.next

    cursor = originals[0]
    while cursor is not None:
        clone = clones[id(cursor)]
        clone.next = clones[id(cursor.next)] if cursor.next is not None else None
        clone.random = clones[id(cursor.random)] if cursor.random is not None else None
        cursor = cursor.next

    readout = []
    cursor = clones[id(originals[0])]
    while cursor is not None:
        readout.append([cursor.value, cursor.random.value if cursor.random else None])
        cursor = cursor.next
    return readout
