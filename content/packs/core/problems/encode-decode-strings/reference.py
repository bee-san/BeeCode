# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def encode(words):
    parts = []
    for word in words:
        parts.append(str(len(word)))
        parts.append("#")
        parts.append(word)
    return "".join(parts)


def decode(encoded):
    words = []
    cursor = 0
    while cursor < len(encoded):
        marker = encoded.index("#", cursor)
        size = int(encoded[cursor:marker])
        start = marker + 1
        words.append(encoded[start:start + size])
        cursor = start + size
    return words


def round_trip(words):
    encoded = encode(words)
    return [encoded, decode(encoded)]
