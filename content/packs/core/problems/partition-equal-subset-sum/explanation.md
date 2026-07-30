## The insight

Two steps.

**The parity check.** An odd total cannot split into two equal integers. That single line
answers the second example, and it is worth doing first because it is free.

**Subset sum.** With `half = total // 2`, the question is whether any subset sums to exactly
`half` — the rest then automatically sums to `half` too, so only one side needs checking.

```python
reachable = [False] * (half + 1)
reachable[0] = True
for value in values:
    for target in range(half, value - 1, -1):
        if reachable[target - value]:
            reachable[target] = True
return reachable[half]
```

## Why the inner loop counts downwards

This is the detail that decides correctness. `reachable[target - value]` must mean "reachable
*without* this value", so it has to be read before this pass writes to it. Iterating upwards
lets a value be used many times — a fresh `True` at a low index is immediately read at a
higher one — which silently answers the unbounded-supply version instead. `[1, 3]` returns
`True` upwards (1 + 1 + ... reaching 2) and `False` downwards, which is correct.

Descending is the standard way to express "each item at most once" in a one-dimensional
knapsack table. The two-dimensional form, indexed by item and target, has no such hazard —
it costs O(n * half) space instead of O(half).

## Why one pass suffices

After processing the first `k` values, `reachable[t]` is exactly "some subset of those `k`
sums to `t`". Adding one more value can only add new reachable sums, computed from the old
ones, so a single pass per value is complete. No iteration to a fixed point is needed.

## Neither group empty

Because every value is at least 1, `half` is at least 1 whenever a split exists, so a
subset summing to `half` is non-empty and its complement is too. No extra check.

## Pitfalls

**Iterating the target upwards.** Allows reuse, as above.

**Forgetting the parity check.** `half` becomes a truncated non-integer half and the answer
is wrong rather than merely slow.

**`reachable[0] = False`.** Nothing is reachable.

**Enumerating all subsets.** `2^200`.

## Cost

O(n * total) time, O(total) space — pseudo-polynomial, since it depends on the magnitude of
the values rather than only their count.
