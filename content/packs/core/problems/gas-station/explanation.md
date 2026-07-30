## The insight

Work with the surplus at each station, `gas[i] - cost[i]`.

**Does any start exist?** Only if the surpluses sum to at least `0`. Driving the full circuit
returns you to where you began, so the total is the same whatever the start — a negative total
means no start works, and a non-negative total means one does.

**Which start?** Keep a running surplus. If it goes negative at index `i`, then no station from
the current candidate through `i` can be the answer, and the next candidate is `i + 1`.

```python
total = running = start = 0
for index in range(len(gas)):
    gained = gas[index] - cost[index]
    total += gained
    running += gained
    if running < 0:
        start = index + 1
        running = 0
return start if total >= 0 else -1
```

One pass gives both answers.

## Why the whole failed stretch can be discarded

Suppose the run from `start` dies at `i`. Any station `j` strictly between them would begin
with less fuel than the run had on arrival at `j` — the run arrived with a non-negative tank,
by construction, since it had not yet failed — so a run beginning at `j` also dies at or before
`i`. Every station in the failed stretch is eliminated at once, which is what makes this O(n)
rather than O(n^2).

## Why one pass suffices

The two facts are independent: `total` decides *whether*, `start` decides *where*. Neither
needs the other, so both accumulate in the same loop. And when `total >= 0`, the surviving
`start` is provably correct — the stretch after it never went negative, and everything before
it was eliminated.

## Pitfalls

**Returning `start` without checking `total`.** On an impossible circuit, `start` is meaningless
and can even be `len(gas)`.

**Forgetting to reset `running` to `0`.** Carries a debt the new start never incurred.

**Simulating from every station.** O(n^2), and it times out at the top of the range.

**Comparing sums of `gas` and `cost` separately.** Equivalent to the total test, but only if you
compare the sums, not the arrays.

## Cost

O(n) time, O(1) space.
