# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def fewest_removals(intervals):
    if not intervals:
        return 0

    ordered = sorted(intervals, key=lambda pair: pair[1])
    kept = 0
    reach = None
    for start, end in ordered:
        if reach is None or start >= reach:
            kept += 1
            reach = end
    return len(intervals) - kept
