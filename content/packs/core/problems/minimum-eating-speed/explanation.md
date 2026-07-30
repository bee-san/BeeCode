## The insight

Binary search does not require a sorted array. It requires a **monotonic predicate** —
a yes/no question whose answer, once it flips to yes, stays yes.

Here the predicate is "can I finish at this speed?" A faster speed never needs more
hours, so feasibility looks like `no no no yes yes yes` across the speed range, and the
answer is the first `yes`.

The bounds:

- **Lower: 1.** Speeds are positive integers.
- **Upper: `max(piles)`.** At that speed every pile takes exactly one hour, so the
  total is `len(piles)` hours — and `hours >= len(piles)` is guaranteed, so this speed
  always works. A feasible upper bound is what makes the search well-defined.

```python
low, high = 1, max(piles)
while low < high:
    middle = (low + high) // 2
    if hours_needed(middle) <= hours:
        high = middle          # feasible: it may be the answer, so keep it
    else:
        low = middle + 1       # infeasible: the answer is strictly larger
return low
```

## The feasibility test

Each pile takes `ceil(pile / speed)` hours, because a partial pile still consumes a
whole hour. Integer division alone (`pile // speed`) is the classic error: it drops the
final partial hour and declares slow speeds workable.

`-(-pile // speed)` is ceiling division without floats — worth preferring over
`math.ceil(pile / speed)`, which converts to a float and at `10^9` is flirting with
precision you do not need to risk.

## Why `high = middle`, not `middle - 1`

`middle` is feasible, so it is still a candidate for the *minimum* — excluding it can
skip the answer. And with `low < high` as the loop condition plus `low = middle + 1` on
the other branch, the range always shrinks, so this cannot spin forever. Those three
choices are a set; changing one in isolation is how this loop hangs.

## Cost

O(n log(max pile)) — about 30 iterations of an O(n) check at the constraint limits.
