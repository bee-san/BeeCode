## The insight

Sorted means equal values are adjacent, so "have I seen this before?" collapses to "is it the same
as the last one I kept?".

```python
write = 1
for read in range(1, len(values)):
    if values[read] != values[write - 1]:
        values[write] = values[read]
        write += 1
return write
```

## Why `write` starts at 1

The first element is always kept — a non-empty list has at least one distinct value — so position
`0` is settled before the loop begins. Starting `write` at `0` would compare against
`values[-1]`, the last element of the list, which in a sorted list is the largest value. On
`[1, 1, 2]` that is `2`, so the first `1` is written, then the second `1` is written again, and the
answer comes out too large.

Comparing against `values[write - 1]` rather than `values[read - 1]` is the other half: the last
*kept* value is what matters, not the previous value scanned. In this problem the two happen to
coincide, because every duplicate run is contiguous — but in the "keep at most two of each"
variant they diverge, and `values[write - 2]` is exactly the comparison that generalises.

## Why the tail is left alone

Truncating a list in place is not something every language offers, so the convention is to return
the count and leave the remainder as scratch. That is why the tests assert on the returned count,
not on the whole list — asserting on the tail would be asserting on something the statement
declares meaningless.

## Pitfalls

**Using a set.** Correct and O(n) space, discarding the sortedness that makes O(1) possible.

**Returning `len(values)`.** The list is not shortened; the count is the answer.

**Comparing against `values[read - 1]`.** Works here, and is the habit that breaks on the
keep-two variant.

**A single element.** The loop does not run and `1` is returned.

## Cost

O(n) time, O(1) space.
