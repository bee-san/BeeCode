## The pigeonhole insight

Sorting costs O(n log n). To beat it you need a way to find the largest gap without
ever putting the values in order, and the way in is a counting argument.

`n` values spanning a range of `width` have `n - 1` gaps between them, so the gaps
average `width / (n - 1)`. **The largest gap is at least the average** — a maximum
is never below a mean.

Now slice the range into buckets of width `span = width / (n - 1)`, the average
itself. Two values inside one bucket differ by less than `span`, hence by less than
the maximum gap. So **the maximum gap's two ends always lie in different buckets**.

That is the whole trick. Everything inside a bucket is irrelevant except its
extremes: the gap you are looking for runs from some bucket's largest value to the
next non-empty bucket's smallest value. Two numbers per bucket, and no sorting.

## The solution

```python
def maximum_gap(nums):
    count = len(nums)
    if count < 2:
        return 0

    lowest, highest = min(nums), max(nums)
    if lowest == highest:
        return 0

    span = max(1, (highest - lowest) // (count - 1))
    bucket_count = (highest - lowest) // span + 1
    smallest = [None] * bucket_count
    largest = [None] * bucket_count

    for value in nums:
        index = (value - lowest) // span
        if smallest[index] is None or value < smallest[index]:
            smallest[index] = value
        if largest[index] is None or value > largest[index]:
            largest[index] = value

    best, previous = 0, None
    for index in range(bucket_count):
        if smallest[index] is None:
            continue
        if previous is not None and smallest[index] - previous > best:
            best = smallest[index] - previous
        previous = largest[index]
    return best
```

Four things that will bite:

**`max(1, ...)` on the span.** Integer division floors, so a tightly packed input
gives `span == 0` and the bucket index divides by zero. The all-equal case is
caught earlier, but `[1, 2, 3]` alone is enough to hit this.

**Skip empty buckets, do not treat them as zero.** An empty bucket has no values;
comparing against a `None` or a default `0` invents a gap that does not exist.

**Compare across the boundary, not inside it.** The candidate gap is *this*
bucket's smallest minus the *previous* non-empty bucket's largest. Comparing within
a bucket measures something the argument above already proved cannot be the answer.

**`previous` tracks the largest, `smallest[index]` opens the gap.** Getting these
the same way round is what makes the boundary comparison meaningful.

## Cost

O(n) time and O(n) space: one pass to find the extremes, one to fill buckets, one
over roughly `n` buckets.

Sorting is O(n log n) and, in practice, entirely reasonable here — this Problem is
worth doing for the pigeonhole argument rather than the speed. That argument, "the
maximum is at least the mean, so choose a granularity below the mean and the answer
must cross a boundary", is the reusable part.
