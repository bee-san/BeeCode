# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def is_anagram(s, t):
    if len(s) != len(t):
        return False

    counts = {}
    for character in s:
        counts[character] = counts.get(character, 0) + 1
    for character in t:
        if character not in counts:
            return False
        counts[character] -= 1
        if counts[character] == 0:
            del counts[character]
    return not counts
