# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def max_profit(prices):
    best = 0
    cheapest_so_far = None
    for price in prices:
        if cheapest_so_far is None or price < cheapest_so_far:
            cheapest_so_far = price
        elif price - cheapest_so_far > best:
            best = price - cheapest_so_far
    return best
