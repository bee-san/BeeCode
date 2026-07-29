## Why merging is the wrong instinct

Merging both lists and taking the middle is correct and O(n + m). Merging only up to the
halfway point is a common "optimisation" that changes nothing asymptotically. To beat
linear time you must never look at most of the values, which means you can never build
the merged list at all.

## The reframing

The median does not need the merged list. It needs a **partition** of it: a left half
and a right half of the right sizes, such that everything on the left is at most
everything on the right. Once you have that, the median is determined by the two values
straddling the boundary — the largest on the left and the smallest on the right.

Here is the move. Choose `take_a`, how many of `a`'s values go in the left half. That
immediately fixes `take_b`, because the left half must hold `half` values in total:

```
take_b = half - take_a          where half = (n + m + 1) // 2
```

So there is only **one** free variable, and it ranges over `0..len(a)`. The partition is
determined by a single number, and that number can be binary-searched.

## The test for a correct cut

A cut is correct when neither side reaches across the boundary:

```
left_a <= right_b   and   left_b <= right_a
```

If `left_a > right_b`, you took too many from `a`: shrink `take_a`. Otherwise you took
too few: grow it. That is a valid discard rule, so the search is
O(log(min(n, m))) — after swapping so `a` is the shorter list.

```python
if left_a <= right_b and left_b <= right_a:
    if total % 2 == 1:
        return float(max(left_a, left_b))
    return (max(left_a, left_b) + min(right_a, right_b)) / 2.0
```

## The infinities are not a trick

```python
left_a  = a[take_a - 1] if take_a > 0     else float("-inf")
right_a = a[take_a]     if take_a < n     else float("inf")
```

`take_a` may legitimately be `0` (take nothing from `a`) or `n` (take all of it), and
either way one of these lookups has no value to read. Treating a missing left value as
negative infinity and a missing right value as positive infinity makes those cases
satisfy the condition automatically, because an infinity is never the binding
constraint.

Without them you need four boundary branches, and the empty-list cases become special.
With them, `[[], [1, 2, 3]]` is handled by the same three lines as everything else. This
is the same device that makes the "outside the array is negative infinity" rule work in
*Find a Peak Element*.

## Three traps

- **Binary-searching the longer list.** Then `take_b = half - take_a` can fall outside
  `b`, and the lookups break. Swapping so `a` is shorter is what guarantees the derived
  cut is always in range — and it is also what makes the bound `log(min(n, m))` rather
  than `log(max(n, m))`.
- **Strict comparisons in the split test.** With duplicates spanning both lists —
  `[2, 2, 2]` and `[2, 2]` — `left_a` can equal `right_b` at the correct cut. Using `<`
  instead of `<=` rejects it and the search never terminates.
- **Integer division on the result.** With an even total the answer is a genuine
  half-integer. Dividing with `//`, or returning an `int`, turns `2.5` into `2`.

## Why this Problem is worth repeating

It is the hardest common instance of "binary search the answer rather than the data".
The list is sorted, but you are not searching *it* — you are searching the space of
possible partitions, and sortedness is only what makes the partition test cheap.

That separation is the transferable idea, and it is the same one *Find a Peak Element*
teaches in easier form: identify a single parameter that determines a candidate answer,
find an O(1) test that says "too high" or "too low", and the logarithm follows.
