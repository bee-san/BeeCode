`n` cities are labelled `0` through `n - 1`. `flights` is a list of `[from, to, price]`
triples describing **directed** flights.

Return the cheapest total price of travelling from `start` to `target` using **at most `k`
stops**, or `-1` if no such route exists. A route with `k` stops uses `k + 1` flights.

## Constraints

- `1 <= n <= 100`
- `0 <= len(flights) <= 1000`
- `1 <= price <= 10_000`
- `0 <= k < n`
- There is at most one flight between any ordered pair of cities.

## Follow-up

Plain Dijkstra's finds the cheapest route ignoring the stop limit, and the cheapest route
overall may use too many flights while a pricier one fits. The stop limit makes this
Bellman-Ford's natural shape: relax every flight `k + 1` times, and after round `i` you
know the cheapest price reachable using at most `i` flights. One subtlety decides
correctness — what must each round read from?
