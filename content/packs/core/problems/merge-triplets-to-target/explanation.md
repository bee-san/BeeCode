## The insight

Merging takes maxima, so no position ever decreases. Two consequences:

**A triple with any entry above the target's is unusable.** Merge it in and that position
overshoots permanently. Discard it outright.

**Among usable triples, only exact matches matter.** For each of the three positions, some
usable triple must already equal the target there — otherwise the maximum across every usable
triple falls short at that position and no merge can lift it.

So: sweep once, ignore unusable triples, and record which positions are hit exactly.

```python
matched = [False, False, False]
for triplet in triplets:
    if all(triplet[i] <= target[i] for i in range(3)):
        for i in range(3):
            if triplet[i] == target[i]:
                matched[i] = True
return all(matched)
```

## Why no merge order matters

The position-wise maximum is associative, commutative and idempotent, so any set of triples
merges to the same result regardless of order or repetition. Merging *all* usable triples is
therefore the strongest thing you can reach without overshooting, and the three flags are just
asking whether that result equals the target. The apparent search over merge sequences
collapses entirely.

## Why the filter comes first

A triple can match the target at one position and exceed it at another —
`[3, 4, 5]` against `[3, 2, 5]` matches at positions 0 and 2. Recording those matches before
checking usability would wrongly return `True`, which is exactly what the second example is
for.

## Pitfalls

**Recording matches before filtering.** Accepts unreachable targets.

**Requiring one triple to match all three positions.** Different triples may cover different
positions; that is the point of merging.

**Trying merge sequences.** Exponential, and unnecessary given idempotence.

**Allowing an entry above the target because another position is short.** The maximum never
decreases; there is no compensation.

## Cost

O(n) time, O(1) space.
