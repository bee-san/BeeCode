## The insight

Sort, then fix the smallest of the three and let the two-pointer scan from
[Two Sum in a Sorted List](two-sum-sorted) find the other two inside the
remaining suffix. Sorting costs O(n log n) once and buys two things: the
two-pointer step, and duplicates sitting next to each other where you can see
them.

```python
def three_sum(nums):
    ordered = sorted(nums)
    found = []
    for first in range(len(ordered) - 2):
        if ordered[first] > 0:
            break
        if first > 0 and ordered[first] == ordered[first - 1]:
            continue
        left, right = first + 1, len(ordered) - 1
        while left < right:
            total = ordered[first] + ordered[left] + ordered[right]
            if total < 0:
                left += 1
            elif total > 0:
                right -= 1
            else:
                found.append([ordered[first], ordered[left], ordered[right]])
                left += 1
                right -= 1
                while left < right and ordered[left] == ordered[left - 1]:
                    left += 1
                while left < right and ordered[right] == ordered[right + 1]:
                    right -= 1
    return found
```

## Deduplication happens twice

This is where the Problem is won or lost, and the two places are genuinely
different.

**Outer.** If `ordered[first]` equals the previous first value, every triplet it
could produce was already produced. Skip it. Note the `first > 0` guard: without
it, index `-1` wraps to the largest element and the first candidate is skipped
whenever the input happens to end in the same value.

**Inner.** After recording a hit, both pointers move once — and then must keep
moving over any repeats of the values just used. Otherwise `[0, 0, 0, 0]`
reports the same triplet twice.

An alternative is a set of tuples, which is shorter and hides the reasoning. It
works; it just teaches less, and it costs the space of every triplet found.

## Pitfalls

**Skipping the `total > 0` break.** Once the smallest of the three is positive,
no triplet can reach zero. Optional, but cheap.

**Positions versus values.** Two equal values at different positions are a legal
pair, but they do not make two answers. `[0, 0, 0, 0]` is one triplet.

## Cost

O(n^2) time: one two-pointer scan of length O(n) per choice of first element,
plus the O(n log n) sort. O(1) extra space beyond the sorted copy and the output.
