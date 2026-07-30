## The insight

Two things to notice, one about the geometry and one about the selection.

**Drop the square root.** Distance is `sqrt(x*x + y*y)`, but `sqrt` is monotonic —
comparing squared distances gives exactly the same ordering. So compare `x*x + y*y`
and avoid both the cost and the floating-point imprecision. Nothing in the answer
depends on the actual distance, only on the order.

**Keep a max-heap of size `k`.** The mirror image of
[Kth Largest in a Stream](kth-largest-in-a-stream): to track the `k` *smallest*
distances you want cheap access to the largest of them, because that is the one a new
point must beat.

```python
import heapq

for x, y in points:
    heapq.heappush(heap, (-(x * x + y * y), x, y))
    if len(heap) > k:
        heapq.heappop(heap)          # drop the furthest; it is not in the closest k
```

Negating makes Python's min-heap behave as a max-heap. Popping the heap at the end
yields the points furthest-first, so reverse.

## Quickselect

The genuinely better answer when all the points are in memory: partition around a
pivot distance until the `k`th smallest sits at index `k - 1`. Everything before it is
the answer set, in no particular order — then sort just those `k`. Expected O(n),
worst case O(n^2) on adversarial pivots, which a random pivot makes vanishingly
unlikely.

The heap wins when the points arrive as a stream, since it never needs them all at
once. State which situation you are in before choosing.

## Pitfalls

**Sorting the whole list.** O(n log n), one line, and the honest baseline. Give it,
then improve on it.

**Using `sqrt`.** Not wrong, just wasteful, and it introduces rounding where integers
would have been exact.

**Forgetting to sort the result.** The output order is by distance. A heap-of-`k`
pops furthest-first and quickselect leaves the prefix unordered — both need a final
ordering step.

**Tuples that compare by point.** With distinct distances the first element always
decides, so nothing else is compared. With ties, a tuple whose next element is a list
would raise; include the coordinates as separate scalars, as above.

## Cost

O(n log k) time and O(k) space for the heap. Expected O(n) for quickselect, plus
O(k log k) to order the winners.
