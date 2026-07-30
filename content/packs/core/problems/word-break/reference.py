# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def can_split(text, dictionary):
    allowed = set(dictionary)
    reachable = [False] * (len(text) + 1)
    reachable[0] = True

    for end in range(1, len(text) + 1):
        for start in range(end):
            if not reachable[start]:
                continue
            if text[start:end] in allowed:
                reachable[end] = True
                break
    return reachable[len(text)]
