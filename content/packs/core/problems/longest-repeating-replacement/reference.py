# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def longest_run(s, k):
    counts = {}
    left = 0
    most_common = 0
    best = 0
    for right in range(len(s)):
        character = s[right]
        counts[character] = counts.get(character, 0) + 1
        if counts[character] > most_common:
            most_common = counts[character]
        while (right - left + 1) - most_common > k:
            counts[s[left]] -= 1
            left += 1
            most_common = max(counts.values())
        if right - left + 1 > best:
            best = right - left + 1
    return best
