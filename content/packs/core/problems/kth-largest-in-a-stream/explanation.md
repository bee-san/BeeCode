## The insight

Keep a **min-heap holding exactly the `k` largest values seen so far**. Its root — the
smallest of those `k` — *is* the `k`th largest overall.

That inversion is the trick worth internalising. To track the `k` largest you want
cheap access to the weakest of them, because that is the one a new arrival has to beat.

```python
import heapq

def add(value):
    heapq.heappush(heap, value)
    if len(heap) > k:
        heapq.heappop(heap)     # discard the smallest; it is not in the top k
    return heap[0]
```

Push unconditionally, then trim if too large. The heap never exceeds `k + 1` entries,
so `add` is O(log k) regardless of how many values have streamed through — the memory
does not grow with the stream, which is the property that matters when it does not
end.

## Why not a max-heap

A max-heap of everything gives the largest in O(1), but reaching the `k`th means
popping `k - 1` values and putting them back. And it must retain every value ever
seen. The min-heap of size `k` discards what can never matter again.

## Pitfalls

**Trimming before pushing.** Check the size after the push, or the heap never reaches
`k` and the root is the wrong value.

**Discarding equal values.** Duplicates count separately, so a new value equal to the
root still belongs. Pushing unconditionally handles this; a comparison like
`if value > heap[0]` needs care about ties, and a set is simply wrong.

**Sorting the initial list and slicing.** Fine for the setup, O(n log n) once. It is
using it for every `add` that is the mistake.

**Negative values.** They are allowed, so a heap seeded with zeros or an assumption
that values are positive breaks.

## Cost

O(len(initial) log k) to set up, then O(log k) per `add`. O(k) space.
