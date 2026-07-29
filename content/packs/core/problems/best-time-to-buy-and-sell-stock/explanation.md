## The insight

Fix the sell day. If you have decided to sell on day `i`, your profit is
`prices[i]` minus whatever you paid, and you obviously want to have paid as little
as possible — so you would have bought on the **cheapest day before `i`**.

That is the whole problem. Every sell day has exactly one sensible buy day, and it
is determined by a single running fact: the minimum price seen so far. Walk left to
right, keep that minimum, and take the best difference you ever see.

Note what this does *not* require. You never search backwards, and you never
consider two candidate buy days at once. The pair search collapses because the
choice of buy day is forced.

## One pass

```python
def max_profit(prices):
    best = 0
    cheapest_so_far = None
    for price in prices:
        if cheapest_so_far is None or price < cheapest_so_far:
            cheapest_so_far = price
        elif price - cheapest_so_far > best:
            best = price - cheapest_so_far
    return best
```

Because `best` starts at 0 and only ever increases, the "no profitable trade"
answer falls out for free — a falling list never enters the `elif` with a positive
difference.

Three mistakes are easy here:

**`max(prices) - min(prices)`.** This is the classic wrong answer and it passes both
worked examples. It breaks the instant the minimum comes *after* the maximum:
`[2, 4, 1]` gives 3 with this formula, but you cannot buy on the last day and sell
before it. The buy must precede the sell, and subtracting two independently-chosen
extremes forgets that.

**Chasing the global minimum.** In `[6, 2, 5, 9, 1, 3]` the cheapest day is the `1`
near the end, but the best trade buys the `2` and sells the `9`. The cheapest day is
only useful if a high price follows it, which is precisely why you compare against
the running minimum at each step instead of finding the minimum first.

**Initialising `best` to something other than 0.** Starting at `-inf` or at
`prices[1] - prices[0]` lets a negative "profit" survive, and then a falling list
returns a loss instead of 0. The floor of 0 encodes the right to not trade.

## Cost

O(n) time, O(1) space. One pass, two scalars.

The nested-pair version is O(n²): at 100,000 days that is five billion subtractions,
so the linear pass is not a micro-optimisation — it is the difference between
finishing and not.
