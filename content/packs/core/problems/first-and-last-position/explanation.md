## The insight

Write one helper — the **lower bound**, the first index whose value is at least `wanted`:

```python
def lower_bound(wanted):
    low, high = 0, len(values)
    while low < high:
        middle = (low + high) // 2
        if values[middle] < wanted:
            low = middle + 1
        else:
            high = middle
    return low
```

Then both answers fall out:

- `first = lower_bound(target)`, valid only if that index holds `target`.
- `last = lower_bound(target + 1) - 1` — the position just before the first value that exceeds
  `target`.

One helper, called twice, instead of two nearly-identical searches with mirror-image off-by-ones.
That is the version worth memorising.

## What makes it find the leftmost

A standard binary search returns as soon as it finds a match. This one has no early return: on a
match it sets `high = middle`, keeping the match in range and continuing to look left. The loop
ends when `low == high`, and that index is the first position where the value is at least `wanted`.

The absence of the early return *is* the change. It is also why the loop is `low < high` with `high`
starting at `len(values)` rather than `len(values) - 1`: the answer may legitimately be "past the
end", when every value is below `wanted`.

## Why `target + 1` works, and where it would not

`lower_bound(target + 1)` is the first index above every occurrence of `target`, so one less is the
last occurrence. This relies on the values being integers — with floats there is no "next" value,
and you would need a separate upper-bound helper testing `<=` instead of `<`.

Worth naming, because the trick is clean and its assumption is invisible.

## The absent case

If `first` is `len(values)`, or the value there is not `target`, the target does not occur. Checking
that once is enough — no need to validate `last` separately, since it is derived from a range that
is known to contain the target.

## Pitfalls

**Finding any occurrence, then scanning outwards.** O(n) when the list is all one value.

**`high = len(values) - 1` with the half-open loop.** Misses the case where the answer is the last
index.

**Returning `first` without checking the value there.** `lower_bound` returns an insertion point,
not a match.

**An empty list.** `lower_bound` returns `0`, which equals `len(values)`, so the absent branch
fires.

## Cost

O(log n) time, O(1) space.
