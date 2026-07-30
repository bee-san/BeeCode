## The insight

Count the cards, then walk the distinct values in increasing order. The smallest value still
present must start a group — no smaller card exists to sit below it — so if it appears `k`
times, there must be `k` groups all starting there, which needs `k` copies of each of the next
`size - 1` values.

```python
if len(cards) % size != 0:
    return False
remaining = Counter(cards)
for card in sorted(remaining):
    needed = remaining[card]
    if needed <= 0:
        continue
    for step in range(size):
        if remaining[card + step] < needed:
            return False
        remaining[card + step] -= needed
return True
```

The `needed <= 0` skip matters: a value fully consumed by earlier groups is not a group start,
and treating it as one double-counts.

## Consume all copies at once

Taking `needed` groups in one go, rather than one group at a time, is what keeps this
O(d * size) in the number of distinct values rather than O(n * size). It is also easier to
reason about: after the inner loop, that value is exhausted, full stop.

## Divisibility first

`len(cards) % size != 0` is an immediate `False`. It is cheap and it removes a whole class of
inputs the grouping logic would otherwise have to notice indirectly.

## Why greedy is safe

The exchange argument is short. In any valid dealing, the smallest card sits in *some* group,
and it must be that group's lowest card. So the group is forced: it is exactly
`card, card+1, ..., card+size-1`. Nothing was chosen, so nothing can have been chosen wrongly.

## Pitfalls

**Skipping the divisibility check.** Usually caught later, but not always cleanly.

**Not skipping exhausted values.** Double-counts groups.

**Sorting the cards rather than the distinct values.** Works, but does redundant passes over
duplicates.

**`size == 1`.** Always `True` — every card is its own group.

**Assuming consecutive means "adjacent after sorting".** It means consecutive *integers*;
`[1, 3]` is not a run of two.

## Cost

O(d log d + d * size) where `d` is the number of distinct values, O(d) space.
