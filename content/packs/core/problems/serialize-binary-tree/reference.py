# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def round_trip(tree):
    def at(index):
        if index >= len(tree):
            return None
        return tree[index]

    parts = []

    def encode(index):
        value = at(index)
        if value is None:
            parts.append("#")
            return
        parts.append(str(value))
        encode(2 * index + 1)
        encode(2 * index + 2)

    encode(0)
    encoded = ",".join(parts)

    tokens = encoded.split(",")
    cursor = [0]
    placed = {}

    def decode(index):
        token = tokens[cursor[0]]
        cursor[0] += 1
        if token == "#":
            return
        placed[index] = int(token)
        decode(2 * index + 1)
        decode(2 * index + 2)

    decode(0)
    if not placed:
        return [encoded, []]
    size = max(placed) + 1
    return [encoded, [placed.get(index) for index in range(size)]]
