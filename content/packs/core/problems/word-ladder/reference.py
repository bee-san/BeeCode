# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def ladder_length(start, target, words):
    allowed = set(words)
    if target not in allowed:
        return 0
    if start == target:
        return 1

    alphabet = "abcdefghijklmnopqrstuvwxyz"
    frontier = [start]
    allowed.discard(start)
    length = 1

    while frontier:
        length += 1
        following = []
        for word in frontier:
            for position in range(len(word)):
                for letter in alphabet:
                    if letter == word[position]:
                        continue
                    candidate = word[:position] + letter + word[position + 1:]
                    if candidate not in allowed:
                        continue
                    if candidate == target:
                        return length
                    allowed.discard(candidate)
                    following.append(candidate)
        frontier = following

    return 0
