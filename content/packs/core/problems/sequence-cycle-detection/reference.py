# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def has_cycle(successors, start):
    slow = start
    fast = start
    while True:
        if fast == -1:
            return False
        fast = successors[fast]
        if fast == -1:
            return False
        fast = successors[fast]
        slow = successors[slow]
        if slow == fast:
            return True
