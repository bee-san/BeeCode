## The insight

This is the lower bound and nothing else: the first index whose value is at least `target`. If the
target is present that index holds it; if not, that is precisely where it belongs.

```python
low, high = 0, len(values)
while low < high:
    middle = (low + high) // 2
    if values[middle] < target:
        low = middle + 1
    else:
        high = middle
return low
```

No match check, no separate not-found branch. The two questions have the same answer, which is why
the problem is easier than it first looks.

## Why `low` is the answer

The invariant is: every index below `low` holds a value strictly less than `target`, and every index
at or above `high` holds a value at least `target`. Both hold vacuously at the start —
`low = 0` and `high = len(values)` make both ranges empty — and each step preserves them.

When `low == high`, the two ranges meet and cover everything, so `low` is the boundary between "below
target" and "at least target". That is the insertion point by definition.

Reasoning from the invariant rather than from a traced example is what makes the off-by-ones stop
being guesswork.

## Why `high` starts past the end

The answer can be `len(values)`, when every value is below the target. A search bounded at
`len(values) - 1` cannot return it. This is the same half-open convention as in
[Where Does the Value Begin and End](first-and-last-position), and for the same reason.

## The overflow note

`(low + high) // 2` can overflow in a fixed-width language when both are near the maximum;
`low + (high - low) // 2` cannot. Python's integers are unbounded so it does not arise here, and the
safer form costs nothing.

## Pitfalls

**Returning `-1` when absent.** The insertion point is the answer.

**`high = len(values) - 1`.** Cannot return the past-the-end position.

**`low = middle` instead of `middle + 1`.** The range stops shrinking and the loop never ends.

**An empty list.** Returns `0`, which is correct — that is where anything would go.

## Cost

O(log n) time, O(1) space.
