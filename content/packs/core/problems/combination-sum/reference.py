# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def combination_sum(candidates, target):
    ordered = sorted(candidates)
    found = []
    chosen = []

    def build(start, remaining):
        if remaining == 0:
            found.append(list(chosen))
            return
        for index in range(start, len(ordered)):
            value = ordered[index]
            if value > remaining:
                break
            chosen.append(value)
            build(index, remaining - value)
            chosen.pop()

    build(0, target)
    return found
