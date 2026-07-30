## The insight

Bellman-Ford, run exactly `k + 1` times. After round `i`, `cheapest[city]` is the least
price reachable from `start` using at most `i` flights, so `k + 1` rounds is precisely the
stop limit:

```python
for _ in range(k + 1):
    updated = list(cheapest)                   # this round reads the previous one
    for source, destination, price in flights:
        if cheapest[source] != INFINITY:
            updated[destination] = min(updated[destination], cheapest[source] + price)
    cheapest = updated
```

## The copy is the whole correctness argument

`updated` is written; `cheapest` is read. Relax in place and a single round can chain
several flights together — city `a` is improved, then an edge out of `a` is relaxed using
that brand-new value in the same round — and the flight count silently exceeds `k + 1`.
The answer comes back too cheap, and only on inputs where a longer route is cheaper, which
is exactly this Problem's point.

This is the single detail that decides the Problem. If you write one thing carefully, write
this.

## Why Dijkstra's is not enough

Dijkstra's settles a city at its cheapest price and never revisits it. But under a stop
limit, a city's cheapest price may be useless — reached in too many flights — while a
pricier arrival at the same city still leads to a valid route. Cost alone no longer
determines what to keep; the pair (cost, flights used) does. A Dijkstra's variant keyed on
that pair works, and Bellman-Ford gets there without the extra state.

## Reading the answer

`cheapest[target]` after the rounds is the answer, or `-1` if it is still infinite. Because
each round starts from a copy, prices never worsen, so the final value is the best over all
route lengths up to `k + 1` — no need to track the minimum across rounds separately.

## Pitfalls

**Running `k` rounds.** `k` stops means `k + 1` flights. Off by one, and it shows on `k = 0`
where the direct flight is the only option.

**Relaxing in place.** As above. The most common wrong answer.

**Skipping the `INFINITY` check.** `inf + price` is `inf` in Python so it happens to work,
but with a large sentinel integer instead it overflows into looking reachable.

**Assuming flights are two-way.** They are directed.

## Cost

O(k * len(flights)) time and O(n) space.
