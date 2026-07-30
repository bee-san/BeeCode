# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def minimum_window(haystack, needle):
    if not needle or len(needle) > len(haystack):
        return ""

    required = {}
    for character in needle:
        required[character] = required.get(character, 0) + 1
    missing = len(required)

    window = {}
    best_start = 0
    best_length = len(haystack) + 1
    left = 0
    for right in range(len(haystack)):
        entering = haystack[right]
        window[entering] = window.get(entering, 0) + 1
        if entering in required and window[entering] == required[entering]:
            missing -= 1
        while missing == 0:
            if right - left + 1 < best_length:
                best_length = right - left + 1
                best_start = left
            leaving = haystack[left]
            if leaving in required and window[leaving] == required[leaving]:
                missing += 1
            window[leaving] -= 1
            left += 1
    if best_length > len(haystack):
        return ""
    return haystack[best_start:best_start + best_length]
