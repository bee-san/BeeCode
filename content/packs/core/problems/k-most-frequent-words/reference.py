# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def top_words(words, count):
    tally = {}
    for word in words:
        if word in tally:
            tally[word] += 1
        else:
            tally[word] = 1

    ordered = sorted(tally.keys(), key=lambda word: (-tally[word], word))
    return ordered[:count]
