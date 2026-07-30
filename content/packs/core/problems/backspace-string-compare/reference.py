# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def same_after_backspaces(first, second):
    def apply(text):
        kept = []
        for character in text:
            if character == "#":
                if kept:
                    kept.pop()
            else:
                kept.append(character)
        return kept

    return apply(first) == apply(second)
