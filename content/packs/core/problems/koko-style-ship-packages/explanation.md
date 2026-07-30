## The insight

Two pieces, and neither is hard alone.

**Feasibility.** For a fixed capacity, greedily fill each day: add packages until the next one would
not fit, then start a new day. That is forced, not a choice — leaving a package for tomorrow when it
fits today can never help, since the order is fixed and postponing only delays everything behind it.

```python
def days_needed(capacity):
    used, load = 1, 0
    for weight in weights:
        if load + weight > capacity:
            used, load = used + 1, 0
        load += weight
    return used
```

**Search.** `days_needed` is non-increasing in the capacity, so the feasible capacities form a
suffix of the integers. Binary search for its lower edge.

## The bounds

- `low = max(weights)`. Any smaller capacity can never carry the heaviest package, so no number of
  days suffices — the predicate is not merely false there, it is *undefined* in the sense that the
  greedy loop would spin. Starting at the maximum keeps the search inside the region where
  feasibility is meaningful.
- `high = sum(weights)`. Everything ships in one day, so this is certainly feasible.

Choosing bounds that make the predicate well-behaved across the whole range is the part of
binary-search-on-answer that is easy to get wrong, and it is why `low` is not `1`.

## Why monotonicity is what licenses the search

Binary search needs the predicate to switch from false to true exactly once. Here, a larger capacity
can only ever pack at least as much per day, so the day count never rises as capacity grows. One
crossing, so binary search finds it.

Same shape as [The Slowest Speed That Finishes in Time](minimum-eating-speed): the answer is not in
the input, so you search the answer space instead.

## Pitfalls

**Starting `low` at 1.** Below `max(weights)` the greedy pass cannot place the heaviest package at
all.

**Initialising `used` at 0.** The first day is used as soon as there is one package.

**Sorting or reordering the weights.** The conveyor order is fixed and the problem changes
completely without it.

**Returning `middle` on the first feasible capacity found.** A smaller one may still work; return
`low` after the loop.

## Cost

O(n log(sum of weights)) time, O(1) space.
