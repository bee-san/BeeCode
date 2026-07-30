## The insight

Merge sort already compares every element that could possibly be inverted — it just
throws the information away. Recover it and the count comes almost free.

Split the list in two. Every inversion is one of three kinds:

- both positions in the left half,
- both in the right half,
- one in each.

The first two are counted by recursion. The third is counted **during the merge**,
and that is the whole trick.

## Counting during the merge

Both halves are sorted by the time you merge them. When you take an element from
the **right** half, it means it was smaller than `left[i]` — the smallest remaining
element on the left. Since the left half is sorted, `left[i]` *and every element
after it* are also greater than this one. All of them sit at earlier positions in
the original list.

So one `+= len(left) - i` counts an entire block of inversions at once:

```python
def count_inversions(nums):
    def sort_and_count(values):
        if len(values) <= 1:
            return values, 0

        middle = len(values) // 2
        left, left_count = sort_and_count(values[:middle])
        right, right_count = sort_and_count(values[middle:])

        merged = []
        total = left_count + right_count
        i = j = 0
        while i < len(left) and j < len(right):
            if left[i] <= right[j]:
                merged.append(left[i])
                i += 1
            else:
                merged.append(right[j])
                j += 1
                total += len(left) - i
        merged.extend(left[i:])
        merged.extend(right[j:])
        return merged, total

    return sort_and_count(list(nums))[1]
```

Three details decide correctness:

**`<=` on the tie, not `<`.** Equal values are not inversions. Using `<` sends the
right element first and counts the whole remaining left block, inflating the answer
on any input with repeats — and passing cleanly on inputs with distinct values.

**Count when taking from the *right*, not the left.** Taking from the left means the
elements are already in order; there is nothing to count.

**`len(left) - i`, not `1`.** Counting one per step gives the right answer only when
the halves interleave perfectly. The point of the technique is counting a block at a
time, which is what keeps it O(n log n) instead of degenerating.

The count is a byproduct; the sorted list is what makes the next level's count
correct, so both return values matter even though only one is the answer.

## Cost

O(n log n) time — the merge sort's own cost, with counting folded in at no extra
asymptotic charge — and O(n) space for the merged copies.

The brute force is O(n²). At 50,000 elements that is 1.25 billion comparisons
against roughly 800,000 merge steps.

A Fenwick tree over compressed values gives the same O(n log n) by a different
route: sweep left to right, and for each element count how many already-inserted
values exceed it. Worth knowing, because the tree generalises to queries the merge
does not.
