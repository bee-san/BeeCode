## The insight

At any moment the next value of the merged output is the smallest of the `k`
front-most unconsumed values — one per list. A heap of size `k` gives you that
smallest in O(log k), so keep exactly one candidate per list in it.

```python
import heapq

def merge_all(lists):
    frontier = []
    for index, values in enumerate(lists):
        if values:
            heapq.heappush(frontier, (values[0], index, 0))
    merged = []
    while frontier:
        value, index, position = heapq.heappop(frontier)
        merged.append(value)
        if position + 1 < len(lists[index]):
            heapq.heappush(frontier, (lists[index][position + 1], index, position + 1))
    return merged
```

Each of the `N` values is pushed once and popped once, so O(N log k).

## Why the tuple has three parts

`(value, index, position)` — and the last two are not decoration. `index` breaks
ties, which matters in Python because comparing tuples falls through to the next
element and comparing two *nodes* would raise `TypeError`. With plain integers it
merely makes the order deterministic. `position` says where to refill from.

## The other O(N log k)

Merge the lists in pairs, then merge the results in pairs, and so on: `log k`
rounds, each touching all `N` values. No heap, and the two-list merge is a
primitive you already have from [Merge Two Sorted Lists](merge-sorted-lists). Many
people find this easier to get right, and it parallelises.

Merging them one at a time into an accumulator is the trap: the accumulator is
re-walked every round, giving O(Nk).

## Pitfalls

**Pushing empty lists.** `values[0]` raises. Filter first.

**Refilling from the wrong list.** The popped tuple must say which list it came
from; recomputing it from the value is wrong when values repeat.

**`sorted(chain(*lists))`.** O(N log N), one line, and genuinely fine in Python
where the sort is C-speed and adaptive. Say it, then give the heap version — the
interviewer is asking whether you can use the sortedness.

## Cost

O(N log k) time, O(k) extra space for the heap.
