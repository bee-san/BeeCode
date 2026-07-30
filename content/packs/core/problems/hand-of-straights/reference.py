# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def can_deal(cards, size):
    if len(cards) % size != 0:
        return False

    remaining = {}
    for card in cards:
        if card in remaining:
            remaining[card] += 1
        else:
            remaining[card] = 1

    for card in sorted(remaining):
        needed = remaining[card]
        if needed <= 0:
            continue
        for step in range(size):
            following = card + step
            if remaining.get(following, 0) < needed:
                return False
            remaining[following] -= needed
    return True
