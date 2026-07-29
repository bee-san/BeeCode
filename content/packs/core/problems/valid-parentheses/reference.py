# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


CLOSER_TO_OPENER = {")": "(", "]": "[", "}": "{"}


def is_valid(s):
    stack = []
    for character in s:
        if character in CLOSER_TO_OPENER:
            if not stack or stack.pop() != CLOSER_TO_OPENER[character]:
                return False
        else:
            stack.append(character)
    return not stack
