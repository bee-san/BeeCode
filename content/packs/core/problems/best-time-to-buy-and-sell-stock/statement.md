You are given a list of integers `prices` where `prices[i]` is the price of a stock
on day `i`.

You may buy on one day and sell on one **later** day, at most once. Return the
largest profit you can make. If no trade is profitable, return `0` — you are always
allowed to do nothing.

## Constraints

- `0 <= len(prices) <= 100_000`
- `0 <= prices[i] <= 10^9`
- You must sell strictly after you buy; buying and selling on the same day earns
  nothing.

## Follow-up

Comparing every buy day against every sell day is O(n²). Walking left to right, what
is the only fact about the days behind you that changes the best profit available
today?
