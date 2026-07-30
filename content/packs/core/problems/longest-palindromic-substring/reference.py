# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def longest_palindrome(text):
    if not text:
        return ""

    best_start = 0
    best_length = 1

    def widen(low, high):
        while low >= 0 and high < len(text) and text[low] == text[high]:
            low -= 1
            high += 1
        return low + 1, high - low - 1

    for centre in range(len(text)):
        start, length = widen(centre, centre)
        if length > best_length:
            best_start = start
            best_length = length
        start, length = widen(centre, centre + 1)
        if length > best_length:
            best_start = start
            best_length = length

    return text[best_start:best_start + best_length]
