## The insight

Let `reach[i]` be the least cost to *arrive at* position `i`, where position `len(cost)` is
the point past the top. You arrive from one stair below or two:

```text
reach[i] = min(reach[i-1] + cost[i-1], reach[i-2] + cost[i-2])
```

`reach[0] = reach[1] = 0`, because starting on either of the first two stairs is free —
you pay only when you step *off*.

The answer is `reach[len(cost)]`, one past the end. Returning `reach[len(cost) - 1]` stops
on the top stair without leaving it, which is the single most common error and is invisible
on inputs where the last cost is cheap.

## Two variables instead of a table

Each value depends only on the two before it, so the whole table collapses:

```python
two_below = one_below = 0
for index in range(2, len(cost) + 1):
    cheapest = min(one_below + cost[index - 1], two_below + cost[index - 2])
    two_below, one_below = one_below, cheapest
return one_below
```

O(1) space instead of O(n). This rolling-variable move works for any recurrence with a
fixed, small look-back — see [Climbing Stairs](climbing-stairs) for the counting version of
the same shape.

## Cost on the stair, not on the step

`cost[i]` is charged for leaving stair `i`, so the cost belongs to the stair you depart,
not the one you land on. Reading it the other way produces an answer that is right for the
first example and wrong for the second.

## Pitfalls

**Returning the value at the last stair.** Off by one, as above.

**Paying to start.** Stairs `0` and `1` are free to stand on.

**Iterating to `len(cost)` exclusive.** The loop must reach `len(cost)` inclusive to
compute the point past the top.

**A greedy "always take the cheaper next stair".** Fails on the second example, where
paying `1` twice beats a single detour.

## Cost

O(n) time, O(1) space.
