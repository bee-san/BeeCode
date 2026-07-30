# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_subsequences(text, pattern):
    counts = [0] * (len(pattern) + 1)
    counts[0] = 1
    for character in text:
        for index in range(len(pattern), 0, -1):
            if pattern[index - 1] == character:
                counts[index] += counts[index - 1]
    return counts[len(pattern)]
