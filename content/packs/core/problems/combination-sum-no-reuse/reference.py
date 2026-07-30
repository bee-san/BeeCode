# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def combination_sum_no_reuse(candidates, target):
    ordered = sorted(candidates)
    found = []
    chosen = []

    def build(start, remaining):
        if remaining == 0:
            found.append(list(chosen))
            return
        index = start
        while index < len(ordered):
            value = ordered[index]
            if value > remaining:
                break
            if index > start and value == ordered[index - 1]:
                index += 1
                continue
            chosen.append(value)
            build(index + 1, remaining - value)
            chosen.pop()
            index += 1

    build(0, target)
    return found
