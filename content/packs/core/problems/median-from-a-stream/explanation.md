## The insight

Keep the values in two halves, each with its boundary exposed:

- **`lower`** — a max-heap of the smaller half, so its root is the largest small value
- **`upper`** — a min-heap of the larger half, so its root is the smallest large value

Maintain two invariants: every value in `lower` is at most every value in `upper`, and
their sizes differ by at most one. Then the median is either `lower`'s root (odd
count, `lower` holding the extra) or the mean of the two roots (even count). Both are
O(1).

## Adding without case analysis

The naive version compares the new value against the roots and branches. This is
shorter and has no cases at all:

```python
heapq.heappush(lower, -value)                    # goes into the lower half
heapq.heappush(upper, -heapq.heappop(lower))     # its largest moves up
if len(upper) > len(lower):
    heapq.heappush(lower, -heapq.heappop(upper)) # rebalance
```

Push into `lower`, immediately move `lower`'s maximum into `upper`, then rebalance if
`upper` got too big. The round trip is what places the value correctly: whatever it
was, `lower`'s largest is now in the right half. No comparison against either root is
needed, and the ordering invariant maintains itself.

## The negation

Python's `heapq` is a min-heap, so `lower` stores negated values to act as a max-heap.
Every push negates and every read negates back — including `lower[0]`. Sign errors
here produce a median that is subtly wrong rather than crashing, so check against a
two-element case where the answer is a `.5`.

## Pitfalls

**Letting the halves drift.** If sizes differ by two the median is not at a boundary
any more. Rebalance on every add, not lazily.

**Integer division.** `(a + b) // 2` gives `1` where `1.5` is correct. This is the
single most common bug, and the reason this Problem's tests compare numerically.

**Which half holds the extra.** Pick a convention — here `lower` — and read the odd
case from that same heap. Mixing them returns a neighbour of the median.

**Sorting on each query.** Correct, and quadratic overall. `bisect.insort` into a
sorted list is O(n) per insert from the shifting, which is a real improvement in
practice but still not O(log n).

## Cost

O(log n) per add, O(1) per median, O(n) space.
