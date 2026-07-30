# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_palindromes(text):
    total = 0

    def widen(low, high):
        found = 0
        while low >= 0 and high < len(text) and text[low] == text[high]:
            found += 1
            low -= 1
            high += 1
        return found

    for centre in range(len(text)):
        total += widen(centre, centre)
        total += widen(centre, centre + 1)
    return total
