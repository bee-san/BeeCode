`prices[i]` is the price on day `i`. You may buy and sell any number of times, but

- you must sell before buying again, and
- after selling you must wait **one day** before buying — the day right after a sale is a
  cooldown.

Return the greatest total profit.

## Constraints

- `1 <= len(prices) <= 5000`
- `0 <= prices[i] <= 1000`

## Follow-up

On each day you are in one of a small number of situations: holding, free to buy, or cooling
down. Work out how each day's situation follows from the day before's, and the whole thing is
three running values. What makes this two-dimensional rather than one — even though there is
only one array?
