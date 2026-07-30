Given a list of distinct `coins` and an `amount`, return the fewest coins that sum to
exactly `amount`. You have an unlimited supply of each coin. If no combination works,
return `-1`.

## Constraints

- `1 <= len(coins) <= 12`
- `1 <= coins[i] <= 10_000`
- `0 <= amount <= 10_000`
- `amount == 0` needs zero coins.

## Follow-up

Taking the largest coin that fits, repeatedly, is the obvious approach and it is wrong:
with `coins = [1, 3, 4]` and `amount = 6` it takes `4 + 1 + 1` for three coins when
`3 + 3` needs two. Greedy fails because a locally cheap choice can leave a remainder
that is expensive to finish. What does that tell you about which subproblems you have
to actually solve?
