# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_balanced(text):
    lowest = 0
    highest = 0
    for character in text:
        if character == "(":
            lowest += 1
            highest += 1
        elif character == ")":
            lowest -= 1
            highest -= 1
        else:
            lowest -= 1
            highest += 1
        if highest < 0:
            return False
        if lowest < 0:
            lowest = 0
    return lowest == 0
