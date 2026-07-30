## The insight

Do not think about basins. Think about one position at a time. The water standing
above position `i` depends on nothing but the tallest bar to its left, the tallest
to its right, and its own height:

```
water(i) = max(0, min(left_max(i), right_max(i)) - heights[i])
```

Write it that way and the Problem becomes "compute two arrays of running maxima",
which is two loops and needs no cleverness at all. That version is worth writing
first — it is where the formula becomes obvious.

## Down to O(1) space

Now the two-pointer form. Keep `left_max` and `right_max` for the parts already
seen, and always advance from the side whose maximum is **smaller**.

That side's answer is fully determined. If `left_max <= right_max`, then whatever
lies between the pointers, the right side already guarantees a wall at least
`right_max` tall — so `min(left_max, right_max)` is `left_max`, and the water at
the position you are stepping onto is `left_max - heights[left]`. You never need
to know the true right maximum, only that it is not the limiting one.

```python
def trapped_water(heights):
    if not heights:
        return 0
    left, right = 0, len(heights) - 1
    left_max, right_max = heights[left], heights[right]
    total = 0
    while left < right:
        if left_max <= right_max:
            left += 1
            left_max = max(left_max, heights[left])
            total += left_max - heights[left]
        else:
            right -= 1
            right_max = max(right_max, heights[right])
            total += right_max - heights[right]
    return total
```

Updating the maximum *before* adding makes the `max(0, ...)` clamp unnecessary:
if the new bar is the tallest so far it becomes `left_max`, and the contribution
is exactly zero rather than negative.

## Pitfalls

**Confusing this with Container With Most Water.** Same two-pointer skeleton,
different quantity: there you want one best pair, here you accumulate every
position. Copying the loop body across gives nonsense.

**Advancing from the taller side.** Then `min` is the maximum you have *not*
established, and the arithmetic is unfounded.

**Forgetting the empty list.** `heights[0]` raises before the loop can protect
you.

**Subtracting without clamping** in the precomputed-array version. Positions that
are themselves the local peak yield zero, not a negative.

## Cost

O(n) time, O(1) extra space. The precomputed version is O(n) space and equally
fast — and easier to defend under pressure.
