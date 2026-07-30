# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def partition_sizes(text):
    last_seen = {}
    for index in range(len(text)):
        last_seen[text[index]] = index

    sizes = []
    start = 0
    end = 0
    for index in range(len(text)):
        if last_seen[text[index]] > end:
            end = last_seen[text[index]]
        if index == end:
            sizes.append(end - start + 1)
            start = index + 1
    return sizes
