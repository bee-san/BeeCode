## The insight

Binary search on an unsorted list sounds like a category error. Binary search needs a
rule for discarding half the range, and sortedness is the usual source of that rule.

But sortedness is not the only source. What binary search actually needs is a
**guarantee that the answer survives in the half you keep**, and here that guarantee
comes from the shape of the list rather than its order.

Look at any adjacent pair, `nums[middle]` and `nums[middle + 1]`. They are never equal,
so exactly one of two things holds:

- **`nums[middle] < nums[middle + 1]`** — the list is going *up* at this point. Walk
  right. Either it keeps rising to the end, in which case the last element is a peak
  (its right neighbour is negative infinity), or it turns down somewhere, and the
  element where it turns is a peak. Either way a peak exists strictly to the right.
- **`nums[middle] > nums[middle + 1]`** — going *down*. By the mirror argument a peak
  exists at `middle` or to its left.

That is a valid discard rule, so the range halves each step: O(log n).

## The loop

```python
def find_peak(nums):
    low, high = 0, len(nums) - 1
    while low < high:
        middle = (low + high) // 2
        if nums[middle] < nums[middle + 1]:
            low = middle + 1
        else:
            high = middle
    return low
```

The invariant is *`[low, high]` contains at least one peak*, and both branches preserve
it. When `low == high` the range holds exactly one element, and by the invariant that
element is a peak — so no final bounds check is needed.

Note `high = middle`, not `middle - 1`. In the descending case `middle` itself might be
the peak, so it must stay in the range. Writing `middle - 1` there is the classic way to
make this loop skip the answer.

## Why the ends need no special case

The rule "outside the list counts as negative infinity" is what removes the boundary
handling. The first element only ever needs to beat its right neighbour, and the last
only its left. A monotonic list therefore always has a peak at whichever end it rises
towards, which is why `[1, 2, 3, 4, 5]` answers `4` rather than "no peak".

## Any peak, not the largest

Several peaks can exist, and the Problem accepts any of them — hence the `any_of`
comparator in the test suite rather than a single expected index.

This is not laziness in the specification, it is the reason the Problem is solvable in
O(log n). Finding the *largest* element requires examining every element, so it is
Ω(n). Discarding half the list means you will never see most of the values, so you
cannot possibly know which peak is highest. Weakening "the maximum" to "a local
maximum" is precisely what buys the logarithmic bound.

## Why this Problem is worth repeating

It separates the *technique* of binary search from the *precondition* people memorise
alongside it. The reusable idea is: find a cheap test on a midpoint whose result proves
an answer still exists on one side. Rotated sorted arrays, `sqrt` by bisection, and
"minimum capacity to ship in D days" are all the same move with different tests.
