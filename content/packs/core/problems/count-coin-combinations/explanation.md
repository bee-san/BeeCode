## The insight

`ways[t]` counts the combinations summing to `t`. Process the coins in the **outer** loop:

```python
ways = [0] * (amount + 1)
ways[0] = 1
for coin in coins:
    for total in range(coin, amount + 1):
        ways[total] += ways[total - coin]
return ways[amount]
```

`ways[0] = 1` — the empty combination — is what seeds everything.

## The loop order is the whole Problem

With the coin loop outside, each coin is fully absorbed before the next is considered, so a
combination is only ever counted in one canonical coin order. That counts **combinations**.

Swap the loops — total outside, coin inside — and each total is built from every coin
independently, so `1 + 2` and `2 + 1` are counted separately. That counts **orderings**, and
for `coins = [1, 2, 5]` with `amount = 5` it gives 9 instead of 4.

Neither loop order is wrong in general — they answer two different questions, and knowing
which is which is the actual skill. [Coin Change](coin-change) minimises, so both orders give
the same answer there, which is why the trap only appears when counting.

## Why ascending, and why in place

The inner loop ascends precisely *because* coins may be reused: `ways[total - coin]` should
already include combinations that use this coin, so reading a value this pass has written is
correct. Compare [Split Into Two Equal Halves](partition-equal-subset-sum), where each item
may be used once and the loop therefore descends. Same table shape, opposite direction, and
the direction encodes the reuse rule.

## Two dimensions, if it helps

`ways[i][t]` = combinations of `t` using only the first `i` coins, with
`ways[i][t] = ways[i-1][t] + ways[i][t-coin]`. That is the honest two-dimensional form, and
the one-dimensional version above is it with the first index collapsed. Writing the 2D form
first makes the loop-order question answer itself.

## Pitfalls

**Swapping the loops.** Counts orderings.

**`ways[0] = 0`.** Everything is zero.

**Starting the inner loop at `0`.** `ways[total - coin]` indexes negatively and wraps.

**Recursion without memoisation over (index, remaining).** Exponential.

## Cost

O(len(coins) * amount) time, O(amount) space.
