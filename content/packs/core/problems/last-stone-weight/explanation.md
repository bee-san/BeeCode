## The insight

The process is stated in terms of "the two heaviest", so use the structure that
answers that question: a max-heap. Pop two, push back the difference when it is
non-zero, repeat.

```python
import heapq

def last_stone(stones):
    pile = [-weight for weight in stones]
    heapq.heapify(pile)
    while len(pile) > 1:
        heaviest = -heapq.heappop(pile)
        second = -heapq.heappop(pile)
        if heaviest != second:
            heapq.heappush(pile, -(heaviest - second))
    return -pile[0] if pile else 0
```

`heapify` is O(n) — cheaper than `n` pushes, and worth using when you have all the
values up front.

## Negating for a max-heap

Python's `heapq` is a min-heap only. Storing negated weights and negating on the way
out turns it into a max-heap. It is the standard idiom and the sign errors are the
standard bug: negate on push *and* on pop, never one or the other, and remember that
`pile[0]` is also negated.

## Why greedy is right here

It is not an optimisation — the rules *dictate* taking the two heaviest, so there is
no choice to make and nothing to prove. This is simulation, not greedy search. Worth
noticing, because the superficially similar question "what is the *smallest* possible
remaining stone if you may choose freely?" is a partition problem
([Partition Into Two Equal-Sum Subsets](partition-equal-subset-sum)) and needs
dynamic programming.

## Pitfalls

**Re-sorting the list every round.** Correct, and O(n^2 log n) overall. With `n <= 30`
it passes, so say why the heap is better rather than claiming the sort fails.

**Pushing a zero difference.** Equal stones destroy each other; pushing `0` leaves a
phantom stone that becomes the answer.

**Stopping at an empty pile without a guard.** All stones can be destroyed, and
`pile[0]` on an empty heap raises.

**Forgetting `x <= y`.** Pop order determines which is which; `second - heaviest`
gives a negative weight.

## Cost

O(n log n) time, O(n) space.
