## The insight

Choosing the **first** balloon to burst does not decompose: after it goes, the two sides can
still interact, because a later burst on the left can see a neighbour on the right.

Choosing the **last** balloon in a range does decompose. If `last` is the final balloon burst
strictly between boundaries `left` and `right`, then at the moment it bursts everything between
them is already gone, so its neighbours are exactly `padded[left]` and `padded[right]` — known
values, fixed in advance. And everything burst before it happened entirely within
`(left, last)` or within `(last, right)`, never across, because `last` was still standing
between them.

```text
best[left][right] = max over last in (left, right) of
    padded[left] * padded[last] * padded[right] + best[left][last] + best[last][right]
```

`best[left][right]` covers the balloons strictly between the two boundaries, so
`best[left][left+1]` is `0` — nothing between adjacent boundaries.

## The padding

Prepending and appending `1` turns "a missing neighbour counts as 1" into an ordinary index
lookup, so the recurrence has no special cases at the ends. The answer is
`best[0][size - 1]`, the whole padded span.

## Filling order

`best[left][right]` needs strictly narrower ranges, so iterate by increasing width. Width `2`
is the base case with nothing in between; the outer loop over width is what makes the
dependencies always already computed.

## Why not greedy

"Burst the smallest first" sounds plausible and is wrong. In `[3, 1, 5, 8]` bursting `1` first
is indeed right, but only because it lets `3` and `5` become neighbours — and no local rule
predicts when that pays. Any greedy rule loses on some input; the range DP is the answer.

## Pitfalls

**Reasoning about the first burst.** Does not decompose; this is the whole lesson.

**Iterating by left endpoint instead of width.** Reads cells not yet filled.

**Inclusive range bounds.** The boundaries are *excluded* from the range they delimit, and
mixing that up produces double-counted balloons.

**Forgetting the padding.** Every end case then needs its own branch.

**Assuming a zero balloon must be handled specially.** It earns nothing whenever it is burst,
and bursting it only joins its neighbours, which never hurts — so zeroes may safely be dropped
from the input before the table is built. Worth knowing, but not worth writing: the recurrence
already handles them.

## Cost

O(n^3) time, O(n^2) space. For `n = 300` that is 27 million steps — fine, and it is why the
constraint stops there.
