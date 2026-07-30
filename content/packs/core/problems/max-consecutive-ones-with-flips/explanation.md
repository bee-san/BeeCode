## The insight

A run of ones after flipping is exactly a window of the original list holding at most `budget`
zeroes. So grow the window on the right, and whenever it holds too many zeroes, shrink it on the
left until it is legal again:

```python
best = zeroes = low = 0
for high, bit in enumerate(bits):
    if bit == 0:
        zeroes += 1
    while zeroes > budget:
        if bits[low] == 0:
            zeroes -= 1
        low += 1
    best = max(best, high - low + 1)
return best
```

## Why the shrink is a `while` and not an `if`

Adding one element can push the zero count over by at most one, so in this problem a single shrink
step always suffices — an `if` would work. The `while` is still what to write: it states the
invariant ("the window is legal before we measure") rather than relying on an increment-by-one
argument that is easy to break when the constraint changes.

## Why the window never needs to shrink permanently

`low` only ever increases, so both pointers sweep the list once and the whole thing is O(n) despite
the nested loop. Each element is added once and removed at most once.

## The variant that does not track the count down

A common alternative never shrinks the window, letting it slide instead: when illegal, advance both
ends together, so the window keeps its size and the answer is the final size. It works because we
only care about the *maximum*, so a window that stops growing does no harm. Slightly slicker, and
harder to explain — worth recognising, and the shrinking version is the one to reach for.

## `budget` of zero

The window may contain no zeroes at all, so the answer is the longest existing run of ones — `0`
when there are none. Handled without a special case.

## Pitfalls

**Shrinking while `zeroes >= budget`.** Off by one; the window may hold exactly `budget` zeroes.

**Measuring before restoring legality.** The measurement must come after the shrink.

**Tracking the ones rather than the zeroes.** The constraint is on the zeroes.

**Resetting the window on every zero.** That answers a different question — the longest run with no
flips at all.

## Cost

O(n) time, O(1) space.
