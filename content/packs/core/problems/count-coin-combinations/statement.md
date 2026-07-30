Given distinct `coins` and an `amount`, return how many **combinations** of coins sum to
exactly `amount`. You have an unlimited supply of each coin.

Two combinations are the same if they use the same coins the same number of times, so order
does not matter: `2 + 1` and `1 + 2` are one combination.

Return `0` if no combination works. `amount == 0` has exactly one combination, the empty one.

## Constraints

- `1 <= len(coins) <= 300`
- `1 <= coins[i] <= 5000`
- `0 <= amount <= 5000`

## Follow-up

[Coin Change](coin-change) asks for the fewest coins; this asks how many ways. The tempting
one-dimensional table gives a wrong answer unless the loops are nested the right way round —
and getting it right is the difference between counting *combinations* and counting
*orderings*. Which loop belongs outside?
