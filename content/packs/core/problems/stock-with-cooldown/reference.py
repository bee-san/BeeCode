# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def max_profit(prices):
    if not prices:
        return 0

    holding = -prices[0]
    free = 0
    cooling = 0

    for index in range(1, len(prices)):
        price = prices[index]
        next_holding = holding
        if free - price > next_holding:
            next_holding = free - price
        next_cooling = holding + price
        next_free = free
        if cooling > next_free:
            next_free = cooling
        holding = next_holding
        cooling = next_cooling
        free = next_free

    if cooling > free:
        return cooling
    return free
