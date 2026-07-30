# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def contains_permutation(haystack, needle):
    width = len(needle)
    if width > len(haystack):
        return False

    wanted = {}
    for character in needle:
        wanted[character] = wanted.get(character, 0) + 1

    window = {}
    for character in haystack[:width]:
        window[character] = window.get(character, 0) + 1
    if window == wanted:
        return True

    for right in range(width, len(haystack)):
        entering = haystack[right]
        window[entering] = window.get(entering, 0) + 1
        leaving = haystack[right - width]
        window[leaving] -= 1
        if window[leaving] == 0:
            del window[leaving]
        if window == wanted:
            return True
    return False
