# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def longest_border(text):
    lengths = [0] * len(text)
    for index in range(1, len(text)):
        candidate = lengths[index - 1]
        while candidate > 0 and text[index] != text[candidate]:
            candidate = lengths[candidate - 1]
        if text[index] == text[candidate]:
            candidate += 1
        else:
            candidate = 0
        lengths[index] = candidate
    return lengths[len(text) - 1]
