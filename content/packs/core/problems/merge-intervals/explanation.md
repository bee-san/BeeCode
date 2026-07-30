## The insight

In the raw input, an interval might overlap anything — you would have to compare every
pair. **Sort by start**, and that stops being true: once the starts are ordered, a new
interval can only overlap the merged block you are currently building. Everything
earlier ended before this one began.

```python
ordered = sorted(intervals, key=lambda pair: pair[0])
merged = [list(ordered[0])]
for start, end in ordered[1:]:
    if start <= merged[-1][1]:          # overlaps the open block
        merged[-1][1] = max(merged[-1][1], end)
    else:                                # a gap: start a new block
        merged.append([start, end])
```

## Three ways to get this wrong

**`<=`, not `<`.** The statement says touching intervals merge, so `[1, 4]` and
`[4, 5]` must become `[1, 5]`. Using `<` returns them separately. Whether touching
counts is a decision the *problem* makes, so read it rather than assume.

**Extend with `max`, not with `end`.** A fully contained interval like `[2, 3]` inside
`[1, 10]` overlaps, so you enter the merge branch — and assigning `end` directly
*shrinks* the block from 10 to 3, silently corrupting later comparisons. `max` is what
makes containment harmless.

**Copy the first interval before mutating it.** `merged = [ordered[0]]` stores a
reference into the caller's data, and `merged[-1][1] = end` then writes through to it.
`list(ordered[0])` avoids modifying the input.

## Cost

O(n log n) time, dominated entirely by the sort — the scan itself is one pass. O(n) for
the output. If the input arrived already sorted, the whole thing would be linear, which
is a useful thing to notice about problems in this family.
