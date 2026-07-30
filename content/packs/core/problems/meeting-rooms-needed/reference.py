# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def rooms_needed(meetings):
    starts = sorted(pair[0] for pair in meetings)
    ends = sorted(pair[1] for pair in meetings)

    most = 0
    running = 0
    next_end = 0
    for start in starts:
        while next_end < len(ends) and ends[next_end] <= start:
            running -= 1
            next_end += 1
        running += 1
        if running > most:
            most = running
    return most
