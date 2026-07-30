## The insight

Greedy fails, so you cannot commit to a first coin and move on. But there is structure
worth exploiting: if you knew the fewest coins for *every* amount below the target, the
answer for the target is easy. Pick a coin, look up the best answer for the remainder,
add one, and take the smallest over all coins.

Build that table upward from zero.

```python
unreachable = amount + 1
best = [0] + [unreachable] * amount
for total in range(1, amount + 1):
    for coin in coins:
        if coin <= total:
            best[total] = min(best[total], best[total - coin] + 1)
return -1 if best[amount] == unreachable else best[amount]
```

`best[0] = 0` is the only base case you need: zero coins make zero.

## Why the sentinel is `amount + 1` and not infinity

Any real answer uses at most `amount` coins, since the smallest possible coin is `1`.
So `amount + 1` is unreachable by construction, and `best[t] == unreachable` at the end
means exactly "no combination exists". Using it instead of `float("inf")` also keeps
every value an `int`, so `best[t - coin] + 1` never produces a float that has to be
converted back.

**Check `coin <= total` before indexing.** Python's negative indexing makes
`best[total - coin]` with an oversized coin read from the *end* of the table — a
plausible-looking number from an unrelated subproblem, which is far worse than a crash.

**Return `-1`, do not return the sentinel.** The two impossible-input tests exist
because it is easy to build the table correctly and forget to translate the sentinel.

## Amount zero

`best[0]` is `0` and the loop body never runs, so `amount = 0` returns `0` with no
special case. Worth checking rather than assuming — a solution that starts its table at
`1` gets this wrong.

## Cost

O(amount × len(coins)) time and O(amount) space. That is 120,000 operations at the
constraint limits, which is why the bound on `amount` is what it is.
