# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def longest_unique_substring(s):
    last_seen = {}
    best = 0
    start = 0
    for index, character in enumerate(s):
        previous = last_seen.get(character)
        # max() is load-bearing: a stale entry from before `start` must not drag
        # the window's left edge backwards.
        if previous is not None and previous >= start:
            start = previous + 1
        last_seen[character] = index
        best = max(best, index - start + 1)
    return best
